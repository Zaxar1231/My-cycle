package io.github.zaxar1231.mycycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccess
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccessStatus
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState

    /** The encrypted data cannot be opened on this device; the user decides whether to start over. */
    data object DataLost : AppState

    data class Ready(val settings: UserSettings) : AppState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataAccess: DataAccess,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var settingsJob: Job? = null

    init {
        open()
    }

    fun setTheme(theme: ThemeSettings) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun startOver() {
        viewModelScope.launch {
            _state.value = AppState.Loading
            dataAccess.resetUnreadableData()
            open()
        }
    }

    // Nothing touches the database until the key check has passed.
    private fun open() {
        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            if (dataAccess.check() != DataAccessStatus.READY) {
                _state.value = AppState.DataLost
                return@launch
            }
            settingsRepository.settings.collect { _state.value = AppState.Ready(it) }
        }
    }
}
