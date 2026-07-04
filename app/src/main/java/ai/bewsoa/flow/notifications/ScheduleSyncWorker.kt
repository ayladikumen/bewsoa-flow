package ai.bewsoa.flow.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Safety net: refreshes the task-end alarms every 6 hours so tomorrow's
 * reminders exist even on days the app is never opened.
 */
class ScheduleSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TaskAlarmScheduler.scheduleUpcoming(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "schedule_sync"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
