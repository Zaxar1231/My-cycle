package io.github.zaxar1231.mycycle.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.ui.component.PlaceholderScreen
import kotlinx.serialization.Serializable

@Serializable
data object StatsRoute

fun NavGraphBuilder.statsScreen() {
    composable<StatsRoute> { StatsScreen() }
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.stats_title),
        text = stringResource(R.string.stats_placeholder),
        modifier = modifier,
    )
}
