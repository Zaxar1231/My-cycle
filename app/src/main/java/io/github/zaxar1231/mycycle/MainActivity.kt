package io.github.zaxar1231.mycycle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.zaxar1231.mycycle.core.ui.theme.TwilightTheme
import io.github.zaxar1231.mycycle.core.update.UpdateInstaller
import io.github.zaxar1231.mycycle.core.update.UpdateViewModel
import io.github.zaxar1231.mycycle.core.update.UpdateWorker
import io.github.zaxar1231.mycycle.core.update.ui.UpdateDialog
import io.github.zaxar1231.mycycle.core.update.ui.UpdatesCard
import io.github.zaxar1231.mycycle.navigation.TwilightAppRoot

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val updates: UpdateViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        if (intent.getBooleanExtra(UpdateWorker.EXTRA_CHECK_UPDATE, false)) updates.check() else updates.autoCheckOnce()

        setContent {
            TwilightTheme {
                val updateState = updates.state.collectAsStateWithLifecycle().value
                val autoCheck = updates.autoCheck.collectAsStateWithLifecycle().value
                TwilightAppRoot(
                    updatesSection = {
                        UpdatesCard(
                            state = updateState,
                            currentVersion = updates.currentVersion,
                            autoCheck = autoCheck,
                            onCheck = { updates.check() },
                            onAutoCheckChange = updates::setAutoCheck,
                        )
                    },
                )
                UpdateDialog(
                    state = updateState,
                    onDownload = updates::download,
                    onOpenInstallSettings = { startActivity(UpdateInstaller.installPermissionIntent(this)) },
                    onDismiss = updates::dismiss,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(UpdateWorker.EXTRA_CHECK_UPDATE, false)) updates.check()
    }

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
