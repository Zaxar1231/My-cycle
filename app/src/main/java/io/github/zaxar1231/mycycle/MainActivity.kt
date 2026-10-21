package io.github.zaxar1231.mycycle

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.security.AutoLock
import io.github.zaxar1231.mycycle.core.ui.component.ThemePicker
import io.github.zaxar1231.mycycle.core.ui.theme.LocalDarkTheme
import io.github.zaxar1231.mycycle.core.ui.theme.TwilightTheme
import io.github.zaxar1231.mycycle.core.update.UpdateInstaller
import io.github.zaxar1231.mycycle.core.update.UpdateViewModel
import io.github.zaxar1231.mycycle.core.update.UpdateWorker
import io.github.zaxar1231.mycycle.core.update.ui.UpdateDialog
import io.github.zaxar1231.mycycle.core.update.ui.UpdatesCard
import io.github.zaxar1231.mycycle.feature.partner.PartnerInviteInbox
import io.github.zaxar1231.mycycle.feature.security.AppLockGate
import io.github.zaxar1231.mycycle.feature.security.AppLockViewModel
import io.github.zaxar1231.mycycle.navigation.TwilightAppRoot
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val updates: UpdateViewModel by viewModels()
    private val main: MainViewModel by viewModels()
    private val lock: AppLockViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    @Inject lateinit var partnerInvites: PartnerInviteInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        if (savedInstanceState == null) {
            if (intent.consumeCheckUpdateExtra()) updates.check() else updates.autoCheckOnce()
            intent.offerPartnerInvite()
        }

        setContent {
            val appState = main.state.collectAsStateWithLifecycle().value
            val settings = (appState as? AppState.Ready)?.settings
            TwilightTheme(settings?.theme ?: ThemeSettings()) {
                // Status and navigation bar icons follow the app's theme, not the system's:
                // AMOLED and Neon are dark even when the phone is in light mode.
                val dark = LocalDarkTheme.current
                LaunchedEffect(dark) {
                    val bars = if (dark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                    // Explicit styles turn the three-button bar's contrast scrim off; keep it.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = true
                }
                Surface(Modifier.fillMaxSize()) {
                    if (appState is AppState.DataLost) DataLostScreen(onStartOver = main::startOver)
                    if (settings != null) {
                        val locked = lock.locked.collectAsStateWithLifecycle().value
                        val hasPin = lock.hasPin.collectAsStateWithLifecycle().value
                        // With a PIN the recents preview must not show what the lock screen hides.
                        // The snapshot is taken on leaving, before the app locks, so it is turned off.
                        LaunchedEffect(hasPin) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!hasPin)
                        }
                        val hideInRecents = settings.security.hideInRecents
                        LaunchedEffect(hideInRecents) {
                            if (hideInRecents) {
                                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            } else {
                                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                            }
                        }
                        val updateState = updates.state.collectAsStateWithLifecycle().value
                        val autoCheck = updates.autoCheck.collectAsStateWithLifecycle().value
                        AppLockGate(lock, biometricUnlock = settings.security.biometricUnlock) {
                            TwilightAppRoot(
                                startWithOnboarding = !settings.onboardingCompleted,
                                appearanceSection = { ThemePicker(settings.theme, main::setTheme, onSeedChange = main::setSeedColor) },
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
                        }
                        // The update dialog would open above the lock screen, so it waits for the unlock.
                        if (!locked) {
                            UpdateDialog(
                                state = updateState,
                                onDownload = updates::download,
                                onOpenInstallSettings = { startActivity(UpdateInstaller.installPermissionIntent(this)) },
                                onDismiss = updates::dismiss,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.consumeCheckUpdateExtra()) updates.check()
        intent.offerPartnerInvite()
    }

    /** A partner invitation link only fills in the code; joining still needs a tap in settings. */
    private fun Intent.offerPartnerInvite() {
        if (action == Intent.ACTION_VIEW) dataString?.let(partnerInvites::offer)
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground(lockAfterMinutes())
    }

    override fun onStop() {
        super.onStop()
        lock.onBackground(isChangingConfigurations, lockAfterMinutes())
    }

    private fun lockAfterMinutes() =
        (main.state.value as? AppState.Ready)?.settings?.security?.lockAfterMinutes ?: AutoLock.DEFAULT_MINUTES

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }

    /** Reads and clears the flag, so a configuration change does not trigger another check. */
    private fun Intent.consumeCheckUpdateExtra(): Boolean =
        getBooleanExtra(UpdateWorker.EXTRA_CHECK_UPDATE, false).also { removeExtra(UpdateWorker.EXTRA_CHECK_UPDATE) }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
