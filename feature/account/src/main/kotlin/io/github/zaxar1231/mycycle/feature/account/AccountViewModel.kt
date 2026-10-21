package io.github.zaxar1231.mycycle.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zaxar1231.mycycle.core.domain.account.Account
import io.github.zaxar1231.mycycle.core.domain.account.AccountRepository
import io.github.zaxar1231.mycycle.core.domain.account.AccountRules
import io.github.zaxar1231.mycycle.core.domain.account.AuthResult
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the form shows under the fields after the last action. */
enum class AccountNotice { CONFIRM_EMAIL, RESET_SENT, BAD_EMAIL, SHORT_PASSWORD }

data class AccountFormState(
    val busy: Boolean = false,
    val result: AuthResult? = null,
    val notice: AccountNotice? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val available: Boolean = accounts.isAvailable
    val account: StateFlow<Account?> = accounts.account
    private val _form = MutableStateFlow(AccountFormState())
    val form: StateFlow<AccountFormState> = _form.asStateFlow()

    fun signIn(email: String, password: String) = submit(email, password) { accounts.signIn(email, password) }

    fun signUp(email: String, password: String) = submit(email, password) { accounts.signUp(email, password) }

    fun resetPassword(email: String) {
        if (!AccountRules.isValidEmail(email)) {
            _form.value = AccountFormState(notice = AccountNotice.BAD_EMAIL)
            return
        }
        perform { accounts.resetPassword(email) }
    }

    fun signOut() {
        viewModelScope.launch {
            accounts.signOut()
            remember(null)
        }
    }

    private fun submit(email: String, password: String, action: suspend () -> AuthResult) {
        val notice = when {
            !AccountRules.isValidEmail(email) -> AccountNotice.BAD_EMAIL
            !AccountRules.isValidPassword(password) -> AccountNotice.SHORT_PASSWORD
            else -> null
        }
        if (notice != null) {
            _form.value = AccountFormState(notice = notice)
            return
        }
        perform(action)
    }

    private fun perform(action: suspend () -> AuthResult) {
        if (_form.value.busy) return
        _form.update { it.copy(busy = true, result = null, notice = null) }
        viewModelScope.launch {
            val result = action()
            if (result is AuthResult.SignedIn) remember(result.account.userId)
            _form.value = AccountFormState(
                result = result,
                notice = when (result) {
                    AuthResult.ConfirmEmail -> AccountNotice.CONFIRM_EMAIL
                    AuthResult.ResetSent -> AccountNotice.RESET_SENT
                    else -> null
                },
            )
        }
    }

    /** The profile keeps the account id, so later stages know the data has an owner online. */
    private suspend fun remember(userId: String?) {
        try {
            settings.update { it.copy(accountId = userId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The account works without it; the id is written again on the next sign-in.
        }
    }
}
