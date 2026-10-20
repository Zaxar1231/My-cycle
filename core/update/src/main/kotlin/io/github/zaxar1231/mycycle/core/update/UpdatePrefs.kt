package io.github.zaxar1231.mycycle.core.update

import android.content.Context
import androidx.core.content.edit

class UpdatePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var autoCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO, value) }

    /** Last version we showed a background notification for, so it is shown once. */
    var notifiedVersion: String?
        get() = prefs.getString(KEY_NOTIFIED, null)
        set(value) = prefs.edit { putString(KEY_NOTIFIED, value) }

    private companion object {
        const val KEY_AUTO = "auto_check"
        const val KEY_NOTIFIED = "notified_version"
    }
}
