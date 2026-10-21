package io.github.zaxar1231.mycycle.core.data.backup

import io.github.zaxar1231.mycycle.core.data.db.DayLogEntity

/**
 * The day logs as a CSV table for a spreadsheet. Notes and sex are left out unless asked for.
 * UTF-8 with a BOM and CRLF line ends, so Excel opens Cyrillic text correctly.
 */
internal object BackupCsv {
    private const val BOM = "﻿"
    private const val EOL = "\r\n"

    fun build(logs: List<DayLogEntity>, medicationNames: Map<Long, String>, includeNotes: Boolean, includeSex: Boolean): String {
        val header = buildList {
            addAll(listOf("date", "period", "flow", "moods", "symptoms"))
            if (includeSex) add("sex")
            addAll(listOf("temperature_c", "weight_kg", "medications"))
            if (includeNotes) add("note")
        }
        return buildString {
            append(BOM)
            append(header.joinToString(",")).append(EOL)
            logs.forEach { log ->
                val meds = log.medicationIds.split(",").mapNotNull { it.toLongOrNull()?.let(medicationNames::get) }.joinToString(";")
                val cells = buildList {
                    add(log.date.toString())
                    add(log.periodEvent.orEmpty())
                    add(log.flow.orEmpty())
                    add(log.moods.replace(",", ";"))
                    add(log.symptoms.replace(",", ";"))
                    if (includeSex) add(log.sexActivity.orEmpty())
                    add(log.temperatureCelsius?.toString().orEmpty())
                    add(log.weightKg?.toString().orEmpty())
                    add(meds)
                    if (includeNotes) add(log.note.orEmpty())
                }
                append(cells.joinToString(",") { cell(it) }).append(EOL)
            }
        }
    }

    /**
     * RFC 4180: quotes around cells with commas, quotes or line breaks; quotes doubled. A cell
     * that a spreadsheet would run as a formula gets a leading apostrophe.
     */
    fun cell(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_START) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')
}
