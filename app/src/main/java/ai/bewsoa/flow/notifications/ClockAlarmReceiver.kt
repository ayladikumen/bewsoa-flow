package ai.bewsoa.flow.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ai.bewsoa.flow.data.exacthour.ClockMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A block boundary just passed. Carries no payload on purpose — it asks the
 * app what should be showing *now* rather than trusting what was true when the
 * alarm was set, so a day that has since been reordered still lands right.
 */
class ClockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClockMirror.syncQuietly(context)
            } finally {
                result.finish()
            }
        }
    }
}
