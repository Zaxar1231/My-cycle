package io.github.zaxar1231.mycycle.core.data.backup

import io.github.zaxar1231.mycycle.core.data.db.CycleEntity
import io.github.zaxar1231.mycycle.core.data.db.DayLogEntity
import io.github.zaxar1231.mycycle.core.data.db.MedicationEntity
import io.github.zaxar1231.mycycle.core.data.db.PetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

/** Everything a backup holds, as JSON inside the encrypted file. */
@Serializable
internal data class BackupContent(
    val version: Int = VERSION,
    val created: String,
    val usualCycleLength: Int,
    val usualPeriodLength: Int,
    val cycles: List<CycleRow> = emptyList(),
    val logs: List<LogRow> = emptyList(),
    val medications: List<MedicationRow> = emptyList(),
    val pet: PetRow? = null,
    val favoriteAffirmations: List<String> = emptyList(),
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
internal data class CycleRow(val start: String, val end: String? = null, val periodLength: Int, val cycleLength: Int? = null)

@Serializable
internal data class LogRow(
    val date: String,
    val periodEvent: String? = null,
    val flow: String? = null,
    val moods: String = "",
    val symptoms: String = "",
    val sex: String? = null,
    val temperature: Double? = null,
    val weight: Double? = null,
    val medicationIds: String = "",
    val note: String? = null,
)

@Serializable
internal data class MedicationRow(val id: Long, val name: String, val dosage: String? = null, val reminder: String? = null, val active: Boolean = true)

@Serializable
internal data class PetRow(
    val type: String,
    val level: Int,
    val xp: Int,
    val coins: Int,
    val equipped: String,
    val owned: String,
    val lastLogReward: String? = null,
    val logStreak: Int = 0,
    val lastBreathingReward: String? = null,
    val lastPartnerFeed: String? = null,
    val lastPartnerGift: String? = null,
)

internal object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(content: BackupContent): ByteArray = json.encodeToString(BackupContent.serializer(), content).toByteArray(Charsets.UTF_8)

    /** Null when the JSON is not a backup this version understands. */
    fun decode(bytes: ByteArray): BackupContent? = runCatching {
        json.decodeFromString(BackupContent.serializer(), String(bytes, Charsets.UTF_8)).takeIf { it.version <= BackupContent.VERSION }
    }.getOrNull()

    fun CycleEntity.toRow() = CycleRow(startDate.toString(), endDate?.toString(), periodLength, cycleLength)
    fun CycleRow.toEntity() = CycleEntity(
        startDate = LocalDate.parse(start),
        endDate = end?.let(LocalDate::parse),
        periodLength = periodLength,
        cycleLength = cycleLength,
        isPredicted = false,
    )

    fun DayLogEntity.toRow() = LogRow(date.toString(), periodEvent, flow, moods, symptoms, sexActivity, temperatureCelsius, weightKg, medicationIds, note)
    fun LogRow.toEntity() = DayLogEntity(LocalDate.parse(date), periodEvent, flow, moods, symptoms, sex, temperature, weight, medicationIds, note)

    fun MedicationEntity.toRow() = MedicationRow(id, name, dosage, reminderTime?.toString(), isActive)
    fun MedicationRow.toEntity() = MedicationEntity(id, name, dosage, reminder?.let(LocalTime::parse), active)

    fun PetEntity.toRow() = PetRow(
        type, level, xp, coins, equippedItems, ownedItems,
        lastLogReward?.toString(), logStreak, lastBreathingReward?.toString(), lastPartnerFeed?.toString(), lastPartnerGift?.toString(),
    )
    fun PetRow.toEntity() = PetEntity(
        type = type,
        level = level,
        xp = xp,
        coins = coins,
        equippedItems = equipped,
        ownedItems = owned,
        lastLogReward = lastLogReward?.let(LocalDate::parse),
        logStreak = logStreak,
        lastBreathingReward = lastBreathingReward?.let(LocalDate::parse),
        lastPartnerFeed = lastPartnerFeed?.let(LocalDate::parse),
        lastPartnerGift = lastPartnerGift?.let(LocalDate::parse),
    )
}
