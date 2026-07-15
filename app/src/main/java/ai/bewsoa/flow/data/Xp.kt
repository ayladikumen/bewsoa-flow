package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.TaskCompletionEntity
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Where you stand on the level curve.
 *
 * @param index 1-based level number.
 * @param xpIntoLevel XP earned since this level started.
 * @param xpForNext XP between this level and the next.
 */
data class LevelInfo(
    val index: Int,
    val title: String,
    val xpIntoLevel: Int,
    val xpForNext: Int
) {
    val progress: Float
        get() = if (xpForNext <= 0) 1f else (xpIntoLevel.toFloat() / xpForNext).coerceIn(0f, 1f)
}

/**
 * The weekly chest as the UI sees it. Progress is *kept days*, and it is
 * deliberately blind to streak freezes and to all-skipped days: the chest
 * rewards work that happened, not absences that were forgiven.
 */
data class ChestState(
    val weekStart: LocalDate,
    val keptDays: Int,
    val opened: Boolean,
    val expired: Boolean
) {
    val unlocked: Boolean get() = keptDays >= Xp.CHEST_UNLOCK_DAYS
    val claimable: Boolean get() = unlocked && !opened && !expired
    val reward: Int get() = Xp.chestReward(keptDays)
}

/**
 * Every XP formula in one pure object — zero Android imports, so the economy
 * (the part most worth getting right) is fully unit-testable.
 *
 * Design intent: the routine is the spine. Blocks pay the most, tasks pay
 * less, focus sessions pay a trickle, and the one-off bonuses (daily goal,
 * chest, milestones) are where the celebration moments come from.
 */
object Xp {

    /** One-off bonus for crossing the daily goal. */
    const val DAY_GOAL_XP = 25

    /** No single block can mint more than this — a 6-hour AI-generated
     *  monolith should not be worth a level. */
    const val BLOCK_XP_CAP = 120

    /** Kept days needed before the weekly chest unlocks. */
    const val CHEST_UNLOCK_DAYS = 5

    /** Days after the week ends before an unopened chest expires. */
    const val CHEST_CLAIM_DAYS = 14L

    /** Streak lengths that pay a once-ever milestone bonus. */
    val STREAK_MILESTONES = listOf(7, 30, 100, 365)

    // Earning ------------------------------------------------------------------

    /**
     * 10 base + 3 per quarter hour, weighted by how central the track is to
     * the mission. Meals and free time pay nothing — rest is its own reward.
     */
    fun blockXp(block: TaskBlock): Int {
        if (!block.counted) return 0
        val base = 10
        val duration = (block.durationMinutes / 15).toInt() * 3
        val mul = when (block.track) {
            Track.YKS, Track.TYT, Track.SAT -> 1.25f
            Track.PROJECT -> 1.15f
            Track.REVIEW -> 1.10f
            Track.GYM -> 1.00f
            Track.MEAL, Track.FREE -> 0f
        }
        return ((base + duration) * mul).roundToInt().coerceIn(0, BLOCK_XP_CAP)
    }

    /** Deliberately below block XP, so ad-hoc tasks never outbid the routine. */
    fun taskXp(estimatedMinutes: Int): Int =
        (6 + estimatedMinutes / 15 * 2).coerceIn(6, 40)

    /** 4 XP per completed 10 focused minutes, capped per session. Only ever
     *  awarded on a confirmed finish — abandoning a session pays nothing. */
    fun focusXp(minutes: Int): Int {
        if (minutes < 5) return 0
        return ((minutes / 10) * 4).coerceAtMost(60)
    }

    /**
     * 80% of a perfect day, rounded to a friendly number. Mirrors the streak's
     * 60% keep threshold in spirit but sits stricter — the goal is the stretch
     * target, the streak is the floor.
     */
    fun dailyGoal(blocks: List<TaskBlock>): Int {
        val perfect = blocks.sumOf { blockXp(it) }
        val rounded = (perfect * 0.8f / 10).roundToInt() * 10
        return rounded.coerceIn(40, 300)
    }

