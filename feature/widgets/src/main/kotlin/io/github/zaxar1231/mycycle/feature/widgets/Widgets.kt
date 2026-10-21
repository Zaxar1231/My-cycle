package io.github.zaxar1231.mycycle.feature.widgets

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.zaxar1231.mycycle.core.domain.cycle.DayKind
import io.github.zaxar1231.mycycle.core.domain.model.DayLog
import io.github.zaxar1231.mycycle.core.domain.repository.DataAccessStatus
import io.github.zaxar1231.mycycle.core.domain.model.Mood
import io.github.zaxar1231.mycycle.core.domain.pet.PetShop
import io.github.zaxar1231.mycycle.core.ui.component.emoji
import io.github.zaxar1231.mycycle.core.ui.component.labelRes
import io.github.zaxar1231.mycycle.core.ui.pet.PetLook
import io.github.zaxar1231.mycycle.core.ui.pet.renderPetBitmap
import io.github.zaxar1231.mycycle.core.ui.theme.toCycleColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.temporal.ChronoUnit

/** Small: today's cycle day and phase. */
class CycleDayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val data = rememberWidgetData(context, initial)
            GlanceTheme { WidgetFrame(data) { ready -> CycleDayContent(ready) } }
        }
    }
}

/** Medium: days to the period and ovulation, with the cycle as a strip. */
class CycleOverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val data = rememberWidgetData(context, initial)
            val strip = remember(data) { (data as? WidgetData.Ready)?.let { cycleStrip(context, it) } }
            GlanceTheme { WidgetFrame(data) { ready -> CycleOverviewContent(ready, strip) } }
        }
    }
}

/** The pet in its current mood. */
class PetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val data = rememberWidgetData(context, initial)
            val picture = remember(data) {
                val ready = data as? WidgetData.Ready
                ready?.pet?.let { pet ->
                    val density = context.resources.displayMetrics.density
                    renderPetBitmap(PetLook(pet.type, ready.petMood, PetShop.worn(pet)), (PET_DP * density).toInt(), density)
                }
            }
            GlanceTheme { WidgetFrame(data) { ready -> PetContent(ready, picture) } }
        }
    }
}

/** Today's mood in one tap. */
class MoodWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetData.load(context)
        provideContent {
            val data = rememberWidgetData(context, initial)
            GlanceTheme { WidgetFrame(data) { ready -> MoodContent(ready) } }
        }
    }
}

/** Follows [WidgetData.observe] for as long as the widget session lives. */
@Composable
private fun rememberWidgetData(context: Context, initial: WidgetData): WidgetData {
    val flow = remember { WidgetData.observe(context) }
    return flow.collectAsState(initial).value
}

class CycleDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CycleDayWidget()
}

class CycleOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CycleOverviewWidget()
}

class PetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetWidget()
}

class MoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoodWidget()
}

/** The card around every widget; it opens the app and hides everything while a PIN is set. */
@Composable
private fun WidgetFrame(data: WidgetData, content: @Composable (WidgetData.Ready) -> Unit) {
    val context = LocalContext.current
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    var modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(GlanceTheme.colors.widgetBackground).padding(12.dp)
    if (open != null) modifier = modifier.clickable(actionStartActivity(open))
    Box(modifier, contentAlignment = Alignment.Center) {
        when (data) {
            is WidgetData.Ready -> content(data)
            WidgetData.Locked -> Message(context.getString(R.string.widget_locked))
            WidgetData.Unavailable -> Message(context.getString(R.string.widget_open_app))
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center))
}

@Composable
private fun CycleDayContent(data: WidgetData.Ready) {
    val context = LocalContext.current
    val status = data.prediction.today
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (status == null) {
            Message(context.getString(R.string.widget_no_data))
            return@Column
        }
        Text(
            context.getString(R.string.widget_day, status.cycleDay),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 30.sp, fontWeight = FontWeight.Bold),
        )
        Text(context.getString(status.phase.labelRes()), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
    }
}

