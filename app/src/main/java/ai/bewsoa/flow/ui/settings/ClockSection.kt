package ai.bewsoa.flow.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.exacthour.DiscoveredClock
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Mint
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet

/**
 * Setting up the LED clock: where it is, whether it's on, and what it mirrors.
 * Driving it by hand lives on its own screen — this is just the wiring.
 */
@Composable
fun ClockCard(
    onOpenClock: () -> Unit,
    viewModel: ClockSetupViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    GlowCard(accent = Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionHeader("Exact Hour clock")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        !ui.configured -> "Not set up"
                        !ui.enabled -> "Set up, switched off"
                        ui.name.isNotBlank() -> "${ui.name} · ${ui.host}"
                        else -> ui.host
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ui.enabled && ui.configured) Mint else TextDim
                )
            }
            Switch(
                checked = ui.enabled,
                onCheckedChange = viewModel::setEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Violet)
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Puts whatever you're counting down on the wall — the block's title " +
                "slides across when it starts, and scrolls back with a ✓ when you " +
                "tick it off.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDim
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = ui.host,
                onValueChange = viewModel::setHost,
                label = { Text("Address") },
                placeholder = {
                    Text(
                        "192.168.1.50",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(14.dp),
                colors = programFieldColors()
            )
            OutlinedTextField(
                value = ui.port,
                onValueChange = viewModel::setPort,
                label = { Text("Port") },
                modifier = Modifier.width(96.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(14.dp),
                colors = programFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(Modifier.height(10.dp))
        if (ui.scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Cyan
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (ui.scanTotal > 0) {
                        "Knocking on doors… ${ui.scanProbed}/${ui.scanTotal}"
                    } else {
                        "Looking for the clock…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::cancelScan) {
                    Text("Cancel", color = TextDim)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::findClock,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Find my clock", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !ui.testing && ui.configured,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (ui.testing) "Testing…" else "Test",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        ui.found.takeIf { it.size > 1 }?.forEach { clock ->
            FoundClockRow(clock = clock, onSelect = { viewModel.select(clock) })
        }

        ui.status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Mint)
        }
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Coral)
        }

        Spacer(Modifier.height(12.dp))
        MirrorToggle(
            label = "Mirror Deep Focus sessions",
            checked = ui.mirrorFocus,
            enabled = ui.enabled,
            onCheckedChange = viewModel::setMirrorFocus
        )
        MirrorToggle(
            label = "Mirror schedule blocks",
            checked = ui.mirrorBlocks,
            enabled = ui.enabled,
            onCheckedChange = viewModel::setMirrorBlocks
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenClock)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Remote control",
                style = MaterialTheme.typography.titleSmall,
                color = TextBright
            )
            Text("→", style = MaterialTheme.typography.titleMedium, color = Cyan)
        }
    }
}

@Composable
private fun FoundClockRow(clock: DiscoveredClock, onSelect: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(clock.name, style = MaterialTheme.typography.bodyMedium, color = TextBright)
            Text(
                "${clock.host}:${clock.port} · ${clock.display}",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
        Text("Use", style = MaterialTheme.typography.labelLarge, color = Cyan)
    }
}

@Composable
private fun MirrorToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) TextBright else TextDim
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
        )
    }
}
