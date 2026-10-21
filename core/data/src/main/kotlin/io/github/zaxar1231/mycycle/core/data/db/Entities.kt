package io.github.zaxar1231.mycycle.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "cycles", indices = [Index(value = ["start_date"], unique = true)])
data class CycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    @ColumnInfo(name = "period_length") val periodLength: Int,
    @ColumnInfo(name = "cycle_length") val cycleLength: Int?,
    @ColumnInfo(name = "is_predicted") val isPredicted: Boolean,
)

@Entity(tableName = "day_logs")
data class DayLogEntity(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "period_event") val periodEvent: String?,
    val flow: String?,
    /** Enum names joined with [Converters.SEPARATOR]. */
    val moods: String,
    val symptoms: String,
    @ColumnInfo(name = "sex_activity") val sexActivity: String?,
    @ColumnInfo(name = "temperature_celsius") val temperatureCelsius: Double?,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    @ColumnInfo(name = "medication_ids") val medicationIds: String,
    val note: String?,
)

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String?,
    @ColumnInfo(name = "reminder_time") val reminderTime: LocalTime?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

/** There is at most one pet, so the row always has id 1. */
@Entity(tableName = "pet")
data class PetEntity(
    @PrimaryKey val id: Int = SINGLE_PET_ID,
    val type: String,
    val level: Int,
    val xp: Int,
    val coins: Int,
    @ColumnInfo(name = "equipped_items") val equippedItems: String,
    @ColumnInfo(name = "owned_items") val ownedItems: String,
    // Added in schema version 2 (pet rewards).
    @ColumnInfo(name = "last_log_reward") val lastLogReward: LocalDate? = null,
    @ColumnInfo(name = "log_streak", defaultValue = "0") val logStreak: Int = 0,
    @ColumnInfo(name = "last_breathing_reward") val lastBreathingReward: LocalDate? = null,
    // Added in schema version 3 (care from the partner).
    @ColumnInfo(name = "last_partner_feed") val lastPartnerFeed: LocalDate? = null,
    @ColumnInfo(name = "last_partner_gift") val lastPartnerGift: LocalDate? = null,
) {
    companion object {
        const val SINGLE_PET_ID = 1
    }
}

@Entity(tableName = "affirmations")
data class AffirmationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
)

@Entity(tableName = "partner_links", primaryKeys = ["owner_id", "partner_id"])
data class PartnerLinkEntity(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "partner_id") val partnerId: String,
    val permissions: String,
    val status: String,
)

/** Profile values that describe the user's body or role, kept in the encrypted database. Single row. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLE_PROFILE_ID,
    val role: String,
    @ColumnInfo(name = "usual_cycle_length") val usualCycleLength: Int,
    @ColumnInfo(name = "usual_period_length") val usualPeriodLength: Int,
    @ColumnInfo(name = "account_id") val accountId: String?,
) {
    companion object {
        const val SINGLE_PROFILE_ID = 1
    }
}
