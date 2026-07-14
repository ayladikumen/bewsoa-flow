package ai.bewsoa.flow.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.DayStat
import ai.bewsoa.flow.data.Insight
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.Track
import ai.bewsoa.flow.data.TrackStat
import ai.bewsoa.flow.data.WeekStats
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.formatWeekRange
import ai.bewsoa.flow.ui.theme.Amber
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import ai.bewsoa.flow.ui.theme.color
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    onOpenReview: () -> Unit = {},
    viewModel: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.stats

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    "Progress",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextBright
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Week of ${formatWeekRange(viewModel.weekStart)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }
        item { StreakCard(state.streak) }
        if (state.insights.isNotEmpty()) {
            item { InsightsCard(state.insights) }
        }
        if (stats != null) {
            item { WeekOverviewCard(stats) }
            item { SectionHeader("Tracks this week") }
            items(stats.tracks, key = { it.track.name }) { stat -> TrackRow(stat) }
            item { NeverMissTwiceCard() }
        }
        item { ReviewEntryCard(onOpenReview) }
    }
}

/** Review lost its tab in the 5-tab bar; this is how you reach it now. */
@Composable
private fun ReviewEntryCard(onOpenReview: () -> Unit) {
    GlowCard(modifier = Modifier.clickable(onClick = onOpenReview)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Weekly review",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextBright
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sunday — score the week and name one thing to change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = TextDim
            )
        }
    }
}

@Composable
private fun StreakCard(streak: StreakInfo) {
    GlowCard(accent = Amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 40.sp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "${streak.current} day${if (streak.current == 1) "" else "s"}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextBright
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    streakMessage(streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }
    }
}

private fun streakMessage(streak: StreakInfo): String {
    val threshold = (ProgramRepository.KEEP_THRESHOLD * 100).roundToInt()
    return when {
        streak.todayKept -> "Today already counts. Keep stacking."
        !streak.yesterdayKept -> "Yesterday slipped. Never miss twice — today is the comeback."
        else -> "Yesterday held. Cross $threshold% today to keep the chain."
    }
}

@Composable
private fun InsightsCard(insights: List<Insight>) {
    GlowCard(accent = Cyan) {
        Text("Insights", style = MaterialTheme.typography.titleMedium, color = TextBright)
        Spacer(Modifier.height(4.dp))
        Text(
            "What your history says",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        Spacer(Modifier.height(10.dp))
        insights.forEach { insight ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(insight.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    insight.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextBright,
                    modifier = Modifier.weight(1f)
                )
            }
            if (insight !== insights.last()) Spacer(Modifier.height(8.dp))
        }
    }
}

private val Insight.emoji: String
    get() = when (kind) {
        Insight.Kind.TREND -> "📈"
        Insight.Kind.WEAK_SPOT -> "🎯"
        Insight.Kind.DAY_CONTRAST -> "📅"
        Insight.Kind.OVERLOAD -> "⚖️"
    }

@Composable
private fun WeekOverviewCard(stats: WeekStats) {
    GlowCard {
        Text("This week", style = MaterialTheme.typography.titleMedium, color = TextBright)
        Spacer(Modifier.height(4.dp))
        Text(
            "${stats.doneBlocks} of ${stats.plannedBlocks} blocks · " +
                "${(stats.overallRatio * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        Spacer(Modifier.height(10.dp))
        StatBar(ratio = stats.overallRatio, color = Violet)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stats.days.forEach { day -> DayBar(day, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DayBar(day: DayStat, modifier: Modifier = Modifier) {
    val isToday = day.date == LocalDate.now()
    val isFuture = day.date.isAfter(LocalDate.now())
    val barColor = when {
        isFuture -> Outline.copy(alpha = 0.4f)
        day.ratio >= ProgramRepository.KEEP_THRESHOLD -> Mint
        day.ratio > 0f -> Amber
        else -> Outline
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
                    .fillMaxWidth(0.6f)
                    .fillMaxHeight(day.ratio.coerceAtLeast(0.06f))
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(barColor)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            day.date.dayOfWeek
                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
                .take(2),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) Cyan else TextDim
        )
    }
}

@Composable
private fun TrackRow(stat: TrackStat) {
    val trackColor = stat.track.color()
    val isGym = stat.track == Track.GYM
    val ratio = if (isGym) {
        if (stat.plannedSessions == 0) 0f
        else stat.doneSessions.toFloat() / stat.plannedSessions
    } else {
        stat.ratio
    }
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.track.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stat.track.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextBright,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isGym) {
                            "${stat.doneSessions}/${stat.plannedSessions} sessions"
                        } else {
                            "${formatHours(stat.doneMinutes)} / ${formatHours(stat.plannedMinutes)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatBar(ratio = ratio, color = trackColor)
            }
        }
    }
}

@Composable
private fun NeverMissTwiceCard() {
    GlowCard(accent = Violet) {
        Text(
            "The one rule that protects everything",
            style = MaterialTheme.typography.titleSmall,
            color = TextBright
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Never miss twice. One missed day is a human being living a life. " +
                "Two missed days is a pattern. Three is a new default. " +
                "You don't have to be perfect — you have to be relentless about coming back.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
    }
}
