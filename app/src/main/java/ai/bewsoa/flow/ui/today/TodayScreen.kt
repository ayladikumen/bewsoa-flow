package ai.bewsoa.flow.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.ProgressRing
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.components.StatBar
import ai.bewsoa.flow.ui.formatCountdown
import ai.bewsoa.flow.ui.formatTime
import ai.bewsoa.flow.ui.theme.Amber
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
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
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            viewModel.onTick()
            delay(1_000L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { TodayHeader(state) }
        item { HeroCard(state, now) }
        item { SectionHeader("Today's blocks") }
        items(state.blocks, key = { it.block.id }) { item ->
            BlockCard(
                item = item,
                now = now,
                onToggle = { viewModel.setDone(item.block.id, !item.done) }
            )
        }
    }
}

@Composable
private fun TodayHeader(state: TodayUiState) {
    Column {
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
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(WeeklyProgram.dayLabel(state.date), Violet)
            Chip("🔥 ${state.streak.current} day streak", Amber)
        }
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

@Composable
private fun BlockCard(item: BlockWithStatus, now: LocalTime, onToggle: () -> Unit) {
    val block = item.block
    val isCurrent = now >= block.start && now < block.end
    val isPastUndone = !item.done && block.counted && now >= block.end
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
                        .background(accent.copy(alpha = 0.5f))
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
                    color = if (item.done) TextDim else TextBright,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null
                )
                if (block.note.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        block.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (item.done) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = if (item.done) "Mark as not done" else "Mark as done",
                        tint = if (item.done) Mint else TextDim,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
