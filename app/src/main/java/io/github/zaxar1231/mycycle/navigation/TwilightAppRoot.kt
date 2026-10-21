package io.github.zaxar1231.mycycle.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.zaxar1231.mycycle.R
import io.github.zaxar1231.mycycle.feature.account.AccountSection
import io.github.zaxar1231.mycycle.feature.account.AccountStepContent
import io.github.zaxar1231.mycycle.feature.backup.DataSection
import io.github.zaxar1231.mycycle.feature.calendar.CalendarRoute
import io.github.zaxar1231.mycycle.feature.calendar.HomeCustomizerRoute
import io.github.zaxar1231.mycycle.feature.calendar.homeCustomizerScreen
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
import io.github.zaxar1231.mycycle.feature.partner.PartnerHomeCard
import io.github.zaxar1231.mycycle.feature.partner.PartnerInviteNavigator
import io.github.zaxar1231.mycycle.feature.partner.PartnerRoute
import io.github.zaxar1231.mycycle.feature.partner.PartnerSection
import io.github.zaxar1231.mycycle.feature.partner.partnerScreen
import io.github.zaxar1231.mycycle.feature.pet.PetHomeCard
import io.github.zaxar1231.mycycle.feature.pet.PetRoute
import io.github.zaxar1231.mycycle.feature.pet.petScreen
import io.github.zaxar1231.mycycle.feature.reminders.RemindersSection
import io.github.zaxar1231.mycycle.feature.security.PinOffer
import io.github.zaxar1231.mycycle.feature.security.SecuritySection
import io.github.zaxar1231.mycycle.feature.settings.SettingsRoute
import io.github.zaxar1231.mycycle.feature.settings.settingsScreen
import io.github.zaxar1231.mycycle.feature.stats.statsScreen
import io.github.zaxar1231.mycycle.feature.wellness.AffirmationCard
import io.github.zaxar1231.mycycle.feature.wellness.BreathingRoute
import io.github.zaxar1231.mycycle.feature.wellness.FavoriteAffirmationsRoute
import io.github.zaxar1231.mycycle.feature.wellness.wellnessScreens
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
            onboardingScreen(
                onFinished = { navController.navigate(CalendarRoute) { popUpTo(OnboardingRoute) { inclusive = true } } },
                pinOffer = { PinOffer() },
                accountStep = { onSignedIn -> AccountStepContent(onSignedIn) },
            )
            calendarScreen(
                onOpenDayLog = { date -> navController.navigate(DayLogRoute(date)) { launchSingleTop = true } },
                onOpenPhases = { navController.navigate(PhasesRoute) { launchSingleTop = true } },
                onOpenHormones = { navController.navigate(HormonesRoute) { launchSingleTop = true } },
                onCustomizeHome = { navController.navigate(HomeCustomizerRoute) { launchSingleTop = true } },
                homeExtras = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PartnerHomeCard(onOpen = { navController.navigate(PartnerRoute) { launchSingleTop = true } })
                        AffirmationCard(
                            onOpenBreathing = { navController.navigate(BreathingRoute) { launchSingleTop = true } },
                            onOpenFavorites = { navController.navigate(FavoriteAffirmationsRoute) { launchSingleTop = true } },
                        )
                    }
                },
                petCard = {
                    PetHomeCard(onOpen = {
                        navController.navigate(PetRoute) {
                            popUpTo(CalendarRoute) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    })
                },
            )
            insightsScreens(onBack = { navController.navigateUp() })
            homeCustomizerScreen(onBack = { navController.navigateUp() })
            wellnessScreens(onBack = { navController.navigateUp() })
            statsScreen(onOpenDayLog = { date -> navController.navigate(DayLogRoute(date)) { launchSingleTop = true } })
            petScreen()
            settingsScreen(
                appearanceSection = appearanceSection,
                securitySection = { SecuritySection() },
                accountSection = { AccountSection() },
                remindersSection = { RemindersSection() },
                dataSection = { DataSection() },
                partnerSection = { PartnerSection(onOpenPartner = { navController.navigate(PartnerRoute) { launchSingleTop = true } }) },
                updatesSection = updatesSection,
                onCustomizeHome = { navController.navigate(HomeCustomizerRoute) { launchSingleTop = true } },
            )
            dayLogScreen(
                onBack = { navController.navigateUp() },
                onManageMedications = { navController.navigate(MedicationsRoute) { launchSingleTop = true } },
            )
            medicationsScreen(onBack = { navController.navigateUp() })
            partnerScreen(onBack = { navController.navigateUp() })
        }
        // An invitation link opens the settings, where the code is filled in for the partner to accept.
        if (!startWithOnboarding || destination?.hasRoute(OnboardingRoute::class) == false) {
            PartnerInviteNavigator(onInvite = {
                navController.navigate(SettingsRoute) {
                    popUpTo(CalendarRoute) { saveState = true }
                    launchSingleTop = true
                }
            })
        }
    }
}

/** Tabs with a large round "+" that rises above the bar. */
@Composable
private fun BottomBar(navController: NavHostController, destination: NavDestination?, onAddLog: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // The bar starts lower than the box, so the raised button stays inside it and receives touches.
        Column {
            Spacer(Modifier.height(FAB_RISE))
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.leftOfFab.forEach { TabItem(it, destination, navController) }
                Spacer(Modifier.weight(1f))
                TopLevelDestination.rightOfFab.forEach { TabItem(it, destination, navController) }
            }
        }
        FloatingActionButton(
            onClick = onAddLog,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(FAB_SIZE),
        ) {
            Icon(painterResource(UiR.drawable.ic_add), stringResource(R.string.nav_add_log), modifier = Modifier.size(FAB_ICON_SIZE))
        }
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
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = Color.Transparent,
        ),
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
private val FAB_SIZE = 72.dp
private val FAB_ICON_SIZE = 36.dp
/** How far the "+" rises above the bar. */
private val FAB_RISE = 28.dp

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
