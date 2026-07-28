package ai.bewsoa.flow.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.bewsoa.flow.data.Track
import ai.bewsoa.flow.data.db.SubtaskEntity
import ai.bewsoa.flow.data.db.TaskEntity
import ai.bewsoa.flow.data.db.TaskWithSubtasks
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.PastelRow
import ai.bewsoa.flow.ui.components.RoundCheck
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.components.pressBounce
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color

/**
 * Callbacks the tasks section fires back to [TasksViewModel]. Grouped so the
 * Day screen forwards them in one place.
 */
class TaskActions(
    val onQuickAdd: (String) -> Unit,
    val onAiAdd: (String) -> Unit,
    val onToggleTask: (TaskWithSubtasks) -> Unit,
    val onToggleSubtask: (TaskWithSubtasks, SubtaskEntity) -> Unit,
    val onSplit: (Long) -> Unit,
    val onDelete: (TaskEntity) -> Unit,
    val onMoveTomorrow: (TaskEntity) -> Unit,
    val onQuadrant: (TaskEntity) -> Unit,
    val onCapacity: (Int) -> Unit,
    val onClearMessage: () -> Unit
)

/**
 * The user-task half of the Day screen: capacity meter, pastel task rows and
 * a composer at the end (the + FAB scrolls here and focuses it).
 */
fun LazyListScope.tasksSection(
    state: TasksUiState,
    actions: TaskActions,
    composerFocus: FocusRequester
) {
    if (state.tasks.isNotEmpty()) {
        item(key = "task_capacity") { CapacityMeter(state, actions.onCapacity) }
        items(state.tasks, key = { "task_${it.task.id}" }) { item ->
            TaskRow(item, state.aiAvailable, state.aiBusy, actions)
        }
    }
    item(key = "task_composer") { TaskComposer(state, actions, composerFocus) }
}

@Composable
private fun TaskComposer(
    state: TasksUiState,
    actions: TaskActions,
    focus: FocusRequester
) {
    val palette = LocalPalette.current
    var text by rememberSaveable { mutableStateOf("") }
    val canAdd = text.isNotBlank() && !state.aiBusy

    Card {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.pill))
                .background(palette.surfaceHigh)
                .padding(horizontal = Space.l, vertical = 12.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    "Add a task — plain words work",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textDim
                )
            }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (state.message != null) actions.onClearMessage()
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textBright),
                cursorBrush = SolidColor(palette.accent),
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            ComposerButton(
                label = "Add",
                icon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = palette.ink) },
                container = palette.textBright,
                content = palette.ink,
                enabled = canAdd
            ) {
                actions.onQuickAdd(text)
                text = ""
            }
            if (state.aiAvailable) {
                ComposerButton(
                    label = "Add with AI",
                    icon = {
                        if (state.aiBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = palette.accent
                            )
                        } else {
                            Icon(
                                Icons.Rounded.AutoAwesome, null,
                                Modifier.size(16.dp), tint = palette.accent
                            )
                        }
                    },
                    container = palette.accent.copy(alpha = 0.14f),
                    content = palette.accent,
                    enabled = canAdd
                ) {
                    actions.onAiAdd(text)
                    text = ""
                }
            }
        }
        if (state.message != null) {
            Spacer(Modifier.height(Space.s))
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger
            )
        }
    }
}

@Composable
private fun ComposerButton(
    label: String,
    icon: @Composable () -> Unit,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressBounce(interaction)
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (enabled) container else palette.outline)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) content else palette.textDim
        )
    }
}

