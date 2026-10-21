package io.github.zaxar1231.mycycle.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.zaxar1231.mycycle.R
import io.github.zaxar1231.mycycle.feature.calendar.CalendarRoute
import io.github.zaxar1231.mycycle.feature.calendar.calendarScreen
import io.github.zaxar1231.mycycle.feature.daylog.DayLogRoute
import io.github.zaxar1231.mycycle.feature.daylog.MedicationsRoute
import io.github.zaxar1231.mycycle.feature.daylog.dayLogScreen
import io.github.zaxar1231.mycycle.feature.daylog.medicationsScreen
import io.github.zaxar1231.mycycle.feature.insights.HormonesRoute
import io.github.zaxar1231.mycycle.feature.insights.PhasesRoute
import io.github.zaxar1231.mycycle.feature.insights.insightsScreens
import io.github.zaxar1231.mycycle.feature.onboarding.OnboardingRoute
import io.github.zaxar1231.mycycle.feature.onboarding.onboardingScreen
import io.github.zaxar1231.mycycle.feature.pet.petScreen
import io.github.zaxar1231.mycycle.feature.settings.settingsScreen
import io.github.zaxar1231.mycycle.feature.stats.statsScreen
import java.time.LocalDate
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Composable
fun TwilightAppRoot(
    startWithOnboarding: Boolean,
    appearanceSection: @Composable () -> Unit,
    updatesSection: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    // Decided once: finishing onboarding navigates on its own instead of swapping the graph start.
    val startDestination: Any = remember { if (startWithOnboarding) OnboardingRoute else CalendarRoute }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBars = TopLevelDestination.entries.any { destination.isOn(it) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBars) {
                BottomBar(navController, destination, onAddLog = {
                    navController.navigate(DayLogRoute(LocalDate.now())) { launchSingleTop = true }
                })
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            onboardingScreen(onFinished = {
                navController.navigate(CalendarRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            })
            calendarScreen(
                onOpenDayLog = { date -> navController.navigate(DayLogRoute(date)) { launchSingleTop = true } },
                onOpenPhases = { navController.navigate(PhasesRoute) { launchSingleTop = true } },
                onOpenHormones = { navController.navigate(HormonesRoute) { launchSingleTop = true } },
            )
            insightsScreens(onBack = { navController.navigateUp() })
            statsScreen(onOpenDayLog = { date -> navController.navigate(DayLogRoute(date)) { launchSingleTop = true } })
            petScreen()
            settingsScreen(appearanceSection = appearanceSection, updatesSection = updatesSection)
            dayLogScreen(
                onBack = { navController.navigateUp() },
                onManageMedications = { navController.navigate(MedicationsRoute) { launchSingleTop = true } },
            )
            medicationsScreen(onBack = { navController.navigateUp() })
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, destination: NavDestination?, onAddLog: () -> Unit) {
    NavigationBar {
        TopLevelDestination.leftOfFab.forEach { TabItem(it, destination, navController) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = onAddLog) {
                Icon(painterResource(UiR.drawable.ic_add), stringResource(R.string.nav_add_log))
            }
        }
        TopLevelDestination.rightOfFab.forEach { TabItem(it, destination, navController) }
    }
}

@Composable
private fun RowScope.TabItem(tab: TopLevelDestination, destination: NavDestination?, navController: NavHostController) {
    NavigationBarItem(
        selected = destination.isOn(tab),
        onClick = {
            navController.navigate(tab.route) {
                // Not graph.findStartDestination(): after onboarding the start route is no longer on the stack.
                popUpTo(CalendarRoute) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
        label = { TabLabel(stringResource(tab.label)) },
    )
}

/** One line always: with four tabs and the FAB, long words or a large font would otherwise wrap by letters. */
@Composable
private fun TabLabel(text: String) {
    val style = MaterialTheme.typography.labelMedium
    BasicText(
        text = text,
        style = style.copy(color = LocalContentColor.current),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
    )
}

private val MIN_LABEL_SIZE = 8.sp

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
