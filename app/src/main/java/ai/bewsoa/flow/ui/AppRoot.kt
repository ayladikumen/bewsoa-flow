package ai.bewsoa.flow.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import ai.bewsoa.flow.ui.clock.ClockScreen
import ai.bewsoa.flow.ui.components.AppBackground
import ai.bewsoa.flow.ui.components.CelebrationHost
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.day.DayScreen
import ai.bewsoa.flow.ui.focus.FocusScreen
import ai.bewsoa.flow.ui.guide.GUIDE_VERSION
import ai.bewsoa.flow.ui.guide.GuideScreen
import ai.bewsoa.flow.ui.guide.WhatsNewOverlay
import ai.bewsoa.flow.ui.home.HomeScreen
import ai.bewsoa.flow.ui.progress.WeekScreen
import ai.bewsoa.flow.ui.review.ReviewScreen
import ai.bewsoa.flow.ui.settings.SettingsScreen
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val DAY = "day"
    const val WEEK = "week"
    const val CHAT = "chat"
    const val PROFILE = "profile"

    /** Pushed on top of a tab, not tabs themselves. */
    const val FOCUS = "focus"
    const val ALERTS = "alerts"
    const val REVIEW = "review"
    const val GUIDE = "guide"
    const val CLOCK = "clock"
}

private data class Dest(val route: String, val label: String, val icon: ImageVector)

/**
 * The 3.0 shell: five destinations on a floating pill, with the Week — the
 * actual goal — sitting in the centre as the loud ink button, exactly like
 * the reference design. Focus, Alerts, Review and the Guide are pushed
 * screens reached from the tabs that own them.
 */
private val left = listOf(
    Dest(Routes.HOME, "Home", Icons.Rounded.Home),
    Dest(Routes.DAY, "Today", Icons.Rounded.TaskAlt)
)
private val right = listOf(
    Dest(Routes.CHAT, "Assistant", Icons.Rounded.AutoAwesome),
    Dest(Routes.PROFILE, "Profile", Icons.Rounded.Person)
)
private val center = Dest(Routes.WEEK, "Week", Icons.Rounded.CalendarMonth)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTab = (left + right + center).any { it.route == currentRoute }

    val context = LocalContext.current
    val settings = remember { SettingsRepository.get(context) }
    // Int.MAX_VALUE until the real value loads, so the overlay never flashes.
    val seenVersion by settings.seenVersionCode
        .collectAsStateWithLifecycle(initialValue = Int.MAX_VALUE)
    val scope = rememberCoroutineScope()

    AppBackground {
        if (seenVersion < GUIDE_VERSION) {
            WhatsNewOverlay(
                onDone = { scope.launch { settings.setSeenVersionCode(GUIDE_VERSION) } }
            )
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = { if (isTab) PillBar(navController, currentRoute) }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(padding),
                    enterTransition = { fadeIn(tween(220, delayMillis = 60)) },
                    exitTransition = { fadeOut(tween(90)) },
                    popEnterTransition = { fadeIn(tween(220, delayMillis = 60)) },
                    popExitTransition = { fadeOut(tween(90)) }
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onOpenDay = { navController.navigateTab(Routes.DAY) },
                            onOpenWeek = { navController.navigateTab(Routes.WEEK) },
                            onOpenFocus = { navController.navigate(Routes.FOCUS) },
                            onOpenAlerts = { navController.navigate(Routes.ALERTS) }
                        )
                    }
                    composable(Routes.DAY) { DayScreen() }
                    composable(Routes.WEEK) {
                        WeekScreen(onOpenReview = { navController.navigate(Routes.REVIEW) })
                    }
                    composable(Routes.CHAT) { ChatScreen() }
                    composable(Routes.PROFILE) {
                        SettingsScreen(
                            onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                            onOpenGuide = { navController.navigate(Routes.GUIDE) },
                            onOpenClock = { navController.navigate(Routes.CLOCK) }
                        )
                    }
                    composable(Routes.FOCUS) {
                        // FocusScreen brings its own headline; just add the way back.
                        SubScreen("", navController::popBackStack) { FocusScreen() }
                    }
                    composable(Routes.ALERTS) {
                        SubScreen("Alerts", navController::popBackStack) { AlertsScreen() }
                    }
                    composable(Routes.REVIEW) {
                        SubScreen("Weekly review", navController::popBackStack) { ReviewScreen() }
                    }
                    composable(Routes.CLOCK) {
                        SubScreen("Exact Hour", navController::popBackStack) { ClockScreen() }
                    }
                    // Guide brings its own back affordance.
                    composable(Routes.GUIDE) {
                        GuideScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
        // Above everything, including the bar: celebrations play the same
        // no matter which screen earned them.
        val xp = remember { XpRepository.get(context) }
        CelebrationHost(xp.celebrations)
    }
}

private fun NavHostController.navigateTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = LocalPalette.current.textBright
                )
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** The floating pill: four quiet icons and one loud ink circle in the middle. */
@Composable
private fun PillBar(navController: NavHostController, currentRoute: String?) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = Space.xl, end = Space.xl, bottom = Space.m),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = if (palette.isLight) 10.dp else 0.dp,
                    shape = RoundedCornerShape(Radius.pill),
                    spotColor = Color(0x40322B7A),
                    ambientColor = Color(0x26322B7A)
                )
                .clip(RoundedCornerShape(Radius.pill))
                .background(if (palette.isLight) palette.surface else palette.surfaceHigh)
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            left.forEach { dest ->
                PillItem(dest, currentRoute == dest.route) {
                    navController.navigateTab(dest.route)
                }
            }
            CenterItem(selected = currentRoute == center.route) {
                navController.navigateTab(center.route)
            }
            right.forEach { dest ->
                PillItem(dest, currentRoute == dest.route) {
                    navController.navigateTab(dest.route)
                }
            }
        }
    }
}

@Composable
private fun PillItem(dest: Dest, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tabScale"
    )
    Column(
        modifier = Modifier
            .pressBounce(interaction, 0.85f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = dest.icon,
            contentDescription = dest.label,
            tint = if (selected) palette.textBright else palette.textDim.copy(alpha = 0.75f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (selected) palette.accent else Color.Transparent)
        )
    }
}

@Composable
private fun CenterItem(selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "centerScale"
    )
    Box(
        modifier = Modifier
            .padding(horizontal = Space.s)
            .size(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pressBounce(interaction, 0.88f)
            .clip(CircleShape)
            .background(palette.textBright)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = center.icon,
            contentDescription = center.label,
            tint = if (LocalPalette.current.isLight) Color.White else palette.background,
            modifier = Modifier.size(24.dp)
        )
    }
}
