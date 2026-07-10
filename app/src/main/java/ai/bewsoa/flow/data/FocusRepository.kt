package ai.bewsoa.flow.data

import android.content.Context
import ai.bewsoa.flow.data.db.AppDatabase
import ai.bewsoa.flow.data.db.FocusSessionEntity
import ai.bewsoa.flow.notifications.FocusAlarmReceiver
import ai.bewsoa.flow.notifications.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The Deep Focus session in progress: one thing, one committed stretch of time.
 * Lives in DataStore (not memory) so a restart mid-session keeps the countdown.
 */
data class ActiveFocus(val label: String, val startedAt: Long, val plannedMinutes: Int) {
    val endsAt: Long get() = startedAt + plannedMinutes * 60_000L
}

/**
 * Deep Focus: commit to "what + how long", run the countdown, and only what the
 * user confirms as finished gets logged — dated by the clock at that moment, so
 * it counts toward that day's deep-work tally and the week's Focus total.
 */
class FocusRepository private constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository
) {

    val activeSession: Flow<ActiveFocus?> = combine(
        settings.focusLabel,
        settings.focusStartedAt,
        settings.focusDurationMinutes
    ) { label, startedAt, minutes ->
        if (label != null && startedAt > 0L && minutes > 0) {
            ActiveFocus(label, startedAt, minutes)
        } else {
            null
        }
    }

    fun observeForDate(date: LocalDate): Flow<List<FocusSessionEntity>> =
        db.focusDao().observeForDate(date.toString())

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FocusSessionEntity>> =
        db.focusDao().observeRange(from.toString(), to.toString())

    /**
     * Commit: write the session down, arm the "time's up" alarm, and pin a live
     * countdown to the notification shade.
     */
    suspend fun start(label: String, minutes: Int) {
        val clean = label.trim().ifEmpty { "Deep focus" }
        val safeMinutes = minutes.coerceIn(5, 4 * 60)
        val startedAt = System.currentTimeMillis()
        val endsAt = startedAt + safeMinutes * 60_000L
        settings.setFocusSession(clean, startedAt, safeMinutes)
        FocusAlarmReceiver.schedule(context, clean, endsAt)
        NotificationHelper.showFocusRunning(context, clean, endsAt)
    }

    /**
     * The user confirms the work happened. Credited with the full committed time,
     * or with [creditedMinutes] (elapsed) on an early finish. Dated "now" — a
     * session confirmed after midnight belongs to the day it was confirmed on.
     */
    suspend fun complete(creditedMinutes: Int? = null) {
        val active = activeSession.first() ?: return
        db.focusDao().insert(
            FocusSessionEntity(
                date = LocalDate.now().toString(),
                label = active.label,
                minutes = (creditedMinutes ?: active.plannedMinutes).coerceAtLeast(1),
                plannedMinutes = active.plannedMinutes,
                startedAt = active.startedAt,
                completedAt = System.currentTimeMillis()
            )
        )
        clear()
    }

    /** Nothing gets logged — no guilt either, the streak never depended on this. */
    suspend fun abandon() = clear()

    private suspend fun clear() {
        settings.clearFocusSession()
        FocusAlarmReceiver.cancel(context)
        NotificationHelper.cancelFocusRunning(context)
    }

    companion object {
        @Volatile
        private var instance: FocusRepository? = null

        fun get(context: Context): FocusRepository =
            instance ?: synchronized(this) {
                instance ?: FocusRepository(
                    context.applicationContext,
                    AppDatabase.getInstance(context),
                    SettingsRepository.get(context)
                ).also { instance = it }
            }
    }
}
