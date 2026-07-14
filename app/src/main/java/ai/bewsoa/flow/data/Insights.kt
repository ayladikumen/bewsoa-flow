package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.TaskCompletionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One finding from the completion history, worded for the Progress screen. */
data class Insight(val kind: Kind, val text: String) {
    enum class Kind { TREND, WEAK_SPOT, DAY_CONTRAST, OVERLOAD }
}

/**
 * Reads patterns out of completion history: adherence trend, repeated weak
 * spots, best/worst days, and whether long blocks are the ones being skipped.
 *
 * Pure Kotlin — the schedule comes in through [blocksFor] so tests can feed a
 * synthetic program, and only days between the first recorded completion and
 * yesterday count (today is still in progress).
 */
object Insights {

    private const val MIN_OCCURRENCES = 3
    private const val WEAK_RATE = 0.5f

    fun compute(
        today: LocalDate,
        rows: List<TaskCompletionEntity>,
        blocksFor: (LocalDate) -> List<TaskBlock>
    ): List<Insight> {
        val firstDate = rows.minOfOrNull { LocalDate.parse(it.date) } ?: return emptyList()
        val lastFull = today.minusDays(1)
        if (firstDate > lastFull) return emptyList()

        val doneByDate: Map<LocalDate, Set<String>> = rows
            .filter { it.done }
            .groupBy({ LocalDate.parse(it.date) }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }
        val skippedByDate: Map<LocalDate, Set<String>> = rows
            .filter { it.skipped }
            .groupBy({ LocalDate.parse(it.date) }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }

        // One record per counted block per elapsed day. Skipped blocks are
        // dropped outright rather than recorded as not-done: an excused block
        // is not evidence of a weak spot, and counting it as one would punish
        // the user for using the feature honestly.
        data class BlockDay(val date: LocalDate, val block: TaskBlock, val done: Boolean)
        val blockDays = generateSequence(firstDate) { it.plusDays(1) }
            .takeWhile { it <= lastFull }
            .flatMap { date ->
                val doneIds = doneByDate[date].orEmpty()
                val skippedIds = skippedByDate[date].orEmpty()
                blocksFor(date).filter { it.counted }
                    .filterNot { skippedIds.contains(it.id) }
                    .map { BlockDay(date, it, doneIds.contains(it.id)) }
            }
            .toList()
        if (blockDays.isEmpty()) return emptyList()

        val insights = mutableListOf<Insight>()

        // Trend: this week so far vs. the weeks before it.
        val thisWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val (recent, earlier) = blockDays.partition { it.date >= thisWeekStart }
        if (recent.size >= MIN_OCCURRENCES && earlier.size >= MIN_OCCURRENCES) {
            val now = recent.ratio { it.done }
            val before = earlier.ratio { it.done }
            when {
                now - before >= 0.10f -> insights += Insight(
                    Insight.Kind.TREND,
                    "Trending up: ${now.pct()} kept this week vs ${before.pct()} before."
                )
                before - now >= 0.10f -> insights += Insight(
                    Insight.Kind.TREND,
                    "Slipping: ${now.pct()} kept this week vs ${before.pct()} before. " +
                        "Catch it now — never miss twice."
                )
            }
        }

        // Weak spot: the (weekday, block) pair you skip the most.
        val weakest = blockDays
            .groupBy { it.date.dayOfWeek to it.block.id }
            .filterValues { it.size >= MIN_OCCURRENCES }
            .minByOrNull { (_, hits) -> hits.ratio { it.done } }
        if (weakest != null) {
            val (key, hits) = weakest
            val rate = hits.ratio { it.done }
            if (rate <= WEAK_RATE) {
                val (day, _) = key
                val skipped = hits.count { !it.done }
                insights += Insight(
                    Insight.Kind.WEAK_SPOT,
                    "“${hits.first().block.title}” on ${day.plural()} is your weak spot — " +
                        "skipped $skipped of the last ${hits.size}."
                )
            }
        }

        // Day contrast: strongest vs weakest weekday.
        val byDay = blockDays.groupBy { it.date.dayOfWeek }
            .filterValues { it.size >= MIN_OCCURRENCES }
            .mapValues { (_, hits) -> hits.ratio { it.done } }
        val best = byDay.maxByOrNull { it.value }
        val worst = byDay.minByOrNull { it.value }
        if (best != null && worst != null && best.value - worst.value >= 0.25f) {
            insights += Insight(
                Insight.Kind.DAY_CONTRAST,
                "${best.key.label()}s are your strongest day (${best.value.pct()}); " +
                    "${worst.key.label()}s are the leak (${worst.value.pct()})."
            )
        }

        // Overload: are the skipped blocks the long ones?
        val skipped = blockDays.filter { !it.done }
        val done = blockDays.filter { it.done }
        if (skipped.size >= MIN_OCCURRENCES && done.size >= MIN_OCCURRENCES) {
            val skippedAvg = skipped.map { it.block.durationMinutes }.average()
            val doneAvg = done.map { it.block.durationMinutes }.average()
            if (doneAvg > 0 && skippedAvg >= doneAvg * 1.5) {
                insights += Insight(
                    Insight.Kind.OVERLOAD,
                    "The blocks you skip average ${(skippedAvg / 60).format1()}h — " +
                        "much longer than the ones you finish. Consider splitting them."
                )
            }
        }

        return insights
    }

    private inline fun <T> List<T>.ratio(predicate: (T) -> Boolean): Float =
        if (isEmpty()) 0f else count(predicate).toFloat() / size

    private fun Float.pct(): String = "${(this * 100).roundToInt()}%"

    private fun Double.format1(): String = String.format(Locale.US, "%.1f", this)

    private fun DayOfWeek.label(): String = getDisplayName(TextStyle.FULL, Locale.US)

    private fun DayOfWeek.plural(): String = label() + "s"
}
