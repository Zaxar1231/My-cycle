package io.github.zaxar1231.mycycle.core.data.di

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkyHomeMigrationTest {
    private val home = stringPreferencesKey("theme_home")
    private val done = booleanPreferencesKey("migration_sky_home")

    @Test
    fun `the old moon default becomes the sky`() = runBlocking {
        val migrated = SkyHomeMigration.migrate(preferencesOf(home to "MOON"))
        assertEquals("SKY", migrated[home])
        assertEquals(true, migrated[done])
    }

    @Test
    fun `other styles stay`() = runBlocking {
        assertEquals("ORBIT", SkyHomeMigration.migrate(preferencesOf(home to "ORBIT"))[home])
    }

    @Test
    fun `empty settings only get the flag`() = runBlocking {
        val migrated = SkyHomeMigration.migrate(emptyPreferences())
        assertNull(migrated[home])
        assertEquals(true, migrated[done])
    }

    @Test
    fun `it runs only once`() = runBlocking {
        assertTrue(SkyHomeMigration.shouldMigrate(preferencesOf(home to "MOON")))
        assertFalse(SkyHomeMigration.shouldMigrate(preferencesOf(home to "MOON", done to true)))
    }
}
