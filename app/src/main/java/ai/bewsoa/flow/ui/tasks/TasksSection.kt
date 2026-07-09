package ai.bewsoa.flow.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.bewsoa.flow.data.Track
import ai.bewsoa.flow.data.db.SubtaskEntity
import ai.bewsoa.flow.data.db.TaskEntity
import ai.bewsoa.flow.data.db.TaskWithSubtasks
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.theme.Amber
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Muted
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import ai.bewsoa.flow.ui.theme.color

/**
 * Callbacks the tasks section fires back to [TasksViewModel]. Grouped so the
 * Today screen forwards them in one place.
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

/** Adds the user-task cards to the Today [LazyListScope] under the routine blocks. */
fun LazyListScope.tasksSection(state: TasksUiState, actions: TaskActions) {
    item(key = "task_composer") { TaskComposer(state, actions) }
    if (state.tasks.isNotEmpty()) {
        item(key = "task_capacity") { CapacityMeter(state, actions.onCapacity) }
        items(state.tasks, key = { "task_${it.task.id}" }) { item ->
            TaskCard(item, state.aiAvailable, state.aiBusy, actions)
        }
    } else {
        item(key = "task_empty") { EmptyHint() }
    }
}

@Composable
private fun TaskComposer(state: TasksUiState, actions: TaskActions) {
    var text by remember { mutableStateOf("") }

    GlowCard {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (state.message != null) actions.onClearMessage()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "What do you need to do? e.g. solve 3 derivative tests tomorrow evening",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright,
                cursorColor = Cyan,
                focusedBorderColor = Cyan.copy(alpha = 0.7f),
                unfocusedBorderColor = Outline,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedPlaceholderColor = TextDim,
                unfocusedPlaceholderColor = TextDim
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (text.isNotBlank()) { actions.onQuickAdd(text); text = "" } },
                enabled = text.isNotBlank() && !state.aiBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet,
                    contentColor = TextBright
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
            if (state.aiAvailable) {
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(
                    onClick = { if (text.isNotBlank()) { actions.onAiAdd(text); text = "" } },
                    enabled = text.isNotBlank() && !state.aiBusy,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Cyan.copy(alpha = 0.18f),
                        contentColor = Cyan
                    )
                ) {
                    if (state.aiBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Cyan
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Add with AI")
                }
            }
        }
        val hint = when {
            state.message != null -> state.message to Coral
            !state.aiAvailable -> "Add an API key in Settings to let AI schedule, size and split your tasks." to TextDim
            else -> null
        }
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint.first, style = MaterialTheme.typography.bodySmall, color = hint.second)
        }
    }
}

@Composable
private fun CapacityMeter(state: TasksUiState, onCapacity: (Int) -> Unit) {
    val planned = state.plannedMinutes
    val capacity = state.capacityMinutes
    GlowCard(accent = if (state.overCapacity) Coral else null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "DAY LOAD",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatHours(planned.toLong())} planned · ${state.doneCount}/${state.tasks.size} done",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextBright
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onCapacity(-30) }) {
                    Icon(Icons.Rounded.Remove, "Lower capacity", tint = TextDim)
                }
                Text(
                    formatHours(capacity.toLong()),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextBright
                )
                IconButton(onClick = { onCapacity(30) }) {
                    Icon(Icons.Rounded.Add, "Raise capacity", tint = TextDim)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        StatBar(
            ratio = state.capacityRatio,
            color = if (state.overCapacity) Coral else Mint
        )
        Spacer(Modifier.height(6.dp))
        val (msg, tint) = if (state.overCapacity) {
            "Over your ${formatHours(capacity.toLong())} by ${formatHours((planned - capacity).toLong())} — trim or move a task." to Coral
        } else {
            "${formatHours((capacity - planned).toLong())} of focus time left today." to TextDim
        }
        Text(msg, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    item: TaskWithSubtasks,
    aiAvailable: Boolean,
    aiBusy: Boolean,
    actions: TaskActions
) {
    val task = item.task
    val complete = item.isComplete
    val track = task.track?.let { runCatching { Track.valueOf(it) }.getOrNull() }
    val accent = track?.color() ?: Cyan

    GlowCard(accent = if (complete) null else accent.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.Top) {
            IconButton(onClick = { actions.onToggleTask(item) }) {
                Icon(
                    imageVector = if (complete) Icons.Rounded.CheckCircle
                    else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (complete) "Mark as not done" else "Mark as done",
                    tint = if (complete) Mint else TextDim,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (complete) TextDim else TextBright,
                    textDecoration = if (complete) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Eisenhower quadrant — a tap walks Do first → Schedule → Quick → Later.
                    val quadrant = quadrantOf(task)
                    MiniChip(quadrant.first, quadrant.second) { actions.onQuadrant(task) }
                    if (track != null) MiniChip("${track.emoji} ${track.label}", accent)
                    if (task.reviewStage.isNotEmpty()) {
                        MiniChip("🔁 Review · ${reviewLabel(task.reviewStage)}", Violet)
                    }
                    if (task.estimatedMinutes > 0) {
                        MiniChip("⏱ ${formatHours(task.estimatedMinutes.toLong())}", Amber)
                    }
                }
                if (task.note.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        task.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.subtasks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    StatBar(ratio = item.progress, color = accent, height = 6.dp)
                    Spacer(Modifier.height(8.dp))
                    item.subtasks.forEach { sub ->
                        SubtaskRow(sub) { actions.onToggleSubtask(item, sub) }
                    }
                }
                val showSplit = item.subtasks.isEmpty() && aiAvailable && task.reviewParentId == null
                val showTomorrow = !complete && task.reviewParentId == null
                if (showSplit || showTomorrow) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (showSplit) {
                            CardAction(
                                icon = Icons.Rounded.AutoAwesome,
                                label = "Break into steps",
                                tint = Cyan,
                                enabled = !aiBusy
                            ) { actions.onSplit(task.id) }
                        }
                        if (showTomorrow) {
                            CardAction(
                                icon = Icons.Rounded.Update,
                                label = "Tomorrow",
                                tint = TextDim,
                                enabled = true
                            ) { actions.onMoveTomorrow(task) }
                        }
                    }
                }
            }
            IconButton(onClick = { actions.onDelete(task) }) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Delete task",
                    tint = TextDim,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(sub: SubtaskEntity, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (sub.done) Icons.Rounded.CheckCircle
                else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (sub.done) Mint else TextDim,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            sub.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (sub.done) TextDim else TextBright,
            textDecoration = if (sub.done) TextDecoration.LineThrough else null
        )
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}

/** Eisenhower chip label + color for a task's current quadrant. */
@Composable
private fun quadrantOf(task: TaskEntity): Pair<String, Color> = when {
    task.urgent && task.important -> "🔥 Do first" to Coral
    task.important -> "🧭 Schedule" to Cyan
    task.urgent -> "⚡ Quick win" to Amber
    else -> "🌙 Later" to Muted
}

@Composable
private fun MiniChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextBright)
    }
}

@Composable
private fun EmptyHint() {
    GlowCard {
        Text(
            "No tasks yet",
            style = MaterialTheme.typography.titleSmall,
            color = TextBright
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Jot down what you actually need to do — the routine above is your time skeleton, " +
                "these are the jobs. AI can size them, split them and schedule reviews. " +
                "Nothing here dents your streak: slide a task to tomorrow whenever you need to.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
    }
}

private fun reviewLabel(stage: String): String = when (stage) {
    "1d" -> "1 day"
    "3d" -> "3 days"
    "1w" -> "1 week"
    "1mo" -> "1 month"
    else -> stage
}
