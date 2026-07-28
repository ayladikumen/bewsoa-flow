package ai.bewsoa.flow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.DayStat
import ai.bewsoa.flow.data.Streak
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.CardTone
import ai.bewsoa.flow.ui.components.FlamePill
import ai.bewsoa.flow.ui.components.ProgressRing
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.components.StatusPill
import ai.bewsoa.flow.ui.components.TrackDot
import ai.bewsoa.flow.ui.components.XpRing
import ai.bewsoa.flow.ui.components.appear
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.formatCountdown
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.progress.ProgressViewModel
import ai.bewsoa.flow.ui.tasks.TasksViewModel
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color
import ai.bewsoa.flow.ui.today.TodayUiState
import ai.bewsoa.flow.ui.today.TodayViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

/**
 * The landing dashboard — the reference design's "productivity revealed"
 * screen. Greeting, one big honest ring, the day's numbers as pastel tiles,
 * the XP loop, a focus launcher and a peek at the week. Everything deep links
 * into its own tab.
 */
@Composable
fun HomeScreen(
    onOpenDay: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenAlerts: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
    progressViewModel: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory),
    tasksViewModel: TasksViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekState by progressViewModel.uiState.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            viewModel.onTick()
            tasksViewModel.onTick()
            delay(1_000L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        item(key = "greeting") {
            GreetingHeader(state, now, onOpenAlerts, Modifier.appear(0))
        }
        item(key = "hero") {
            HeroCard(state, now, onOpenDay, Modifier.appear(1))
        }
        proposal?.let {
            item(key = "coach_chip") {
                CoachChip(onOpenWeek, Modifier.appear(1))
            }
        }
        item(key = "tiles") {
            Row(
                modifier = Modifier.appear(2),
                horizontalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                StatTile(
                    label = "Routine",
                    done = state.doneCount,
                    total = state.countedCount,
                    unit = "blocks",
                    tint = LocalPalette.current.success,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDay
                )
                StatTile(
                    label = "Tasks",
                    done = tasksState.doneCount,
                    total = tasksState.tasks.size,
                    unit = "tasks",
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDay
                )
            }
        }
        item(key = "xp") { XpCard(state, Modifier.appear(3)) }
        item(key = "focus") { FocusCard(state, onOpenFocus, Modifier.appear(4)) }
        weekState.stats?.let { stats ->
            item(key = "week") {
                WeekPeekCard(
                    days = stats.days,
                    chestReady = weekState.xp.chest?.claimable == true ||
                        weekState.xp.lastChest != null,
                    onOpenWeek = onOpenWeek,
                    modifier = Modifier.appear(5)
                )
            }
        }
        item(key = "upnext") { UpNextCard(state, now, onOpenDay, Modifier.appear(6)) }
    }
}

@Composable
private fun GreetingHeader(
    state: TodayUiState,
    now: LocalTime,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            SectionLabel(state.date.format(headerDate))
            Spacer(Modifier.height(2.dp))
            Text(
                greetingFor(now),
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textBright
            )
        }
        IconButton(onClick = onOpenAlerts) {
            Icon(
                Icons.Rounded.Notifications,
                contentDescription = "Alerts",
                tint = palette.textDim
            )
        }
        FlamePill(days = state.streak.current, todayKept = state.streak.todayKept)
    }
}

private fun greetingFor(now: LocalTime): String = when {
    now.hour < 6 -> "Night owl 🦉"
    now.hour < 12 -> "Good morning ☀️"
    now.hour < 18 -> "Good afternoon 🌤️"
    else -> "Good evening 🌙"
}

/** The big ring: today's completion, plus what's happening right now. */
@Composable
private fun HeroCard(
    state: TodayUiState,
    now: LocalTime,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val current = state.blocks.firstOrNull {
        !it.skipped && now >= it.block.start && now < it.block.end
    }
    val next = state.blocks.firstOrNull { !it.skipped && it.block.start > now }

    Card(
        modifier = modifier
            .pressBounce(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenDay)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${(state.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textBright
                )
                Text(
                    "of today's plan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textDim
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    "${state.doneCount} done · ${state.countedCount - state.doneCount} to go",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textBright
                )
            }
            ProgressRing(
                progress = state.progress,
                color = palette.success,
                modifier = Modifier.size(104.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.doneCount}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textBright
                    )
                    Text(
                        "of ${state.countedCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim
                    )
                }
            }
        }
        val line = when {
            current != null -> Triple(
                current.block.track.color(),
                "${current.block.track.emoji} ${current.block.title}",
                "ends in ${formatCountdown(Duration.between(now, current.block.end))}"
            )
            next != null -> Triple(
                next.block.track.color(),
                "${next.block.track.emoji} ${next.block.title}",
                "up next · ~${formatHours(next.block.durationMinutes)}"
            )
            else -> null
        }
        if (line != null) {
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackDot(line.first)
                Spacer(Modifier.width(Space.s))
                Text(
                    line.second,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    line.third,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textDim
                )
            }
        }
    }
}

