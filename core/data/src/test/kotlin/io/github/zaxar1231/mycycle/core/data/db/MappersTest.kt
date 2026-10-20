package io.github.zaxar1231.mycycle.core.data.db

import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Flow
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.PeriodEvent
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.SexActivity
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class MappersTest {
    @Test
    fun `day log survives a round trip`() {
        val log = DayLog(
            date = LocalDate.of(2026, 10, 20),
            periodEvent = PeriodEvent.STARTED,
            flow = Flow.HEAVY,
            moods = setOf(Mood.TIRED, Mood.SENSITIVE),
            symptoms = setOf(Symptom.CRAMPS),
            sexActivity = SexActivity.PROTECTED,
            temperatureCelsius = 36.6,
            weightKg = 58.2,
            medicationIds = setOf(1, 7),
            note = "ok",
        )
        assertEquals(log, log.toEntity().toDomain())
    }

    @Test
    fun `empty day log survives a round trip`() {
        val log = DayLog(date = LocalDate.of(2026, 1, 1))
        assertEquals(log, log.toEntity().toDomain())
    }

    @Test
    fun `unknown enum names are dropped`() {
        val entity = DayLog(date = LocalDate.of(2026, 1, 1)).toEntity().copy(moods = "HAPPY,FROM_THE_FUTURE", flow = "XL")
        val log = entity.toDomain()
        assertEquals(setOf(Mood.HAPPY), log.moods)
        assertNull(log.flow)
    }

    @Test
    fun `pet survives a round trip`() {
        val pet = Pet(PetType.PANDA, level = 3, xp = 40, coins = 12, equippedItems = setOf("hat"), ownedItems = setOf("hat", "scarf"))
        assertEquals(pet, pet.toEntity().toDomain())
    }

    @Test
    fun `converters handle dates and times`() {
        val c = Converters()
        val date = LocalDate.of(2026, 10, 20)
        assertEquals(date, c.epochDayToDate(c.dateToEpochDay(date)))
        val time = LocalTime.of(21, 30)
        assertEquals(time, c.secondOfDayToTime(c.timeToSecondOfDay(time)))
        assertNull(c.dateToEpochDay(null))
    }
}
