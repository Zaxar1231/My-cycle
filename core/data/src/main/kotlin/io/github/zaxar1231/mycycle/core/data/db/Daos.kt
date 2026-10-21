package io.github.zaxar1231.mycycle.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CycleDao {
    @Query("SELECT * FROM cycles ORDER BY start_date")
    fun observeAll(): Flow<List<CycleEntity>>

    @Query("SELECT * FROM cycles ORDER BY start_date")
    suspend fun all(): List<CycleEntity>

    @Query("SELECT id FROM cycles WHERE start_date = :startDate")
    suspend fun idForStartDate(startDate: LocalDate): Long?

    @Insert
    suspend fun insert(cycle: CycleEntity): Long

    @Update
    suspend fun update(cycle: CycleEntity)

    /** A cycle is identified by its start date: saving the same start again updates that cycle. */
    @Transaction
    suspend fun save(cycle: CycleEntity): Long {
        val id = if (cycle.id != 0L) cycle.id else idForStartDate(cycle.startDate)
        return if (id == null) insert(cycle) else id.also { update(cycle.copy(id = it)) }
    }

    @Delete
    suspend fun delete(cycle: CycleEntity)
}

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_logs WHERE date = :date")
    fun observe(date: LocalDate): Flow<DayLogEntity?>

    @Query("SELECT * FROM day_logs WHERE date = :date")
    suspend fun get(date: LocalDate): DayLogEntity?

    @Query("SELECT * FROM day_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayLogEntity>>

    @Upsert
    suspend fun upsert(log: DayLogEntity)

    @Query("DELETE FROM day_logs WHERE date = :date")
    suspend fun delete(date: LocalDate)
}

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY name")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Upsert
    suspend fun upsert(medication: MedicationEntity): Long

    @Delete
    suspend fun delete(medication: MedicationEntity)
}

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = ${PetEntity.SINGLE_PET_ID}")
    fun observe(): Flow<PetEntity?>

    /** IGNORE: a second pet never replaces the first one, and retrying onboarding is harmless. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pet: PetEntity)

    @Update
    suspend fun update(pet: PetEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = ${ProfileEntity.SINGLE_PROFILE_ID}")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}
