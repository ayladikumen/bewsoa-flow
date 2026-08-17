package ai.bewsoa.flow.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.WeeklyProgramDraft
import ai.bewsoa.flow.ui.components.Chip
import ai.bewsoa.flow.ui.components.PastelRow
import ai.bewsoa.flow.ui.components.SectionLabel
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.formatTime
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Radius
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The reusable weekly preview: the Mon–Sun strip, one day's blocks, and a
 * read-only whole-week rundown for the save review. Mobile first — a day
 * selector plus a vertical stack, never a dense seven-column calendar.
 *
 * It borrows the plan's existing visual language wholesale: pastel track rows,
 * the same day chips as the Week tab, the same spacing and radii.
 */
@Composable
fun WeekDayStrip(
    draft: WeeklyProgramDraft,
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    daysWithErrors: Set<DayOfWeek> = emptySet()
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        DayOfWeek.entries.forEach { day ->
            DayStripChip(
                day = day,
                blocks = draft.blocksFor(day).size,
                selected = day == selected,
                hasError = day in daysWithErrors,
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayStripChip(
    day: DayOfWeek,
    blocks: Int,
    selected: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.row))
            .background(if (selected) palette.primaryDeep else palette.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(vertical = Space.s),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            day.getDisplayName(TextStyle.NARROW, Locale.US),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.textBright else palette.textDim
        )
        Spacer(Modifier.height(3.dp))
        Text(
            blocks.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                hasError -> palette.danger
                blocks == 0 -> palette.textDim
                else -> palette.textBright
            }
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(
                    when {
                        hasError -> palette.danger
                        blocks > 0 -> palette.success
                        else -> Color.Transparent
                    }
                )
        )
    }
}

/**
 * One block in the editor: track rail colour, times, duration, note — and the
 * two nudges that reorder a day without a drag.
 */
@Composable
fun DraftBlockRow(
    block: TaskBlock,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    val accent = block.track.color()
    PastelRow(
        tint = if (hasError) palette.danger else accent,
        onClick = onClick,
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
                block.title.ifBlank { "Untitled block" },
                style = MaterialTheme.typography.titleMedium,
                color = palette.textBright,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatTime(block.start)}–${formatTime(block.end)} · " +
                    formatHours(block.durationMinutes.coerceAtLeast(0)) +
                    (if (block.counted) "" else " · doesn't count"),
                style = MaterialTheme.typography.labelMedium,
                color = if (hasError) palette.danger else palette.textDim
            )
            if (block.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    block.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMoveUp != null || onMoveDown != null) {
            Column {
                NudgeButton(Icons.Rounded.KeyboardArrowUp, "Move earlier", onMoveUp)
                NudgeButton(Icons.Rounded.KeyboardArrowDown, "Move later", onMoveDown)
            }
        }
    }
}

@Composable
private fun NudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: (() -> Unit)?
) {
    val palette = LocalPalette.current
    IconButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (onClick != null) palette.textDim else palette.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** The dashed-feeling "+ Add block" affordance that ends every day. */
@Composable
fun AddBlockRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(palette.surfaceHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = palette.textBright,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Space.s))
        Text(
            "Add block",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textBright
        )
    }
}

/**
 * Seven day chips: multi-select for "repeat on" / "also add on", single-tap for
 * "pick a day" (move, copy day). Scrollable, so a large font scale pushes it
 * sideways instead of clipping it.
 */
@Composable
fun DayPickerRow(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    exclude: DayOfWeek? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        DayOfWeek.entries.forEach { day ->
            Chip(
                text = day.getDisplayName(TextStyle.SHORT, Locale.US).take(2),
                selected = day in selected,
                onClick = if (day == exclude) null else ({ onToggle(day) })
            )
        }
    }
}

/**
 * The whole week, read-only — used in the save review so the user sees what they
 * are about to commit to without leaving the sheet.
 */
@Composable
fun WeeklyProgramPreview(draft: WeeklyProgramDraft, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            val blocks = draft.blocksFor(day)
            Spacer(Modifier.height(Space.m))
            SectionLabel(day.getDisplayName(TextStyle.FULL, Locale.US))
            Spacer(Modifier.height(Space.xs))
            if (blocks.isEmpty()) {
                Text(
                    "Nothing scheduled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textDim
                )
            } else {
                blocks.forEach { block ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(block.track.color())
                        )
                        Spacer(Modifier.width(Space.s))
                        Text(
                            "${formatTime(block.start)}–${formatTime(block.end)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textDim,
                            modifier = Modifier.width(96.dp)
                        )
                        Text(
                            "${block.track.emoji} ${block.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
