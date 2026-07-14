package ai.bewsoa.flow.data

import android.content.Context
import ai.bewsoa.flow.data.db.AppDatabase
import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.NotificationLogEntity
import ai.bewsoa.flow.data.db.StreakFreezeEntity
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * How many of this week's skips are spent. Derived from the completion rows, so
 * the Monday reset is free and the count can never drift from the skips it's
 * counting.
 */
data class SkipBudget(val used: Int, val cap: Int) {
    val remaining: Int get() = (cap - used).coerceAtLeast(0)
    val exhausted: Boolean get() = remaining == 0
}

class ProgramRepository private constructor(private val db: AppDatabase) {

    fun blocksFor(date: LocalDate): List<TaskBlock> = WeeklyProgram.blocksFor(date)

    fun observeDay(date: LocalDate): Flow<List<TaskCompletionEntity>> =
        db.completionDao().observeForDate(date.toString())

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<TaskCompletionEntity>> =
        db.completionDao().observeRange(from.toString(), to.toString())

    fun observeWeek(weekStart: LocalDate): Flow<List<TaskCompletionEntity>> =
        db.completionDao().observeRange(weekStart.toString(), weekStart.plusDays(6).toString())

    suspend fun getWeek(weekStart: LocalDate): List<TaskCompletionEntity> =
        db.completionDao().getRange(weekStart.toString(), weekStart.plusDays(6).toString())

    suspend fun getRange(from: LocalDate, to: LocalDate): List<TaskCompletionEntity> =
        db.completionDao().getRange(from.toString(), to.toString())

    suspend fun setState(
        date: LocalDate,
        taskId: String,
        state: CompletionState,
        reason: String? = null
    ) {
        db.completionDao().upsert(
            TaskCompletionEntity(
                date = date.toString(),
                taskId = taskId,
                state = state.name,
                completedAt = if (state == CompletionState.DONE) System.currentTimeMillis() else null,
                skipReason = reason
            )
        )
    }

    /** Kept so notification actions and widgets don't need to know about states. */
    suspend fun setDone(date: LocalDate, taskId: String, done: Boolean) =
        setState(date, taskId, if (done) CompletionState.DONE else CompletionState.PENDING)

    /**
     * Excuses a block for one day. Returns false when the week's budget is
     * spent — the cap is what stops "skip everything" from being a way to fake
     * a perfect streak.
     */
    suspend fun skip(date: LocalDate, taskId: String, reason: String? = null): Boolean {
        val existing = db.completionDao().get(date.toString(), taskId)
        // Re-skipping an already-skipped block shouldn't cost a second charge.
        if (existing?.skipped != true && getSkipBudget(date).exhausted) return false
        setState(date, taskId, CompletionState.SKIPPED, reason)
        return true
    }

    /** Puts a skipped block back on the hook, refunding the budget charge. */
    suspend fun unskip(date: LocalDate, taskId: String) =
        setState(date, taskId, CompletionState.PENDING)

    suspend fun isDone(date: LocalDate, taskId: String): Boolean =
        getState(date, taskId) == CompletionState.DONE

    /** No row means PENDING — an untouched block is not a special case. */
    suspend fun getState(date: LocalDate, taskId: String): CompletionState =
        db.completionDao().get(date.toString(), taskId)
            ?.let { runCatching { CompletionState.valueOf(it.state) }.getOrNull() }
            ?: CompletionState.PENDING

    suspend fun getDoneIds(date: LocalDate): Set<String> =
        db.completionDao().getRange(date.toString(), date.toString())
            .filter { it.done }
            .map { it.taskId }
            .toSet()

    suspend fun getSkippedIds(date: LocalDate): Set<String> =
        db.completionDao().getRange(date.toString(), date.toString())
            .filter { it.skipped }
            .map { it.taskId }
            .toSet()

    // Skip budget ------------------------------------------------------------

    fun observeSkipBudget(today: LocalDate): Flow<SkipBudget> {
        val week = weekOf(today)
        return db.completionDao()
            .observeSkipCount(week.first.toString(), week.second.toString())
            .map { SkipBudget(used = it, cap = SKIP_CAP_PER_WEEK) }
    }

    suspend fun getSkipBudget(today: LocalDate): SkipBudget {
        val week = weekOf(today)
        val used = db.completionDao().getSkipCount(week.first.toString(), week.second.toString())
        return SkipBudget(used = used, cap = SKIP_CAP_PER_WEEK)
    }

    /** ISO week: Monday to Sunday, matching buildWeekStats. */
    private fun weekOf(date: LocalDate): Pair<LocalDate, LocalDate> {
        val monday = date.minusDays(date.dayOfWeek.value - 1L)
        return monday to monday.plusDays(6)
    }

    // Streak -----------------------------------------------------------------

    /**
     * A pure read — safe to call from widgets and workers. It reports the freeze
     * it *would* spend but never spends it; see [settleStreak].
     */
    suspend fun computeStreak(today: LocalDate): StreakInfo {
        val from = today.minusDays(Streak.LOOKBACK_DAYS)
        val rows = db.completionDao().getRange(from.toString(), today.toString())
        val used = db.streakFreezeDao().getAll()
            .map { LocalDate.parse(it.usedForDate) }
            .toSet()
        return Streak.compute(today, rows, used, WeeklyProgram::blocksFor)
    }

    /**
     * The one place a freeze is actually spent. Call from a foreground moment
     * (MainActivity resume, ScheduleSyncWorker) and nowhere else — the whole
     * point of splitting this out is that reads stay reads.
     */
    suspend fun settleStreak(today: LocalDate): StreakInfo {
        val info = computeStreak(today)
        val consume = info.freezeToConsume ?: return info
        db.streakFreezeDao().consume(
            StreakFreezeEntity(
                usedForDate = consume.toString(),
                usedAt = System.currentTimeMillis()
            )
        )
        return computeStreak(today)
    }

    // Weekly reviews -------------------------------------------------------

    suspend fun getReview(weekStart: LocalDate): WeeklyReviewEntity? =
        db.reviewDao().get(weekStart.toString())

    suspend fun saveReview(review: WeeklyReviewEntity) =
        db.reviewDao().upsert(review)

    fun observeReviews(): Flow<List<WeeklyReviewEntity>> =
        db.reviewDao().observeAll()

    // Notification history --------------------------------------------------

    fun observeNotificationLog(): Flow<List<NotificationLogEntity>> =
        db.notificationLogDao().observeRecent(50)

    suspend fun logNotification(kind: String, title: String, message: String) {
        val dao = db.notificationLogDao()
        dao.insert(
            NotificationLogEntity(
                timestamp = System.currentTimeMillis(),
                kind = kind,
                title = title,
                message = message
            )
        )
        // Two weeks of history is plenty for the Alerts screen.
        dao.pruneOlderThan(System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000)
    }

    companion object {
        /** Kept for call sites that report the rule; the logic lives in [Streak]. */
        const val KEEP_THRESHOLD = Streak.KEEP_THRESHOLD

        /** Three excuses a week. Enough for real life, too few to fake a streak. */
        const val SKIP_CAP_PER_WEEK = 3

        @Volatile
        private var instance: ProgramRepository? = null

        fun get(context: Context): ProgramRepository =
            instance ?: synchronized(this) {
                instance ?: ProgramRepository(AppDatabase.getInstance(context)).also { instance = it }
            }
    }
}
