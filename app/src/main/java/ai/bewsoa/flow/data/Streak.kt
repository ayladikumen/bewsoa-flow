package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.TaskCompletionEntity
import java.time.LocalDate

/**
 * "Never miss twice" state.
 *
 * @param freezeToConsume the day a held freeze should be spent on, or null.
 *        [Streak.compute] only *decides* this; it never writes. Persisting it is
 *        the job of the single caller that owns settling (see
 *        ProgramRepository.settleStreak), because computeStreak is also called
 *        from widgets and workers — three uncoordinated writers on a read path
 *        is a data race, not a feature.
 */
data class StreakInfo(
    val current: Int,
    val yesterdayKept: Boolean,
    val todayKept: Boolean,
    val freezesAvailable: Int = 0,
    val freezeToConsume: LocalDate? = null
)

/**
 * The streak rules, as pure functions. Kept free of Room and Android so the
 * freeze logic — the part most likely to be wrong — is actually testable.
 */
object Streak {

    /** A day is kept when at least this share of its *effective* blocks are done. */
    const val KEEP_THRESHOLD = 0.6f

    const val LOOKBACK_DAYS = 90L

    /** One freeze per this many kept days. */
    const val FREEZE_EARN_EVERY = 7

    /** You can hold at most this many. Freezes are insurance, not a bank. */
    const val FREEZE_CAP = 2

    /** A freeze only rescues a streak that was actually worth something. */
    const val FREEZE_MIN_STREAK = 3

    /**
     * @param freezesUsed days already rescued by a freeze; they count as kept.
     * @param blocksFor injected so tests can supply a synthetic program.
     */
    fun compute(
        today: LocalDate,
        rows: List<TaskCompletionEntity>,
        freezesUsed: Set<LocalDate>,
        blocksFor: (LocalDate) -> List<TaskBlock>
    ): StreakInfo {
        val from = today.minusDays(LOOKBACK_DAYS)
        val doneByDate = rows.filter { it.done }
            .groupBy({ it.date }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }
        val skippedByDate = rows.filter { it.skipped }
            .groupBy({ it.date }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }

        fun kept(date: LocalDate): Boolean {
            if (date in freezesUsed) return true
            val counted = blocksFor(date).filter { it.counted }
            val skipped = skippedByDate[date.toString()].orEmpty()
            // The excused-skip contract: a skipped block leaves the denominator
            // entirely. It can't be a miss, and it can't inflate the ratio either.
            val effective = counted.filterNot { skipped.contains(it.id) }
            if (effective.isEmpty()) return true
            val doneIds = doneByDate[date.toString()].orEmpty()
            val done = effective.count { doneIds.contains(it.id) }
            return done.toFloat() / effective.size >= KEEP_THRESHOLD
        }

        fun walkBack(startingAt: LocalDate): Int {
            var streak = 0
            var cursor = startingAt
            while (cursor >= from && kept(cursor)) {
                streak++
                cursor = cursor.minusDays(1)
            }
            return streak
        }

        val yesterday = today.minusDays(1)
        val yesterdayKept = kept(yesterday)
        val todayKept = kept(today)

        var streak = walkBack(yesterday)
        var freezeToConsume: LocalDate? = null

        // Count kept days only from the first day the user actually recorded
        // something. Empty days count as kept, so measuring from `from` would
        // treat the 90 days before install as a earned-freeze windfall.
        val firstRecorded = rows.minOfOrNull { LocalDate.parse(it.date) }
        val totalKept = if (firstRecorded == null) {
            0
        } else {
            countKeptDays(from = maxOf(firstRecorded, from), to = yesterday, kept = ::kept)
        }
        val available = freezesAvailable(totalKept, freezesUsed.size)

        if (!yesterdayKept && available > 0) {
            // Only rescue a streak that had built up something worth saving.
            val before = walkBack(yesterday.minusDays(1))
            if (before >= FREEZE_MIN_STREAK) {
                freezeToConsume = yesterday
                streak = before + 1
            }
        }
        if (todayKept) streak++

        return StreakInfo(
            current = streak,
            yesterdayKept = yesterdayKept || freezeToConsume != null,
            todayKept = todayKept,
            freezesAvailable = available,
            freezeToConsume = freezeToConsume
        )
    }

    /**
     * Earned minus spent, capped. Derived rather than stored: a counter that
     * disagrees with the days behind it is worse than no counter.
     */
    fun freezesAvailable(totalKeptDays: Int, freezesUsed: Int): Int =
        (totalKeptDays / FREEZE_EARN_EVERY - freezesUsed).coerceIn(0, FREEZE_CAP)

    private inline fun countKeptDays(
        from: LocalDate,
        to: LocalDate,
        kept: (LocalDate) -> Boolean
    ): Int {
        var count = 0
        var cursor = from
        while (cursor <= to) {
            if (kept(cursor)) count++
            cursor = cursor.plusDays(1)
        }
        return count
    }
}
