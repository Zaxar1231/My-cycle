package io.github.zaxar1231.mycycle.feature.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.zaxar1231.mycycle.core.domain.model.ThemeStyle
import io.github.zaxar1231.mycycle.core.ui.theme.GlassBackdrop
import io.github.zaxar1231.mycycle.core.ui.theme.TwilightThemeExtras
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zaxar1231.mycycle.core.domain.security.PinPolicy
import kotlinx.coroutines.delay

/**
 * A full-screen PIN prompt: title, dots for the entered digits, a message line and a keypad.
 * [bottom] holds extra actions such as "Forgot PIN?".
 */
@Composable
internal fun PinScreen(
    title: String,
    entered: Int,
    message: String?,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)? = null,
    bottom: @Composable () -> Unit = {},
) {
    val style = TwilightThemeExtras.style
    Box(Modifier.fillMaxSize()) {
        // Opaque in every style: nothing of the app may show through the lock screen.
        if (style == ThemeStyle.LIQUID_GLASS) {
            GlassBackdrop(MaterialTheme.colorScheme, TwilightThemeExtras.solidBackground)
        } else {
            Box(Modifier.fillMaxSize().background(TwilightThemeExtras.solidBackground))
        }
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            PinDots(entered)
            Text(
                message.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Keypad(enabled, onDigit, onBackspace, onBiometric)
            bottom()
        }
    }
}

@Composable
private fun PinDots(entered: Int) {
    val description = stringResource(R.string.pin_dots_description, entered, PinPolicy.LENGTH)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.semantics { contentDescription = description }) {
        repeat(PinPolicy.LENGTH) { index ->
            val filled = index < entered
            Box(
                Modifier
                    .size(DOT_SIZE)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            )
        }
    }
}

@Composable
private fun Keypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit, onBiometric: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { DigitKey(it, enabled, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                if (onBiometric != null) {
                    IconButton(onClick = onBiometric, modifier = Modifier.size(KEY_SIZE)) {
                        Icon(painterResource(R.drawable.ic_fingerprint), stringResource(R.string.pin_biometric))
                    }
                }
            }
            DigitKey('0', enabled, onDigit)
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(KEY_SIZE)) {
                Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.pin_backspace))
            }
        }
    }
}

/** Keys follow the theme: glass panes, glowing neon rings, inked paper squares or tonal circles. */
@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    val label: @Composable () -> Unit = { Text(digit.toString(), style = MaterialTheme.typography.headlineSmall) }
    val onClick = { onDigit(digit) }
    val scheme = MaterialTheme.colorScheme
    when (TwilightThemeExtras.style) {
        ThemeStyle.LIQUID_GLASS -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = GLASS_EDGE_ALPHA)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = GLASS_PANE_ALPHA), contentColor = scheme.onSurface),
            modifier = Modifier.size(KEY_SIZE),
            content = { label() },
        )
        ThemeStyle.NEON -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(NEON_RING, scheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary),
            modifier = Modifier
                .size(KEY_SIZE)
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(scheme.primary.copy(alpha = NEON_GLOW_ALPHA), Color.Transparent)), size.minDimension * NEON_GLOW_SCALE)
                },
            content = { label() },
        )
        ThemeStyle.PAPER -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(PAPER_CORNER),
            border = BorderStroke(1.dp, scheme.onSurface.copy(alpha = PAPER_INK_ALPHA)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
            modifier = Modifier.size(KEY_SIZE),
            content = { label() },
        )
        ThemeStyle.AMOLED -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = AMOLED_RING_ALPHA)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
            modifier = Modifier.size(KEY_SIZE),
            content = { label() },
        )
        else -> FilledTonalButton(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.size(KEY_SIZE), content = { label() })
    }
}

/** "Wrong PIN, N attempts left" or a live countdown while input is frozen. */
@Composable
internal fun pinMessage(state: PinEntryState, onFreezeEnded: () -> Unit): String? {
    val until = state.frozenUntil
    if (until != null) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(until) {
            while (true) {
                now = System.currentTimeMillis()
                if (now >= until) {
                    onFreezeEnded()
                    break
                }
                delay(TICK_MILLIS)
            }
        }
        val seconds = ((until - now).coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        if (seconds > 0) return stringResource(R.string.pin_frozen, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }
    val left = state.attemptsLeft ?: return null
    return pluralStringResource(R.plurals.pin_wrong, left, left)
}

private val DOT_SIZE = 16.dp
private val KEY_SIZE = 72.dp
private const val GLASS_EDGE_ALPHA = 0.45f
private const val GLASS_PANE_ALPHA = 0.16f
private val NEON_RING = 1.5.dp
private const val NEON_GLOW_ALPHA = 0.35f
private const val NEON_GLOW_SCALE = 0.7f
private val PAPER_CORNER = 14.dp
private const val PAPER_INK_ALPHA = 0.55f
private const val AMOLED_RING_ALPHA = 0.6f
private const val TICK_MILLIS = 250L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