@Composable
private fun CapacityMeter(state: TasksUiState, onCapacity: (Int) -> Unit) {
    val palette = LocalPalette.current
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                SectionLabel("Day load")
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatHours(state.plannedMinutes.toLong())} planned · " +
                        "${state.doneCount}/${state.tasks.size} done",
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.textBright
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onCapacity(-30) }) {
                    Icon(Icons.Rounded.Remove, "Lower capacity", tint = palette.textDim)
                }
                Text(
                    formatHours(state.capacityMinutes.toLong()),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textBright
                )
                IconButton(onClick = { onCapacity(30) }) {
                    Icon(Icons.Rounded.Add, "Raise capacity", tint = palette.textDim)
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        StatBar(
            ratio = state.capacityRatio,
            color = if (state.overCapacity) palette.danger else palette.success,
            height = 6.dp
        )
        if (state.overCapacity) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Over your ${formatHours(state.capacityMinutes.toLong())} by " +
                    formatHours((state.plannedMinutes - state.capacityMinutes).toLong()) +
                    " — trim or move a task. The week absorbs it.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskRow(
    item: TaskWithSubtasks,
    aiAvailable: Boolean,
    aiBusy: Boolean,
    actions: TaskActions
) {
    val palette = LocalPalette.current
    val task = item.task
    val complete = item.isComplete
    val track = task.track?.let { runCatching { Track.valueOf(it) }.getOrNull() }
    val accent = track?.color() ?: palette.accent
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }

    PastelRow(
        tint = accent,
        dimmed = complete,
        onClick = { expanded = !expanded },
        verticalPadding = 12.dp
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (complete) palette.textDim else palette.textBright,
                textDecoration = if (complete) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(2.dp))
            Text(
                taskMeta(task),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtasks.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                StatBar(ratio = item.progress, color = accent, height = 4.dp)
                Spacer(Modifier.height(Space.s))
                item.subtasks.forEach { sub ->
                    SubtaskRow(sub) { actions.onToggleSubtask(item, sub) }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(Space.s))
                if (task.note.isNotEmpty()) {
                    Text(
                        task.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textDim
                    )
                    Spacer(Modifier.height(Space.s))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (qLabel, qColor) = quadrantOf(task, palette)
                    MiniChip(qLabel, qColor) { actions.onQuadrant(task) }
                    if (item.subtasks.isEmpty() && aiAvailable && task.reviewParentId == null) {
                        MiniChip(
                            if (aiBusy) "…" else "✨ Split into steps",
                            palette.accent
                        ) { if (!aiBusy) actions.onSplit(task.id) }
                    }
                    if (!complete && task.reviewParentId == null) {
                        MiniChip("→ Tomorrow", palette.textDim) { actions.onMoveTomorrow(task) }
                    }
                    MiniChip("✕ Delete", palette.danger) { actions.onDelete(task) }
                }
            }
        }
        Spacer(Modifier.width(Space.m))
        RoundCheck(
            checked = complete,
            color = accent,
            onToggle = { actions.onToggleTask(item) }
        )
    }
}

@Composable
private fun SubtaskRow(sub: SubtaskEntity, onToggle: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundCheck(
            checked = sub.done,
            color = palette.textDim,
            onToggle = onToggle,
            size = 20.dp
        )
        Spacer(Modifier.width(Space.s))
        Text(
            sub.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (sub.done) palette.textDim else palette.textBright,
            textDecoration = if (sub.done) TextDecoration.LineThrough else null
        )
    }
}

@Composable
private fun MiniChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(color.copy(alpha = 0.14f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** "~45m · YKS · review in 3 days" — everything that matters, one quiet line. */
private fun taskMeta(task: TaskEntity): String = buildList {
    if (task.estimatedMinutes > 0) add("~${formatHours(task.estimatedMinutes.toLong())}")
    task.track?.let { add(it) }
    if (task.reviewStage.isNotEmpty()) add("🔁 ${reviewLabel(task.reviewStage)}")
    if (task.urgent && task.important) add("🔥 do first")
}.joinToString(" · ").ifEmpty { "tap for actions" }

private fun quadrantOf(
    task: TaskEntity,
    palette: ai.bewsoa.flow.ui.theme.Palette
): Pair<String, Color> = when {
    task.urgent && task.important -> "🔥 Do first" to palette.danger
    task.important -> "🧭 Schedule" to palette.primary
    task.urgent -> "⚡ Quick win" to palette.warn
    else -> "🌙 Later" to palette.slate
}

private fun reviewLabel(stage: String): String = when (stage) {
    "1d" -> "1 day"
    "3d" -> "3 days"
    "1w" -> "1 week"
    "1mo" -> "1 month"
    else -> stage
}
