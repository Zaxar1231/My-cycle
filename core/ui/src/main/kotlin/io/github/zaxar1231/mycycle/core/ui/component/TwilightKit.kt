package io.github.zaxar1231.mycycle.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/*
 * Twilight's own look. Built on Compose foundation, not on Material 3 components: the theme
 * colours still come from MaterialTheme, but the shapes, fills and edges below are ours.
 */

/** A soft panel: a faint primary glow fading into the card tone, with a hairline edge. */
@Composable
fun TwPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PANEL_CORNER)
    val fill = Brush.linearGradient(listOf(scheme.primary.copy(alpha = PANEL_GLOW), scheme.surfaceContainer))
    Box(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, scheme.outline.copy(alpha = EDGE_ALPHA), shape)
            .padding(PANEL_PADDING),
    ) {
        content()
    }
}

/** A toggle pill: filled with the accent when [selected], quiet otherwise. */
@Composable
fun TwPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = PILL_PADDING_H, vertical = PILL_PADDING_V),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimary else scheme.onSurface)
    }
}

/** A button-like capsule for one action. It is the only accent-filled shape on a screen. */
@Composable
fun TwAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(CircleShape)
            .background(scheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PILL_PADDING_H, vertical = PILL_PADDING_V),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
    }
}

private val PANEL_CORNER = 28.dp
private val PANEL_PADDING = 20.dp
private val PILL_PADDING_H = 16.dp
private val PILL_PADDING_V = 10.dp
private const val PANEL_GLOW = 0.16f
private const val EDGE_ALPHA = 0.25f
