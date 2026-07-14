package ai.bewsoa.flow.ui.today

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import ai.bewsoa.flow.data.SkipBudget
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.ProgressRing
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.formatCountdown
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.formatTime
import ai.bewsoa.flow.ui.tasks.TaskActions
import ai.bewsoa.flow.ui.tasks.TasksViewModel
import ai.bewsoa.flow.ui.tasks.tasksSection
import ai.bewsoa.flow.ui.theme.Amber
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import ai.bewsoa.flow.ui.theme.color
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val headerDate = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

@Composable
fun TodayScreen(
    onOpenAlerts: () -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
    tasksViewModel: TasksViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    // A refused skip has to say why, or the cap just looks like a broken button.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.skipRejected.collect {
            Toast.makeText(
                context,
                "No skips left this week — they reset Monday.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TodayHeader(state, onOpenAlerts) }
        proposal?.let { pending ->
            item {
                CoachProposalCard(
                    proposal = pending,
                    onAccept = viewModel::acceptProposal,
                    onDismiss = viewModel::dismissProposal
                )
            }
        }
        item { HeroCard(state, now) }
        item { DeepWorkCard(state) }

        val todayMissed = state.blocks.filter {
            !it.done && it.block.counted && now >= it.block.end
        }
        if (todayMissed.isNotEmpty() || state.yesterdayMissed.isNotEmpty()) {
            item { SectionHeader("Catch up") }
            if (todayMissed.isNotEmpty()) {
                item { CatchUpLabel("Today · ended, not logged") }
                items(todayMissed, key = { "miss_t_${it.block.id}" }) { item ->
                    CatchUpRow(item) { viewModel.setDone(item.block.id, true) }
                }
            }
            if (state.yesterdayMissed.isNotEmpty()) {
                item { CatchUpLabel("Yesterday") }
                items(state.yesterdayMissed, key = { "miss_y_${it.block.id}" }) { item ->
                    CatchUpRow(item) { viewModel.setYesterdayDone(item.block.id, true) }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("Today's blocks")
                SkipBudgetLabel(state.skipBudget)
            }
        }
        items(state.blocks, key = { it.block.id }) { item ->
            BlockCard(
                item = item,
                now = now,
                onToggle = { viewModel.setDone(item.block.id, !item.done) },
                onSkip = { viewModel.skip(item.block.id) },
                onUnskip = { viewModel.unskip(item.block.id) }
            )
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionHeader("My tasks") }
        tasksSection(
            state = tasksState,
            actions = TaskActions(
                onQuickAdd = tasksViewModel::addQuick,
                onAiAdd = tasksViewModel::addWithAi,
                onToggleTask = tasksViewModel::toggleTask,
                onToggleSubtask = tasksViewModel::toggleSubtask,
                onSplit = tasksViewModel::splitTask,
                onDelete = tasksViewModel::deleteTask,
                onMoveTomorrow = tasksViewModel::moveToTomorrow,
                onCapacity = tasksViewModel::adjustCapacity,
                onClearMessage = tasksViewModel::clearMessage
            )
        )
    }
}

@Composable
private fun CoachProposalCard(
    proposal: CoachProposal,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    GlowCard(accent = Violet) {
        Text(
            "🧠 Your coach drafted next week",
            style = MaterialTheme.typography.titleMedium,
            color = TextBright
        )
        if (proposal.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                proposal.note,
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "WHAT WOULD CHANGE",
            style = MaterialTheme.typography.labelSmall,
            color = Cyan
        )
        Spacer(Modifier.height(6.dp))
        proposal.diff.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = TextBright,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                Text("Accept", color = Color.White)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Not this week", color = TextDim)
            }
        }
    }
}

@Composable
private fun TodayHeader(state: TodayUiState, onOpenAlerts: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "BEWSOA FLOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.date.format(headerDate),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextBright
                )
            }
            IconButton(onClick = onOpenAlerts) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = "Alerts",
                    tint = TextDim
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(WeeklyProgram.dayLabel(state.date), Violet)
            Chip("🔥 ${state.streak.current} day streak", Amber)
        }
    }
}

/**
 * "●●○ 2 of 3 skips left". The budget has to be on screen: an invisible cap is
 * just a surprise, and a visible one is what keeps a skip feeling like a real
 * decision rather than a free pass.
 */
@Composable
private fun SkipBudgetLabel(budget: SkipBudget) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(budget.cap) { i ->
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < budget.remaining) Cyan else Outline)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (budget.exhausted) {
                "no skips left"
            } else {
                "${budget.remaining} of ${budget.cap} skips"
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = TextBright)
    }
}

