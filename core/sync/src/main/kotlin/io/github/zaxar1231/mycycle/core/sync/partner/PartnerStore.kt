package io.github.zaxar1231.mycycle.core.sync.partner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.sync.SealedFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** This phone's side of one account's link. The shared key lives only here, in a [SealedFile]. */
@Serializable
data class StoredLink(
    /** The account the link belongs to; another account signed in on this phone does not use it. */
    val userId: String,
    val role: Role,
    val linkId: String,
    /** Base64 of the 32-byte AES key from the invitation. */
    val key: String,
    /** Wall-clock millis when a pending invitation runs out. */
    val expiresAt: Long = 0,
    val permissions: List<String> = emptyList(),
    val notify: Boolean = true,
    val lastNotice: String? = null,
    /** Owner: the account that accepted the invitation, for the verification code. */
    val partnerId: String? = null,
) {
    enum class Role { INVITING, CONFIRMING, SHARING, FOLLOWING }
}

/** Links by account id, so signing in with another account on this phone never overwrites a key. */
@Serializable
private data class StoredLinks(val links: Map<String, StoredLink> = emptyMap())

@Singleton
class PartnerStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = SealedFile(context, FILE, KEY_ALIAS)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): Map<String, StoredLink> = runCatching {
        json.decodeFromString(StoredLinks.serializer(), file.read() ?: return emptyMap()).links
    }.getOrDefault(emptyMap())

    fun write(links: Map<String, StoredLink>) {
        if (links.isEmpty()) file.delete() else file.write(json.encodeToString(StoredLinks.serializer(), StoredLinks(links)))
    }

    private companion object {
        const val FILE = "partner.bin"
        const val KEY_ALIAS = "twilight_partner_key"
    }
}
