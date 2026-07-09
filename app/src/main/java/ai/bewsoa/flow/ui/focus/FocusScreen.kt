package ai.bewsoa.flow.ui.focus

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.db.FocusSessionEntity
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.ProgressRing
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.formatCountdown
import ai.bewsoa.flow.ui.formatHours
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Ink
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sessionTime = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val DURATION_PRESETS = listOf(25, 45, 60, 90, 120)

@Composable
fun FocusScreen(
    viewModel: FocusViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    "Deep Focus",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextBright
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "One thing, one committed stretch of time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }

        item {
            when {
                state.active == null -> ComposerCard(onStart = viewModel::start)
                state.timeUp -> VerdictCard(
                    state = state,
                    onCompleted = viewModel::completeFull,
                    onDiscard = viewModel::abandon
                )
                else -> RunningCard(
                    state = state,
                    onFinishEarly = viewModel::finishEarly,
                    onAbandon = viewModel::abandon
                )
            }
        }

        item { SectionHeader("Today") }
        if (state.todaySessions.isEmpty()) {
            item {
                GlowCard {
                    Text(
                        "Nothing logged yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextBright
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Finished sessions land here at the moment you confirm them, " +
                            "count toward today's deep-work meter, and roll into the " +
                            "week's Deep Focus total on Progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
            }
        } else {
            item { TotalCard(state.todayMinutes, state.todaySessions.size) }
            items(state.todaySessions, key = { it.id }) { session ->
                SessionRow(session)
            }
        }
    }
}

// Idle: commit to "what + how long" ---------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerCard(onStart: (String, Int) -> Unit) {
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(45) }

    GlowCard(accent = Cyan) {
        Text(
            "START A SESSION",
            style = MaterialTheme.typography.labelSmall,
            color = Cyan,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "What will you do? e.g. 40 paragraf sorusu",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            maxLines = 2,
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
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DURATION_PRESETS.forEach { preset ->
                DurationChip(
                    text = formatHours(preset.toLong()),
                    selected = minutes == preset
                ) { minutes = preset }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { minutes = (minutes - 5).coerceAtLeast(5) }) {
                    Icon(Icons.Rounded.Remove, "Less time", tint = TextDim)
                }
                Text(
                    formatHours(minutes.toLong()),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextBright
                )
                IconButton(onClick = { minutes = (minutes + 5).coerceAtMost(240) }) {
                    Icon(Icons.Rounded.Add, "More time", tint = TextDim)
                }
            }
            Button(
                onClick = { onStart(label, minutes) },
                enabled = label.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet,
                    contentColor = TextBright
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start focus")
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "When time's up you confirm what happened — only confirmed sessions count.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
    }
}

@Composable
private fun DurationChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Cyan else TextDim
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Cyan.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (selected) 0.7f else 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Cyan else TextDim
        )
    }
}

// Running: the countdown --------------------------------------------------

@Composable
private fun RunningCard(
    state: FocusUiState,
    onFinishEarly: () -> Unit,
    onAbandon: () -> Unit
) {
    val active = state.active ?: return
    GlowCard(accent = Cyan) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FOCUS RUNNING",
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(14.dp))
            ProgressRing(
                progress = state.sessionProgress,
                modifier = Modifier.size(190.dp),
                strokeWidth = 10.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatCountdown(Duration.ofMillis(state.remainingMillis)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextBright
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "of ${formatHours(active.plannedMinutes.toLong())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextDim
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                active.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextBright,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onFinishEarly,
                    enabled = state.elapsedMinutes >= 1,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Mint.copy(alpha = 0.18f),
                        contentColor = Mint
                    )
                ) { Text("Finish now · ${state.elapsedMinutes}m") }
                TextButton(onClick = onAbandon) {
                    Text("Abandon", color = Coral)
                }
            }
        }
    }
}

// Time's up: the verdict ---------------------------------------------------

@Composable
private fun VerdictCard(
    state: FocusUiState,
    onCompleted: () -> Unit,
    onDiscard: () -> Unit
) {
    val active = state.active ?: return
    GlowCard(accent = Mint) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "TIME'S UP",
                style = MaterialTheme.typography.labelSmall,
                color = Mint,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                active.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextBright,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatHours(active.plannedMinutes.toLong())} committed — did you finish?",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCompleted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        contentColor = Ink
                    )
                ) { Text("Completed ✓") }
                TextButton(onClick = onDiscard) {
                    Text("Discard", color = TextDim)
                }
            }
        }
    }
}

// Today's tally -------------------------------------------------------------

@Composable
private fun TotalCard(totalMinutes: Int, sessions: Int) {
    GlowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "FOCUSED TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    formatHours(totalMinutes.toLong()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextBright
                )
            }
            Text(
                "$sessions session${if (sessions == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = TextDim
            )
        }
    }
}

@Composable
private fun SessionRow(session: FocusSessionEntity) {
    val finishedAt = Instant.ofEpochMilli(session.completedAt)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧠", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    session.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextBright
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "done ${sessionTime.format(finishedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
            Text(
                formatHours(session.minutes.toLong()),
                style = MaterialTheme.typography.labelLarge,
                color = Mint
            )
        }
    }
}
