package io.github.zaxar1231.mycycle.feature.partner

import io.github.zaxar1231.mycycle.core.domain.partner.InviteCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An invitation that arrived as a link. It only fills in the code field; joining still takes a tap,
 * so a link alone can never connect anyone.
 */
@Singleton
class PartnerInviteInbox @Inject constructor() {
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    /** Keeps the code from [link] if it holds a valid one. */
    fun offer(link: String) {
        InviteCode.parse(link)?.let { _code.value = InviteCode.format(it) }
    }

    fun consume() {
        _code.value = null
    }
}
