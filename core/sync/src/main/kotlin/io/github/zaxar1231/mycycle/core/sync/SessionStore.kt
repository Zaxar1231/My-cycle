package io.github.zaxar1231.mycycle.core.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Wall-clock millis when the access token expires. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

/**
 * The sign-in session, encrypted with an AES key that never leaves the Android Keystore and
 * kept in no-backup storage, so tokens are not readable from copied files or backups.
 */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE))

    @Synchronized
    fun read(): Session? = runCatching {
        if (!file.baseFile.exists()) return null
        val stored = file.readFully()
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES)) }
        val json = JSONObject(String(cipher.doFinal(stored, IV_BYTES, stored.size - IV_BYTES), Charsets.UTF_8))
        Session(
            accessToken = json.getString("access"),
            refreshToken = json.getString("refresh"),
            expiresAt = json.getLong("expires"),
            userId = json.getString("user"),
            email = json.getString("email"),
        )
    }.getOrNull()

    @Synchronized
    fun write(session: Session) {
        val json = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("expires", session.expiresAt)
            .put("user", session.userId)
            .put("email", session.email)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val stored = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
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
    fun clear() {
        file.delete()
    }

    private fun key(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: run {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val FILE = "session.bin"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "twilight_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val AES_KEY_BITS = 256
    }
}
