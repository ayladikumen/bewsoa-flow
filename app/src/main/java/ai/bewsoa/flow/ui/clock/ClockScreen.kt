package ai.bewsoa.flow.ui.clock

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.exacthour.AutomationTemplate
import ai.bewsoa.flow.data.exacthour.ClockState
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.NumberStepper
import ai.bewsoa.flow.ui.components.PrimaryButton
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.settings.programFieldColors
import ai.bewsoa.flow.ui.theme.Amber
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim

/**
 * Driving the Exact Hour clock by hand: the whole device API, one screen.
 *
 * Two of its rules come straight from the hardware. The big readout is
 * whatever the device says it is, verbatim — that's how "BITTI" and the
 * hour-long H:MM:SS form arrive for free. And the ± / set controls grey out
 * while the timer runs, because the device silently drops those commands
 * exactly as the physical buttons do.
 */
@Composable
fun ClockScreen(
    viewModel: ClockRemoteViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Only once we actually know there's no endpoint — never on the
        // not-yet-loaded state, which would flash at everyone.
        if (ui.configured == false) {
            GlowCard {
                Text(
                    "No clock set up yet — add its address in Profile first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim
                )
            }
            return@Column
        }

        DisplayCard(ui = ui, viewModel = viewModel)
        TimerCard(ui = ui, viewModel = viewModel)
        TextCard(viewModel = viewModel)
        AutomationsCard(ui = ui, viewModel = viewModel)
    }
}

/**
 * Three buttons across a phone leaves each one narrower than Material's default
 * padding assumes, and "Resume" breaks mid-word into "Resu / me". Tight padding
 * plus a single hard line is what keeps every label whole.
 */
private val TightButton = PaddingValues(horizontal = 4.dp, vertical = 8.dp)

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun DisplayCard(ui: ClockRemoteUiState, viewModel: ClockRemoteViewModel) {
    GlowCard(accent = Cyan) {
        Text(
            // Verbatim. Recomputing from minutes/seconds would lose both
            // "BITTI" and the H:MM:SS form past an hour.
            text = ui.status?.display ?: "—",
            style = MaterialTheme.typography.displayMedium,
            color = TextBright,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (ui.state) {
                ClockState.RUNNING -> "Running"
                ClockState.PAUSED -> "Paused"
                ClockState.FINISHED -> "Finished"
                ClockState.IDLE -> "Ready"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (ui.state) {
                ClockState.RUNNING -> Mint
                ClockState.PAUSED -> Amber
                ClockState.FINISHED -> Cyan
                ClockState.IDLE -> TextDim
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (ui.holdMinutes > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Auto-sync paused for ${ui.holdMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                TextButton(onClick = viewModel::releaseHold) {
                    Text("Resume now", color = Cyan)
                }
            }
        }

        ui.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Coral)
        }
    }
}

@Composable
private fun TimerCard(ui: ClockRemoteUiState, viewModel: ClockRemoteViewModel) {
    GlowCard {
        SectionHeader("Timer")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::start,
                enabled = ui.state != ClockState.RUNNING && !ui.busy,
                contentPadding = TightButton,
                modifier = Modifier.weight(1f)
            ) { ButtonLabel("Start") }
            OutlinedButton(
                onClick = viewModel::pause,
                enabled = ui.state == ClockState.RUNNING && !ui.busy,
                contentPadding = TightButton,
                modifier = Modifier.weight(1f)
            ) { ButtonLabel("Pause") }
            OutlinedButton(
                onClick = viewModel::resume,
                enabled = ui.state == ClockState.PAUSED && !ui.busy,
                contentPadding = TightButton,
                modifier = Modifier.weight(1f)
            ) { ButtonLabel("Resume") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::toggle,
                enabled = !ui.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Toggle") }
            OutlinedButton(
                onClick = viewModel::reset,
                enabled = !ui.busy,
                modifier = Modifier.weight(1f)
            ) { Text("Reset") }
        }

        Spacer(Modifier.height(14.dp))
        if (!ui.timerEditable) {
            Text(
                "Locked while the timer runs — pause first.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(-10, -5, -1, 1, 5, 10).forEach { delta ->
                OutlinedButton(
                    onClick = { viewModel.adjust(delta) },
                    enabled = ui.timerEditable && !ui.busy,
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (delta > 0) "+$delta" else "$delta",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            // The API's {delta} carries no unit; the physical buttons are
            // minutes, so these are too.
            "minutes",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )

        Spacer(Modifier.height(14.dp))
        SetTimeRow(ui = ui, viewModel = viewModel)
    }
}

@Composable
private fun SetTimeRow(ui: ClockRemoteUiState, viewModel: ClockRemoteViewModel) {
    var minutes by remember { mutableStateOf("25") }
    var seconds by remember { mutableStateOf("0") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Min") },
            enabled = ui.timerEditable,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(14.dp),
            colors = programFieldColors(),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = seconds,
            onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(2) },
            label = { Text("Sec") },
            enabled = ui.timerEditable,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(14.dp),
            colors = programFieldColors(),
            modifier = Modifier.weight(1f)
        )
        PrimaryButton(
            text = "Set",
            onClick = {
                viewModel.setTime(
                    minutes.toIntOrNull()?.coerceIn(0, ui.maxMinutes) ?: 0,
                    seconds.toIntOrNull()?.coerceIn(0, 59) ?: 0
                )
            },
            enabled = ui.timerEditable && !ui.busy
        )
    }
}

