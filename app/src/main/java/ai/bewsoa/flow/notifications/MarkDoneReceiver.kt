package ai.bewsoa.flow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import ai.bewsoa.flow.data.ProgramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles the "Mark as done" action button on task-end notifications. */
class MarkDoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ProgramRepository.get(context).setDone(LocalDate.parse(dateText), taskId, true)
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                result.finish()
            }
        }
    }
}
