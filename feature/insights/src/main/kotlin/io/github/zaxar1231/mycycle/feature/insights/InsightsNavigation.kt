package io.github.zaxar1231.mycycle.feature.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.domain.cycle.CyclePhase
import io.github.zaxar1231.mycycle.core.domain.model.PhaseContent
import kotlinx.serialization.Serializable
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Serializable
data object HormonesRoute

@Serializable
data object PhasesRoute

fun NavGraphBuilder.insightsScreens(onBack: () -> Unit) {
    composable<HormonesRoute> {
        InsightsScaffold(R.string.hormones_title, onBack) { now, _, padding -> HormonesContent(now, padding) }
    }
    composable<PhasesRoute> {
        InsightsScaffold(R.string.phases_title, onBack) { now, content, padding -> PhasesContent(now, content, padding) }
    }
}

/** Top bar, loading and empty states shared by both screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightsScaffold(
    title: Int,
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
    body: @Composable (CycleNow, Map<CyclePhase, PhaseContent>, PaddingValues) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(language) { viewModel.setLanguage(language) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.insights_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            InsightsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            InsightsUiState.NoData -> Text(
                stringResource(R.string.insights_no_data),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            is InsightsUiState.Ready -> body(s.now, content, padding)
        }
    }
}