@Composable
private fun HeroCard(state: TodayUiState, now: LocalTime) {
    val current = state.blocks.firstOrNull { now >= it.block.start && now < it.block.end }
    val next = state.blocks.firstOrNull { it.block.start > now }

    GlowCard(accent = current?.block?.track?.color()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = state.progress,
                modifier = Modifier.size(116.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(state.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextBright
                    )
                    Text(
                        "${state.doneCount}/${state.countedCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                when {
                    current != null -> {
                        val block = current.block
                        Text(
                            "NOW",
                            style = MaterialTheme.typography.labelSmall,
                            color = block.track.color(),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${block.track.emoji} ${block.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextBright,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "ends in ${formatCountdown(Duration.between(now, block.end))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                        Spacer(Modifier.height(10.dp))
                        val total = Duration.between(block.start, block.end).seconds.toFloat()
                        val elapsed = Duration.between(block.start, now).seconds.toFloat()
                        StatBar(
                            ratio = if (total <= 0f) 0f else elapsed / total,
                            color = block.track.color()
                        )
                    }
                    next != null -> {
                        Text(
                            "UP NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${next.block.track.emoji} ${next.block.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextBright,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "starts at ${formatTime(next.block.start)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                    else -> {
                        Text(
                            "Day complete",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextBright
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.progress >= 1f) {
                                "Everything logged. Never missing twice looks good on you."
                            } else {
                                "Blocks are over — log what you finished."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
            }
        }
    }
}

private const val DEEP_WORK_GOAL_MIN = 360 // 6h — a soft daily reference, not a rule.

@Composable
private fun DeepWorkCard(state: TodayUiState) {
    GlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "DEEP WORK TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (state.deepWorkMinutes == 0) "0m" else formatHours(state.deepWorkMinutes.toLong()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextBright
                )
            }
            Text(
                "goal ${formatHours(DEEP_WORK_GOAL_MIN.toLong())}",
                style = MaterialTheme.typography.labelMedium,
                color = TextDim
            )
        }
        Spacer(Modifier.height(10.dp))
        StatBar(
            ratio = state.deepWorkMinutes.toFloat() / DEEP_WORK_GOAL_MIN,
            color = Cyan
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Focused YKS / TYT / SAT / project blocks you've logged today.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
    }
}

@Composable
private fun CatchUpLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextDim
    )
}

@Composable
private fun CatchUpRow(item: BlockWithStatus, onLog: () -> Unit) {
    val block = item.block
    GlowCard(accent = Coral) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${block.track.emoji} ${block.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextBright,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatTime(block.start)}–${formatTime(block.end)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim
                )
            }
            IconButton(onClick = onLog) {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Log as done",
                    tint = Coral,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockCard(
    item: BlockWithStatus,
    now: LocalTime,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit
) {
    val block = item.block
    val isCurrent = now >= block.start && now < block.end && !item.skipped
    // A skipped block can't be "not logged yet" — that's the entire point.
    val isPastUndone = !item.done && !item.skipped && block.counted && now >= block.end
    val accent = block.track.color()

    GlowCard(accent = if (isCurrent) accent else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatTime(block.start),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) accent else TextDim
                )
                Box(
                    Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(
                            if (item.skipped) Outline else accent.copy(alpha = 0.5f)
                        )
                )
                Text(
                    formatTime(block.end),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDim
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${block.track.emoji} ${block.title}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.done || item.skipped) TextDim else TextBright,
                    textDecoration = if (item.done || item.skipped) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    }
                )
                if (block.note.isNotEmpty() && !item.skipped) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        block.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.skipped) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Skipped today · doesn't count against you",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim
                    )
                }
                if (isPastUndone) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ended — not logged yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = Coral
                    )
                }
            }
            if (block.counted) {
                Spacer(Modifier.width(6.dp))
                if (item.skipped) {
                    TextButton(onClick = onUnskip) {
                        Text(
                            "Undo",
                            style = MaterialTheme.typography.labelMedium,
                            color = Cyan
                        )
                    }
                } else {
                    // Skipping is only offered for something not already done —
                    // there's nothing to excuse once you've finished it.
                    if (!item.done) {
                        IconButton(onClick = onSkip) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = "Skip today",
                                tint = TextDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (item.done) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = if (item.done) {
                                "Mark as not done"
                            } else {
                                "Mark as done"
                            },
                            tint = if (item.done) Mint else TextDim,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}
