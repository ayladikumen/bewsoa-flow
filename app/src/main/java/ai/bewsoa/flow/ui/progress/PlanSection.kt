package ai.bewsoa.flow.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.bewsoa.flow.data.Streak
import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.ui.components.Card
import ai.bewsoa.flow.ui.components.Rail
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.formatTime
import ai.bewsoa.flow.ui.formatWeekRange
import ai.bewsoa.flow.ui.theme.LocalPalette
import ai.bewsoa.flow.ui.theme.Space
import ai.bewsoa.flow.ui.theme.color
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day mode of the weekly plan: pick any day of any week and see its real
 * agenda — titles, notes, times, and what the log says happened. This is the
 * "see the whole plan, not just today" feature; the packed 7-column grid
 * (Week mode) comes later and slots in beside it.
 */
@Composable
fun PlanCard(
    state: PlanUiState,
    onSelectDay: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onJumpToday: () -> Unit,
    onToggle: (LocalDate, String, Boolean) -> Unit,
    onSkip: (LocalDate, String) -> Unit,
    onUnskip: (LocalDate, String) -> Unit
) {
    val palette = LocalPalette.current
    val today = LocalDate.now()

    Card {
        // ‹ Week range › — with a way home when you've wandered off.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onShiftWeek(-1) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous week",
                    tint = palette.textDim
                )
            }
            Text(
                formatWeekRange(state.weekStart),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textBright,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onShiftWeek(1) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next week",
                    tint = palette.textDim
                )
            }
        }
        if (today !in state.weekStart..state.weekStart.plusDays(6)) {
            TextButton(
                onClick = onJumpToday,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    "Back to today",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
            }
        }
        Spacer(Modifier.height(Space.s))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            state.chips.forEach { chip ->
                DayChip(
                    chip = chip,
                    selected = chip.date == state.selected,
                    isToday = chip.date == today,
                    onClick = { onSelectDay(chip.date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(Space.l))

        if (state.blocks.isEmpty()) {
            Text(
                "Nothing scheduled this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textDim,
                modifier = Modifier.padding(vertical = Space.m)
            )
        } else {
            state.blocks.forEachIndexed { index, planBlock ->
                PlanBlockRow(
                    planBlock = planBlock,
                    date = state.selected,
                    today = today,
                    onToggle = onToggle,
                    onSkip = onSkip,
                    onUnskip = onUnskip
                )
                if (index != state.blocks.lastIndex) Spacer(Modifier.height(Space.m))
            }
            Spacer(Modifier.height(Space.l))
            val counted = state.blocks.filter {
                it.block.counted && it.state != CompletionState.SKIPPED
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${counted.size} counted block${if (counted.size == 1) "" else "s"} · " +
                        formatHours(counted.sumOf { it.block.durationMinutes }),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textDim
                )
                if (state.selected == today) {
                    Text(
                        if (state.skipBudget.exhausted) "no skips left"
                        else "${state.skipBudget.remaining} of ${state.skipBudget.cap} skips left",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textDim
                    )
                }
            }
        }
    }
}

/**
 * One day in the Mon..Sun strip: letter, date, and a status dot that tells
 * the week's story at a glance — kept, partial, missed, or not yet written.
 */
@Composable
private fun DayChip(
    chip: PlanDayChip,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val isFuture = chip.date.isAfter(LocalDate.now())
    val ratio = if (chip.planned == 0) 0f else chip.done.toFloat() / chip.planned
    val dotColor = when {
        isFuture -> null
        chip.planned == 0 -> palette.outline
        ratio >= Streak.KEEP_THRESHOLD -> palette.success
        chip.done > 0 -> palette.warn
        else -> palette.outline
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.primaryDeep else palette.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(vertical = Space.s),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            chip.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) palette.accent else palette.textDim
        )
        Spacer(Modifier.height(2.dp))
        Text(
            chip.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textBright
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor ?: androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Composable
private fun PlanBlockRow(
    planBlock: PlanBlock,
    date: LocalDate,
    today: LocalDate,
    onToggle: (LocalDate, String, Boolean) -> Unit,
    onSkip: (LocalDate, String) -> Unit,
    onUnskip: (LocalDate, String) -> Unit
) {
    val palette = LocalPalette.current
    val block = planBlock.block
    val done = planBlock.state == CompletionState.DONE
    val skipped = planBlock.state == CompletionState.SKIPPED
    // Logging is for today and the past; the future is view-only. Skipping is
    // today-only — "cancel this task for today" was the contract.
    val loggable = block.counted && !date.isAfter(today)
    val skippable = block.counted && date == today && !done
    val trackColor = block.track.color()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(42.dp)) {
            Text(
                formatTime(block.start),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textBright
            )
            Text(
                formatTime(block.end),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textDim
            )
        }
        Spacer(Modifier.width(Space.m))
        Rail(if (skipped) palette.railDim else trackColor, modifier = Modifier.fillMaxHeight())
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                "${block.track.emoji} ${block.title}",
                style = MaterialTheme.typography.titleMedium,
                color = if (done || skipped) palette.textDim else palette.textBright,
                textDecoration = if (done || skipped) {
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                } else {
                    null
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (skipped) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Skipped · excused",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textDim
                )
            } else if (block.note.isNotEmpty()) {
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
        if (skipped) {
            TextButton(onClick = { onUnskip(date, block.id) }) {
                Text(
                    "Undo",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
            }
        } else if (loggable) {
            if (skippable) {
                IconButton(onClick = { onSkip(date, block.id) }) {
                    Icon(
                        Icons.Rounded.Block,
                        contentDescription = "Skip today",
                        tint = palette.textDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = { onToggle(date, block.id, !done) }) {
                Icon(
                    imageVector = if (done) {
                        Icons.Rounded.CheckCircle
                    } else {
                        Icons.Rounded.RadioButtonUnchecked
                    },
                    contentDescription = if (done) "Mark as not done" else "Mark as done",
                    tint = if (done) palette.success else palette.textDim,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
