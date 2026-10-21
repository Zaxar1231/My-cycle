package io.github.zaxar1231.mycycle.core.data.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupCryptoTest {
    private val content = BackupContent(
        created = "2026-10-21T10:00:00Z",
        usualCycleLength = 29,
        usualPeriodLength = 5,
        cycles = listOf(CycleRow("2026-10-01", periodLength = 5)),
        logs = listOf(LogRow("2026-10-02", moods = "HAPPY,TIRED", note = "privat, \"quoted\"")),
        medications = listOf(MedicationRow(1, "Iron", "1 tab", "08:00")),
        pet = PetRow("FOX", 2, 60, 12, "bow", "bow,scarf", logStreak = 3),
        favoriteAffirmations = listOf("I am enough"),
    )

    @Test
    fun `a backup opens with its password and gives back the same content`() {
        val file = BackupCrypto.seal(BackupCodec.encode(content), "correct horse".toCharArray())
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Iron"))
        val opened = BackupCrypto.open(file, "correct horse".toCharArray())
        assertTrue(opened is BackupCrypto.Opened.Plain)
        assertEquals(content, BackupCodec.decode((opened as BackupCrypto.Opened.Plain).bytes))
    }

    @Test
    fun `a wrong password or another file is told apart`() {
        val file = BackupCrypto.seal(BackupCodec.encode(content), "correct horse".toCharArray())
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(file, "wrong horse".toCharArray()))
        assertEquals(BackupCrypto.Opened.NotABackup, BackupCrypto.open("hello".toByteArray(), "x".toCharArray()))
        val damaged = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(damaged, "correct horse".toCharArray()))
    }

    @Test
    fun `two backups of the same data differ`() {
        val a = BackupCrypto.seal(byteArrayOf(1, 2, 3), "password1".toCharArray())
        val b = BackupCrypto.seal(byteArrayOf(1, 2, 3), "password1".toCharArray())
        assertFalse(a.contentEquals(b))
        assertArrayEquals(byteArrayOf(1, 2, 3), (BackupCrypto.open(a, "password1".toCharArray()) as BackupCrypto.Opened.Plain).bytes)
    }

    @Test
    fun `a newer backup version is not read`() {
        assertEquals(null, BackupCodec.decode(BackupCodec.encode(content.copy(version = BackupContent.VERSION + 1))))
    }
}
