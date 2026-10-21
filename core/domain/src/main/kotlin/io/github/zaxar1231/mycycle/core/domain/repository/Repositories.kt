package io.github.zaxar1231.mycycle.core.domain.repository

import io.github.zaxar1231.mycycle.core.domain.model.Affirmation
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.UserSettings
import io.github.zaxar1231.mycycle.core.domain.security.PinCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Runs several repository writes as one database transaction: all of them apply, or none. */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

interface CycleRepository {
    fun observeCycles(): Flow<List<Cycle>>

    /**
     * One-shot read, ordered by start date. The Room implementation queries directly, so it is safe
     * inside [TransactionRunner.inTransaction]; this default is only for test fakes.
     */
    suspend fun cycles(): List<Cycle> = observeCycles().first()
    /** Inserts or updates the cycle with the same start date and returns its id. */
    suspend fun upsert(cycle: Cycle): Long
    suspend fun delete(cycle: Cycle)
}

interface DayLogRepository {
    fun observe(date: LocalDate): Flow<DayLog?>

    /** One-shot reads. The Room implementation is safe inside [TransactionRunner.inTransaction]. */
    suspend fun get(date: LocalDate): DayLog? = observe(date).first()
    suspend fun range(from: LocalDate, to: LocalDate): List<DayLog> = observeRange(from, to).first()
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayLog>>

    /** Every log, oldest first. */
    fun observeAll(): Flow<List<DayLog>>
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

/**
 * The app PIN. Only a keyed hash of it is stored, and the key never leaves the Android Keystore,
 * so the PIN cannot be brute-forced from copied files.
 */
interface PinRepository {
    /** Read synchronously at start-up, so the very first frame is already locked. */
    val hasPin: StateFlow<Boolean>
    suspend fun setPin(pin: String)
    suspend fun check(pin: String): PinCheck

    /** Wrong-attempt freeze still in force, if any. */
    suspend fun lockedUntil(): Long?

    /** After another proof of identity, such as biometrics. */
    suspend fun resetFailures()
    suspend fun clear()
}

/** Favourite affirmations, stored on the device. */
interface AffirmationRepository {
    fun observeFavorites(): Flow<List<Affirmation>>
    fun observeIsFavorite(text: String): Flow<Boolean>
    suspend fun setFavorite(text: String, favorite: Boolean)
}

interface PhaseContentRepository {
    /** Content in the requested language, falling back to Russian. */
    suspend fun content(phase: CyclePhase, language: String): PhaseContent
}
