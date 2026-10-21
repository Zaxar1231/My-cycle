package io.github.zaxar1231.mycycle.core.data.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The backup file: a header, a random salt and IV, and AES-256-GCM ciphertext with a key derived
 * from the password (PBKDF2-HMAC-SHA256). The header is authenticated too.
 */
internal object BackupCrypto {
    private val MAGIC = "TCB1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 310_000
    private val random = SecureRandom()

    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(MAGIC)
        }
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    sealed interface Opened {
        class Plain(val bytes: ByteArray) : Opened
        data object WrongPassword : Opened
        data object NotABackup : Opened
    }

    fun open(file: ByteArray, password: CharArray): Opened {
        val header = MAGIC.size + SALT_BYTES + IV_BYTES
        if (file.size <= header || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return Opened.NotABackup
        val salt = file.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = file.copyOfRange(MAGIC.size + SALT_BYTES, header)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(MAGIC)
            }
            Opened.Plain(cipher.doFinal(file, header, file.size - header))
        } catch (e: javax.crypto.AEADBadTagException) {
            // GCM cannot tell a wrong password from a damaged file; a wrong password is far likelier.
            Opened.WrongPassword
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
