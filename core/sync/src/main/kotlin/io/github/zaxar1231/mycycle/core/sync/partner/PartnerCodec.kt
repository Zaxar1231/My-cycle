package io.github.zaxar1231.mycycle.core.sync.partner

import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.model.PetType
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerSnapshot
import io.github.zaxar1231.mycycle.core.domain.pet.PetCare
import io.github.zaxar1231.mycycle.core.domain.pet.PetMood
import io.github.zaxar1231.mycycle.core.domain.pet.SharedPet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The snapshot as JSON before encryption. Names are short and stable; unknown values from a
 * newer app version are skipped instead of failing, so both phones need not update together.
 */
internal object PartnerCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Dto(
        val v: Int = 1,
        val date: String,
        val day: Int? = null,
        val phase: String? = null,
        val next: String? = null,
        val late: Int = 0,
        val ovulation: String? = null,
        val fertileFrom: String? = null,
        val fertileTo: String? = null,
        val moods: List<String> = emptyList(),
        val symptoms: List<String> = emptyList(),
        val petType: String? = null,
        val petLevel: Int = 1,
        val petWorn: List<String> = emptyList(),
        val petOwned: List<String> = emptyList(),
        val petMood: String? = null,
    )

    @Serializable
    private data class CareDto(val kind: String, val date: String, val item: String? = null)

    fun encode(snapshot: PartnerSnapshot): ByteArray = json.encodeToString(
        Dto.serializer(),
        Dto(
            date = snapshot.date.toString(),
            day = snapshot.cycleDay,
            phase = snapshot.phase?.name,
            next = snapshot.nextPeriodStart?.toString(),
            late = snapshot.daysLate,
            ovulation = snapshot.ovulation?.toString(),
            fertileFrom = snapshot.fertileStart?.toString(),
            fertileTo = snapshot.fertileEnd?.toString(),
            moods = snapshot.moods.map { it.name }.sorted(),
            symptoms = snapshot.symptoms.map { it.name }.sorted(),
            petType = snapshot.pet?.type?.name,
            petLevel = snapshot.pet?.level ?: 1,
            petWorn = snapshot.pet?.worn.orEmpty().sorted(),
            petOwned = snapshot.pet?.ownedItems.orEmpty().sorted(),
            petMood = snapshot.pet?.mood?.name,
        ),
    ).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): PartnerSnapshot? = runCatching {
        val dto = json.decodeFromString(Dto.serializer(), String(bytes, Charsets.UTF_8))
        PartnerSnapshot(
            date = LocalDate.parse(dto.date),
            cycleDay = dto.day,
            phase = dto.phase?.let { name -> CyclePhase.entries.firstOrNull { it.name == name } },
            nextPeriodStart = dto.next?.let(LocalDate::parse),
            daysLate = dto.late,
            ovulation = dto.ovulation?.let(LocalDate::parse),
            fertileStart = dto.fertileFrom?.let(LocalDate::parse),
            fertileEnd = dto.fertileTo?.let(LocalDate::parse),
            moods = dto.moods.mapNotNull { name -> Mood.entries.firstOrNull { it.name == name } }.toSet(),
            symptoms = dto.symptoms.mapNotNull { name -> Symptom.entries.firstOrNull { it.name == name } }.toSet(),
            pet = dto.petType?.let { type -> PetType.entries.firstOrNull { it.name == type } }?.let { type ->
                SharedPet(
                    type = type,
                    level = dto.petLevel,
                    worn = dto.petWorn.toSet(),
                    ownedItems = dto.petOwned.toSet(),
                    mood = PetMood.entries.firstOrNull { it.name == dto.petMood } ?: PetMood.CALM,
                )
            },
        )
    }.getOrNull()

    fun encodeCare(care: PetCare): ByteArray {
        val dto = when (care) {
            is PetCare.Feed -> CareDto(FEED, care.date.toString())
            is PetCare.Gift -> CareDto(GIFT, care.date.toString(), care.itemId)
        }
        return json.encodeToString(CareDto.serializer(), dto).toByteArray(Charsets.UTF_8)
    }

    fun decodeCare(bytes: ByteArray): PetCare? = runCatching {
        val dto = json.decodeFromString(CareDto.serializer(), String(bytes, Charsets.UTF_8))
        val date = LocalDate.parse(dto.date)
        when (dto.kind) {
            FEED -> PetCare.Feed(date)
            GIFT -> dto.item?.let { PetCare.Gift(it, date) }
            else -> null
        }
    }.getOrNull()

    private const val FEED = "feed"
    private const val GIFT = "gift"
}
