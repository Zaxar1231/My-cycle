package io.github.zaxar1231.mycycle.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.zaxar1231.mycycle.core.data.db.Migrations
import io.github.zaxar1231.mycycle.core.data.db.TwilightDatabase
import io.github.zaxar1231.mycycle.core.data.repository.RoomCycleRepository
import io.github.zaxar1231.mycycle.core.data.repository.RoomDayLogRepository
import io.github.zaxar1231.mycycle.core.data.repository.RoomMedicationRepository
import io.github.zaxar1231.mycycle.core.data.repository.RoomPetRepository
import io.github.zaxar1231.mycycle.core.data.security.DatabaseKeyProvider
import io.github.zaxar1231.mycycle.core.data.security.EncryptedDataAccess
import io.github.zaxar1231.mycycle.core.data.settings.DataStoreSettingsRepository
import io.github.zaxar1231.mycycle.core.domain.repository.CycleRepository
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccess
import io.github.zaxar1231.mycycle.core.domain.repository.DayLogRepository
import io.github.zaxar1231.mycycle.core.domain.repository.MedicationRepository
import io.github.zaxar1231.mycycle.core.domain.repository.PetRepository
import io.github.zaxar1231.mycycle.core.domain.repository.SettingsRepository
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val SQLCIPHER_LIBRARY = "sqlcipher"
    private const val SETTINGS_FILE = "settings"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, keys: DatabaseKeyProvider): TwilightDatabase {
        System.loadLibrary(SQLCIPHER_LIBRARY)
        val factory = SupportOpenHelperFactory(keys.passphrase())
        return Room.databaseBuilder(context, TwilightDatabase::class.java, TwilightDatabase.NAME)
            .openHelperFactory(factory)
            .addMigrations(*Migrations.ALL)
            .build()
    }

    @Provides fun cycleDao(db: TwilightDatabase) = db.cycleDao()

    @Provides fun dayLogDao(db: TwilightDatabase) = db.dayLogDao()

    @Provides fun medicationDao(db: TwilightDatabase) = db.medicationDao()

    @Provides fun petDao(db: TwilightDatabase) = db.petDao()

    @Provides fun profileDao(db: TwilightDatabase) = db.profileDao()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { context.preferencesDataStoreFile(SETTINGS_FILE) }
}

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds fun cycles(impl: RoomCycleRepository): CycleRepository

    @Binds fun dayLogs(impl: RoomDayLogRepository): DayLogRepository

    @Binds fun medications(impl: RoomMedicationRepository): MedicationRepository

    @Binds fun pets(impl: RoomPetRepository): PetRepository

    @Binds fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds fun dataAccess(impl: EncryptedDataAccess): DataAccess
}
