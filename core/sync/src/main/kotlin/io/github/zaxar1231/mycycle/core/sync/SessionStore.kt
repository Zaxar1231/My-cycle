package io.github.zaxar1231.mycycle.core.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
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

/** The sign-in session, kept in a [SealedFile]. */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = SealedFile(context, FILE, KEY_ALIAS)

    fun read(): Session? = runCatching {
        val json = JSONObject(file.read() ?: return null)
        Session(
            accessToken = json.getString("access"),
            refreshToken = json.getString("refresh"),
            expiresAt = json.getLong("expires"),
            userId = json.getString("user"),
            email = json.getString("email"),
        )
    }.getOrNull()

    fun write(session: Session) {
        val json = JSONObject()
            .put("access", session.accessToken)
            .put("refresh", session.refreshToken)
            .put("expires", session.expiresAt)
            .put("user", session.userId)
            .put("email", session.email)
        file.write(json.toString())
    }

    fun clear() = file.delete()

    private companion object {
        const val FILE = "session.bin"
        const val KEY_ALIAS = "twilight_session_key"
    }
}
