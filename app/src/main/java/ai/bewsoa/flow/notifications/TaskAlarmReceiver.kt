package ai.bewsoa.flow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.bewsoa.flow.data.ProgramRepository
import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.WeeklyProgram
import ai.bewsoa.flow.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires when a block's reminder alarm goes off. Skips the notification if the
 * block was already marked done — no nagging about finished work.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val date = LocalDate.parse(dateText)
                // A block just ended — the widgets' "current block" moved on.
                Widgets.refreshAll(context)
                val block = WeeklyProgram.blockById(date, taskId) ?: return@launch
                // Nothing to nag about if it's done — or if the user already
                // excused it for today.
                val state = ProgramRepository.get(context).getState(date, taskId)
                if (state == CompletionState.DONE || state == CompletionState.SKIPPED) return@launch
                val offset = SettingsRepository.get(context).reminderOffsetMinutes.first()
                NotificationHelper.showTaskEnd(context, block, date, offset)
            } finally {
                result.finish()
            }
        }
    }
}
