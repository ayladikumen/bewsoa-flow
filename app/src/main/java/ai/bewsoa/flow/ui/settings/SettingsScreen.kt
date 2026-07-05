package ai.bewsoa.flow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.SettingsRepository
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
import ai.bewsoa.flow.ui.theme.VioletDeep
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
            SectionHeader("Program · change it with AI")
            Spacer(Modifier.height(6.dp))
            Text(
                ui.programName,
                style = MaterialTheme.typography.bodyLarge,
                color = TextBright
            )
            Spacer(Modifier.height(2.dp))
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
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.customActive) Mint else TextDim
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.changeText,
                onValueChange = viewModel::setChangeText,
                label = { Text("What do you want to change?") },
                placeholder = {
                    Text(
                        "e.g. move gym to 18:00, add a run on Sunday morning",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(14.dp),
                colors = programFieldColors()
            )
            Spacer(Modifier.height(10.dp))
            val gemini = ui.provider == SettingsRepository.PROVIDER_GEMINI
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderChip("Claude", SettingsRepository.PROVIDER_CLAUDE, ui.provider, viewModel::setProvider)
                ProviderChip("Gemini", SettingsRepository.PROVIDER_GEMINI, ui.provider, viewModel::setProvider)
            }
            Spacer(Modifier.height(10.dp))
            ApiKeySection(
                gemini = gemini,
                apiKey = ui.apiKey,
                onKeyChange = viewModel::setApiKey
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
                        if (ui.justUpdated) "Program updated ✓" else "Update program with AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
            if (ui.diff != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "WHAT CHANGED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan
                )
                Spacer(Modifier.height(6.dp))
                ui.diff!!.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBright,
                        modifier = Modifier.padding(vertical = 2.dp)
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

/**
 * API-key credential row, styled like modern developer consoles: once a key is
 * saved it collapses to a masked "•••• last4 · Active" row with a Change action;
 * editing shows a field with a show/hide eye.
 */
@Composable
private fun ApiKeySection(
    gemini: Boolean,
    apiKey: String,
    onKeyChange: (String) -> Unit
) {
    var editing by remember(gemini) { mutableStateOf(false) }
    var showKey by remember(gemini) { mutableStateOf(false) }
    val label = if (gemini) "Gemini API key" else "Anthropic API key"

    if (apiKey.isNotBlank() && !editing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Key,
                contentDescription = null,
                tint = Mint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim)
                Text(
                    "••••••••" + apiKey.trim().takeLast(4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextBright
                )
            }
            Box(
                Modifier
                    .size(8.dp)
                    .background(Mint, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text("Active", style = MaterialTheme.typography.labelSmall, color = Mint)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { editing = true }) {
                Text("Change", color = Cyan, style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onKeyChange,
            label = { Text(label) },
            placeholder = {
                Text(
                    if (gemini) "AIza…" else "sk-ant-…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showKey) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showKey) "Hide key" else "Show key",
                        tint = TextDim
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = programFieldColors()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (gemini) {
                    "Stays on this phone. Free at aistudio.google.com."
                } else {
                    "Stays on this phone. Get one at console.anthropic.com."
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
                modifier = Modifier.weight(1f)
            )
            if (apiKey.isNotBlank()) {
                TextButton(onClick = { editing = false; showKey = false }) {
                    Text("Done", color = Cyan, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VioletDeep,
            selectedLabelColor = TextBright,
            labelColor = TextDim
        )
    )
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
