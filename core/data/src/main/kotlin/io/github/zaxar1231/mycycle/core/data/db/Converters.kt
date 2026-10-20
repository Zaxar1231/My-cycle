package io.github.zaxar1231.mycycle.core.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun timeToSecondOfDay(time: LocalTime?): Int? = time?.toSecondOfDay()

    @TypeConverter
    fun secondOfDayToTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    companion object {
        const val SEPARATOR = ","

        fun join(values: Iterable<Any>): String = values.joinToString(SEPARATOR)

        fun split(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

        /** Unknown names (for example from a newer app version) are dropped instead of crashing. */
        inline fun <reified E : Enum<E>> enumSet(value: String): Set<E> =
            split(value).mapNotNull { name -> enumValues<E>().firstOrNull { it.name == name } }.toSet()

        inline fun <reified E : Enum<E>> enumOrNull(value: String?): E? =
            value?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
    }
}
