package ai.bewsoa.flow.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.Track
import ai.bewsoa.flow.ui.components.AppTextField
import ai.bewsoa.flow.ui.components.Chip
import ai.bewsoa.flow.ui.components.DangerButton
import ai.bewsoa.flow.ui.components.GhostButton
import ai.bewsoa.flow.ui.components.NumberStepper
import ai.bewsoa.flow.ui.components.PrimaryButton
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.formatTime
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** What the editor sheet is open for. */
sealed interface BlockEdit {
    val day: DayOfWeek

    /** A block that doesn't exist yet — it gets its id when it is added. */
    data class New(override val day: DayOfWeek) : BlockEdit

    /** An existing block; its id is preserved through every edit. */
    data class Existing(override val day: DayOfWeek, val block: TaskBlock) : BlockEdit
}

/** Everything the sheet can ask the ViewModel to do, in one holder. */
data class BlockEditorActions(
    val onDismiss: () -> Unit,
    val onAdd: (DayOfWeek, TaskBlock, Set<DayOfWeek>) -> Unit,
    val onUpdate: (DayOfWeek, TaskBlock) -> Unit,
    val onDelete: (DayOfWeek, String) -> Unit,
    val onDuplicate: (DayOfWeek, String) -> Unit,
    val onMoveTo: (DayOfWeek, DayOfWeek, String) -> Unit,
    val onRepeatOn: (DayOfWeek, String, Set<DayOfWeek>) -> Unit
)

/**
 * A bottom sheet built from the app's own surfaces rather than Material's, so it
 * carries the same paper-card look, keeps the keyboard out of the way, and needs
 * no experimental APIs.
 */
@Composable
fun BuilderSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPalette.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.scrim)
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                .background(palette.surface)
                // Taps inside the sheet must not reach the dismissing scrim.
                .clickable(interactionSource = sheetInteraction, indication = null) {}
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Space.l),
            content = content
        )
    }
}

/**
 * Tap a block, edit it here. Title, track, times, note and whether it counts —
 * plus the actions that only make sense for one block: duplicate, repeat on
 * other days, move to another day, delete.
 *
 * Saving an existing block keeps its id, so its completion history follows the
 * edit; everything that copies goes through the draft's id minting instead.
 */
