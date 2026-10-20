package io.github.zaxar1231.mycycle.feature.daylog

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.ui.component.PlaceholderScreen
import kotlinx.serialization.Serializable
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Serializable
data object DayLogRoute

fun NavGraphBuilder.dayLogScreen(onBack: () -> Unit) {
    composable<DayLogRoute> { DayLogScreen(onBack = onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daylog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.daylog_back))
                    }
                },
            )
        },
    ) { padding ->
        PlaceholderScreen(
            title = stringResource(R.string.daylog_title),
            text = stringResource(R.string.daylog_placeholder),
            modifier = Modifier.padding(padding),
        )
    }
}
