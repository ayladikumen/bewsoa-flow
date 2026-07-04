package ai.bewsoa.flow.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.TaskBlock
import ai.bewsoa.flow.data.db.NotificationLogEntity
import ai.bewsoa.flow.notifications.MotivationWorker
import ai.bewsoa.flow.notifications.TaskAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** One end-of-block reminder scheduled for today. */
data class UpcomingReminder(
    val block: TaskBlock,
    val fireAt: LocalTime,
    val done: Boolean
)

class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository.get(app)
    private val repo = ProgramRepository.get(app)
    private val today: LocalDate = LocalDate.now()

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

    /** Today's end-of-block reminders and whether their block is already done. */
    val todayReminders: StateFlow<List<UpcomingReminder>> =
        combine(settings.reminderOffsetMinutes, repo.observeDay(today)) { offset, rows ->
            val doneIds = rows.filter { it.done }.map { it.taskId }.toSet()
            repo.blocksFor(today)
                .filter { it.counted }
                .map { block ->
                    UpcomingReminder(
                        block = block,
                        fireAt = block.end.plusMinutes(offset.toLong()),
                        done = block.id in doneIds
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything the app has actually sent, newest first. */
    val history: StateFlow<List<NotificationLogEntity>> = repo.observeNotificationLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
