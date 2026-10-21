package io.github.zaxar1231.mycycle.core.sync.partner

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerPermission
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerRepository
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerResult
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerShare
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerSnapshot
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerState
import io.github.zaxar1231.mycycle.core.domain.partner.SharedCycle
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the partner up to date. While the app runs, every change to the cycle, today's log or the
 * permissions is shared a moment later; the background worker covers the time the app is closed
 * and reminds the partner before a period.
 */
@Singleton
class PartnerSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val partners: PartnerRepository,
    private val observePrediction: ObserveCyclePredictionUseCase,
    private val dayLogs: DayLogRepository,
    private val dates: DateProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            partners.state.map { it is PartnerState.Sharing || it is PartnerState.Following }.distinctUntilChanged().collect { linked ->
                if (linked) PartnerWorker.schedule(context) else PartnerWorker.cancel(context)
            }
        }
        scope.launch {
            partners.state.map { (it as? PartnerState.Sharing)?.permissions }.distinctUntilChanged().collectLatest { permissions ->
                if (permissions == null) return@collectLatest
                snapshots(permissions).distinctUntilChanged().debounce(SETTLE_MILLIS).collect { partners.share(it) }
            }
        }
        // Notices an accepted invitation or a link the other side removed while the app was closed.
        scope.launch { partners.refresh() }
    }

    /** One background pass; false asks the worker to retry later (no network). */
    suspend fun runOnce(): Boolean {
        val refreshed = partners.refresh()
        if (refreshed is PartnerResult.Failed && refreshed.error == PartnerError.NETWORK) return false
        return when (val state = partners.state.value) {
            is PartnerState.Sharing -> {
                val today = dates.today()
                val result = partners.share(snapshotOn(today, state.permissions))
                !(result is PartnerResult.Failed && result.error == PartnerError.NETWORK)
            }
            is PartnerState.Following -> remindIfDue(state.notify)
            else -> true
        }
    }

    private suspend fun remindIfDue(notify: Boolean): Boolean {
        val shared = when (val result = partners.fetchShared()) {
            is SharedCycle.Shared -> result.snapshot
            is SharedCycle.Failed -> return result.error != PartnerError.NETWORK
            else -> return true
        }
        val today = dates.today()
        val due = PartnerShare.noticeFor(shared, today, partners.lastNotice()) ?: return true
        if (notify) PartnerNotifications.periodSoon(context, PartnerShare.view(shared, today).daysUntilPeriod ?: 0)
        partners.setLastNotice(due)
        return true
    }

    private suspend fun snapshotOn(today: LocalDate, permissions: Set<PartnerPermission>): PartnerSnapshot =
        PartnerShare.snapshot(observePrediction(today).first(), dayLogs.get(today), permissions, today)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun snapshots(permissions: Set<PartnerPermission>): Flow<PartnerSnapshot> = todays().flatMapLatest { today ->
        combine(observePrediction(today), dayLogs.observe(today)) { prediction, log ->
            PartnerShare.snapshot(prediction, log, permissions, today)
        }
    }

    /** Today's date, again after midnight. */
    private fun todays(): Flow<LocalDate> = flow {
        while (true) {
            emit(dates.today())
            delay(DATE_CHECK_MILLIS)
        }
    }.distinctUntilChanged()

    private companion object {
        /** Several quick edits in a row go out as one upload. */
        const val SETTLE_MILLIS = 3_000L
        const val DATE_CHECK_MILLIS = 15 * 60 * 1000L
    }
}
