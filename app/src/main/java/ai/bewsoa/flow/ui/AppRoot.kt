package ai.bewsoa.flow.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ai.bewsoa.flow.ui.alerts.AlertsScreen
import ai.bewsoa.flow.ui.components.GradientBackground
import ai.bewsoa.flow.ui.focus.FocusScreen
import ai.bewsoa.flow.ui.guide.GUIDE_VERSION
import ai.bewsoa.flow.ui.guide.GuideScreen
import ai.bewsoa.flow.ui.guide.WhatsNewOverlay
import ai.bewsoa.flow.ui.progress.ProgressScreen
import ai.bewsoa.flow.ui.review.ReviewScreen
import ai.bewsoa.flow.ui.settings.SettingsScreen
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.SurfaceNavy
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.VioletDeep
import ai.bewsoa.flow.ui.today.TodayScreen
import kotlinx.coroutines.launch

private data class Dest(val route: String, val label: String, val icon: ImageVector)

// Focus sits mid-bar on purpose: the "start deep work" button lives at the center.
private val destinations = listOf(
    Dest("today", "Today", Icons.Rounded.Bolt),
    Dest("progress", "Progress", Icons.Rounded.Insights),
    Dest("focus", "Focus", Icons.Rounded.CenterFocusStrong),
    Dest("review", "Review", Icons.Rounded.EditNote),
    Dest("alerts", "Alerts", Icons.Rounded.Notifications),
    Dest("settings", "Settings", Icons.Rounded.Settings)
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    // Int.MAX_VALUE until the real value loads, so the overlay never flashes.
    val seenVersion by settings.seenVersionCode
        .collectAsStateWithLifecycle(initialValue = Int.MAX_VALUE)
    val scope = rememberCoroutineScope()

    GradientBackground {
        if (seenVersion < GUIDE_VERSION) {
            // First launch after an update: walk through what changed + the map.
            WhatsNewOverlay(
                onDone = { scope.launch { settings.setSeenVersionCode(GUIDE_VERSION) } }
            )
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { BottomBar(navController) }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = "today",
                    modifier = Modifier.padding(padding)
                ) {
                    composable("today") { TodayScreen() }
                    composable("progress") { ProgressScreen() }
                    composable("focus") { FocusScreen() }
                    composable("review") { ReviewScreen() }
                    composable("alerts") { AlertsScreen() }
                    composable("settings") {
                        SettingsScreen(onOpenGuide = { navController.navigate("guide") })
                    }
                    composable("guide") {
                        GuideScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    NavigationBar(containerColor = SurfaceNavy.copy(alpha = 0.94f)) {
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
                    selectedIconColor = Cyan,
                    selectedTextColor = Cyan,
                    unselectedIconColor = TextDim,
                    unselectedTextColor = TextDim,
                    indicatorColor = VioletDeep
                )
            )
        }
    }
}
