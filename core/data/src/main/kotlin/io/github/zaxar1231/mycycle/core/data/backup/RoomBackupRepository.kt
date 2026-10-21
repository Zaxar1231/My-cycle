package io.github.zaxar1231.mycycle.core.data.backup

import androidx.room.withTransaction
import io.github.zaxar1231.mycycle.core.data.backup.BackupCodec.toEntity
import io.github.zaxar1231.mycycle.core.data.backup.BackupCodec.toRow
import io.github.zaxar1231.mycycle.core.data.db.AffirmationEntity
import io.github.zaxar1231.mycycle.core.data.db.ProfileEntity
import io.github.zaxar1231.mycycle.core.data.db.TwilightDatabase
import io.github.zaxar1231.mycycle.core.domain.model.UserRole
import io.github.zaxar1231.mycycle.core.domain.backup.BackupRepository
import io.github.zaxar1231.mycycle.core.domain.backup.RestoreResult
import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backups of the whole database, encrypted with the user's password. The cycle predictions are
 * not stored: they are worked out again from the restored cycles.
 */
@Singleton
class RoomBackupRepository @Inject constructor(private val db: TwilightDatabase) : BackupRepository {

    override suspend fun exportEncrypted(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val plain = BackupCodec.encode(snapshot())
        try {
            BackupCrypto.seal(plain, password)
        } finally {
            plain.fill(0)
        }
    }

    override fun looksLikeBackup(start: ByteArray) = BackupCrypto.hasMagic(start)

    override suspend fun restoreEncrypted(file: ByteArray, password: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        val content = when (val opened = BackupCrypto.open(file, password)) {
            BackupCrypto.Opened.NotABackup -> return@withContext RestoreResult.NOT_A_BACKUP
            BackupCrypto.Opened.WrongPassword -> return@withContext RestoreResult.WRONG_PASSWORD
            is BackupCrypto.Opened.Plain -> try {
                BackupCodec.decode(opened.bytes)
            } finally {
                opened.bytes.fill(0)
            } ?: return@withContext RestoreResult.NOT_A_BACKUP
        }
        try {
            replaceAll(content)
            RestoreResult.RESTORED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The transaction rolled back: the data is exactly as before.
            RestoreResult.FAILED
        }
    }

    override suspend fun exportCsv(includeNotes: Boolean, includeSex: Boolean): String = withContext(Dispatchers.IO) {
        BackupCsv.build(db.dayLogDao().all(), db.medicationDao().all().associate { it.id to it.name }, includeNotes, includeSex)
    }

    private suspend fun snapshot(): BackupContent = db.withTransaction {
        val profile = db.profileDao().get()
        BackupContent(
            created = Instant.now().toString(),
            usualCycleLength = profile?.usualCycleLength ?: CycleConstants.DEFAULT_CYCLE_LENGTH,
            usualPeriodLength = profile?.usualPeriodLength ?: CycleConstants.DEFAULT_PERIOD_LENGTH,
            cycles = db.cycleDao().all().filterNot { it.isPredicted }.map { it.toRow() },
            logs = db.dayLogDao().all().map { it.toRow() },
            medications = db.medicationDao().all().map { it.toRow() },
            pet = db.petDao().get()?.toRow(),
            favoriteAffirmations = db.affirmationDao().favoriteTexts(),
        )
    }

    /** Everything in one transaction: a failure part way leaves the old data untouched. */
    private suspend fun replaceAll(content: BackupContent) = db.withTransaction {
        db.cycleDao().deleteAll()
        db.dayLogDao().deleteAll()
        db.medicationDao().deleteAll()
        db.affirmationDao().deleteAll()
        content.cycles.forEach { db.cycleDao().insert(it.toEntity()) }
        content.medications.forEach { db.medicationDao().upsert(it.toEntity()) }
        content.logs.forEach { db.dayLogDao().upsert(it.toEntity()) }
        content.pet?.let { db.petDao().replace(it.toEntity()) }
        content.favoriteAffirmations.forEach { db.affirmationDao().insert(AffirmationEntity(text = it, isFavorite = true)) }
        val profile = db.profileDao().get()
        db.profileDao().upsert(
            (profile ?: ProfileEntity(role = UserRole.TRACKER.name, usualCycleLength = 0, usualPeriodLength = 0, accountId = null))
                .copy(usualCycleLength = content.usualCycleLength, usualPeriodLength = content.usualPeriodLength),
        )
    }
}
