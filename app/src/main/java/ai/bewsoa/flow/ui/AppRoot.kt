package ai.bewsoa.flow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.XpRepository
import ai.bewsoa.flow.ui.alerts.AlertsScreen
import ai.bewsoa.flow.ui.chat.ChatScreen
import ai.bewsoa.flow.ui.components.AppBackground
import ai.bewsoa.flow.ui.components.CelebrationHost
import ai.bewsoa.flow.ui.focus.FocusScreen
import ai.bewsoa.flow.ui.guide.GUIDE_VERSION
import ai.bewsoa.flow.ui.guide.GuideScreen
import ai.bewsoa.flow.ui.guide.WhatsNewOverlay
import ai.bewsoa.flow.ui.progress.ProgressScreen
import ai.bewsoa.flow.ui.review.ReviewScreen
import ai.bewsoa.flow.ui.settings.SettingsScreen
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.today.TodayScreen
import kotlinx.coroutines.launch

private data class Dest(val route: String, val label: String, val icon: ImageVector)

/**
 * The bottom bar, and nothing else. Five tabs is the ceiling — Review, Alerts
 * and the Guide are reachable from the screens that own them (see [Routes])
 * rather than competing for a slot here.
 *
 * Focus keeps the centre slot it had at v1.4: the "start deep work" button
 * belongs under the thumb.
 */
private val destinations = listOf(
    Dest(Routes.TODAY, "Today", Icons.Rounded.Bolt),
    Dest(Routes.PROGRESS, "Progress", Icons.Rounded.CalendarViewWeek),
    Dest(Routes.FOCUS, "Focus", Icons.Rounded.CenterFocusStrong),
    Dest(Routes.CHAT, "Chat", Icons.Rounded.AutoAwesome),
    Dest(Routes.SETTINGS, "Settings", Icons.Rounded.Settings)
)

object Routes {
    const val TODAY = "today"
    const val FOCUS = "focus"
    const val PROGRESS = "progress"
    const val CHAT = "chat"
    const val SETTINGS = "settings"

    /** Pushed on top of a tab, not tabs themselves. */
    const val ALERTS = "alerts"
    const val REVIEW = "review"
    const val GUIDE = "guide"
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTab = destinations.any { it.route == currentRoute }

    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    // Int.MAX_VALUE until the real value loads, so the overlay never flashes.
    val seenVersion by settings.seenVersionCode
        .collectAsStateWithLifecycle(initialValue = Int.MAX_VALUE)
    val scope = rememberCoroutineScope()

    AppBackground {
        if (seenVersion < GUIDE_VERSION) {
            // First launch after an update: walk through what changed + the map.
            WhatsNewOverlay(
                onDone = { scope.launch { settings.setSeenVersionCode(GUIDE_VERSION) } }
            )
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { if (isTab) BottomBar(navController, currentRoute) }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.TODAY,
                    modifier = Modifier.padding(padding)
                ) {
                    composable(Routes.TODAY) {
                        TodayScreen(onOpenAlerts = { navController.navigate(Routes.ALERTS) })
                    }
                    composable(Routes.PROGRESS) {
                        ProgressScreen(onOpenReview = { navController.navigate(Routes.REVIEW) })
                    }
                    composable(Routes.FOCUS) { FocusScreen() }
                    composable(Routes.CHAT) { ChatScreen() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                            onOpenGuide = { navController.navigate(Routes.GUIDE) }
                        )
                    }
                    composable(Routes.ALERTS) {
                        SubScreen("Alerts", navController::popBackStack) { AlertsScreen() }
                    }
                    composable(Routes.REVIEW) {
                        SubScreen("Weekly review", navController::popBackStack) { ReviewScreen() }
                    }
                    // Guide brings its own back affordance.
                    composable(Routes.GUIDE) {
                        GuideScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
        // Above everything, including the bottom bar: a level-up earned from a
        // widget or a chest tap celebrates the same way no matter the screen.
        val xp = remember { XpRepository.get(context) }
        CelebrationHost(xp.celebrations)
    }
}

/** A pushed screen: back affordance, no bottom bar. */
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.s, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = LocalPalette.current.textBright
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = LocalPalette.current.textBright
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    val palette = LocalPalette.current
    // Opaque: the old 94%-alpha bar over a gradient is what made this read muddy.
    NavigationBar(containerColor = palette.surface) {
        destinations.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route,
                onClick = {
                    if (currentRoute != dest.route) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.accent,
                    selectedTextColor = palette.accent,
                    unselectedIconColor = palette.textDim,
                    unselectedTextColor = palette.textDim,
                    indicatorColor = palette.primaryDeep
                )
            )
        }
    }
}
