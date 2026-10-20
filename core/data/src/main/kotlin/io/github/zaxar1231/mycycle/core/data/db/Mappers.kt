package io.github.zaxar1231.mycycle.core.data.db

import io.github.zaxar1231.mycycle.core.data.db.Converters.Companion.enumOrNull
import io.github.zaxar1231.mycycle.core.data.db.Converters.Companion.enumSet
import io.github.zaxar1231.mycycle.core.data.db.Converters.Companion.join
import io.github.zaxar1231.mycycle.core.data.db.Converters.Companion.split
import io.github.zaxar1231.mycycle.core.domain.model.Cycle
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Pet
import io.github.zaxar1231.mycycle.core.domain.model.PetType

fun CycleEntity.toDomain() = Cycle(id, startDate, endDate, periodLength, cycleLength, isPredicted)

fun Cycle.toEntity() = CycleEntity(id, startDate, endDate, periodLength, cycleLength, isPredicted)

fun DayLogEntity.toDomain() = DayLog(
    date = date,
    periodEvent = enumOrNull(periodEvent),
    flow = enumOrNull(flow),
    moods = enumSet(moods),
    symptoms = enumSet(symptoms),
    sexActivity = enumOrNull(sexActivity),
    temperatureCelsius = temperatureCelsius,
    weightKg = weightKg,
    medicationIds = split(medicationIds).mapNotNull(String::toLongOrNull).toSet(),
    note = note,
)

fun DayLog.toEntity() = DayLogEntity(
    date = date,
    periodEvent = periodEvent?.name,
    flow = flow?.name,
    moods = join(moods.map { it.name }),
    symptoms = join(symptoms.map { it.name }),
    sexActivity = sexActivity?.name,
    temperatureCelsius = temperatureCelsius,
    weightKg = weightKg,
    medicationIds = join(medicationIds),
    note = note,
)

fun MedicationEntity.toDomain() = Medication(id, name, dosage, reminderTime, isActive)

fun Medication.toEntity() = MedicationEntity(id, name, dosage, reminderTime, isActive)

fun PetEntity.toDomain(): Pet? = enumOrNull<PetType>(type)?.let {
    Pet(it, level, xp, coins, split(equippedItems).toSet(), split(ownedItems).toSet())
}

fun Pet.toEntity() = PetEntity(
    type = type.name,
    level = level,
    xp = xp,
    coins = coins,
    equippedItems = join(equippedItems),
    ownedItems = join(ownedItems),
)
