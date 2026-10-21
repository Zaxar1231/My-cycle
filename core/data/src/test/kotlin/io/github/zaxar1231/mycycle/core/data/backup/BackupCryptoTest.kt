package io.github.zaxar1231.mycycle.core.data.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
        val file = BackupCrypto.seal(BackupCodec.encode(content), "correct horse".toCharArray(), FAST)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Iron"))
        val opened = BackupCrypto.open(file, "correct horse".toCharArray())
        assertTrue(opened is BackupCrypto.Opened.Plain)
        assertEquals(content, BackupCodec.decode((opened as BackupCrypto.Opened.Plain).bytes))
    }

    @Test
    fun `a wrong password or another file is told apart`() {
        val file = BackupCrypto.seal(BackupCodec.encode(content), "correct horse".toCharArray(), FAST)
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(file, "wrong horse".toCharArray()))
        assertEquals(BackupCrypto.Opened.NotABackup, BackupCrypto.open("hello".toByteArray(), "x".toCharArray()))
        val damaged = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(damaged, "correct horse".toCharArray()))
    }

    @Test
    fun `two backups of the same data differ`() {
        val a = BackupCrypto.seal(byteArrayOf(1, 2, 3), "password1".toCharArray(), FAST)
        val b = BackupCrypto.seal(byteArrayOf(1, 2, 3), "password1".toCharArray(), FAST)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(byteArrayOf(1, 2, 3), (BackupCrypto.open(a, "password1".toCharArray()) as BackupCrypto.Opened.Plain).bytes)
    }

    @Test
    fun `a newer backup version is not read`() {
        assertEquals(null, BackupCodec.decode(BackupCodec.encode(content.copy(version = BackupContent.VERSION + 1))))
    }

    @Test
    fun `a changed header is detected`() {
        val file = BackupCrypto.seal(byteArrayOf(1, 2, 3), "password1".toCharArray(), FAST)
        // Iterations live in bytes 4..7: one more round gives another key and fails the tag.
        val changed = file.copyOf().also { ByteBuffer.wrap(it, 4, 4).putInt(FAST + 1) }
        assertEquals(BackupCrypto.Opened.WrongPassword, BackupCrypto.open(changed, "password1".toCharArray()))
        val absurd = file.copyOf().also { ByteBuffer.wrap(it, 4, 4).putInt(Int.MAX_VALUE) }
        assertEquals(BackupCrypto.Opened.NotABackup, BackupCrypto.open(absurd, "password1".toCharArray()))
    }

    @Test
    fun `new backups use the stronger key derivation`() {
        val file = BackupCrypto.seal(byteArrayOf(1), "password1".toCharArray())
        assertEquals("TCB2", String(file, 0, 4, Charsets.US_ASCII))
        assertEquals(BackupCrypto.ITERATIONS, ByteBuffer.wrap(file, 4, 4).int)
    }

    @Test
    fun `a first-version backup still opens`() {
        val magic = "TCB1".toByteArray(Charsets.US_ASCII)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val spec = PBEKeySpec("password1".toCharArray(), salt, 310_000, 256)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            updateAAD(magic)
        }
        val file = magic + salt + iv + cipher.doFinal(byteArrayOf(4, 5, 6))
        assertArrayEquals(byteArrayOf(4, 5, 6), (BackupCrypto.open(file, "password1".toCharArray()) as BackupCrypto.Opened.Plain).bytes)
        assertTrue(BackupCrypto.hasMagic(file))
        assertFalse(BackupCrypto.hasMagic("PK\u0003\u0004".toByteArray()))
    }

    @Test
    fun `the backup content survives encoding with every field`() {
        val full = content.copy(
            cycles = listOf(CycleRow("2026-09-03", "2026-09-30", 5, 28), CycleRow("2026-10-01", periodLength = 5)),
            logs = listOf(LogRow("2026-10-02", "START", "HEAVY", "HAPPY", "CRAMPS", "PROTECTED", 36.7, 61.5, "1", "note")),
            medications = listOf(MedicationRow(1, "Iron", null, null, active = false)),
            pet = PetRow("CAT", 4, 10, 99, "", "bow", "2026-10-20", 7, "2026-10-19", "2026-10-18", "2026-10-11"),
        )
        assertEquals(full, BackupCodec.decode(BackupCodec.encode(full)))
        assertEquals(null, BackupCodec.decode("not json".toByteArray()))
    }

    private companion object {
        /** Tests need no slow key derivation; the iterations travel in the header. */
        const val FAST = 1_000
    }
}
