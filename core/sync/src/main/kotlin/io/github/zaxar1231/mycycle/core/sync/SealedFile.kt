package io.github.zaxar1231.mycycle.core.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A small file in no-backup storage, encrypted with an AES key that never leaves the Android
 * Keystore, so its contents are not readable from copied files or backups.
 */
class SealedFile(context: Context, name: String, private val keyAlias: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, name))

    /** The decrypted contents, or null when there is no file or it cannot be read. */
    @Synchronized
    fun read(): String? = runCatching {
        if (!file.baseFile.exists()) return null
        val stored = file.readFully()
        val key = keyStore().getKey(keyAlias, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES)) }
        String(cipher.doFinal(stored, IV_BYTES, stored.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    fun write(text: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val stored = cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try {
            out.write(stored)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    @Synchronized
    fun delete() {
        file.delete()
    }

    private fun key(): SecretKey = keyStore().getKey(keyAlias, null) as? SecretKey ?: run {
        val spec = KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val AES_KEY_BITS = 256
    }
}
