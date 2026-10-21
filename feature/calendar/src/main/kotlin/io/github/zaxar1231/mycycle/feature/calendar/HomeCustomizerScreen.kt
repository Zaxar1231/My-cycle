package io.github.zaxar1231.mycycle.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.model.BlockLook
import io.github.zaxar1231.mycycle.core.domain.model.BlockSize
import io.github.zaxar1231.mycycle.core.domain.model.BlockStyle
import io.github.zaxar1231.mycycle.core.domain.model.HomeBlock
import io.github.zaxar1231.mycycle.core.domain.model.HomeLayout
import io.github.zaxar1231.mycycle.core.ui.component.BlockCard
import io.github.zaxar1231.mycycle.core.ui.component.ColorWheel
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Serializable
data object HomeCustomizerRoute

fun NavGraphBuilder.homeCustomizerScreen(onBack: () -> Unit) {
    composable<HomeCustomizerRoute> { HomeCustomizerScreen(onBack) }
}

/**
 * Order, visibility, size, look, corners and colour of every home block. Blocks move by dragging
 * the handle (after a long press) or with the arrow buttons, which also work with TalkBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeCustomizerScreen(onBack: () -> Unit, viewModel: HomeCustomizerViewModel = hiltViewModel()) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val saveFailed by viewModel.saveFailed.collectAsStateWithLifecycle()
    val close = {
        viewModel.flush()
        onBack()
    }
    BackHandler(onBack = close)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_customize)) },
                navigationIcon = {
                    IconButton(onClick = close) { Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.customize_back)) }
                },
                actions = { TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.customize_reset)) } },
            )
        },
    ) { padding ->
        val current = layout ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 24.dp, vertical = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            Text(stringResource(R.string.customize_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (saveFailed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
            BlockList(current, viewModel::move, viewModel::moveBlock, viewModel::setStyle)
        }
    }
}

@Composable
private fun BlockList(
    layout: HomeLayout,
    onMove: (Int, Int) -> Unit,
    onMoveBlock: (HomeBlock, Int) -> Unit,
    onStyle: (HomeBlock, BlockStyle) -> Unit,
) {
    val order by rememberUpdatedState(layout.order)
    // A swap moves the row by its neighbour's height plus the gap between rows.
    val gap = with(LocalDensity.current) { ROW_GAP.toPx() }
    val heights = remember { mutableStateMapOf<HomeBlock, Int>() }
    var dragged by remember { mutableStateOf<HomeBlock?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var expanded by rememberSaveable { mutableStateOf<HomeBlock?>(null) }

    layout.order.forEachIndexed { index, block ->
        key(block) {
            val isDragged = dragged == block
            BlockRow(
                block = block,
                style = layout.style(block),
                index = index,
                count = layout.order.size,
                expanded = expanded == block,
                onToggle = { expanded = if (expanded == block) null else block },
                onMove = onMove,
                onStyle = { onStyle(block, it) },
                handleModifier = Modifier.pointerInput(block) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragged = block
                            offset = 0f
                        },
                        onDragEnd = { dragged = null; offset = 0f },
                        onDragCancel = { dragged = null; offset = 0f },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        // Swap with a neighbour once dragged past half of its height.
                        val i = order.indexOf(block)
                        val next = order.getOrNull(i + 1)
                        val previous = order.getOrNull(i - 1)
                        val nextHeight = next?.let { heights[it] } ?: 0
                        val previousHeight = previous?.let { heights[it] } ?: 0
                        if (next != null && offset > nextHeight / 2f) {
                            onMoveBlock(block, 1)
                            offset -= nextHeight + gap
                        } else if (previous != null && offset < -previousHeight / 2f) {
                            onMoveBlock(block, -1)
                            offset += previousHeight + gap
                        }
                    }
                },
                modifier = Modifier
                    .onSizeChanged { heights[block] = it.height }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else 0f },
            )
        }
    }
}

@Composable
private fun BlockRow(
    block: HomeBlock,
    style: BlockStyle,
    index: Int,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onStyle: (BlockStyle) -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val name = stringResource(block.labelRes())
    // The row itself is drawn in the block's own style, as a live preview.
    BlockCard(modifier.animateContentSize(), style = style.copy(look = if (style.look == BlockLook.NONE) BlockLook.OUTLINED else style.look, visible = true)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dragLabel = stringResource(R.string.customize_drag, name)
                Icon(
                    painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null,
                    modifier = handleModifier.padding(12.dp).semantics { contentDescription = dragLabel },
                )
                TextButton(onClick = onToggle, modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                }
                IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.customize_up, name))
                }
                IconButton(onClick = { onMove(index, index + 1) }, enabled = index < count - 1) {
                    Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.customize_down, name))
                }
                val visibility = stringResource(R.string.customize_visible, name)
                Switch(
                    checked = style.visible,
                    onCheckedChange = { onStyle(style.copy(visible = it)) },
                    modifier = Modifier.semantics { contentDescription = visibility },
                )
            }
            if (expanded) StyleOptions(style, onStyle)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleOptions(style: BlockStyle, onStyle: (BlockStyle) -> Unit) {
    Text(stringResource(R.string.customize_look), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockLook.entries.forEach { look ->
            FilterChip(selected = style.look == look, onClick = { onStyle(style.copy(look = look)) }, label = { Text(stringResource(look.labelRes())) })
        }
    }
    Text(stringResource(R.string.customize_size), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockSize.entries.forEach { size ->
            FilterChip(selected = style.size == size, onClick = { onStyle(style.copy(size = size)) }, label = { Text(stringResource(size.labelRes())) })
        }
    }
    // Without a container there are no corners to round.
    if (style.look != BlockLook.NONE) {
        Text(stringResource(R.string.customize_corners, style.cornerDp), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = style.cornerDp.toFloat(),
            onValueChange = { onStyle(style.copy(cornerDp = it.roundToInt())) },
            valueRange = 0f..BlockStyle.MAX_CORNER.toFloat(),
        )
    }
    Text(stringResource(R.string.customize_color), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = style.color == null, onClick = { onStyle(style.copy(color = null)) }, label = { Text(stringResource(R.string.customize_color_theme)) })
        FilterChip(
            selected = style.color != null,
            onClick = { if (style.color == null) onStyle(style.copy(color = DEFAULT_BLOCK_COLOR)) },
            label = { Text(stringResource(R.string.customize_color_own)) },
        )
    }
    style.color?.let { color ->
        ColorWheel(color = color, onChange = { onStyle(style.copy(color = it)) }, modifier = Modifier.fillMaxWidth())
    }
}

private fun HomeBlock.labelRes() = when (this) {
    HomeBlock.HERO -> R.string.block_hero
    HomeBlock.KEY_DATES -> R.string.block_key_dates
    HomeBlock.CALENDAR -> R.string.block_calendar
    HomeBlock.TODAY -> R.string.block_today
    HomeBlock.AFFIRMATION -> R.string.block_affirmation
    HomeBlock.PET -> R.string.block_pet
}

private fun BlockLook.labelRes() = when (this) {
    BlockLook.NONE -> R.string.look_none
    BlockLook.FILLED -> R.string.look_filled
    BlockLook.TONAL -> R.string.look_tonal
    BlockLook.OUTLINED -> R.string.look_outlined
    BlockLook.GLASS -> R.string.look_glass
}

private fun BlockSize.labelRes() = when (this) {
    BlockSize.COMPACT -> R.string.size_compact
    BlockSize.NORMAL -> R.string.size_normal
    BlockSize.LARGE -> R.string.size_large
}

private const val DEFAULT_BLOCK_COLOR = 0xFFB4A6E0.toInt()
private val ROW_GAP = 12.dp
