package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.cycle.DayKind
import io.github.zaxar1231.mycycle.core.ui.theme.CycleColors
import io.github.zaxar1231.mycycle.core.ui.theme.TwilightThemeExtras
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Composable
fun MonthCalendar(
    month: YearMonth,
    today: LocalDate,
    prediction: CyclePrediction?,
    loggedDays: Set<LocalDate>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val title = remember(month, locale) {
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
        "$name ${month.year}"
    }
    val colors = TwilightThemeExtras.cycleColors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(
                    painterResource(UiR.drawable.ic_chevron_left),
                    stringResource(R.string.calendar_previous_month),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNext) {
                Icon(
                    painterResource(UiR.drawable.ic_chevron_left),
                    stringResource(R.string.calendar_next_month),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(HALF_TURN_DEGREES),
                )
            }
        }
        if (YearMonth.from(today) != month) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.calendar_back_to_today))
            }
        }
        Row {
            (0 until DAYS_IN_WEEK).forEach { i ->
                Text(
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale).uppercase(locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Whole weeks: the grid starts and ends with greyed days of the neighbouring months.
        val leadingDays = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val firstCell = month.atDay(1).minusDays(leadingDays.toLong())
        val cellCount = (leadingDays + month.lengthOfMonth() + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK * DAYS_IN_WEEK
        val dates = List(cellCount) { firstCell.plusDays(it.toLong()) }
        dates.chunked(DAYS_IN_WEEK).forEach { week ->
            val kinds = week.map { date -> prediction?.dayKind(date) ?: DayKind.OTHER }
            Row(Modifier.fillMaxWidth()) {
                week.forEachIndexed { index, date ->
                    val band = kinds[index].band()
                    DayCell(
                        date = date,
                        kind = kinds[index],
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        hasLog = date in loggedDays,
                        // A band is drawn as one rounded bar across consecutive days of the same kind.
                        joinStart = band != null && kinds.getOrNull(index - 1)?.band() == band,
                        joinEnd = band != null && kinds.getOrNull(index + 1)?.band() == band,
                        colors = colors,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        CalendarLegend(colors)
    }
}

/** Days drawn as a continuous bar: the period and the fertile window (ovulation sits inside it). */
private enum class Band { PERIOD, PREDICTED_PERIOD, FERTILE }

private fun DayKind.band(): Band? = when (this) {
    DayKind.PERIOD -> Band.PERIOD
    DayKind.PREDICTED_PERIOD -> Band.PREDICTED_PERIOD
    DayKind.FERTILE, DayKind.OVULATION -> Band.FERTILE
    DayKind.LUTEAL, DayKind.OTHER -> null
}

@Composable
private fun DayCell(
    date: LocalDate,
    kind: DayKind,
    inMonth: Boolean,
    isToday: Boolean,
    hasLog: Boolean,
    joinStart: Boolean,
    joinEnd: Boolean,
    colors: CycleColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bandColor = when (kind.band()) {
        Band.PERIOD -> colors.period
        Band.PREDICTED_PERIOD -> colors.predictedPeriod
        Band.FERTILE -> colors.fertile
        null -> Color.Transparent
    }.let { if (inMonth) it else it.copy(alpha = it.alpha * OUTSIDE_ALPHA) }
    val bandShape = RoundedCornerShape(
        topStartPercent = if (joinStart) 0 else HALF_PERCENT,
        bottomStartPercent = if (joinStart) 0 else HALF_PERCENT,
        topEndPercent = if (joinEnd) 0 else HALF_PERCENT,
        bottomEndPercent = if (joinEnd) 0 else HALF_PERCENT,
    )
    // Today and ovulation get a solid circle on top of the bar.
    val (circle, textColor) = when {
        isToday -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        kind == DayKind.OVULATION -> colors.ovulation to colors.onOvulation
        !inMonth -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OUTSIDE_ALPHA)
        // Neighbouring months stay grey even on a band; today and ovulation above keep their circle.
        kind.band() == Band.PERIOD -> Color.Transparent to colors.onPeriod
        kind.band() == Band.FERTILE -> Color.Transparent to colors.onFertile
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    val description = stringResource(kind.labelRes())
    Box(
        modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(BAND_HEIGHT)
                .background(bandColor, bandShape),
        )
        Box(
            Modifier
                .fillMaxHeight(BAND_HEIGHT)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(circle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (hasLog) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-2).dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(colors: CycleColors) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(DayKind.PERIOD, Modifier.background(colors.period))
        LegendItem(DayKind.PREDICTED_PERIOD, Modifier.background(colors.predictedPeriod))
        LegendItem(DayKind.FERTILE, Modifier.background(colors.fertile))
        LegendItem(DayKind.OVULATION, Modifier.background(colors.ovulation))
    }
}

@Composable
private fun LegendItem(kind: DayKind, swatch: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).then(swatch))
        Text(stringResource(kind.labelRes()), style = MaterialTheme.typography.labelSmall)
    }
}

fun DayKind.labelRes() = when (this) {
    DayKind.PERIOD -> R.string.kind_period
    DayKind.PREDICTED_PERIOD -> R.string.kind_predicted_period
    DayKind.FERTILE -> R.string.kind_fertile
    DayKind.OVULATION -> R.string.kind_ovulation
    DayKind.LUTEAL -> R.string.kind_luteal
    DayKind.OTHER -> R.string.kind_other
}

private const val DAYS_IN_WEEK = 7
private const val HALF_TURN_DEGREES = 180f
private const val HALF_PERCENT = 50
private const val BAND_HEIGHT = 0.78f
private const val OUTSIDE_ALPHA = 0.5f

