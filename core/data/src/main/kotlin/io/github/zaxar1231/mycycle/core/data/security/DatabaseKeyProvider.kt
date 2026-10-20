package io.github.zaxar1231.mycycle.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.data.db.TwilightDatabase
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccessStatus
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase for the SQLCipher database. A random passphrase is generated once, encrypted with an
 * AES key that never leaves the Android Keystore, and stored atomically in no-backup storage.
 *
 * If the key is lost (Keystore reset, files copied to another device) the database cannot be
 * read any more. That is reported as [DataAccessStatus.KEY_LOST] instead of crashing, and only
 * [reset] — after the user agreed — deletes the unreadable data.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(@ApplicationContext private val context: Context) {
    private val keyFile = AtomicFile(File(context.noBackupFilesDir, KEY_FILE))
    private var cached: ByteArray? = null

    /** Opens or creates the key. Called off the main thread before the database is first used. */
    @Synchronized
    fun prepare(): DataAccessStatus {
        if (cached != null) return DataAccessStatus.READY
        val file = keyFile.baseFile
        val databaseExists = context.getDatabasePath(TwilightDatabase.NAME).exists()
        cached = when {
            file.exists() -> runCatching { decrypt(keyFile.readFully()) }.getOrNull() ?: return DataAccessStatus.KEY_LOST
            databaseExists -> return DataAccessStatus.KEY_LOST
            else -> createPassphrase()
        }
        return DataAccessStatus.READY
    }

    /** Only valid after [prepare] returned [DataAccessStatus.READY]. */
    @Synchronized
    fun passphrase(): ByteArray {
        if (prepare() != DataAccessStatus.READY) throw IllegalStateException("database key is not available")
        return requireNotNull(cached).copyOf()
    }

    /** Deletes the unreadable database and its key, so the app can start from scratch. */
    @Synchronized
    fun reset() {
        cached = null
        context.deleteDatabase(TwilightDatabase.NAME)
        keyFile.delete()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun createPassphrase(): ByteArray {
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKeystoreKey()) }
        val stored = byteArrayOf(FORMAT_VERSION) + cipher.iv + cipher.doFinal(passphrase)
        val out = keyFile.startWrite()
        try {
            out.write(stored)
            keyFile.finishWrite(out)
        } catch (e: Exception) {
            keyFile.failWrite(out)
            throw e
        }
        return passphrase
    }

    private fun decrypt(stored: ByteArray): ByteArray {
        if (stored.size <= HEADER_BYTES || stored[0] != FORMAT_VERSION) throw GeneralSecurityException("bad key file")
        // Never create a new key here: a new key cannot decrypt the old passphrase.
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: throw GeneralSecurityException("key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, stored, 1, IV_BYTES))
        }
        return cipher.doFinal(stored, HEADER_BYTES, stored.size - HEADER_BYTES)
    }

    private fun createKeystoreKey(): SecretKey {
        keyStore().deleteEntry(KEY_ALIAS)
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "twilight_database_key"
        const val KEY_FILE = "database.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 1
        const val PASSPHRASE_BYTES = 32
        const val AES_KEY_BITS = 256
        const val IV_BYTES = 12
        const val HEADER_BYTES = 1 + IV_BYTES
        const val TAG_BITS = 128
    }
}
