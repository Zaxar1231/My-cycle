package io.github.zaxar1231.mycycle.core.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object NoReleases : UpdateState
    data class Error(val message: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val release: ReleaseInfo, val apk: File) : UpdateState
    data class DownloadFailed(val release: ReleaseInfo, val message: String) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UpdatePrefs(app)
    val currentVersion: String = app.appVersionName()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.autoCheck)
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private var job: Job? = null
    private var autoChecked = false

    /** Called on app start; silent unless an update is found. */
    fun autoCheckOnce() {
        if (autoChecked || !prefs.autoCheck) return
        autoChecked = true
        check(silent = true)
    }

    fun check(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            if (!silent) _state.value = UpdateState.Checking
            _state.value = try {
                when (val r = UpdateChecker.check(currentVersion)) {
                    is CheckResult.Available -> UpdateState.Available(r.release)
                    CheckResult.UpToDate -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                    CheckResult.NoReleases -> if (silent) UpdateState.Idle else UpdateState.NoReleases
                }
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                val apk = UpdateInstaller.download(getApplication(), release) { p ->
                    _state.value = UpdateState.Downloading(release, p)
                }
                install(release, apk)
            } catch (e: Exception) {
                _state.value = UpdateState.DownloadFailed(release, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun install(release: ReleaseInfo, apk: File) {
        val app = getApplication<Application>()
        if (UpdateInstaller.canInstall(app)) {
            UpdateInstaller.install(app, apk)
            _state.value = UpdateState.Available(release)
        } else {
            _state.value = UpdateState.NeedsPermission(release, apk)
        }
    }

    /** Called when the user returns from the "install unknown apps" settings screen. */
    fun onResume() {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && UpdateInstaller.canInstall(getApplication())) install(s.release, s.apk)
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun setAutoCheck(enabled: Boolean) {
        prefs.autoCheck = enabled
        _autoCheck.value = enabled
        if (enabled) UpdateWorker.schedule(getApplication()) else UpdateWorker.cancel(getApplication())
    }
}
