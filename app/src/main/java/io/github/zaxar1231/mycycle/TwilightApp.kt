package io.github.zaxar1231.mycycle

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.zaxar1231.mycycle.core.update.UpdatePrefs
import io.github.zaxar1231.mycycle.core.update.UpdateWorker

@HiltAndroidApp
class TwilightApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        if (UpdatePrefs(this).autoCheck) UpdateWorker.schedule(this)
    }
}
