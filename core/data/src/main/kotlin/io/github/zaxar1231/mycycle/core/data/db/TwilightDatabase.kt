package io.github.zaxar1231.mycycle.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Encrypted with SQLCipher (see DatabaseModule). Every schema change bumps [version] and adds a
 * migration in [Migrations]; destructive fallback is never used, so health data is never lost.
 */
@Database(
    entities = [
        CycleEntity::class,
        DayLogEntity::class,
        MedicationEntity::class,
        PetEntity::class,
        AffirmationEntity::class,
        PartnerLinkEntity::class,
        ProfileEntity::class,
    ],
    version = TwilightDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TwilightDatabase : RoomDatabase() {
    abstract fun cycleDao(): CycleDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun medicationDao(): MedicationDao
    abstract fun petDao(): PetDao
    abstract fun profileDao(): ProfileDao
    abstract fun affirmationDao(): AffirmationDao

    companion object {
        const val VERSION = 3
        const val NAME = "twilight.db"
    }
}

/** Migrations between schema versions, registered in DatabaseModule. */
object Migrations {
    /** Pet rewards: when a day log and a breathing session last paid, and the log streak. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pet ADD COLUMN last_log_reward INTEGER")
            db.execSQL("ALTER TABLE pet ADD COLUMN log_streak INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE pet ADD COLUMN last_breathing_reward INTEGER")
        }
    }

    /** Partner mode: when the partner last fed the pet and gave it a gift. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pet ADD COLUMN last_partner_feed INTEGER")
            db.execSQL("ALTER TABLE pet ADD COLUMN last_partner_gift INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
