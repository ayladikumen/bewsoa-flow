package ai.bewsoa.flow.ui.day

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.SkipBudget
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.DayPaceBar
import ai.bewsoa.flow.ui.components.PastelRow
import ai.bewsoa.flow.ui.components.RoundCheck
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.components.StatusPill
import ai.bewsoa.flow.ui.components.appear
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.formatCountdown
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.home.daypartOf
import ai.bewsoa.flow.ui.tasks.TaskActions
import ai.bewsoa.flow.ui.tasks.TasksViewModel
import ai.bewsoa.flow.ui.tasks.tasksSection
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color
import ai.bewsoa.flow.ui.today.BlockWithStatus
import ai.bewsoa.flow.ui.today.TodayUiState
import ai.bewsoa.flow.ui.today.TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val subDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/**
 * The day as a checklist, not a timetable — the reference design's Tasks
 * screen. Pastel rows carry the plan in day-parts and durations; the pace bar
 * up top keeps the clock honest without drawing a single hour line. The goal
 * is the week; the day is meant to flex.
 */
@Composable
fun DayScreen(
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory),
    tasksViewModel: TasksViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val palette = LocalPalette.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalTime.now()) }
    var catchUpOpen by rememberSaveable { mutableStateOf(false) }
    val composerFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            viewModel.onTick()
            tasksViewModel.onTick()
            delay(1_000L)
        }
    }

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

    // Reorder locally during the drag, commit on drop.
    var localBlocks by remember { mutableStateOf(state.blocks) }
    LaunchedEffect(state.blocks) { localBlocks = state.blocks }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String
        val toKey = to.key as? String
        if (fromKey?.startsWith("block_") == true && toKey?.startsWith("block_") == true) {
            val fromIndex = localBlocks.indexOfFirst { "block_${it.block.id}" == fromKey }
            val toIndex = localBlocks.indexOfFirst { "block_${it.block.id}" == toKey }
            if (fromIndex >= 0 && toIndex >= 0) {
                localBlocks = localBlocks.toMutableList()
                    .apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.l, end = Space.l, top = Space.xl, bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            item(key = "header") { DayHeader(state, Modifier.appear(0)) }
            item(key = "pace") { PaceCard(state, now, Modifier.appear(1)) }

            val todayMissed = state.blocks.filter {
                !it.done && !it.skipped && it.block.counted && now >= it.block.end
            }
            val missedCount = todayMissed.size + state.yesterdayMissed.size
            if (missedCount > 0) {
                item(key = "catchup") {
                    CatchUpCard(missedCount, catchUpOpen) { catchUpOpen = !catchUpOpen }
                }
                if (catchUpOpen) {
                    items(todayMissed, key = { "miss_t_${it.block.id}" }) { item ->
                        CatchUpRow(item, "today") { viewModel.setDone(item.block.id, true) }
                    }
                    items(state.yesterdayMissed, key = { "miss_y_${it.block.id}" }) { item ->
                        CatchUpRow(item, "yesterday") { viewModel.setYesterdayDone(item.block.id, true) }
                    }
                }
            }

            item(key = "routine_head") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("My routine · hold & drag")
                    SkipDots(state.skipBudget)
                }
            }
            items(localBlocks, key = { "block_${it.block.id}" }) { item ->
                ReorderableItem(reorderableState, key = "block_${item.block.id}") { isDragging ->
                    BlockRow(
                        item = item,
                        now = now,
                        onToggle = { viewModel.setDone(item.block.id, !item.done) },
                        onSkip = { viewModel.skip(item.block.id) },
                        onUnskip = { viewModel.unskip(item.block.id) },
                        modifier = Modifier
                            .longPressDraggableHandle(
                                onDragStopped = {
                                    viewModel.commitBlockOrder(localBlocks.map { it.block.id })
                                }
                            )
                            .graphicsLayer {
                                if (isDragging) {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                }
                            }
                    )
                }
            }

            item(key = "tasks_head") {
                Box(Modifier.padding(top = Space.s)) { SectionLabel("My tasks") }
            }
            tasksSection(tasksState, taskActions(tasksViewModel), composerFocus)
        }

        // The + button: down to the composer, keyboard up, start typing.
        val fabInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Space.l, bottom = Space.l)
                .size(56.dp)
                .pressBounce(fabInteraction, 0.88f)
                .clip(CircleShape)
                .background(palette.textBright)
                .clickable(interactionSource = fabInteraction, indication = null) {
                    scope.launch {
                        val last = listState.layoutInfo.totalItemsCount - 1
                        if (last >= 0) listState.animateScrollToItem(last)
                        // The composer may still be sliding into composition.
                        runCatching { composerFocus.requestFocus() }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "Add a task",
                tint = palette.surface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private fun taskActions(viewModel: TasksViewModel) = TaskActions(
    onQuickAdd = viewModel::addQuick,
    onAiAdd = viewModel::addWithAi,
    onToggleTask = viewModel::toggleTask,
    onToggleSubtask = viewModel::toggleSubtask,
    onSplit = viewModel::splitTask,
    onDelete = viewModel::deleteTask,
    onMoveTomorrow = viewModel::moveToTomorrow,
    onQuadrant = viewModel::cycleQuadrant,
    onCapacity = viewModel::adjustCapacity,
    onClearMessage = viewModel::clearMessage
)

@Composable
private fun DayHeader(state: TodayUiState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val left = state.countedCount - state.doneCount
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Today",
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textBright
            )
            Text(
                "${state.date.format(subDate)} · ${WeeklyProgram.dayLabel(state.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
        }
        if (left > 0) {
            StatusPill("$left left", palette.warn, filled = true)
        } else if (state.countedCount > 0) {
            StatusPill("all done ✓", palette.success, filled = true)
        }
    }
}

