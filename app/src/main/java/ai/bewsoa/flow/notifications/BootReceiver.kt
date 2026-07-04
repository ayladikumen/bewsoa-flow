package ai.bewsoa.flow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms don't survive a reboot; this puts them back and restarts the
 * motivation chain after boot or an app update.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TaskAlarmScheduler.scheduleUpcoming(context)
                MotivationWorker.kickoff(context)
                ScheduleSyncWorker.enqueue(context)
            } finally {
                result.finish()
            }
        }
    }
}
