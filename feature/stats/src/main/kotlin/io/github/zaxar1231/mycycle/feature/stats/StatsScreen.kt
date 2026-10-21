package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data object StatsRoute

fun NavGraphBuilder.statsScreen(onOpenDayLog: (LocalDate) -> Unit) {
    composable<StatsRoute> { StatsScreen(onOpenDayLog = onOpenDayLog) }
}

private enum class StatsTab(val title: Int) {
    CYCLES(R.string.stats_tab_cycles),
    RECORDS(R.string.stats_tab_records),
    CHRONOLOGY(R.string.stats_tab_chronology),
}

@Composable
fun StatsScreen(
    onOpenDayLog: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val todayContent by viewModel.todayContent.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }

    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            StatsTab.entries.forEachIndexed { index, item ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(item.title)) })
            }
        }
        val current = state
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        when (StatsTab.entries[tab]) {
            StatsTab.CYCLES -> CyclesTab(current, todayContent, onEdit = { viewModel.startEditing(it.cycle) })
            StatsTab.RECORDS -> RecordsTab(current)
            StatsTab.CHRONOLOGY -> ChronologyTab(current, onOpenDayLog)
        }
    }

    val edit = editing
    val loaded = state
    val item = edit?.let { e -> loaded?.history?.firstOrNull { it.cycle.id == e.cycleId } }
    if (edit != null && loaded != null && item != null) {
        EditCycleDialog(
            item = item,
            today = loaded.today,
            edit = edit,
            onSave = { start, length -> viewModel.save(item.cycle, start, length) },
            onDelete = { viewModel.delete(item.cycle) },
            onDismiss = viewModel::stopEditing,
        )
    }
}
