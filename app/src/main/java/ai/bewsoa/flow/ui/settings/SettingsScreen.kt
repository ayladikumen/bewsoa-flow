package ai.bewsoa.flow.ui.settings

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.ui.AppViewModelProvider
import ai.bewsoa.flow.ui.components.GlowCard
import ai.bewsoa.flow.ui.components.SectionHeader
import ai.bewsoa.flow.ui.theme.Coral
import ai.bewsoa.flow.ui.theme.Cyan
import ai.bewsoa.flow.ui.theme.Outline
import ai.bewsoa.flow.ui.theme.TextBright
import ai.bewsoa.flow.ui.theme.TextDim
import ai.bewsoa.flow.ui.theme.Violet
import ai.bewsoa.flow.ui.theme.VioletDeep
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val offset by viewModel.reminderOffset.collectAsStateWithLifecycle()
    val motivationEnabled by viewModel.motivationEnabled.collectAsStateWithLifecycle()
    val intensity by viewModel.motivationIntensity.collectAsStateWithLifecycle()

    // Permission states re-checked every time the user comes back from system settings.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsEnabled = remember(refreshKey) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactAlarmsAllowed = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)
                ?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

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

        if (!notificationsEnabled || !exactAlarmsAllowed) {
            GlowCard(accent = Coral) {
                SectionHeader("Needs attention")
                Spacer(Modifier.height(8.dp))
                if (!notificationsEnabled) {
                    PermissionRow(
                        text = "Notifications are off — reminders can't reach you.",
                        action = "Enable"
                    ) { openNotificationSettings(context) }
                }
                if (!exactAlarmsAllowed) {
                    PermissionRow(
                        text = "Exact alarms not allowed — reminders may drift by a few minutes.",
                        action = "Allow"
                    ) { openExactAlarmSettings(context) }
                }
            }
        }

        GlowCard {
            SectionHeader("Task-end reminder")
            Spacer(Modifier.height(8.dp))
            var sliderValue by remember(offset) { mutableFloatStateOf(offset.toFloat()) }
            Text(
                "${sliderValue.roundToInt()} minutes after a block ends",
                style = MaterialTheme.typography.bodyMedium,
                color = TextBright
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    viewModel.setReminderOffset(sliderValue.roundToInt())
                },
                valueRange = 0f..60f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Violet,
                    inactiveTrackColor = Outline
                )
            )
            Text(
                "The nudge that asks whether the block got done. 20 minutes is the default breathing room.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }

        GlowCard {
            SectionHeader("Motivation boosts")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Random notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextBright
                    )
                    Text(
                        "Tied to your goals — Bewsoa AI, Exact Hour, YKS, the university abroad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = motivationEnabled,
                    onCheckedChange = viewModel::setMotivationEnabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Violet,
                        checkedThumbColor = Color.White
                    )
                )
            }
            if (motivationEnabled) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntensityChip("Chill", "~2/day", SettingsRepository.INTENSITY_CHILL, intensity, viewModel::setMotivationIntensity)
                    IntensityChip("Normal", "~4/day", SettingsRepository.INTENSITY_NORMAL, intensity, viewModel::setMotivationIntensity)
                    IntensityChip("Beast", "~7/day", SettingsRepository.INTENSITY_BEAST, intensity, viewModel::setMotivationIntensity)
                }
            }
        }

        GlowCard {
            SectionHeader("About")
            Spacer(Modifier.height(8.dp))
            Text(
                "Bewsoa Flow v1.0",
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
private fun PermissionRow(text: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = TextBright,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClick) {
            Text(action, color = Cyan)
        }
    }
}

@Composable
private fun IntensityChip(
    label: String,
    detail: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = TextDim)
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VioletDeep,
            selectedLabelColor = TextBright,
            labelColor = TextDim
        )
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent)
    }
}
