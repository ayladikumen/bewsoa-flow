package ai.bewsoa.flow.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a Deep Focus session's committed time runs out, so the "time's up —
 * did you finish?" nudge reaches the user even with the screen off. The session
 * itself is only logged when the user confirms it in the app.
 */
class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_FOCUS_LABEL) ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The alert replaces the live countdown.
                NotificationHelper.cancelFocusRunning(context)
                NotificationHelper.showFocusEnd(context, label)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val EXTRA_FOCUS_LABEL = "extra_focus_label"

        // One session at a time, so one fixed request code: re-arming overwrites.
        private const val REQUEST_CODE = 9101

        private fun pending(context: Context, label: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, FocusAlarmReceiver::class.java)
                    .putExtra(EXTRA_FOCUS_LABEL, label),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun schedule(context: Context, label: String, triggerAt: Long) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = pending(context, label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // No exact-alarm permission: a short window still lands close enough.
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(pending(context, ""))
        }
    }
}
