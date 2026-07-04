package ai.bewsoa.flow.ui

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun formatTime(time: LocalTime): String =
    String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

fun formatHours(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

fun formatCountdown(duration: Duration): String {
    val total = duration.seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%dh %02dm %02ds", h, m, s)
    } else {
        String.format(Locale.US, "%dm %02ds", m, s)
    }
}

fun formatWeekRange(weekStart: LocalDate): String =
    "${weekStart.format(monthDay)} – ${weekStart.plusDays(6).format(monthDay)}"
