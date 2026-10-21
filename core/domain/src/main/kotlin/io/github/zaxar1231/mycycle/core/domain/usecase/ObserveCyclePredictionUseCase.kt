package io.github.zaxar1231.mycycle.core.domain.usecase

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

/** The cycle prediction as of [today], updated whenever cycles, settings or temperatures change. */
class ObserveCyclePredictionUseCase @Inject constructor(
    private val cycles: CycleRepository,
    private val settings: SettingsRepository,
    private val dayLogs: DayLogRepository,
    private val predictor: CyclePredictor,
) {
    operator fun invoke(today: LocalDate): Flow<CyclePrediction> = combine(
        cycles.observeCycles(),
        settings.settings,
        dayLogs.observeRange(today.minusDays(TEMPERATURE_LOOKBACK_DAYS), today),
    ) { cycleList, s, logs ->
        predictor.predict(
            CyclePredictor.Input(
                cycles = cycleList,
                usualCycleLength = s.usualCycleLength,
                usualPeriodLength = s.usualPeriodLength,
                usualLengthsEstimated = s.usualLengthsEstimated,
                temperatures = logs.mapNotNull { log -> log.temperatureCelsius?.let { log.date to it } }.toMap(),
                today = today,
            ),
        )
    }

    private companion object {
        /** Enough days to cover the current cycle when looking for a temperature shift. */
        const val TEMPERATURE_LOOKBACK_DAYS = 60L
    }
}
