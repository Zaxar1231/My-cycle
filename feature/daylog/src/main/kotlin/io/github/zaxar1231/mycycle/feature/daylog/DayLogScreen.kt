package io.github.zaxar1231.mycycle.feature.daylog

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.zaxar1231.mycycle.core.ui.component.PlaceholderScreen
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.zaxar1231.mycycle.core.ui.R as UiR

/** Log for one day. The date travels as epoch day, a plain Long works with type-safe routes. */
@Serializable
data class DayLogRoute(val epochDay: Long) {
    constructor(date: LocalDate) : this(date.toEpochDay())

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

fun NavGraphBuilder.dayLogScreen(onBack: () -> Unit) {
    composable<DayLogRoute> { entry -> DayLogScreen(date = entry.toRoute<DayLogRoute>().date, onBack = onBack) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayLogScreen(date: LocalDate, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(date.format(formatter)) },
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