@Composable
private fun TextCard(viewModel: ClockRemoteViewModel) {
    var message by remember { mutableStateOf("") }
    var scroll by remember { mutableStateOf(true) }
    GlowCard {
        SectionHeader("Show text")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(120) },
            placeholder = {
                Text(
                    "STRETCH",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(14.dp),
            colors = programFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Rides on top of the countdown — the digits come back when it ends.",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { scroll = !scroll },
                contentPadding = TightButton,
                modifier = Modifier.weight(1f)
            ) { ButtonLabel(if (scroll) "Scrolling" else "Static") }
            OutlinedButton(
                onClick = { viewModel.showText(message, scroll) },
                enabled = message.isNotBlank(),
                contentPadding = TightButton,
                modifier = Modifier.weight(1f)
            ) { ButtonLabel("Show") }
            OutlinedButton(
                onClick = {
                    message = ""
                    viewModel.clearText()
                },
                contentPadding = TightButton,
                modifier = Modifier.weight(1f)
            ) { ButtonLabel("Clear") }
        }
    }
}

@Composable
private fun AutomationsCard(ui: ClockRemoteUiState, viewModel: ClockRemoteViewModel) {
    GlowCard {
        SectionHeader("Automations")
        Spacer(Modifier.height(8.dp))

        if (!ui.automationsAvailable) {
            Text(
                "Automations are switched off on the clock.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
            return@GlowCard
        }

        ui.status?.automation?.takeIf { it.running }?.let { running ->
            Text(
                "${running.name ?: "Running"} · step ${running.step} of ${running.steps}" +
                    (running.label?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = Mint
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::stopAutomation,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Stop") }
            Spacer(Modifier.height(12.dp))
        }

        // A rejected preset comes back as HTTP 200 with the reason in here —
        // it's the only place the user can find out why nothing happened.
        ui.automationError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Coral)
            Spacer(Modifier.height(10.dp))
        }

        if (ui.templates.isEmpty()) {
            Text(
                "No presets on the clock yet.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }

        ui.templates.forEach { template ->
            TemplateRow(
                template = template,
                values = viewModel.paramsFor(template),
                enabled = !ui.automationRunning && !ui.busy,
                onParam = { name, value -> viewModel.setParam(template.id, name, value) },
                onRun = { viewModel.runAutomation(template) }
            )
        }
    }
}

@Composable
private fun TemplateRow(
    template: AutomationTemplate,
    values: Map<String, Int>,
    enabled: Boolean,
    onParam: (String, Int) -> Unit,
    onRun: () -> Unit
) {
    Spacer(Modifier.height(10.dp))
    Text(template.name, style = MaterialTheme.typography.titleSmall, color = TextBright)
    if (template.description.isNotBlank()) {
        Text(
            template.description,
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )
    }
    template.params.forEach { param ->
        val value = values[param.name] ?: param.default
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(param.label, style = MaterialTheme.typography.bodySmall, color = TextDim)
            NumberStepper(
                value = value.toString(),
                // Clamping on every tap keeps the UI inside the preset's own
                // limits, so the device never has to reject anything.
                onDecrease = { onParam(param.name, param.clamp(value - 1)) },
                onIncrease = { onParam(param.name, param.clamp(value + 1)) }
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(
        onClick = onRun,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Run ${template.name}") }
}
