package io.github.zaxar1231.mycycle.feature.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerRepository
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerShare
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerSnapshot
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerState
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerView
import io.github.zaxar1231.mycycle.core.domain.partner.SharedCycle
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PartnerCycleUi {
    data object Loading : PartnerCycleUi
    data class Shown(val view: PartnerView, val snapshot: PartnerSnapshot) : PartnerCycleUi
    data object Waiting : PartnerCycleUi
    data object Ended : PartnerCycleUi
    data class Failed(val error: PartnerError) : PartnerCycleUi
}

/** The partner's side: the shared cycle, downloaded on open and kept only in memory. */
@HiltViewModel
class PartnerCycleViewModel @Inject constructor(
    private val partners: PartnerRepository,
    private val dates: DateProvider,
) : ViewModel() {
    val following: StateFlow<Boolean> = partners.state
        .map { it is PartnerState.Following }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, partners.state.value is PartnerState.Following)

    private val _cycle = MutableStateFlow<PartnerCycleUi>(PartnerCycleUi.Loading)
    val cycle: StateFlow<PartnerCycleUi> = _cycle.asStateFlow()
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            following.collect { linked -> if (linked) load() else _cycle.value = PartnerCycleUi.Ended }
        }
    }

    fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            if (_cycle.value !is PartnerCycleUi.Shown) _cycle.value = PartnerCycleUi.Loading
            _cycle.value = when (val result = partners.fetchShared()) {
                is SharedCycle.Shared -> PartnerCycleUi.Shown(PartnerShare.view(result.snapshot, dates.today()), result.snapshot)
                SharedCycle.Waiting -> PartnerCycleUi.Waiting
                SharedCycle.Ended -> PartnerCycleUi.Ended
                // A failed refresh keeps what is already on screen.
                is SharedCycle.Failed -> (_cycle.value as? PartnerCycleUi.Shown) ?: PartnerCycleUi.Failed(result.error)
            }
        }
    }
}

/** Opens settings when an invitation link arrives, so the code can be accepted there. */
@HiltViewModel
class PartnerInviteViewModel @Inject constructor(inbox: PartnerInviteInbox) : ViewModel() {
    val code: StateFlow<String?> = inbox.code

    /** The code settings were last opened for; kept across rotation so one link opens them once. */
    var handled: String? = null
}
