package io.github.zaxar1231.mycycle

import android.app.Application
import io.github.zaxar1231.mycycle.update.UpdatePrefs
import io.github.zaxar1231.mycycle.update.UpdateWorker

class MyCycleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        if (UpdatePrefs(this).autoCheck) UpdateWorker.schedule(this)
    }
}