@Composable
fun BlockEditorSheet(edit: BlockEdit, actions: BlockEditorActions) {
    val palette = LocalPalette.current
    val existing = (edit as? BlockEdit.Existing)?.block

    var title by remember(edit) { mutableStateOf(existing?.title ?: "") }
    var track by remember(edit) { mutableStateOf(existing?.track ?: Track.YKS) }
    var start by remember(edit) { mutableStateOf(existing?.start ?: LocalTime.of(9, 0)) }
    var end by remember(edit) { mutableStateOf(existing?.end ?: LocalTime.of(10, 0)) }
    var note by remember(edit) { mutableStateOf(existing?.note.orEmpty()) }
    var counted by remember(edit) { mutableStateOf(existing?.counted ?: true) }
    var otherDays by remember(edit) { mutableStateOf(emptySet<DayOfWeek>()) }
    var movingTo by remember(edit) { mutableStateOf(false) }

    val minutes = Duration.between(start, end).toMinutes()
    val timesValid = minutes > 0
    val canSave = title.isNotBlank() && timesValid

    BuilderSheet(onDismiss = actions.onDismiss) {
        SectionLabel(
            if (existing == null) {
                "New block · ${dayName(edit.day)}"
            } else {
                "Edit block · ${dayName(edit.day)}"
            }
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            title.ifBlank { "Untitled block" },
            style = MaterialTheme.typography.headlineSmall,
            color = palette.textBright
        )
        Spacer(Modifier.height(Space.l))

        AppTextField(
            value = title,
            onValueChange = { title = it },
            label = "Title",
            placeholder = "YKS — deep work"
        )
        Spacer(Modifier.height(Space.l))

        SectionLabel("Track")
        Spacer(Modifier.height(Space.s))
        TrackPicker(
            selected = track,
            onSelect = { picked ->
                track = picked
                // Meals and free time don't count toward progress; picking one
                // sets the sensible default, still visible in the toggle below.
                counted = picked != Track.MEAL && picked != Track.FREE
            }
        )
        Spacer(Modifier.height(Space.l))

        TimeRow(
            label = "Start",
            value = start,
            onChange = { moved ->
                // Moving the start drags the block: its length is kept.
                start = moved
                end = moved.shifted(minutes.coerceAtLeast(5))
            }
        )
        Spacer(Modifier.height(Space.m))
        TimeRow(label = "End", value = end, onChange = { end = it })
        Spacer(Modifier.height(Space.s))
        Text(
            if (timesValid) {
                "Duration ${formatHours(minutes)}"
            } else {
                "The end has to come after the start."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (timesValid) palette.textDim else palette.danger
        )
        Spacer(Modifier.height(Space.l))

        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = "Note (optional)",
            singleLine = false,
            minLines = 2
        )
        Spacer(Modifier.height(Space.m))

        Chip(
            text = if (counted) "✓ Counts toward progress" else "Doesn't count toward progress",
            selected = counted,
            color = palette.success,
            onClick = { counted = !counted }
        )
        Spacer(Modifier.height(Space.l))

        if (existing == null) {
            SectionLabel("Also add on")
            Spacer(Modifier.height(Space.s))
            DayPickerRow(
                selected = otherDays,
                exclude = edit.day,
                onToggle = { day ->
                    otherDays = if (day in otherDays) otherDays - day else otherDays + day
                }
            )
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                text = if (otherDays.isEmpty()) {
                    "Add block"
                } else {
                    "Add to ${otherDays.size + 1} days"
                },
                enabled = canSave,
                onClick = {
                    actions.onAdd(
                        edit.day,
                        TaskBlock(
                            id = "",
                            title = title.trim(),
                            track = track,
                            start = start,
                            end = end,
                            note = note.trim(),
                            counted = counted
                        ),
                        otherDays
                    )
                    actions.onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            SectionLabel("Repeat on")
            Spacer(Modifier.height(Space.s))
            DayPickerRow(
                selected = otherDays,
                exclude = edit.day,
                onToggle = { day ->
                    otherDays = if (day in otherDays) otherDays - day else otherDays + day
                }
            )
            if (otherDays.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                GhostButton(
                    text = "Repeat on ${otherDays.size} more day${if (otherDays.size == 1) "" else "s"}",
                    onClick = {
                        actions.onRepeatOn(edit.day, existing.id, otherDays)
                        actions.onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                text = "Save block",
                enabled = canSave,
                onClick = {
                    // The id is copied through untouched — this block keeps its history.
                    actions.onUpdate(
                        edit.day,
                        existing.copy(
                            title = title.trim(),
                            track = track,
                            start = start,
                            end = end,
                            note = note.trim(),
                            counted = counted
                        )
                    )
                    actions.onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                GhostButton(
                    text = "Duplicate",
                    onClick = {
                        actions.onDuplicate(edit.day, existing.id)
                        actions.onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    text = if (movingTo) "Pick a day" else "Move to…",
                    onClick = { movingTo = !movingTo },
                    modifier = Modifier.weight(1f)
                )
            }
            if (movingTo) {
                Spacer(Modifier.height(Space.s))
                DayPickerRow(
                    selected = emptySet(),
                    exclude = edit.day,
                    onToggle = { day ->
                        actions.onMoveTo(edit.day, day, existing.id)
                        actions.onDismiss()
                    }
                )
            }
            Spacer(Modifier.height(Space.s))
            DangerButton(
                text = "Delete block",
                onClick = {
                    actions.onDelete(edit.day, existing.id)
                    actions.onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(Space.s))
        GhostButton(
            text = "Cancel",
            onClick = actions.onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Space.s))
    }
}

@Composable
private fun TrackPicker(selected: Track, onSelect: (Track) -> Unit) {
    val rows = Track.entries.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { track ->
                    Chip(
                        text = "${track.emoji} ${track.label}",
                        selected = track == selected,
                        color = track.color(),
                        onClick = { onSelect(track) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Hour and quarter-hour nudges: fast on a phone, and it can't produce a bad time. */
@Composable
private fun TimeRow(label: String, value: LocalTime, onChange: (LocalTime) -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(80.dp)) {
            SectionLabel(label)
            Text(
                formatTime(value),
                style = MaterialTheme.typography.titleLarge,
                color = palette.textBright
            )
        }
        Spacer(Modifier.weight(1f))
        NumberStepper(
            value = "h",
            onDecrease = { onChange(value.shifted(-60)) },
            onIncrease = { onChange(value.shifted(60)) }
        )
        Spacer(Modifier.width(Space.s))
        NumberStepper(
            value = "m",
            onDecrease = { onChange(value.shifted(-15)) },
            onIncrease = { onChange(value.shifted(15)) }
        )
    }
}

/**
 * Minute arithmetic that stays inside one day — a block never wraps past
 * midnight, and 23:59 is the last minute, matching the program's convention.
 */
private fun LocalTime.shifted(minutes: Long): LocalTime {
    val total = (hour * 60L + minute + minutes).coerceIn(0L, 23L * 60 + 59)
    return LocalTime.of((total / 60).toInt(), (total % 60).toInt())
}

private fun dayName(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, Locale.US)
