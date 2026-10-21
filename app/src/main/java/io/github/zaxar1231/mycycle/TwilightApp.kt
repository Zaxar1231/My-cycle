package io.github.zaxar1231.mycycle

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.zaxar1231.mycycle.core.sync.partner.PartnerNotifications
import io.github.zaxar1231.mycycle.core.sync.partner.PartnerSync
import io.github.zaxar1231.mycycle.core.update.UpdatePrefs
import io.github.zaxar1231.mycycle.core.update.UpdateWorker
import javax.inject.Inject

@HiltAndroidApp
class TwilightApp : Application() {
    @Inject lateinit var partnerSync: PartnerSync

    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        PartnerNotifications.createChannel(this)
        if (UpdatePrefs(this).autoCheck) UpdateWorker.schedule(this)
        partnerSync.start()
    }
}
