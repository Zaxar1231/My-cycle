package io.github.zaxar1231.mycycle.feature.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PinRepository
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Redraws the widgets when the cycle, today's log, the pet or the PIN change while the app runs;
 * the system refreshes them hourly otherwise (see res/xml).
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy: these open the database, which must wait for the key check.
    private val observePrediction: dagger.Lazy<ObserveCyclePredictionUseCase>,
    private val dayLogs: dagger.Lazy<DayLogRepository>,
    private val pets: dagger.Lazy<PetRepository>,
    private val pin: PinRepository,
    private val dates: DateProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private val started = AtomicBoolean(false)

    /** Called once the database may be opened (after the key check). */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val todays = flow {
                while (true) {
                    emit(dates.today())
                    delay(DATE_CHECK_MILLIS)
                }
            }.distinctUntilChanged()
            todays.flatMapLatest { today ->
                combine(observePrediction.get()(today), dayLogs.get().observe(today), pets.get().observePet(), pin.hasPin) { a, b, c, d -> listOf(a, b, c, d) }
            }.distinctUntilChanged().debounce(SETTLE_MILLIS).collect { refreshNow() }
        }
        // A new or removed PIN changes what widgets may show: at once, without the settle delay.
        scope.launch { pin.hasPin.drop(1).collect { refreshNow() } }
    }

    suspend fun refreshNow() {
        try {
            CycleDayWidget().updateAll(context)
            CycleOverviewWidget().updateAll(context)
            PetWidget().updateAll(context)
            MoodWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A widget host that is gone; the next change tries again.
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val DATE_CHECK_MILLIS = 15 * 60 * 1000L
    }
}
