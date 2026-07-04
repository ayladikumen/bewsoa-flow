package ai.bewsoa.flow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val updatedStamp = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = TextBright
        )

        Text(
            "Notification settings live in the Alerts tab.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )

        GlowCard(accent = Cyan) {
            SectionHeader("Program · update from markdown with AI")
            Spacer(Modifier.height(6.dp))
            Text(
                if (ui.customActive) {
                    "AI-built program active" +
                        if (ui.updatedAt > 0L) {
                            " · " + Instant.ofEpochMilli(ui.updatedAt)
                                .atZone(ZoneId.systemDefault()).format(updatedStamp)
                        } else ""
                } else {
                    "Built-in program active"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ui.customActive) Mint else TextBright
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Edit your weekly program below, then let Claude rebuild the app's schedule " +
                    "from it. Blocks keep their history when they keep their place.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.mdText,
                onValueChange = viewModel::setMdText,
                label = { Text("weekly_program.md") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(14.dp),
                colors = programFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ui.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text("Anthropic API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                colors = programFieldColors()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Stays on this phone. Get one at console.anthropic.com.",
                style = MaterialTheme.typography.labelSmall,
                color = TextDim
            )
            if (ui.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ui.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = Coral
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::rebuildWithAi,
                enabled = !ui.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                if (ui.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Rebuilding…",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                } else {
                    Text(
                        if (ui.justUpdated) "Program updated ✓" else "Rebuild program with AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
            if (ui.customActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = viewModel::resetToBuiltIn) {
                        Text("Reset to built-in program", color = TextDim)
                    }
                }
            }
        }

        GlowCard {
            SectionHeader("About")
            Spacer(Modifier.height(8.dp))
            Text(
                "Bewsoa Flow v1.1",
                style = MaterialTheme.typography.bodyMedium,
                color = TextBright
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "The daily engine behind Bewsoa AI. Built around one weekly program: " +
                    "YKS mornings, TYT Saturdays, SAT evenings, Exact Hour nights, gym in between — " +
                    "and one rule: never miss twice.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun programFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Violet,
    unfocusedBorderColor = Outline,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = TextDim,
    cursorColor = Cyan,
    focusedTextColor = TextBright,
    unfocusedTextColor = TextBright
)
