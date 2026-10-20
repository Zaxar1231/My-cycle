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
import io.github.zaxar1231.mycycle.ui.HomeScreen
import io.github.zaxar1231.mycycle.ui.theme.MyCycleTheme
import io.github.zaxar1231.mycycle.update.UpdateInstaller
import io.github.zaxar1231.mycycle.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updates: UpdateViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        if (intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)) updates.check() else updates.autoCheckOnce()

        setContent {
            MyCycleTheme {
                HomeScreen(
                    state = updates.state.collectAsStateWithLifecycle().value,
                    autoCheck = updates.autoCheck.collectAsStateWithLifecycle().value,
                    onCheck = { updates.check() },
                    onAutoCheckChange = updates::setAutoCheck,
                    onDownload = updates::download,
                    onOpenInstallSettings = { startActivity(UpdateInstaller.installPermissionIntent(this)) },
                    onDismiss = updates::dismiss,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false)) updates.check()
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

    companion object {
        const val EXTRA_CHECK_UPDATE = "check_update"
    }
}
