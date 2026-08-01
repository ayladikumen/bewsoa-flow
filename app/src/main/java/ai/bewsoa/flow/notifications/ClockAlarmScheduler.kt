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
 * Wakes the app at every block boundary so the Exact Hour clock changes over
 * with the schedule instead of at the next time someone opens the app.
 *
 * Both edges, deduplicated: a block's start is when its countdown begins, and
 * its end matters too — otherwise the last block of the day, and any gap in
 * the middle of one, would leave a finished countdown sitting on the wall.
 *
 * Piggybacks on [TaskAlarmScheduler.scheduleUpcoming], which every "the
 * schedule changed" path already calls, so there is nothing new to remember to
 * invoke.
 */
object ClockAlarmScheduler {

    suspend fun scheduleUpcoming(context: Context) {
        // Integration off: no alarms, no wakeups, no battery.
        if (!SettingsRepository.get(context).clockEnabled.first()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = LocalDateTime.now()

        for (dayShift in 0L..1L) {
            val date = LocalDate.now().plusDays(dayShift)
            val boundaries = WeeklyProgram.blocksFor(date)
                .flatMap { listOf(it.start, it.end) }
                .distinct()
                .sorted()

            boundaries.forEachIndexed { index, time ->
                val fireAt = LocalDateTime.of(date, time)
                if (fireAt.isBefore(now)) return@forEachIndexed

                // No extras: the receiver recomputes from current truth, which
                // makes a leftover alarm from a since-reordered day harmless.
                val intent = Intent(context, ClockAlarmReceiver::class.java)
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

    /**
     * A band of its own, above [TaskAlarmScheduler]'s. Strictly belt-and-braces
     * — PendingIntent matching includes the target component, and this receiver
     * is a different class — but it means a future refactor that merges the two
     * receivers can't silently start overwriting reminders.
     */
    private const val BASE = 2_000_000

    private fun requestCode(date: LocalDate, index: Int): Int =
        BASE + ((date.toEpochDay() % 20_000L).toInt() * 100) + index
}
