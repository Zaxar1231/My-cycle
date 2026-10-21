package io.github.zaxar1231.mycycle.feature.partner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerPermission
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerRepository
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerResult
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PartnerFormState(val busy: Boolean = false, val error: PartnerError? = null)

@HiltViewModel
class PartnerViewModel @Inject constructor(
    private val partners: PartnerRepository,
    private val inbox: PartnerInviteInbox,
) : ViewModel() {
    val state: StateFlow<PartnerState> = partners.state
    val incomingCode: StateFlow<String?> = inbox.code

    private val _form = MutableStateFlow(PartnerFormState())
    val form: StateFlow<PartnerFormState> = _form.asStateFlow()

    fun invite() = perform { partners.createInvite() }

    fun accept(code: String) = perform { partners.acceptInvite(code) }

    fun unlink() = perform { partners.unlink() }

    fun cancelInvite() {
        viewModelScope.launch { partners.cancelInvite() }
    }

    fun setPermission(permission: PartnerPermission, granted: Boolean) {
        val current = (state.value as? PartnerState.Sharing)?.permissions ?: return
        viewModelScope.launch { partners.setPermissions(if (granted) current + permission else current - permission) }
    }

    fun setNotify(enabled: Boolean) {
        viewModelScope.launch { partners.setNotify(enabled) }
    }

    /** A quiet check, used while an invitation waits: errors are not shown. */
    suspend fun poll() {
        partners.refresh()
    }

    fun consumeIncoming() = inbox.consume()

    private fun perform(action: suspend () -> PartnerResult) {
        if (_form.value.busy) return
        _form.value = PartnerFormState(busy = true)
        viewModelScope.launch {
            val result = action()
            _form.value = PartnerFormState(error = (result as? PartnerResult.Failed)?.error)
        }
    }
}
