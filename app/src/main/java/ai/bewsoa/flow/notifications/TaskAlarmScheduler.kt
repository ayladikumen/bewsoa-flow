package ai.bewsoa.flow.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ai.bewsoa.flow.data.SettingsRepository
import ai.bewsoa.flow.data.WeeklyProgram
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the end-of-block reminders. For every counted block today and
 * tomorrow, an alarm fires [SettingsRepository.reminderOffsetMinutes] minutes
 * after the block's end time.
 *
 * Scheduling is idempotent: request codes are derived from (date, block index),
 * so re-running simply overwrites the same alarms. It runs on app start, on
 * boot, and from a periodic worker so alarms survive reboots and quiet days.
 */
object TaskAlarmScheduler {

    suspend fun scheduleUpcoming(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val offset = SettingsRepository.get(context).reminderOffsetMinutes.first().toLong()
        val now = LocalDateTime.now()

        for (dayShift in 0L..1L) {
            val date = LocalDate.now().plusDays(dayShift)
            WeeklyProgram.blocksFor(date).forEachIndexed { index, block ->
                if (!block.counted) return@forEachIndexed
                val fireAt = LocalDateTime.of(date, block.end).plusMinutes(offset)
                if (fireAt.isBefore(now)) return@forEachIndexed

                val intent = Intent(context, TaskAlarmReceiver::class.java)
                    .putExtra(EXTRA_TASK_ID, block.id)
                    .putExtra(EXTRA_DATE, date.toString())
                val pending = PendingIntent.getBroadcast(
                    context,
                    requestCode(date, index),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    // Exact alarm permission not granted: fall back to a 10-minute window.
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60_000L, pending
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAt, pending
                    )
                }
            }
        }
    }

    private fun requestCode(date: LocalDate, index: Int): Int =
        ((date.toEpochDay() % 20_000L).toInt() * 100) + index
}
