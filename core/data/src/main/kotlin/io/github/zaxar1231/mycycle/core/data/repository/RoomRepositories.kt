package io.github.zaxar1231.mycycle.core.data.repository

import androidx.room.withTransaction
import io.github.zaxar1231.mycycle.core.data.db.AffirmationDao
import io.github.zaxar1231.mycycle.core.data.db.CycleDao
import io.github.zaxar1231.mycycle.core.data.db.DayLogDao
import io.github.zaxar1231.mycycle.core.data.db.MedicationDao
import io.github.zaxar1231.mycycle.core.data.db.PetDao
import io.github.zaxar1231.mycycle.core.data.db.TwilightDatabase
import io.github.zaxar1231.mycycle.core.data.db.toDomain
import io.github.zaxar1231.mycycle.core.data.db.toEntity
import io.github.zaxar1231.mycycle.core.domain.model.Affirmation
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.repository.AffirmationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class RoomCycleRepository @Inject constructor(private val dao: CycleDao) : CycleRepository {
    override fun observeCycles(): Flow<List<Cycle>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun cycles(): List<Cycle> = dao.all().map { it.toDomain() }
    override suspend fun upsert(cycle: Cycle): Long = dao.save(cycle.toEntity())
    override suspend fun delete(cycle: Cycle) = dao.delete(cycle.toEntity())
}

class RoomDayLogRepository @Inject constructor(private val dao: DayLogDao) : DayLogRepository {
    override fun observe(date: LocalDate): Flow<DayLog?> = dao.observe(date).map { it?.toDomain() }
    override suspend fun get(date: LocalDate): DayLog? = dao.get(date)?.toDomain()
    override suspend fun range(from: LocalDate, to: LocalDate): List<DayLog> = dao.range(from, to).map { it.toDomain() }
    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayLog>> =
        dao.observeRange(from, to).map { list -> list.map { it.toDomain() } }
    override fun observeAll(): Flow<List<DayLog>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun save(log: DayLog) = dao.upsert(log.toEntity())
    override suspend fun delete(date: LocalDate) = dao.delete(date)
}

class RoomMedicationRepository @Inject constructor(private val dao: MedicationDao) : MedicationRepository {
    override fun observeMedications(): Flow<List<Medication>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(medication: Medication): Long = dao.upsert(medication.toEntity())
    override suspend fun delete(medication: Medication) = dao.delete(medication.toEntity())
}

class RoomPetRepository @Inject constructor(private val dao: PetDao) : PetRepository {
    override fun observePet(): Flow<Pet?> = dao.observe().map { it?.toDomain() }
    override suspend fun adopt(pet: Pet) = dao.insert(pet.toEntity())
    override suspend fun update(pet: Pet) = dao.update(pet.toEntity())
}

class RoomTransactionRunner @Inject constructor(private val db: TwilightDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}

class RoomAffirmationRepository @Inject constructor(private val dao: AffirmationDao) : AffirmationRepository {
    override fun observeFavorites(): Flow<List<Affirmation>> =
        dao.observeFavorites().map { list -> list.map { Affirmation(it.id, it.text, it.isFavorite) } }
    override fun observeIsFavorite(text: String): Flow<Boolean> = dao.observeIsFavorite(text)
    override suspend fun setFavorite(text: String, favorite: Boolean) = dao.setFavorite(text, favorite)
}