    // Levels ---------------------------------------------------------------------

    /**
     * Quadratic curve: fast early levels (L5 in under a week at a normal
     * pace), then a steady climb that never stalls. L2=100, L5=1000, L10=4500.
     */
    fun totalXpAtLevel(level: Int): Int = 50 * (level - 1) * level

    fun levelFor(totalXp: Int): LevelInfo {
        val xp = totalXp.coerceAtLeast(0)
        var level = 1
        while (totalXpAtLevel(level + 1) <= xp) level++
        val floor = totalXpAtLevel(level)
        return LevelInfo(
            index = level,
            title = titleFor(level),
            xpIntoLevel = xp - floor,
            xpForNext = totalXpAtLevel(level + 1) - floor
        )
    }

    private val TITLES = listOf(
        "Spark", "Kindling", "Ember", "Flame", "Torch", "Beacon",
        "Blaze", "Furnace", "Forge", "Crucible", "Inferno", "Wildfire"
    )

    /** Past the named twelve, the fire just keeps counting: Wildfire II, III… */
    fun titleFor(level: Int): String = when {
        level <= TITLES.size -> TITLES[(level - 1).coerceAtLeast(0)]
        else -> "Wildfire ${roman(level - TITLES.size + 1)}"
    }

    private fun roman(n: Int): String {
        val pairs = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C",
            90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX",
            5 to "V", 4 to "IV", 1 to "I"
        )
        var rest = n
        val sb = StringBuilder()
        pairs.forEach { (value, glyph) ->
            while (rest >= value) {
                sb.append(glyph)
                rest -= value
            }
        }
        return sb.toString()
    }

    // Weekly chest --------------------------------------------------------------

    /** 150 at the 5-day unlock, +25 for each kept day beyond it. */
    fun chestReward(keptDays: Int): Int =
        if (keptDays < CHEST_UNLOCK_DAYS) 0 else 150 + 25 * (keptDays - CHEST_UNLOCK_DAYS)

    /** Last day an unopened chest can still be claimed. */
    fun chestExpiry(weekStart: LocalDate): LocalDate =
        weekStart.plusDays(6 + CHEST_CLAIM_DAYS)

    /**
     * Kept days for chest progress. Same 60% rule as the streak, with two
     * deliberate differences: no freeze input at all (a frozen day is still an
     * unworked day), and a day whose effective plan is empty — nothing counted,
     * or everything excused — is *not* kept, because the chest pays for work.
     */
    fun chestKeptDays(
        weekStart: LocalDate,
        today: LocalDate,
        rows: List<TaskCompletionEntity>,
        blocksFor: (LocalDate) -> List<TaskBlock>
    ): Int {
        val doneByDate = rows.filter { it.done }
            .groupBy({ it.date }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }
        val skippedByDate = rows.filter { it.skipped }
            .groupBy({ it.date }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }

        return (0L..6L).count { shift ->
            val date = weekStart.plusDays(shift)
            if (date.isAfter(today)) return@count false
            val skipped = skippedByDate[date.toString()].orEmpty()
            val effective = blocksFor(date)
                .filter { it.counted }
                .filterNot { skipped.contains(it.id) }
            if (effective.isEmpty()) return@count false
            val doneIds = doneByDate[date.toString()].orEmpty()
            val done = effective.count { doneIds.contains(it.id) }
            done.toFloat() / effective.size >= Streak.KEEP_THRESHOLD
        }
    }

    // Milestones ------------------------------------------------------------------

    /** Paid once ever per milestone — a rebuilt streak doesn't re-mint them. */
    fun milestoneXp(days: Int): Int = when (days) {
        7 -> 50
        30 -> 100
        100 -> 250
        365 -> 500
        else -> 0
    }
}