@Composable
private fun CoachChip(onOpenWeek: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .pressBounce(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenWeek),
        tone = CardTone.Accent(palette.accent)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = palette.accent, fontSize = 18.sp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    "Your coach drafted next week",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textBright
                )
                Text(
                    "Review it on the Week tab",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = palette.accent
            )
        }
    }
}

/** One pastel number tile — the reference design's stat cards. */
@Composable
private fun StatTile(
    label: String,
    done: Int,
    total: Int,
    unit: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressBounce(interaction, 0.97f)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .background(tint.copy(alpha = 0.14f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(Space.l)
    ) {
        SectionLabel(label)
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$done",
                style = MaterialTheme.typography.displaySmall,
                color = tint
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "of $total",
                style = MaterialTheme.typography.titleSmall,
                color = palette.textDim,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            "$unit done",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
    }
}

/** The Duolingo loop: today's XP goal ring + where the account stands. */
@Composable
private fun XpCard(state: TodayUiState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XpRing(
                current = state.xp.earned,
                goal = state.xp.goal,
                modifier = Modifier.size(64.dp),
                strokeWidth = 7.dp
            ) {
                Text("⚡", fontSize = 22.sp)
            }
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                SectionLabel("Daily goal")
                Spacer(Modifier.height(2.dp))
                Text(
                    "${state.xp.earned} / ${state.xp.goal} XP",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
                if (state.xp.goalHit) {
                    Text(
                        "Goal hit — bonus banked ✓",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.success
                    )
                } else {
                    Text(
                        "${state.xp.goal - state.xp.earned} XP to today's bonus",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                }
            }
            Spacer(Modifier.width(Space.s))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.xp.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${state.xp.level.index}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = palette.xp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    state.xp.level.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textDim
                )
            }
        }
    }
}

/** Deep-focus launcher plus today's focused time against the soft 6h goal. */
@Composable
private fun FocusCard(
    state: TodayUiState,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .pressBounce(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenFocus)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.focus.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🧠", fontSize = 20.sp)
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    "Deep focus",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
                Text(
                    "${formatHours(state.deepWorkMinutes.toLong())} focused today · goal 6h",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.textBright),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Start focus",
                    tint = palette.surface
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        StatBar(
            ratio = state.deepWorkMinutes / 360f,
            color = palette.focus,
            height = 6.dp
        )
    }
}

/** Mini bar chart of the week so far — the door to the Week tab. */
@Composable
private fun WeekPeekCard(
    days: List<DayStat>,
    chestReady: Boolean,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .pressBounce(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenWeek)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("This week")
            Spacer(Modifier.weight(1f))
            if (chestReady) StatusPill("🎁 chest ready", palette.xp)
        }
        Spacer(Modifier.height(Space.m))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            days.forEach { day -> WeekBar(day, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun WeekBar(day: DayStat, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val isToday = day.date == LocalDate.now()
    val isFuture = day.date.isAfter(LocalDate.now())
    val barColor = when {
        isFuture -> palette.outline.copy(alpha = 0.5f)
        day.ratio >= Streak.KEEP_THRESHOLD -> palette.success
        day.ratio > 0f -> palette.warn
        else -> palette.outline
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height((52.dp * day.ratio.coerceAtLeast(0.08f)))
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US).take(2),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) palette.success else palette.textDim
        )
    }
}

/** The next few things on deck, timeline-style with coloured dots. */
@Composable
private fun UpNextCard(
    state: TodayUiState,
    now: LocalTime,
    onOpenDay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val upcoming = state.blocks
        .filter { !it.done && !it.skipped && (it.block.end > now) }
        .take(3)
    if (upcoming.isEmpty()) return

    Card(
        modifier = modifier
            .pressBounce(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenDay)
    ) {
        SectionLabel("Up next")
        Spacer(Modifier.height(Space.s))
        upcoming.forEachIndexed { index, item ->
            val isNow = now >= item.block.start && now < item.block.end
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackDot(item.block.track.color())
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${item.block.track.emoji} ${item.block.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "~${formatHours(item.block.durationMinutes)} · ${daypartOf(item.block.start)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim
                    )
                }
                if (isNow) StatusPill("NOW", palette.success, filled = true)
            }
            if (index != upcoming.lastIndex) {
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .width(2.dp)
                        .height(10.dp)
                        .background(palette.outline)
                )
            }
        }
    }
}

internal fun daypartOf(start: LocalTime): String = when {
    start.hour < 12 -> "morning"
    start.hour < 18 -> "afternoon"
    else -> "evening"
}
