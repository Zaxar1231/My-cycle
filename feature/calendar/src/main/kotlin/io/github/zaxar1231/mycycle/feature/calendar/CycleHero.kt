package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePrediction
import io.github.zaxar1231.mycycle.core.domain.insights.PregnancyChanceModel
import io.github.zaxar1231.mycycle.core.ui.component.FlowerBackdrop
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import io.github.zaxar1231.mycycle.core.ui.theme.LocalShowFlowers
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import io.github.zaxar1231.mycycle.core.ui.R as UiR

/** One page of the ring: a big value, what it means and an optional small note. */
private class HeroPage(val value: String, val label: String, val note: String? = null)

/**
 * The top of the home screen: a large ring over decorative flowers. Swiping the ring shows days
 * until the period, days until the fertile window, the cycle day and the chance to conceive.
 */
@Composable
fun CycleHero(
    prediction: CyclePrediction,
    today: LocalDate,
    onEditPeriod: () -> Unit,
    onPeriodStarted: () -> Unit,
    onOpenTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = prediction.today
    val pages = heroPages(prediction, today)
    BoxWithConstraints(modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        if (LocalShowFlowers.current) {
            FlowerBackdrop(
                colors = listOf(MaterialTheme.colorScheme.primary, CORAL, MAGENTA, MaterialTheme.colorScheme.secondary),
                dark = MaterialTheme.colorScheme.background.luminance() < HALF,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val ring = min(maxWidth * RING_SHARE, MAX_RING)
        Box(
            Modifier
                .align(Alignment.Center)
                .size(ring)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
                .border(RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(horizontal = RING_WIDTH + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    pages.isNotEmpty() -> {
                        val pager = rememberPagerState { pages.size }
                        // A fixed height: pages differ in lines, and the ring must not jump while swiping.
                        HorizontalPager(pager, Modifier.fillMaxWidth().height(ring * PAGER_SHARE)) { index -> PageContent(pages[index]) }
                        // When the period is due or late, marking its start is the main action.
                        val due = status != null && (status.daysLate > 0 || status.daysUntilNextPeriod == 0)
                        if (due) {
                            Button(onClick = onPeriodStarted) { Text(stringResource(R.string.hero_period_started), textAlign = TextAlign.Center) }
                        } else {
                            Button(onClick = onEditPeriod) { Text(stringResource(R.string.hero_edit_period), textAlign = TextAlign.Center) }
                        }
                        PageDots(count = pages.size, current = pager.currentPage)
                    }
                    status == null && prediction.timeline.isNotEmpty() -> Text(
                        stringResource(R.string.today_cycle_in_future),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    else -> {
                        Text(stringResource(R.string.today_no_data_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Button(onClick = onPeriodStarted) { Text(stringResource(R.string.today_period_started), textAlign = TextAlign.Center) }
                    }
                }
            }
        }
        Surface(
            onClick = onOpenTheme,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.background.copy(alpha = PALETTE_ALPHA),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(PALETTE_SIZE),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(UiR.drawable.ic_palette),
                    contentDescription = stringResource(R.string.hero_theme),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Text sizes shrink to fit: "Сегодня" in 80sp or a large font scale would not fit the ring. */
@Composable
private fun PageContent(page: HeroPage) {
    val color = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            page.value,
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, color = color, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_VALUE_SIZE, maxFontSize = VALUE_SIZE),
        )
        BasicText(
            page.label,
            style = MaterialTheme.typography.titleLarge.copy(color = color, textAlign = TextAlign.Center),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = MaterialTheme.typography.titleLarge.fontSize),
        )
        page.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(DOT_SIZE).clip(CircleShape).background(color))
        }
    }
}

/** Empty when there is no current cycle to describe. */
@Composable
private fun heroPages(prediction: CyclePrediction, today: LocalDate): List<HeroPage> {
    val s = prediction.today ?: return emptyList()
    val due = s.daysLate > 0 || s.daysUntilNextPeriod == 0
    val todayWord = stringResource(R.string.hero_today)
    return buildList {
        add(
            when {
                s.daysLate > 0 -> HeroPage(s.daysLate.toString(), pluralStringResource(R.plurals.hero_days_late, s.daysLate))
                s.daysUntilNextPeriod == 0 -> HeroPage(todayWord, stringResource(R.string.hero_period_expected))
                s.phase == CyclePhase.MENSTRUAL -> HeroPage(s.cycleDay.toString(), stringResource(R.string.hero_period_day))
                else -> HeroPage(s.daysUntilNextPeriod.toString(), pluralStringResource(R.plurals.hero_until_period, s.daysUntilNextPeriod))
            },
        )
        if (!due) {
            val untilFertile = ChronoUnit.DAYS.between(today, s.fertileWindow.start).toInt()
            add(
                when {
                    untilFertile > 0 -> HeroPage(untilFertile.toString(), pluralStringResource(R.plurals.hero_until_fertile, untilFertile))
                    s.daysUntilOvulation > 0 -> HeroPage(
                        s.daysUntilOvulation.toString(),
                        pluralStringResource(R.plurals.hero_until_ovulation, s.daysUntilOvulation),
                        stringResource(R.string.hero_fertile_now),
                    )
                    else -> HeroPage(todayWord, stringResource(R.string.hero_ovulation))
                },
            )
        }
        add(HeroPage(s.cycleDay.toString(), stringResource(R.string.hero_cycle_day), stringResource(s.phase.labelRes())))
        if (!due) {
            PregnancyChanceModel.on(today, prediction)?.let { chance ->
                add(
                    HeroPage(
                        stringResource(R.string.hero_percent, chance.percent),
                        stringResource(R.string.hero_chance),
                        stringResource(R.string.chance_not_contraception),
                    ),
                )
            }
        }
    }
}

private val HERO_HEIGHT = 380.dp
private const val RING_SHARE = 0.78f
private val MAX_RING = 320.dp
private val RING_WIDTH = 14.dp
private val VALUE_SIZE = 80.sp
private val MIN_VALUE_SIZE = 24.sp
private val MIN_LABEL_SIZE = 12.sp
/** Share of the ring's diameter given to the swipeable pages. */
private const val PAGER_SHARE = 0.46f
private val DOT_SIZE = 8.dp
private val PALETTE_SIZE = 56.dp
private const val PALETTE_ALPHA = 0.6f
private const val HALF = 0.5f
private val CORAL = Color(0xFFE07A5F)
private val MAGENTA = Color(0xFFB0558F)
