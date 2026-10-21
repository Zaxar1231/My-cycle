package io.github.zaxar1231.mycycle.core.sync.partner

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of what is shared with the partner: AES-256-GCM with the key from the
 * invitation. The link id is bound in as associated data, so a payload cannot be moved to another
 * link. The server stores only the result.
 */
internal object PartnerCrypto {
    private const val VERSION: Byte = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BYTES = 32
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    fun seal(key: ByteArray, linkId: String, plain: ByteArray): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad(linkId))
        }
        return Base64.getEncoder().encodeToString(byteArrayOf(VERSION) + iv + cipher.doFinal(plain))
    }

    /** The plain bytes, or null when the payload is damaged, from another link or for another key. */
    fun open(key: ByteArray, linkId: String, sealed: String): ByteArray? = runCatching {
        val bytes = Base64.getDecoder().decode(sealed)
        if (bytes.size <= 1 + IV_BYTES || bytes[0] != VERSION) return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes, 1, IV_BYTES))
            updateAAD(aad(linkId))
        }
        cipher.doFinal(bytes, 1 + IV_BYTES, bytes.size - 1 - IV_BYTES)
    }.getOrNull()

    private fun aad(linkId: String) = "twilight-partner:$linkId".toByteArray(Charsets.UTF_8)
}
