package io.github.zaxar1231.mycycle.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.zaxar1231.mycycle.core.ui.component.PlaceholderScreen
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Serializable
data object CalendarRoute

fun NavGraphBuilder.calendarScreen() {
    composable<CalendarRoute> { CalendarScreen() }
}

@Composable
fun CalendarScreen(modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val today = viewModel.today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    PlaceholderScreen(
        title = stringResource(R.string.calendar_title),
        text = stringResource(R.string.calendar_placeholder, today),
        modifier = modifier,
    )
}
