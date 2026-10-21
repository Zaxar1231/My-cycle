package io.github.zaxar1231.mycycle.core.sync.partner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.sync.SealedFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** This phone's side of the link. The shared key lives only here, in a [SealedFile]. */
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
) {
    enum class Role { INVITING, SHARING, FOLLOWING }
}

@Singleton
class PartnerStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = SealedFile(context, FILE, KEY_ALIAS)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(): StoredLink? = runCatching { json.decodeFromString(StoredLink.serializer(), file.read() ?: return null) }.getOrNull()

    fun write(link: StoredLink) = file.write(json.encodeToString(StoredLink.serializer(), link))

    fun clear() = file.delete()

    private companion object {
        const val FILE = "partner.bin"
        const val KEY_ALIAS = "twilight_partner_key"
    }
}
