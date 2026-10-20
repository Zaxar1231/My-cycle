package io.github.zaxar1231.mycycle.core.domain.repository

import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface CycleRepository {
    fun observeCycles(): Flow<List<Cycle>>
    suspend fun upsert(cycle: Cycle): Long
    suspend fun delete(cycle: Cycle)
}

interface DayLogRepository {
    fun observe(date: LocalDate): Flow<DayLog?>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayLog>>
    suspend fun save(log: DayLog)
    suspend fun delete(date: LocalDate)
}

interface MedicationRepository {
    fun observeMedications(): Flow<List<Medication>>
    suspend fun upsert(medication: Medication): Long
    suspend fun delete(medication: Medication)
}

interface PetRepository {
    fun observePet(): Flow<Pet?>

    /** Adopts the pet. Does nothing if a pet already exists: the choice is permanent. */
    suspend fun adopt(pet: Pet)
    suspend fun update(pet: Pet)
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun update(transform: (UserSettings) -> UserSettings)
    suspend fun setTheme(theme: ThemeSettings) = update { it.copy(theme = theme) }
}

enum class DataAccessStatus { READY, KEY_LOST }

/** Gate in front of the encrypted storage: it must be READY before any repository is used. */
interface DataAccess {
    suspend fun check(): DataAccessStatus

    /** Deletes data that can no longer be decrypted. Only after the user explicitly agreed. */
    suspend fun resetUnreadableData()
}
