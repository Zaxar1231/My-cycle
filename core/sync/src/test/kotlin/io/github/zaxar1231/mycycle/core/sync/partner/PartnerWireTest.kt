package io.github.zaxar1231.mycycle.core.sync.partner

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerError
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PartnerWireTest {
    private val snapshot = PartnerSnapshot(
        date = LocalDate.of(2026, 10, 21),
        cycleDay = 12,
        phase = CyclePhase.FOLLICULAR,
        nextPeriodStart = LocalDate.of(2026, 11, 7),
        fertileStart = LocalDate.of(2026, 10, 19),
        fertileEnd = LocalDate.of(2026, 10, 24),
        moods = setOf(Mood.HAPPY, Mood.TIRED),
        symptoms = setOf(Symptom.HEADACHE),
    )

    @Test
    fun `a sealed snapshot opens with the same key and link`() {
        val key = PartnerCrypto.newKey()
        val sealed = PartnerCrypto.seal(key, "link-1", PartnerCodec.encode(snapshot))
        assertFalse("FOLLICULAR" in sealed)
        val opened = PartnerCrypto.open(key, "link-1", sealed)
        assertEquals(snapshot, PartnerCodec.decode(opened!!))
    }

    @Test
    fun `every snapshot has the same payload length`() {
        val key = PartnerCrypto.newKey()
        val empty = PartnerCrypto.seal(key, "link-1", PartnerCodec.encode(PartnerSnapshot(date = snapshot.date)))
        val full = PartnerCrypto.seal(key, "link-1", PartnerCodec.encode(snapshot.copy(moods = Mood.entries.toSet(), symptoms = Symptom.entries.toSet())))
        val luteal = PartnerCrypto.seal(key, "link-1", PartnerCodec.encode(snapshot.copy(phase = CyclePhase.LUTEAL, cycleDay = 3)))
        assertEquals(empty.length, full.length)
        assertEquals(empty.length, luteal.length)
    }

    @Test
    fun `another key, another link or a changed byte cannot open it`() {
        val key = PartnerCrypto.newKey()
        val sealed = PartnerCrypto.seal(key, "link-1", PartnerCodec.encode(snapshot))
        assertNull(PartnerCrypto.open(PartnerCrypto.newKey(), "link-1", sealed))
        assertNull(PartnerCrypto.open(key, "link-2", sealed))
        val bytes = java.util.Base64.getDecoder().decode(sealed)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(PartnerCrypto.open(key, "link-1", java.util.Base64.getEncoder().encodeToString(bytes)))
        assertNull(PartnerCrypto.open(key, "link-1", "not base64!"))
    }

    @Test
    fun `values from a newer app version are skipped`() {
        val json = """{"v":2,"date":"2026-10-21","phase":"NEW_PHASE","moods":["HAPPY","FUTURE"],"extra":1}"""
        val decoded = PartnerCodec.decode(json.toByteArray())!!
        assertNull(decoded.phase)
        assertEquals(setOf(Mood.HAPPY), decoded.moods)
    }

    @Test
    fun `server errors are told apart`() {
        assertEquals(PartnerError.INVITE_NOT_FOUND, PartnerErrors.map(400, """{"code":"P0001","message":"invite_not_found"}"""))
        assertEquals(PartnerError.OWN_INVITE, PartnerErrors.map(400, """{"code":"P0001","message":"own_invite"}"""))
        assertEquals(PartnerError.ALREADY_LINKED, PartnerErrors.map(409, """{"code":"23505","message":"duplicate key"}"""))
        assertEquals(PartnerError.SERVER_NOT_READY, PartnerErrors.map(404, """{"code":"PGRST205","message":"Could not find the table"}"""))
        assertEquals(PartnerError.SIGNED_OUT, PartnerErrors.map(401, """{"message":"JWT expired"}"""))
        assertEquals(PartnerError.UNKNOWN, PartnerErrors.map(500, ""))
    }

    @Test
    fun `row values are read from a JSON array`() {
        assertEquals(listOf("a", "b"), PartnerErrors.column("""[{"id":"a"},{"id":"b"}]""", "id"))
        assertTrue(PartnerErrors.column("""{"message":"error"}""", "id").isEmpty())
    }
}
