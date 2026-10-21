package io.github.zaxar1231.mycycle.core.sync.partner

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of what is shared with the partner: AES-256-GCM with the key from the
 * invitation. The link id is bound in as associated data, so a payload cannot be moved to another
 * link. Every payload is padded to the same size first, so its length does not tell the server
 * the phase or how much was logged. The server stores only the result.
 */
internal object PartnerCrypto {
    private const val VERSION: Byte = 2

    /** Plain bytes are padded to this size: a 2-byte length, the data, then zeros. */
    const val PADDED_BYTES = 2048
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BYTES = 32
    private const val LENGTH_BYTES = 2
    private const val BYTE_MASK = 0xFF
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    fun seal(key: ByteArray, linkId: String, plain: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad(linkId))
        }
        return Base64.getEncoder().encodeToString(byteArrayOf(VERSION) + iv + cipher.doFinal(pad(plain)))
    }

    /** The plain bytes, or null when the payload is damaged, from another link or for another key. */
    fun open(key: ByteArray, linkId: String, sealed: String): ByteArray? = runCatching {
        val bytes = Base64.getDecoder().decode(sealed)
        if (bytes.size <= 1 + IV_BYTES || bytes[0] != VERSION) return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes, 1, IV_BYTES))
            updateAAD(aad(linkId))
        }
        unpad(cipher.doFinal(bytes, 1 + IV_BYTES, bytes.size - 1 - IV_BYTES))
    }.getOrNull()

    private fun pad(plain: ByteArray): ByteArray {
        require(plain.size <= PADDED_BYTES - LENGTH_BYTES) { "snapshot too large" }
        return ByteArray(PADDED_BYTES).also { out ->
            out[0] = (plain.size shr Byte.SIZE_BITS).toByte()
            out[1] = plain.size.toByte()
            plain.copyInto(out, LENGTH_BYTES)
        }
    }

    private fun unpad(padded: ByteArray): ByteArray? {
        if (padded.size != PADDED_BYTES) return null
        val length = ((padded[0].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (padded[1].toInt() and BYTE_MASK)
        if (length > PADDED_BYTES - LENGTH_BYTES) return null
        return padded.copyOfRange(LENGTH_BYTES, LENGTH_BYTES + length)
    }

    private fun aad(linkId: String) = "twilight-partner:$linkId".toByteArray(Charsets.UTF_8)
}
