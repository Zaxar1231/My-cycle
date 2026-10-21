package io.github.zaxar1231.mycycle.core.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The backup file: a header, a random salt and IV, and AES-256-GCM ciphertext with a key derived
 * from the password (PBKDF2-HMAC-SHA256). The whole header is authenticated too.
 *
 * - `TCB2` + iterations (4 bytes) + salt + IV + ciphertext; the header is the AAD.
 * - `TCB1` + salt + IV + ciphertext with 310 000 iterations and the magic as AAD: still read.
 */
internal object BackupCrypto {
    private val MAGIC_V1 = "TCB1".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "TCB2".toByteArray(Charsets.US_ASCII)
    private const val MAGIC_BYTES = 4
    private const val ITERATION_BYTES = 4
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS_V1 = 310_000
    const val ITERATIONS = 600_000

    /** Far above [ITERATIONS], so a damaged header cannot make opening run for hours. */
    private const val MAX_ITERATIONS = 3_000_000
    private val random = SecureRandom()

    /** Whether [start] looks like the beginning of a backup file. */
    fun hasMagic(start: ByteArray): Boolean {
        if (start.size < MAGIC_BYTES) return false
        val magic = start.copyOfRange(0, MAGIC_BYTES)
        return magic.contentEquals(MAGIC_V1) || magic.contentEquals(MAGIC_V2)
    }

    fun seal(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val header = MAGIC_V2 + ByteBuffer.allocate(ITERATION_BYTES).putInt(iterations).array() + salt + iv
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header)
        }
        return header + cipher.doFinal(plain)
    }

    sealed interface Opened {
        class Plain(val bytes: ByteArray) : Opened
        data object WrongPassword : Opened
        data object NotABackup : Opened
    }

    fun open(file: ByteArray, password: CharArray): Opened {
        if (!hasMagic(file)) return Opened.NotABackup
        val v2 = file.copyOfRange(0, MAGIC_BYTES).contentEquals(MAGIC_V2)
        val iterationsEnd = if (v2) MAGIC_BYTES + ITERATION_BYTES else MAGIC_BYTES
        val header = iterationsEnd + SALT_BYTES + IV_BYTES
        if (file.size <= header) return Opened.NotABackup
        val iterations = if (v2) ByteBuffer.wrap(file, MAGIC_BYTES, ITERATION_BYTES).int else ITERATIONS_V1
        if (iterations !in 1..MAX_ITERATIONS) return Opened.NotABackup
        val salt = file.copyOfRange(iterationsEnd, iterationsEnd + SALT_BYTES)
        val iv = file.copyOfRange(iterationsEnd + SALT_BYTES, header)
        val aad = if (v2) file.copyOfRange(0, header) else MAGIC_V1
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(aad)
            }
            Opened.Plain(cipher.doFinal(file, header, file.size - header))
        } catch (e: AEADBadTagException) {
            // GCM cannot tell a wrong password from a damaged file; a wrong password is far likelier.
            Opened.WrongPassword
        } catch (e: GeneralSecurityException) {
            Opened.NotABackup
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            // SecretKeySpec keeps its own copy.
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
