package io.github.zaxar1231.mycycle.core.domain.partner

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePredictor
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.model.SexActivity
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class PartnerTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val today = start.plusDays(3)
    private val prediction = CyclePredictor().predict(
        CyclePredictor.Input(
            cycles = listOf(Cycle(id = 1, startDate = start, periodLength = 5)),
            usualCycleLength = 28,
            usualPeriodLength = 5,
            usualLengthsEstimated = false,
            today = today,
        ),
    )
    private val log = DayLog(
        date = today,
        moods = setOf(Mood.TIRED),
        symptoms = setOf(Symptom.CRAMPS),
        sexActivity = SexActivity.PROTECTED,
        note = "private",
    )

    @Test
    fun `an invite code survives a round trip and is found inside a message`() {
        val invite = Invite(UUID.randomUUID(), ByteArray(InviteCode.KEY_BYTES) { it.toByte() })
        val code = InviteCode.format(invite)
        val parsed = InviteCode.parse("Join me: ${InviteCode.LINK_PREFIX}$code\nthanks")
        assertNotNull(parsed)
        assertEquals(invite.linkId, parsed!!.linkId)
        assertArrayEquals(invite.key, parsed.key)
        assertEquals(invite.linkId, InviteCode.parse(code)!!.linkId)
    }

    @Test
    fun `the verification code depends on the key and the partner`() {
        val key = ByteArray(InviteCode.KEY_BYTES) { 7 }
        val code = PartnerVerification.code(key, "partner-a")
        assertTrue(Regex("\\d{3} \\d{3}").matches(code))
        assertEquals(code, PartnerVerification.code(key.copyOf(), "partner-a"))
        assertFalse(code == PartnerVerification.code(key, "partner-b"))
        assertFalse(code == PartnerVerification.code(ByteArray(InviteCode.KEY_BYTES) { 8 }, "partner-a"))
    }

    @Test
    fun `broken codes are rejected`() {
        val code = InviteCode.format(Invite(UUID.randomUUID(), ByteArray(InviteCode.KEY_BYTES)))
        assertNull(InviteCode.parse(code.dropLast(1)))
        assertNull(InviteCode.parse(code + "A"))
        assertNull(InviteCode.parse("hello"))
    }

    @Test
    fun `only the phase is shared by default`() {
        val snapshot = PartnerShare.snapshot(prediction, log, DEFAULT_PARTNER_PERMISSIONS, today)
        assertEquals(4, snapshot.cycleDay)
        assertEquals(CyclePhase.MENSTRUAL, snapshot.phase)
        assertEquals(start.plusDays(28), snapshot.nextPeriodStart)
        assertNull(snapshot.ovulation)
        assertNull(snapshot.fertileStart)
        assertTrue(snapshot.moods.isEmpty())
        assertTrue(snapshot.symptoms.isEmpty())
    }

    @Test
    fun `nothing is shared without permissions`() {
        val snapshot = PartnerShare.snapshot(prediction, log, emptySet(), today)
        assertEquals(PartnerSnapshot(date = today), snapshot)
    }

    @Test
    fun `granted fields are shared`() {
        val snapshot = PartnerShare.snapshot(prediction, log, PartnerPermission.entries.toSet(), today)
        assertEquals(setOf(Mood.TIRED), snapshot.moods)
        assertEquals(setOf(Symptom.CRAMPS), snapshot.symptoms)
        assertEquals(start.plusDays(13), snapshot.ovulation)
        assertEquals(start.plusDays(8), snapshot.fertileStart)
    }

    @Test
    fun `the shared pet does not give away a hidden period`() {
        val pet = Pet(PetType.FOX, level = 2, equippedItems = setOf("bow"), ownedItems = setOf("bow"))
        val hidden = PartnerShare.snapshot(prediction, log, setOf(PartnerPermission.PET), today, pet)
        assertEquals(PetMood.CALM, hidden.pet!!.mood)
        assertEquals(setOf("bow"), hidden.pet!!.worn)
        val shared = PartnerShare.snapshot(prediction, log, DEFAULT_PARTNER_PERMISSIONS, today, pet)
        assertEquals(PetMood.COZY, shared.pet!!.mood)
    }

    @Test
    fun `the pet needs its own permission and mixes no hidden data into its mood`() {
        val pet = Pet(PetType.FOX)
        assertNull(PartnerShare.snapshot(prediction, log, setOf(PartnerPermission.PHASE), today, pet).pet)
        val sad = log.copy(moods = setOf(Mood.SAD))
        // Phase without mood: the sad day stays hidden, the period shows.
        assertEquals(PetMood.COZY, PartnerShare.snapshot(prediction, sad, setOf(PartnerPermission.PHASE, PartnerPermission.PET), today, pet).pet!!.mood)
        // Mood without phase: no hot water bottle.
        assertEquals(PetMood.SAD, PartnerShare.snapshot(prediction, sad, setOf(PartnerPermission.MOOD, PartnerPermission.PET), today, pet).pet!!.mood)
        assertEquals(PetMood.CALM, PartnerShare.snapshot(prediction, sad, setOf(PartnerPermission.FERTILITY, PartnerPermission.PET), today, pet).pet!!.mood)
    }

    @Test
    fun `a log from another day is not shared as today`() {
        val snapshot = PartnerShare.snapshot(prediction, log.copy(date = today.minusDays(1)), PartnerPermission.entries.toSet(), today)
        assertTrue(snapshot.moods.isEmpty())
    }

    @Test
    fun `an old snapshot is counted on to today`() {
        val snapshot = PartnerShare.snapshot(prediction, log, PartnerPermission.entries.toSet(), today)
        val view = PartnerShare.view(snapshot, today.plusDays(6))
        assertEquals(10, view.cycleDay)
        assertEquals(6, view.daysOld)
        assertEquals(19, view.daysUntilPeriod)
        assertTrue(view.fertileToday)
    }

    @Test
    fun `a period past its expected start shows as late`() {
        val snapshot = PartnerShare.snapshot(prediction, null, DEFAULT_PARTNER_PERMISSIONS, today)
        val view = PartnerShare.view(snapshot, start.plusDays(30))
        assertEquals(0, view.daysUntilPeriod)
        assertEquals(2, view.daysLate)
        assertFalse(view.fertileToday)
    }

    @Test
    fun `the reminder comes once, shortly before the period`() {
        val snapshot = PartnerShare.snapshot(prediction, null, DEFAULT_PARTNER_PERMISSIONS, today)
        val next = start.plusDays(28)
        assertNull(PartnerShare.noticeFor(snapshot, next.minusDays(3), null))
        assertEquals(next, PartnerShare.noticeFor(snapshot, next.minusDays(2), null))
        assertEquals(next, PartnerShare.noticeFor(snapshot, next, null))
        assertNull(PartnerShare.noticeFor(snapshot, next.minusDays(1), lastNotice = next))
        assertNull(PartnerShare.noticeFor(snapshot, next.plusDays(1), null))
    }
}
