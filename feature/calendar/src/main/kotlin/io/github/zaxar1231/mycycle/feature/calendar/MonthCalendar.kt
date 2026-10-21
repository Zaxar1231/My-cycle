package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
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
    val locale = Locale.getDefault()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val title = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val colors = TwilightThemeExtras.cycleColors

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.calendar_previous_month))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = onNext) {
                Icon(
                    painterResource(UiR.drawable.ic_arrow_back),
                    stringResource(R.string.calendar_next_month),
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
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val leadingBlanks = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
        val cells = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                kind = prediction?.dayKind(date) ?: DayKind.OTHER,
                                isToday = date == today,
                                hasLog = date in loggedDays,
                                colors = colors,
                                onClick = { onDayClick(date) },
                            )
                        }
                    }
                }
                repeat(DAYS_IN_WEEK - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        CalendarLegend(colors)
    }
}

@Composable
private fun DayCell(date: LocalDate, kind: DayKind, isToday: Boolean, hasLog: Boolean, colors: CycleColors, onClick: () -> Unit) {
    val (background, content) = when (kind) {
        DayKind.PERIOD -> colors.period to colors.onPeriod
        DayKind.FERTILE -> colors.fertile to colors.onFertile
        DayKind.OVULATION -> colors.ovulation to colors.onOvulation
        DayKind.LUTEAL -> colors.luteal.copy(alpha = LUTEAL_ALPHA) to MaterialTheme.colorScheme.onSurface
        DayKind.PREDICTED_PERIOD, DayKind.OTHER -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        kind == DayKind.PREDICTED_PERIOD -> BorderStroke(2.dp, colors.period)
        isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else -> null
    }
    val description = stringResource(kind.labelRes())
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = content,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (hasLog) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-4).dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(content),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend(colors: CycleColors) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(DayKind.PERIOD, Modifier.background(colors.period))
        LegendItem(DayKind.PREDICTED_PERIOD, Modifier.border(2.dp, colors.period, CircleShape))
        LegendItem(DayKind.FERTILE, Modifier.background(colors.fertile))
        LegendItem(DayKind.OVULATION, Modifier.background(colors.ovulation))
        LegendItem(DayKind.LUTEAL, Modifier.background(colors.luteal.copy(alpha = LUTEAL_ALPHA)))
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
private const val LUTEAL_ALPHA = 0.6f

