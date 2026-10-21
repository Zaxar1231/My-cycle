package io.github.zaxar1231.mycycle.feature.backup

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import io.github.zaxar1231.mycycle.core.domain.model.Medication
import io.github.zaxar1231.mycycle.core.domain.model.Symptom
import io.github.zaxar1231.mycycle.core.domain.stats.CycleHistoryItem
import io.github.zaxar1231.mycycle.core.domain.stats.CycleSummary
import io.github.zaxar1231.mycycle.core.domain.stats.Regularity
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What goes into the doctor's report. Notes, sex and weight are left out on purpose. */
data class ReportData(
    val generated: LocalDate,
    val summary: CycleSummary,
    /** Newest first. */
    val cycles: List<CycleHistoryItem>,
    val symptoms: List<Pair<Symptom, Int>>,
    val medications: List<Medication>,
    val usualCycleLength: Int,
    val usualPeriodLength: Int,
)

/** An A4 report drawn with the platform PDF API: no library, nothing leaves the phone. */
object ReportPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val LINE = 18f
    private const val MAX_CYCLES = 12
    private const val MAX_SYMPTOMS = 8

    fun write(context: Context, data: ReportData, out: OutputStream) {
        val document = PdfDocument()
        try {
            draw(context, data, Writer(document))
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun draw(context: Context, data: ReportData, writer: Writer) {
        val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val s = data.summary
        writer.title(context.getString(R.string.report_title))
        writer.text(context.getString(R.string.report_generated, data.generated.format(dates)))
        writer.gap()

        writer.heading(context.getString(R.string.report_summary))
        writer.text(context.getString(R.string.report_cycles_count, s.completedCount))
        s.averageCycleLength?.let { writer.text(context.getString(R.string.report_avg_cycle, it)) }
            ?: writer.text(context.getString(R.string.report_usual_cycle, data.usualCycleLength))
        s.averagePeriodLength?.let { writer.text(context.getString(R.string.report_avg_period, it)) }
            ?: writer.text(context.getString(R.string.report_usual_period, data.usualPeriodLength))
        if (s.shortestCycle != null && s.longestCycle != null) {
            writer.text(context.getString(R.string.report_range, s.shortestCycle, s.longestCycle))
        }
        writer.text(
            context.getString(
                when (s.regularity) {
                    Regularity.REGULAR -> R.string.report_regular
                    Regularity.IRREGULAR -> R.string.report_irregular
                    Regularity.NOT_ENOUGH_DATA -> R.string.report_regularity_unknown
                },
            ),
        )
        writer.gap()

        writer.heading(context.getString(R.string.report_history))
        writer.row(context.getString(R.string.report_col_start), context.getString(R.string.report_col_length), context.getString(R.string.report_col_period), bold = true)
        data.cycles.take(MAX_CYCLES).forEach { item ->
            val length = if (item.isCurrent) context.getString(R.string.report_current, item.elapsedDays) else item.segment.length.toString()
            writer.row(item.start.format(dates), length, item.periodLength.toString())
        }
        writer.gap()

        if (data.symptoms.isNotEmpty()) {
            writer.heading(context.getString(R.string.report_symptoms))
            data.symptoms.take(MAX_SYMPTOMS).forEach { (symptom, days) ->
                writer.text(context.getString(R.string.report_symptom_days, context.getString(symptom.labelRes()), days))
            }
            writer.gap()
        }
        val active = data.medications.filter { it.isActive }
        if (active.isNotEmpty()) {
            writer.heading(context.getString(R.string.report_medications))
            active.forEach { med ->
                writer.wrapped(med.dosage?.takeIf { it.isNotBlank() }?.let { context.getString(R.string.report_medication_dose, med.name, it) } ?: med.name)
            }
            writer.gap()
        }
        writer.small(context.getString(R.string.report_disclaimer))
        writer.finish()
    }

    /** Lines top to bottom, with a new page when one is full. */
    private class Writer(private val document: PdfDocument) {
        private var pageNumber = 0
        private var page: PdfDocument.Page = newPage()
        private var y = MARGIN
        private val body = paint(11f)
        private val bold = paint(11f, Typeface.BOLD)
        private val heading = paint(14f, Typeface.BOLD)
        private val titlePaint = paint(20f, Typeface.BOLD)
        private val smallPaint = paint(9f)

        fun title(text: String) = line(text, titlePaint, LINE * 1.6f)
        fun heading(text: String) = line(text, heading, LINE * 1.3f)
        fun text(text: String) = line(text, body, LINE)
        fun wrapped(text: String) = wrap(text, body).forEach { line(it, body, LINE) }
        fun small(text: String) = wrap(text, smallPaint).forEach { line(it, smallPaint, LINE * 0.8f) }
        fun gap() {
            y += LINE / 2
        }

        fun row(a: String, b: String, c: String, bold: Boolean = false) {
            val p = if (bold) this.bold else body
            ensure(LINE)
            page.canvas.drawText(a, MARGIN, y, p)
            page.canvas.drawText(b, MARGIN + COLUMN_2, y, p)
            page.canvas.drawText(c, MARGIN + COLUMN_3, y, p)
            y += LINE
        }

        fun finish() = document.finishPage(page)

        private fun line(text: String, paint: Paint, height: Float) {
            ensure(height)
            page.canvas.drawText(text, MARGIN, y, paint)
            y += height
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                page = newPage()
                y = MARGIN
            }
        }

        private fun newPage(): PdfDocument.Page {
            pageNumber++
            return document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        }

        private fun wrap(text: String, paint: Paint): List<String> {
            val width = PAGE_WIDTH - 2 * MARGIN
            val lines = mutableListOf<String>()
            var current = ""
            text.split(" ").forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) > width && current.isNotEmpty()) {
                    lines += current
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }

        private fun paint(size: Float, style: Int = Typeface.NORMAL) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

        private companion object {
            const val COLUMN_2 = 200f
            const val COLUMN_3 = 330f
        }
    }
}