/**
 * "Aware of time, but not boring": the knob is the day passing (07:00–24:00),
 * the fill is the plan getting done, and one line of copy sells the weekly
 * philosophy — every tick today is work the weekend never sees.
 */
@Composable
private fun PaceCard(state: TodayUiState, now: LocalTime, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val dayStart = LocalTime.of(7, 0)
    val daySpan = Duration.between(dayStart, LocalTime.of(23, 59)).toMinutes().toFloat()
    val dayFraction = (Duration.between(dayStart, now).toMinutes() / daySpan).coerceIn(0f, 1f)
    val planFraction = state.progress
    val remainingMinutes = state.blocks
        .filter { it.block.counted && !it.done && !it.skipped }
        .sumOf { it.block.durationMinutes }

    Card(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                paceCopy(now, dayFraction, planFraction),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textBright,
                modifier = Modifier.weight(1f)
            )
            if (remainingMinutes > 0) {
                Text(
                    "≈${formatHours(remainingMinutes)} left",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textDim
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        DayPaceBar(dayFraction = dayFraction, planFraction = planFraction)
        Spacer(Modifier.height(Space.s))
        Text(
            "The goal is the week — today just makes Sunday lighter.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textDim
        )
    }
}

private fun paceCopy(now: LocalTime, dayFraction: Float, planFraction: Float): String = when {
    planFraction >= 1f -> "Plan complete — the rest is yours 🎉"
    now.hour < 7 -> "Early hours — nothing counts yet"
    planFraction >= dayFraction -> "Ahead of the clock ✨"
    dayFraction - planFraction < 0.18f -> "Right on pace — keep ticking"
    else -> "The day is moving faster than the plan"
}

@Composable
private fun SkipDots(budget: SkipBudget) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(budget.cap) { i ->
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < budget.remaining) palette.accent else palette.outline)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            if (budget.exhausted) "no skips left" else "${budget.remaining} skips",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textDim
        )
    }
}

@Composable
private fun CatchUpCard(count: Int, open: Boolean, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    PastelRow(tint = palette.danger, onClick = onToggle) {
        Column(Modifier.weight(1f)) {
            Text(
                "⏰ Catch up",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textBright
            )
            Text(
                "$count block${if (count == 1) "" else "s"} ended without a log",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textDim
            )
        }
        Icon(
            imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (open) "Collapse" else "Expand",
            tint = palette.danger
        )
    }
}

@Composable
private fun CatchUpRow(item: BlockWithStatus, dayLabel: String, onLog: () -> Unit) {
    val palette = LocalPalette.current
    val block = item.block
    PastelRow(tint = palette.danger, verticalPadding = 10.dp) {
        Column(Modifier.weight(1f)) {
            Text(
                "${block.track.emoji} ${block.title}",
                style = MaterialTheme.typography.titleSmall,
                color = palette.textBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$dayLabel · ~${formatHours(block.durationMinutes)}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textDim
            )
        }
        RoundCheck(checked = false, color = palette.danger, onToggle = onLog, size = 26.dp)
    }
}

/** One routine block as a pastel checklist row. Times only whisper. */
@Composable
private fun BlockRow(
    item: BlockWithStatus,
    now: LocalTime,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val block = item.block
    val isCurrent = !item.done && !item.skipped && now >= block.start && now < block.end
    val isPastUndone = !item.done && !item.skipped && block.counted && now >= block.end
    val accent = block.track.color()

    PastelRow(
        tint = accent,
        dimmed = item.done || item.skipped,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(palette.surface.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Text(block.track.emoji, fontSize = 17.sp)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                block.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.done || item.skipped) palette.textDim else palette.textBright,
                textDecoration = if (item.done || item.skipped) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val meta = when {
                item.skipped -> "skipped · excused, doesn't count"
                isCurrent -> "ends in ${formatCountdown(Duration.between(now, block.end))}"
                isPastUndone -> "ended · not logged yet"
                else -> "~${formatHours(block.durationMinutes)} · ${daypartOf(block.start)}"
            }
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isCurrent -> accent
                    isPastUndone -> palette.danger
                    else -> palette.textDim
                }
            )
        }
        Spacer(Modifier.width(Space.s))
        if (isCurrent) {
            StatusPill("NOW", accent, filled = true)
            Spacer(Modifier.width(Space.s))
        }
        if (item.skipped) {
            TextButton(onClick = onUnskip) {
                Text(
                    "Undo",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
            }
        } else {
            // Skips spend the weekly budget, so only counted blocks offer one;
            // meals and free time are still tickable — checking off dinner is
            // free (0 XP, outside the streak math) but satisfying.
            if (block.counted && !item.done) {
                IconButton(onClick = onSkip, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Block,
                        contentDescription = "Skip today",
                        tint = palette.textDim.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(Space.xs))
            }
            RoundCheck(checked = item.done, color = accent, onToggle = onToggle)
        }
    }
}
