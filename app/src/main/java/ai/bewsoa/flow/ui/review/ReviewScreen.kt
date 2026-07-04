package ai.bewsoa.flow.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.ProgramRepository
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
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val pastWeeks by viewModel.pastWeeks.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                "Weekly Review",
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

        report?.let { r ->
            GlowCard(accent = Violet) {
                SectionHeader("Weekly report · automatic")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 26.sp)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(
                            "${r.streak.current} day streak",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextBright
                        )
                        Text(
                            "Built from your logged days — nothing to fill in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                WeekReportBody(r.stats)
            }
        }

        GlowCard(accent = Cyan) {
            SectionHeader("Notes · 1 minute")
            Spacer(Modifier.height(12.dp))
            FlowTextField(
                value = notes.tytScore,
                onValueChange = { v -> viewModel.update { it.copy(tytScore = v) } },
                label = "TYT / deneme score (optional)",
                numeric = true
            )
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = notes.slowedMeDown,
                onValueChange = { v -> viewModel.update { it.copy(slowedMeDown = v) } },
                label = "One thing that slowed me down"
            )
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = notes.nextWeekTask,
                onValueChange = { v -> viewModel.update { it.copy(nextWeekTask = v) } },
                label = "Next week's single most important task"
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                Text(
                    if (justSaved) "Saved ✓" else "Save notes",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }

        if (pastWeeks.isNotEmpty()) {
            SectionHeader("Past weeks")
            pastWeeks.forEach { week -> PastWeekCard(week) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The report itself: days kept, block totals, per-track hours. Read-only. */
@Composable
private fun WeekReportBody(stats: WeekStats) {
    DaysKeptRow(stats)
    Spacer(Modifier.height(14.dp))
    Text(
        "${stats.doneBlocks} of ${stats.plannedBlocks} blocks · " +
            "${(stats.overallRatio * 100).roundToInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = TextDim
    )
    Spacer(Modifier.height(8.dp))
    StatBar(ratio = stats.overallRatio, color = Violet)
    Spacer(Modifier.height(14.dp))
    stats.tracks.forEach { stat -> TrackLine(stat) }
}

@Composable
private fun DaysKeptRow(stats: WeekStats) {
    val today = LocalDate.now()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.days.forEach { day ->
            val kept = day.ratio >= ProgramRepository.KEEP_THRESHOLD
            val (symbol, color) = when {
                day.date.isAfter(today) -> "·" to Outline
                kept -> "✓" to Mint
                day.date == today -> "…" to Amber
                else -> "✗" to Coral
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    day.date.dayOfWeek
                        .getDisplayName(TextStyle.SHORT, Locale.US)
                        .take(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.date == today) Cyan else TextDim
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.16f))
                        .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(symbol, color = color, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TrackLine(stat: TrackStat) {
    val isGym = stat.track == Track.GYM
    if (stat.plannedMinutes == 0L && stat.plannedSessions == 0) return
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${stat.track.emoji} ${stat.track.label}",
            style = MaterialTheme.typography.bodyMedium,
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
}

/** A saved week: tap to expand the full report + notes. */
@Composable
private fun PastWeekCard(week: PastWeek) {
    var expanded by remember(week.weekStart) { mutableStateOf(false) }
    GlowCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Week of ${formatWeekRange(week.weekStart)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextBright
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${week.stats.doneBlocks}/${week.stats.plannedBlocks} blocks · " +
                        "${(week.stats.overallRatio * 100).roundToInt()}%" +
                        if (week.notes.tytScore.isNotBlank()) " · TYT ${week.notes.tytScore}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
            Text(
                if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.titleMedium,
                color = Cyan
            )
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            WeekReportBody(week.stats)
            if (week.notes.slowedMeDown.isNotBlank() || week.notes.nextWeekTask.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Notes")
                Spacer(Modifier.height(6.dp))
                if (week.notes.slowedMeDown.isNotBlank()) {
                    Text(
                        "Slowed me down: ${week.notes.slowedMeDown}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
                if (week.notes.nextWeekTask.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "→ ${week.notes.nextWeekTask}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet,
            unfocusedBorderColor = Outline,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = TextDim,
            cursorColor = Cyan,
            focusedTextColor = TextBright,
            unfocusedTextColor = TextBright
        )
    )
}