@Composable
private fun CycleOverviewContent(data: WidgetData.Ready, strip: Bitmap?) {
    val context = LocalContext.current
    val status = data.prediction.today
    if (status == null) {
        Message(context.getString(R.string.widget_no_data))
        return
    }
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.widget_day, status.cycleDay),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(context.getString(status.phase.labelRes()), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
        }
        Spacer(GlanceModifier.height(4.dp))
        val period = when {
            status.daysLate > 0 -> context.resources.getQuantityString(R.plurals.widget_late, status.daysLate, status.daysLate)
            status.daysUntilNextPeriod == 0 -> context.getString(R.string.widget_period_today)
            else -> context.resources.getQuantityString(R.plurals.widget_period_in, status.daysUntilNextPeriod, status.daysUntilNextPeriod)
        }
        Text(period, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
        val ovulation = if (status.daysUntilOvulation == 0) {
            context.getString(R.string.widget_ovulation_today)
        } else {
            context.resources.getQuantityString(R.plurals.widget_ovulation_in, status.daysUntilOvulation, status.daysUntilOvulation)
        }
        Text(ovulation, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        strip?.let {
            Spacer(GlanceModifier.height(8.dp))
            Image(ImageProvider(it), context.getString(R.string.widget_strip_description), GlanceModifier.fillMaxWidth().height(14.dp))
        }
    }
}

@Composable
private fun PetContent(data: WidgetData.Ready, picture: Bitmap?) {
    val context = LocalContext.current
    val pet = data.pet
    if (pet == null || picture == null) {
        Message(context.getString(R.string.widget_no_pet))
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(ImageProvider(picture), context.getString(R.string.widget_pet_description), GlanceModifier.size(96.dp))
        Text(context.getString(R.string.widget_pet_level, pet.level, pet.coins), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp))
    }
}

@Composable
private fun MoodContent(data: WidgetData.Ready) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(context.getString(R.string.widget_mood_title), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
        Spacer(GlanceModifier.height(6.dp))
        Row {
            WIDGET_MOODS.forEach { mood ->
                val selected = mood in data.todayMoods
                Box(
                    GlanceModifier
                        .size(40.dp)
                        .cornerRadius(20.dp)
                        .background(if (selected) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.widgetBackground)
                        .clickable(actionRunCallback<ToggleMoodAction>(actionParametersOf(MOOD_KEY to mood.name))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mood.emoji(), style = TextStyle(fontSize = 20.sp))
                }
            }
        }
    }
}

/** Adds or removes a mood in today's log, then refreshes the widgets. */
class ToggleMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mood = parameters[MOOD_KEY]?.let { name -> Mood.entries.firstOrNull { it.name == name } } ?: return
        val deps = context.widgetDependencies()
        if (deps.pin().hasPin.value) return
        try {
            if (deps.dataAccess().check() != DataAccessStatus.READY) return
            val today = deps.dates().today()
            val periodLength = deps.settings().settings.first().usualPeriodLength
            // Read and write together, so an edit in the app at the same moment is not lost.
            val moods = deps.transactions().inTransaction {
                val log = deps.dayLogs().get(today) ?: DayLog(date = today)
                val moods = if (mood in log.moods) log.moods - mood else log.moods + mood
                deps.saveDayLog()(log.copy(moods = moods), periodLength)
                moods
            }
            if (moods.isNotEmpty()) deps.rewardPet().dayLogged(today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not saved; the widget keeps showing the stored moods.
        }
        deps.refresher().refreshNow()
    }
}

/** The whole cycle as a strip: period, fertile days, ovulation and the rest, with today marked. */
private fun cycleStrip(context: Context, data: WidgetData.Ready): Bitmap? {
    val segment = data.prediction.segmentFor(data.today) ?: return null
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val colors = scheme.toCycleColors()
    val days = segment.length
    val bitmap = Bitmap.createBitmap(STRIP_WIDTH_PX, STRIP_HEIGHT_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cell = STRIP_WIDTH_PX.toFloat() / days
    for (i in 0 until days) {
        val date = segment.start.plusDays(i.toLong())
        paint.color = when (data.prediction.dayKind(date)) {
            DayKind.PERIOD, DayKind.PREDICTED_PERIOD -> colors.period
            DayKind.OVULATION -> colors.ovulation
            DayKind.FERTILE -> colors.fertile
            DayKind.LUTEAL -> colors.luteal
            DayKind.OTHER -> colors.follicular
        }.toArgb()
        val inset = if (ChronoUnit.DAYS.between(data.today, date) == 0L) 0f else STRIP_HEIGHT_PX * 0.2f
        canvas.drawRoundRect(RectF(i * cell + 1f, inset, (i + 1) * cell - 1f, STRIP_HEIGHT_PX - inset), 6f, 6f, paint)
    }
    return bitmap
}

internal val MOOD_KEY = ActionParameters.Key<String>("mood")
private val WIDGET_MOODS = listOf(Mood.HAPPY, Mood.CALM, Mood.TIRED, Mood.SAD, Mood.ANXIOUS)
private const val PET_DP = 96
private const val STRIP_WIDTH_PX = 560
private const val STRIP_HEIGHT_PX = 40
