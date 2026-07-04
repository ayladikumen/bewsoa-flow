package ai.bewsoa.flow.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.notifications.MotivationWorker
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)

    val reminderOffset: StateFlow<Int> = settings.reminderOffsetMinutes
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.DEFAULT_OFFSET
        )

    val motivationEnabled: StateFlow<Boolean> = settings.motivationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val motivationIntensity: StateFlow<String> = settings.motivationIntensity
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.INTENSITY_NORMAL
        )

    fun setReminderOffset(minutes: Int) {
        viewModelScope.launch {
            settings.setReminderOffset(minutes)
            // Existing alarms move to the new offset immediately.
            TaskAlarmScheduler.scheduleUpcoming(getApplication())
        }
    }

    fun setMotivationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setMotivationEnabled(enabled)
            if (enabled) MotivationWorker.kickoff(getApplication())
        }
    }

    fun setMotivationIntensity(value: String) {
        viewModelScope.launch { settings.setMotivationIntensity(value) }
    }
}
