package io.github.zaxar1231.mycycle.core.domain.partner

import io.github.zaxar1231.mycycle.core.domain.cycle.CycleConstants
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.pet.PetRules
import io.github.zaxar1231.mycycle.core.domain.pet.SharedPet
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Exactly what the partner can read, made on the owner's phone and encrypted before upload.
 * Fields outside the granted permissions stay empty, so they never leave the phone.
 */
data class PartnerSnapshot(
    /** The owner's date when the snapshot was made. */
    val date: LocalDate,
    val cycleDay: Int? = null,
    val phase: CyclePhase? = null,
    val nextPeriodStart: LocalDate? = null,
    val daysLate: Int = 0,
    val ovulation: LocalDate? = null,
    val fertileStart: LocalDate? = null,
    val fertileEnd: LocalDate? = null,
    /** Logged on [date]. */
    val moods: Set<Mood> = emptySet(),
    val symptoms: Set<Symptom> = emptySet(),
    /**
     * The shared pet, with [PartnerPermission.PET]; its mood is worked out only from what the
     * permissions share. The owner's clock is left out (the partner's phone shows night itself).
     */
    val pet: SharedPet? = null,
)

/** The snapshot as the partner sees it today. */
data class PartnerView(
    val cycleDay: Int?,
    val phase: CyclePhase?,
    /** 0 when the period is expected today or is already late. */
    val daysUntilPeriod: Int?,
    val daysLate: Int,
    val fertileStart: LocalDate?,
    val fertileEnd: LocalDate?,
    val fertileToday: Boolean,
    /** Days since the owner's phone last shared; phase and mood may have changed meanwhile. */
    val daysOld: Int,
    val moods: Set<Mood>,
    val symptoms: Set<Symptom>,
)

object PartnerShare {
    private const val NOON = 12

    fun snapshot(
        prediction: CyclePrediction,
        todayLog: DayLog?,
        permissions: Set<PartnerPermission>,
        today: LocalDate,
        pet: Pet? = null,
    ): PartnerSnapshot {
        val status = prediction.today
        val phase = PartnerPermission.PHASE in permissions && status != null
        val fertility = PartnerPermission.FERTILITY in permissions && status != null
        val log = todayLog?.takeIf { it.date == today }
        val moods = if (PartnerPermission.MOOD in permissions) log?.moods.orEmpty() else emptySet()
        // A hot water bottle would tell the period, so the pet only knows what the partner may see.
        val sharedPet = pet?.takeIf { PartnerPermission.PET in permissions }?.let {
            SharedPet(
                type = it.type,
                level = it.level,
                worn = it.equippedItems,
                ownedItems = it.ownedItems,
                mood = PetRules.mood(status?.phase?.takeIf { phase }, moods, NOON),
            )
        }
        return PartnerSnapshot(
            date = today,
            cycleDay = status?.cycleDay?.takeIf { phase },
            phase = status?.phase?.takeIf { phase },
            nextPeriodStart = status?.nextPeriodStart?.takeIf { phase },
            daysLate = if (phase) status?.daysLate ?: 0 else 0,
            ovulation = status?.ovulation?.takeIf { fertility },
            fertileStart = status?.fertileWindow?.start?.takeIf { fertility },
            fertileEnd = status?.fertileWindow?.endInclusive?.takeIf { fertility },
            moods = moods,
            symptoms = if (PartnerPermission.SYMPTOMS in permissions) log?.symptoms.orEmpty() else emptySet(),
            pet = sharedPet,
        )
    }

    /** Moves an older snapshot to [today]: the cycle day and the days left are counted on. */
    fun view(snapshot: PartnerSnapshot, today: LocalDate): PartnerView {
        val daysOld = ChronoUnit.DAYS.between(snapshot.date, today).toInt().coerceAtLeast(0)
        val untilPeriod = snapshot.nextPeriodStart?.let { ChronoUnit.DAYS.between(today, it).toInt() }
        val late = when {
            untilPeriod == null -> 0
            untilPeriod < 0 -> -untilPeriod + snapshot.daysLate
            else -> if (daysOld == 0) snapshot.daysLate else 0
        }
        val fertileToday = snapshot.fertileStart != null && snapshot.fertileEnd != null &&
            !today.isBefore(snapshot.fertileStart) && !today.isAfter(snapshot.fertileEnd)
        return PartnerView(
            cycleDay = snapshot.cycleDay?.plus(daysOld),
            phase = snapshot.phase,
            daysUntilPeriod = untilPeriod?.coerceAtLeast(0),
            daysLate = late,
            fertileStart = snapshot.fertileStart,
            fertileEnd = snapshot.fertileEnd,
            fertileToday = fertileToday,
            daysOld = daysOld,
            moods = snapshot.moods,
            symptoms = snapshot.symptoms,
        )
    }

    /**
     * The period start to remind the partner about today, or null. A reminder goes out once per
     * period, from [CycleConstants.PARTNER_NOTICE_DAYS_BEFORE] days before it to the day itself.
     */
    fun noticeFor(snapshot: PartnerSnapshot, today: LocalDate, lastNotice: LocalDate?): LocalDate? {
        val start = snapshot.nextPeriodStart ?: return null
        if (start == lastNotice) return null
        val days = ChronoUnit.DAYS.between(today, start)
        return start.takeIf { days in 0..CycleConstants.PARTNER_NOTICE_DAYS_BEFORE.toLong() }
    }
}
