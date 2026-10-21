package io.github.zaxar1231.mycycle.feature.widgets

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.pet.PetRules
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccess
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccessStatus
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PinRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.usecase.ObserveCyclePredictionUseCase
import io.github.zaxar1231.mycycle.core.domain.usecase.RewardPetUseCase
import io.github.zaxar1231.mycycle.core.domain.usecase.SaveDayLogUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependencies {
    fun dataAccess(): DataAccess
    fun pin(): PinRepository
    fun dates(): DateProvider
    fun prediction(): ObserveCyclePredictionUseCase
    fun dayLogs(): DayLogRepository
    fun pets(): PetRepository
    fun settings(): SettingsRepository
    fun saveDayLog(): SaveDayLogUseCase
    fun rewardPet(): RewardPetUseCase
    fun transactions(): TransactionRunner
    fun refresher(): WidgetRefresher
}

internal fun Context.widgetDependencies(): WidgetDependencies =
    EntryPointAccessors.fromApplication(applicationContext, WidgetDependencies::class.java)

/** What the widgets show. */
sealed interface WidgetData {
    /** A PIN protects the app, so the home screen shows nothing personal. */
    data object Locked : WidgetData

    /** The data cannot be read yet (no data, or it was lost); the widget asks to open the app. */
    data object Unavailable : WidgetData

    data class Ready(
        val today: LocalDate,
        val prediction: CyclePrediction,
        val todayMoods: Set<Mood>,
        val pet: Pet?,
        val petMood: PetMood,
    ) : WidgetData

    companion object {
        /**
         * The data, again whenever it changes: a widget session can outlive one update, so the
         * content follows this flow instead of a value read once (a new PIN hides it at once).
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun observe(context: Context): Flow<WidgetData> {
            val deps = context.widgetDependencies()
            return deps.pin().hasPin.flatMapLatest { locked ->
                if (locked) {
                    flowOf(Locked)
                } else {
                    flow {
                        if (deps.dataAccess().check() != DataAccessStatus.READY) {
                            emit(Unavailable)
                            return@flow
                        }
                        val today = deps.dates().today()
                        emitAll(
                            combine(deps.prediction()(today), deps.dayLogs().observe(today), deps.pets().observePet()) { prediction, log, pet ->
                                val moods = log?.moods.orEmpty()
                                Ready(today, prediction, moods, pet, PetRules.mood(prediction.today?.phase, moods, LocalTime.now().hour))
                            },
                        )
                    }
                }
            }.catch { emit(Unavailable) }
        }

        suspend fun load(context: Context): WidgetData {
            val deps = context.widgetDependencies()
            if (deps.pin().hasPin.value) return Locked
            return try {
                if (deps.dataAccess().check() != DataAccessStatus.READY) return Unavailable
                val today = deps.dates().today()
                val prediction = deps.prediction()(today).first()
                val moods = deps.dayLogs().get(today)?.moods.orEmpty()
                Ready(
                    today = today,
                    prediction = prediction,
                    todayMoods = moods,
                    pet = deps.pets().pet(),
                    petMood = PetRules.mood(prediction.today?.phase, moods, LocalTime.now().hour),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Unavailable
            }
        }
    }
}
