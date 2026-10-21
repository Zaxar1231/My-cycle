package io.github.zaxar1231.mycycle.core.ui.component

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.model.AppFont
import io.github.zaxar1231.mycycle.core.domain.model.HomeStyle
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle
import io.github.zaxar1231.mycycle.core.ui.R
import io.github.zaxar1231.mycycle.core.ui.theme.CustomFonts
import io.github.zaxar1231.mycycle.core.ui.theme.family
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Home screen picture, theme style with the colour wheel, and light/dark, shared by onboarding and settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(
    settings: ThemeSettings,
    onChange: (ThemeSettings) -> Unit,
    modifier: Modifier = Modifier,
    onSeedChange: (Int) -> Unit = { onChange(settings.copy(seedColor = it)) },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.theme_home), style = MaterialTheme.typography.titleSmall)
        Chips(HomeStyle.entries, settings.home, { stringResource(it.labelRes()) }) { onChange(settings.copy(home = it)) }
        if (settings.home == HomeStyle.RING) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.theme_flowers), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.flowers, onCheckedChange = { onChange(settings.copy(flowers = it)) })
            }
        }

        Text(stringResource(R.string.theme_style), style = MaterialTheme.typography.titleSmall)
        Chips(ThemeStyle.entries, settings.style, { stringResource(it.labelRes()) }) { onChange(settings.copy(style = it)) }
        if (settings.style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Hint(R.string.theme_material_you_unsupported)
        }
        if (settings.style.alwaysDark) Hint(R.string.theme_always_dark)
        if (settings.style == ThemeStyle.LIQUID_GLASS && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Hint(R.string.theme_glass_no_blur)
        }
        if (settings.style == ThemeStyle.CUSTOM) {
            Hint(R.string.theme_custom_hint)
            SeedPicker(settings.seedColor, onSeedChange)
        }

        Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    enabled = !settings.style.alwaysDark,
                    // Always-dark styles show "Dark"; the saved mode returns with another style.
                    selected = if (settings.style.alwaysDark) option == ThemeMode.DARK else option == settings.mode,
                    onClick = { onChange(settings.copy(mode = option)) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                ) { Text(stringResource(option.labelRes())) }
            }
        }

        Text(stringResource(R.string.theme_font), style = MaterialTheme.typography.titleSmall)
        FontChips(settings, onChange)
    }
}

/** Each bundled font is shown in itself; "Own file" opens the system file picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontChips(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var failed by rememberSaveable { mutableStateOf(false) }
    val missing = remember(settings.font, settings.customFontFile) {
        settings.font == AppFont.CUSTOM && settings.customFontFile?.let { CustomFonts.exists(context, it) } != true
    }
    val latestSettings by rememberUpdatedState(settings)
    val latestOnChange by rememberUpdatedState(onChange)
    // The picker returns a content URI; nothing but the chosen file is read.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Finishes even if the sheet closes meanwhile, so the copied file is always saved.
            scope.launch(NonCancellable) {
                val name = CustomFonts.import(appContext, uri, keep = latestSettings.customFontFile)
                failed = name == null
                if (name != null) latestOnChange(latestSettings.copy(font = AppFont.CUSTOM, customFontFile = name))
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppFont.entries.filter { it != AppFont.CUSTOM }.forEach { font ->
            val family = remember(font) { font.family(context, null) }
            FilterChip(
                selected = settings.font == font,
                onClick = {
                    failed = false
                    onChange(settings.copy(font = font))
                },
                label = { Text(stringResource(font.labelRes()), fontFamily = family) },
            )
        }
        FilterChip(
            selected = settings.font == AppFont.CUSTOM,
            onClick = {
                failed = false
                pick.launch(FONT_TYPES)
            },
            label = { Text(stringResource(R.string.font_custom)) },
        )
    }
    if (failed || missing) {
        Text(
            stringResource(if (failed) R.string.font_custom_failed else R.string.font_custom_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    } else if (settings.font == AppFont.CUSTOM) {
        Hint(R.string.font_custom_hint)
    }
}

private fun AppFont.labelRes() = when (this) {
    AppFont.SYSTEM -> R.string.font_system
    AppFont.INTER -> R.string.font_inter
    AppFont.NUNITO -> R.string.font_nunito
    AppFont.COMFORTAA -> R.string.font_comfortaa
    AppFont.LORA -> R.string.font_lora
    AppFont.CAVEAT -> R.string.font_caveat
    AppFont.CUSTOM -> R.string.font_custom
}

/** File managers label fonts differently; anything that is not a font is rejected after import. */
private val FONT_TYPES = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/font-sfnt",
    "application/vnd.ms-opentype",
    "application/octet-stream",
)

/**
 * The wheel moves on every frame of a drag; the colour is saved once the finger rests, and
 * a colour not yet saved is saved when the picker closes.
 */
@Composable
private fun SeedPicker(saved: Int, onCommit: (Int) -> Unit) {
    var seed by rememberSaveable { mutableIntStateOf(saved) }
    val commit by rememberUpdatedState(onCommit)
    val latestSaved by rememberUpdatedState(saved)
    val latestSeed by rememberUpdatedState(seed)
    LaunchedEffect(seed) {
        if (seed == latestSaved) return@LaunchedEffect
        delay(COMMIT_DELAY_MILLIS)
        commit(seed)
    }
    DisposableEffect(Unit) {
        onDispose { if (latestSeed != latestSaved) commit(latestSeed) }
    }
    ColorWheel(color = seed, onChange = { seed = it }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Hint(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

private fun HomeStyle.labelRes() = when (this) {
    HomeStyle.SKY -> R.string.home_sky
    HomeStyle.MOON -> R.string.home_moon
    HomeStyle.ORBIT -> R.string.home_orbit
    HomeStyle.WAVE -> R.string.home_wave
    HomeStyle.MOSAIC -> R.string.home_mosaic
    HomeStyle.RING -> R.string.home_ring
}

private fun ThemeStyle.labelRes() = when (this) {
    ThemeStyle.ROSE -> R.string.theme_style_rose
    ThemeStyle.MATERIAL_YOU -> R.string.theme_style_material_you
    ThemeStyle.TWILIGHT -> R.string.theme_style_twilight
    ThemeStyle.CUSTOM -> R.string.theme_style_custom
    ThemeStyle.LIQUID_GLASS -> R.string.theme_style_glass
    ThemeStyle.AMOLED -> R.string.theme_style_amoled
    ThemeStyle.PASTEL -> R.string.theme_style_pastel
    ThemeStyle.PAPER -> R.string.theme_style_paper
    ThemeStyle.NEON -> R.string.theme_style_neon
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

private const val COMMIT_DELAY_MILLIS = 300L
