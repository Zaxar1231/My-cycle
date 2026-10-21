package io.github.zaxar1231.mycycle.core.domain.partner

import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID

/** An invitation: the link id the server knows and the encryption key it never sees. */
class Invite(val linkId: UUID, val key: ByteArray) {
    init {
        require(key.size == InviteCode.KEY_BYTES) { "the key must be ${InviteCode.KEY_BYTES} bytes" }
    }
}

/**
 * The invitation as text: a prefix and the link id with the key in URL-safe Base64, so it fits a
 * QR code, a message and a link. [parse] finds the code anywhere in a pasted message or link.
 */
object InviteCode {
    const val KEY_BYTES = 32
    private const val PREFIX = "TC1"
    private const val ID_BYTES = 16
    private const val ENCODED_LENGTH = 64 // (16 + 32) bytes in Base64 without padding
    private val CODE = Regex("""$PREFIX([A-Za-z0-9_-]{$ENCODED_LENGTH})(?![A-Za-z0-9_-])""")

    /** The scheme of the link that opens the app with the code filled in. */
    const val LINK_PREFIX = "twilightcycle://partner?code="

    fun format(invite: Invite): String {
        val bytes = ByteBuffer.allocate(ID_BYTES + KEY_BYTES)
            .putLong(invite.linkId.mostSignificantBits)
            .putLong(invite.linkId.leastSignificantBits)
            .put(invite.key)
            .array()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun link(invite: Invite) = LINK_PREFIX + format(invite)

    fun parse(text: String): Invite? {
        val encoded = CODE.find(text)?.groupValues?.get(1) ?: return null
        val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull() ?: return null
        if (bytes.size != ID_BYTES + KEY_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes)
        val id = UUID(buffer.long, buffer.long)
        val key = ByteArray(KEY_BYTES).also { buffer.get(it) }
        return Invite(id, key)
    }
}
