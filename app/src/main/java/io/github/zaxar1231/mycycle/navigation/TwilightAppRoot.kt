package io.github.zaxar1231.mycycle.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.zaxar1231.mycycle.R
import io.github.zaxar1231.mycycle.feature.calendar.CalendarRoute
import io.github.zaxar1231.mycycle.feature.calendar.calendarScreen
import io.github.zaxar1231.mycycle.feature.daylog.DayLogRoute
import io.github.zaxar1231.mycycle.feature.daylog.dayLogScreen
import io.github.zaxar1231.mycycle.feature.pet.petScreen
import io.github.zaxar1231.mycycle.feature.settings.settingsScreen
import io.github.zaxar1231.mycycle.feature.stats.statsScreen
import io.github.zaxar1231.mycycle.core.ui.R as UiR

@Composable
fun TwilightAppRoot(updatesSection: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBars = TopLevelDestination.entries.any { destination.isOn(it) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBars) {
                BottomBar(navController, destination, onAddLog = {
                    navController.navigate(DayLogRoute) { launchSingleTop = true }
                })
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = CalendarRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            calendarScreen()
            statsScreen()
            petScreen()
            settingsScreen(updatesSection = updatesSection)
            dayLogScreen(onBack = { navController.navigateUp() })
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
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
        label = { Text(stringResource(tab.label)) },
    )
}

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
