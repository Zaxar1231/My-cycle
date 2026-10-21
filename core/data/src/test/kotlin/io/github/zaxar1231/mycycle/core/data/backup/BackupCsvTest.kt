package io.github.zaxar1231.mycycle.core.data.backup

import io.github.zaxar1231.mycycle.core.data.db.DayLogEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BackupCsvTest {
    private val log = DayLogEntity(
        LocalDate.of(2026, 10, 2), "START", "MEDIUM", "HAPPY,TIRED", "HEADACHE", "PROTECTED", 36.6, null, "1,2", "privat, \"quoted\"",
    )
    private val meds = mapOf(1L to "Iron", 2L to "=HYPERLINK(\"x\")")

    @Test
    fun `notes and sex are left out unless asked for`() {
        val csv = BackupCsv.build(listOf(log), meds, includeNotes = false, includeSex = false)
        assertFalse("privat" in csv)
        assertFalse("PROTECTED" in csv)
        assertFalse("note" in csv)
        val full = BackupCsv.build(listOf(log), meds, includeNotes = true, includeSex = true)
        assertTrue("\"privat, \"\"quoted\"\"\"" in full)
        assertTrue("PROTECTED" in full)
    }

    @Test
    fun `the file opens in a spreadsheet as text`() {
        val csv = BackupCsv.build(listOf(log), meds, includeNotes = false, includeSex = false)
        assertTrue(csv.startsWith(Char(0xFEFF) + "date,period,flow,moods,symptoms,temperature_c,weight_kg,medications\r\n"))
        assertEquals("2026-10-02,START,MEDIUM,HAPPY;TIRED,HEADACHE,36.6,,\"Iron;=HYPERLINK(\"\"x\"\")\"\r\n", csv.substringAfter("\r\n"))
    }

    @Test
    fun `a cell that looks like a formula is not run`() {
        assertEquals("'=1+1", BackupCsv.cell("=1+1"))
        assertEquals("'+7 999", BackupCsv.cell("+7 999"))
        assertEquals("'-2", BackupCsv.cell("-2"))
        assertEquals("'@SUM(A1)", BackupCsv.cell("@SUM(A1)"))
        assertEquals("\"'=A1,B1\"", BackupCsv.cell("=A1,B1"))
        assertEquals("36.6", BackupCsv.cell("36.6"))
    }
}
