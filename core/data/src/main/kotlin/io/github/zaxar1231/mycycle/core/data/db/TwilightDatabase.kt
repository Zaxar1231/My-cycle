package io.github.zaxar1231.mycycle.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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

    companion object {
        const val VERSION = 1
        const val NAME = "twilight.db"
    }
}

/** Migrations between schema versions, registered in DatabaseModule. Empty until version 2. */
object Migrations {
    val ALL = emptyArray<androidx.room.migration.Migration>()
}
