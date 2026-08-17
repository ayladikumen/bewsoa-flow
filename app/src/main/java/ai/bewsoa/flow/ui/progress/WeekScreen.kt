package ai.bewsoa.flow.ui.progress

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.Insight
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.Streak
import ai.bewsoa.flow.data.StreakInfo
import ai.bewsoa.flow.data.Track
import ai.bewsoa.flow.data.TrackStat
import ai.bewsoa.flow.data.WeekStats
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.program.BuilderStart
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.CardTone
import ai.bewsoa.flow.ui.components.DayMark
import ai.bewsoa.flow.ui.components.DraftCard
import ai.bewsoa.flow.ui.components.GhostButton
import ai.bewsoa.flow.ui.components.PrimaryButton
import ai.bewsoa.flow.ui.components.ProgressRing
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.components.WeekDot
import ai.bewsoa.flow.ui.components.WeekDots
import ai.bewsoa.flow.ui.components.appear
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.formatWeekRange
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The centre tab — because the goal IS the week. One honest ring for the
 * week, the streak told in dots, the XP economy's chest and level, the
 * browsable plan, and the place every AI draft of next week gets decided.
 */
@Composable
fun WeekScreen(
    onOpenReview: () -> Unit = {},
    onOpenBuilder: (BuilderStart) -> Unit = {},
    viewModel: ProgressViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val plan by viewModel.planState.collectAsStateWithLifecycle()
    val proposal by viewModel.proposal.collectAsStateWithLifecycle()
    val customProgram by viewModel.customProgramActive.collectAsStateWithLifecycle()
    val stats = state.stats

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.skipRejected.collect {
            Toast.makeText(
                context,
                "No skips left that week — they reset Monday.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.l, end = Space.l, top = Space.xl, bottom = Space.xl
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        item(key = "header") {
            Column(Modifier.appear(0)) {
                SectionLabel("Week of ${formatWeekRange(viewModel.weekStart)}")
                Spacer(Modifier.height(2.dp))
                Text(
                    "Your week",
                    style = MaterialTheme.typography.headlineLarge,
                    color = LocalPalette.current.textBright
                )
            }
        }
        proposal?.let { pending ->
            item(key = "coach_draft") {
                DraftCard(
                    scopeLabel = "✦ coach draft · next week",
                    title = "Your coach drafted next week",
                    changes = pending.diff,
                    note = pending.note.ifBlank { null },
                    applyText = "Accept next week",
                    dismissText = "Not this week",
                    onApply = viewModel::acceptProposal,
                    onDismiss = viewModel::dismissProposal,
                    modifier = Modifier.appear(1)
                )
            }
        }
        if (stats != null) {
            item(key = "hero") { WeekHero(stats, state.streak, Modifier.appear(1)) }
        }
        item(key = "streak") { StreakCard(state.streak, stats, Modifier.appear(2)) }
        item(key = "level") { Column(Modifier.appear(3)) { LevelCard(state.xp) } }
        state.xp.chest?.let { chest ->
            item(key = "chest") { Column(Modifier.appear(4)) { ChestCard(chest, viewModel::openChest) } }
        }
        state.xp.lastChest?.let { chest ->
            item(key = "last_chest") { LastChestCard(chest, viewModel::openLastChest) }
        }
        item(key = "program_head") { SectionLabel("Your weekly program") }
        item(key = "program_entry") {
            ProgramEntryCard(customActive = customProgram, onOpenBuilder = onOpenBuilder)
        }
        item(key = "plan_head") { SectionLabel("The plan · any day, any week") }
        item(key = "plan") {
            PlanCard(
                state = plan,
                onSelectDay = viewModel::selectDay,
                onShiftWeek = viewModel::shiftWeek,
                onJumpToday = viewModel::jumpToToday,
                onToggle = viewModel::setPlanDone,
                onSkip = viewModel::skipPlanBlock,
                onUnskip = viewModel::unskipPlanBlock
            )
        }
        if (state.insights.isNotEmpty()) {
            item(key = "insights") { InsightsCard(state.insights) }
        }
        if (stats != null) {
            item(key = "tracks_head") { SectionLabel("Tracks this week") }
            items(stats.tracks, key = { it.track.name }) { stat -> TrackRow(stat) }
            item(key = "focus_week") { FocusWeekCard(state.focus) }
        }
        item(key = "review") { ReviewEntryCard(onOpenReview) }
        item(key = "rule") { NeverMissTwiceCard() }
    }
}

/**
 * The way into the weekly program builder — and, before there is a custom
 * program, the loudest thing on the tab. The three starting points map onto the
 * builder's entry points; every one of them opens a draft, never an edit of the
 * live program.
 */
@Composable
private fun ProgramEntryCard(
    customActive: Boolean,
    onOpenBuilder: (BuilderStart) -> Unit
) {
    val palette = LocalPalette.current
    Card(tone = if (customActive) CardTone.Plain else CardTone.Accent(palette.accent)) {
        Text(
            if (customActive) "Your week, your rules" else "Your week is still using the default plan.",
            style = MaterialTheme.typography.titleLarge,
            color = palette.textBright
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            if (customActive) {
                "Edit the recurring Monday–Sunday program, or have the AI redraft it. " +
                    "Nothing changes until you save."
            } else {
                "Build a recurring Monday–Sunday program of your own — describe it, " +
                    "or start from the week you already have."
            },
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
        Spacer(Modifier.height(Space.l))
        if (customActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                PrimaryButton(
                    text = "Edit weekly program",
                    onClick = { onOpenBuilder(BuilderStart.CURRENT) },
                    modifier = Modifier.weight(1.4f)
                )
                GhostButton(
                    text = "Build with AI",
                    onClick = { onOpenBuilder(BuilderStart.AI) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Space.s))
            TextButton(onClick = { onOpenBuilder(BuilderStart.SCRATCH) }) {
                Text(
                    "Start a new program from scratch",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textDim
                )
            }
        } else {
            PrimaryButton(
                text = "Build with AI",
                onClick = { onOpenBuilder(BuilderStart.AI) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                GhostButton(
                    text = "Start from scratch",
                    onClick = { onOpenBuilder(BuilderStart.SCRATCH) },
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    text = "Use current week",
                    onClick = { onOpenBuilder(BuilderStart.CURRENT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** The week ring plus its philosophy: daily wins beat a Sunday pile-up. */
@Composable
private fun WeekHero(stats: WeekStats, streak: StreakInfo, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val keptDays = stats.days.count {
        !it.date.isAfter(LocalDate.now()) && it.ratio >= Streak.KEEP_THRESHOLD
    }
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = stats.overallRatio,
                color = palette.success,
                modifier = Modifier.size(104.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(stats.overallRatio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textBright
                    )
                    Text(
                        "of the week",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textDim
                    )
                }
            }
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(
                    "${stats.doneBlocks} of ${stats.plannedBlocks} blocks",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$keptDays day${if (keptDays == 1) "" else "s"} kept so far",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textDim
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    weekPaceCopy(stats),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.success
                )
            }
        }
    }
}

/**
 * The pace sentence that carries the whole philosophy: ease any single day,
 * protect the week.
 */
private fun weekPaceCopy(stats: WeekStats): String {
    val today = LocalDate.now()
    val passedDays = stats.days.count { !it.date.isAfter(today) }
    if (passedDays == 0 || stats.plannedBlocks == 0) return "Fresh week — start anywhere."
    val expected = passedDays.toFloat() / 7f
    return when {
        stats.overallRatio >= expected -> "Ahead of pace — Sunday will be light ✨"
        stats.overallRatio >= expected * 0.7f -> "Close to pace — one strong day fixes it"
        else -> "Behind pace — small daily wins beat a weekend cram"
    }
}

@Composable
private fun StreakCard(streak: StreakInfo, stats: WeekStats?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 36.sp)
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(
                    "${streak.current} day${if (streak.current == 1) "" else "s"}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textBright
                )
                Text(
                    streakMessage(streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
        }
        if (stats != null) {
            Spacer(Modifier.height(Space.l))
            WeekDots(
                stats.days.map { day ->
                    val isToday = day.date == LocalDate.now()
                    WeekDot(
                        letter = day.date.dayOfWeek
                            .getDisplayName(TextStyle.NARROW, Locale.US),
                        mark = when {
                            day.date.isAfter(LocalDate.now()) -> DayMark.FUTURE
                            day.ratio >= Streak.KEEP_THRESHOLD -> DayMark.KEPT
                            isToday -> DayMark.TODAY
                            day.ratio > 0f -> DayMark.PARTIAL
                            else -> DayMark.MISSED
                        }
                    )
                }
            )
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
    val palette = LocalPalette.current
    Card {
        Text(
            "Insights",
            style = MaterialTheme.typography.titleLarge,
            color = palette.textBright
        )
        Text(
            "What your history says",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
        Spacer(Modifier.height(Space.m))
        insights.forEachIndexed { index, insight ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(insight.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(Space.m))
                Text(
                    insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textBright,
                    modifier = Modifier.weight(1f)
                )
            }
            if (index != insights.lastIndex) Spacer(Modifier.height(Space.s))
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
private fun TrackRow(stat: TrackStat) {
    val palette = LocalPalette.current
    val trackColor = stat.track.color()
    val isGym = stat.track == Track.GYM
    val ratio = if (isGym) {
        if (stat.plannedSessions == 0) 0f
        else stat.doneSessions.toFloat() / stat.plannedSessions
    } else {
        stat.ratio
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.track.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stat.track.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textBright,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isGym) {
                            "${stat.doneSessions}/${stat.plannedSessions} sessions"
                        } else {
                            "${formatHours(stat.doneMinutes)} / ${formatHours(stat.plannedMinutes)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim
                    )
                }
                Spacer(Modifier.height(Space.s))
                StatBar(ratio = ratio, color = trackColor, height = 6.dp)
            }
        }
    }
}

/** The week's Deep Focus sessions. */
@Composable
private fun FocusWeekCard(focus: FocusWeekStats) {
    val palette = LocalPalette.current
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧠", fontSize = 20.sp)
                Spacer(Modifier.width(Space.m))
                Column {
                    Text(
                        "Focus sessions",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textBright
                    )
                    Text(
                        "${focus.sessions} session${if (focus.sessions == 1) "" else "s"} this week",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                }
            }
            Text(
                formatHours(focus.totalMinutes.toLong()),
                style = MaterialTheme.typography.titleLarge,
                color = palette.focus
            )
        }
    }
}

/** Review lost its tab; this is how you reach it now. */
@Composable
private fun ReviewEntryCard(onOpenReview: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .pressBounce(interaction, 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpenReview)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Weekly review",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textBright
                )
                Text(
                    "Sunday — score the week and name one thing to change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = palette.textDim
            )
        }
    }
}

@Composable
private fun NeverMissTwiceCard() {
    val palette = LocalPalette.current
    Card {
        Text(
            "The one rule that protects everything",
            style = MaterialTheme.typography.titleSmall,
            color = palette.textBright
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Never miss twice. One missed day is a human being living a life. " +
                "Two missed days is a pattern. You don't have to be perfect — " +
                "you have to be relentless about coming back.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
    }
}
