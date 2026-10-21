package io.github.zaxar1231.mycycle.core.ui.component

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.model.ThemeMode
import io.github.zaxar1231.mycycle.core.domain.model.ThemeSettings
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle
import io.github.zaxar1231.mycycle.core.ui.R

/** Theme style and light/dark choice, shared by onboarding and settings. */
@Composable
fun ThemePicker(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.theme_style), style = MaterialTheme.typography.titleSmall)
        Choice(
            options = ThemeStyle.entries,
            selected = settings.style,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(settings.copy(style = it)) },
        )
        if (settings.style == ThemeStyle.MATERIAL_YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Text(
                stringResource(R.string.theme_material_you_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleSmall)
        Choice(
            options = ThemeMode.entries,
            selected = settings.mode,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(settings.copy(mode = it)) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.theme_flowers), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = settings.flowers, onCheckedChange = { onChange(settings.copy(flowers = it)) })
        }
    }
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

private fun ThemeStyle.labelRes() = when (this) {
    ThemeStyle.ROSE -> R.string.theme_style_rose
    ThemeStyle.MATERIAL_YOU -> R.string.theme_style_material_you
    ThemeStyle.TWILIGHT -> R.string.theme_style_twilight
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}
