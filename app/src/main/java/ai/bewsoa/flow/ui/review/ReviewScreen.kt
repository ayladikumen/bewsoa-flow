package ai.bewsoa.flow.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.formatWeekRange
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Orange
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val justSaved by viewModel.justSaved.collectAsStateWithLifecycle()
    val pastReviews by viewModel.pastReviews.collectAsStateWithLifecycle()

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
                "Week of ${formatWeekRange(viewModel.weekStart)} · Sunday, 20 minutes",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }

        GlowCard(accent = Violet) {
            SectionHeader("Exams")
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "YKS weekday blocks kept",
                value = form.yksBlocksKept,
                max = 5,
                onChange = { v -> viewModel.update { it.copy(yksBlocksKept = v) } }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowTextField(
                    value = form.tytScore,
                    onValueChange = { v -> viewModel.update { it.copy(tytScore = v) } },
                    label = "TYT score",
                    modifier = Modifier.weight(1f),
                    numeric = true
                )
                FlowTextField(
                    value = form.tytPrevScore,
                    onValueChange = { v -> viewModel.update { it.copy(tytPrevScore = v) } },
                    label = "Last week",
                    modifier = Modifier.weight(1f),
                    numeric = true
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = form.biggestGap,
                onValueChange = { v -> viewModel.update { it.copy(biggestGap = v) } },
                label = "Biggest gap from mistake review"
            )
        }

        GlowCard(accent = Cyan) {
            SectionHeader("SAT")
            Spacer(Modifier.height(12.dp))
            FlowTextField(
                value = form.satHours,
                onValueChange = { v -> viewModel.update { it.copy(satHours = v) } },
                label = "Hours this week (target 6)",
                numeric = true
            )
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = form.satWeakest,
                onValueChange = { v -> viewModel.update { it.copy(satWeakest = v) } },
                label = "Weakest area right now"
            )
        }

        GlowCard(accent = Mint) {
            SectionHeader("Projects")
            Spacer(Modifier.height(12.dp))
            FlowTextField(
                value = form.exactHourNotes,
                onValueChange = { v -> viewModel.update { it.copy(exactHourNotes = v) } },
                label = "exact-hour: shipped / blocked"
            )
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = form.bewsoaClockNotes,
                onValueChange = { v -> viewModel.update { it.copy(bewsoaClockNotes = v) } },
                label = "bewsoa-ai-clock: notes"
            )
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = form.commitCount,
                onValueChange = { v -> viewModel.update { it.copy(commitCount = v) } },
                label = "Commits this week",
                numeric = true
            )
        }

        GlowCard(accent = Orange) {
            SectionHeader("Body")
            Spacer(Modifier.height(12.dp))
            LabeledSlider(
                label = "Gym sessions",
                value = form.gymSessions,
                max = 7,
                onChange = { v -> viewModel.update { it.copy(gymSessions = v) } }
            )
            Spacer(Modifier.height(8.dp))
            FlowTextField(
                value = form.sleepAverage,
                onValueChange = { v -> viewModel.update { it.copy(sleepAverage = v) } },
                label = "Sleep average (hours)",
                numeric = true
            )
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                label = "Energy",
                value = form.energy,
                min = 1,
                max = 10,
                onChange = { v -> viewModel.update { it.copy(energy = v) } }
            )
        }

        GlowCard {
            SectionHeader("Reflection")
            Spacer(Modifier.height(12.dp))
            FlowTextField(
                value = form.slowedMeDown,
                onValueChange = { v -> viewModel.update { it.copy(slowedMeDown = v) } },
                label = "One thing that slowed me down"
            )
            Spacer(Modifier.height(10.dp))
            FlowTextField(
                value = form.nextWeekTask,
                onValueChange = { v -> viewModel.update { it.copy(nextWeekTask = v) } },
                label = "Next week's single most important task"
            )
        }

        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Violet)
        ) {
            Text(
                if (justSaved) "Saved ✓" else "Save review",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }

        if (pastReviews.isNotEmpty()) {
            SectionHeader("Past reviews")
            pastReviews.forEach { review -> PastReviewCard(review) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    max: Int,
    onChange: (Int) -> Unit,
    min: Int = 0
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextBright,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$value / $max",
                style = MaterialTheme.typography.labelMedium,
                color = Cyan
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Violet,
                inactiveTrackColor = Outline
            )
        )
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

@Composable
private fun PastReviewCard(review: WeeklyReviewEntity) {
    GlowCard {
        Text(
            "Week of ${review.weekStart}",
            style = MaterialTheme.typography.titleSmall,
            color = TextBright
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "TYT ${review.tytScore.ifBlank { "—" }} · " +
                "SAT ${review.satHours.ifBlank { "—" }}h · " +
                "Gym ${review.gymSessions} · " +
                "Energy ${review.energy}/10",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
        if (review.nextWeekTask.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "→ ${review.nextWeekTask}",
                style = MaterialTheme.typography.bodySmall,
                color = Cyan
            )
        }
    }
}
