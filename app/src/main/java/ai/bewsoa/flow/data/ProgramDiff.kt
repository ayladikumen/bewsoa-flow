package ai.bewsoa.flow.data

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Short human summary of what changed between two weekly schedules,
 * shown after an AI rebuild. Identical changes are grouped across days
 * ("Mon–Fri") so the list stays a few lines long.
 */
object ProgramDiff {

    fun summarize(
        old: Map<DayOfWeek, List<TaskBlock>>,
        new: Map<DayOfWeek, List<TaskBlock>>
    ): List<String> {
        val changes = LinkedHashMap<String, MutableSet<DayOfWeek>>()
        fun add(text: String, day: DayOfWeek) {
            changes.getOrPut(text) { linkedSetOf() }.add(day)
        }

        DayOfWeek.entries.forEach { day ->
            val before = old[day].orEmpty().associateBy { it.id }
            val after = new[day].orEmpty().associateBy { it.id }
            (after.keys - before.keys).forEach { id ->
                val b = after.getValue(id)
                add("+ ${b.title} ${b.start}–${b.end}", day)
            }
            (before.keys - after.keys).forEach { id ->
                val b = before.getValue(id)
                add("− ${b.title}", day)
            }
            (before.keys intersect after.keys).forEach { id ->
                val b = before.getValue(id)
                val a = after.getValue(id)
                if (b.start != a.start || b.end != a.end) {
                    add("${a.title}: ${b.start}–${b.end} → ${a.start}–${a.end}", day)
                } else if (b.title != a.title) {
                    add("“${b.title}” → “${a.title}”", day)
                }
            }
        }

        if (changes.isEmpty()) return listOf("Same schedule — nothing changed.")
        val lines = changes.map { (text, days) -> "$text · ${dayLabel(days)}" }
        return if (lines.size <= 8) lines else lines.take(8) + "…and ${lines.size - 8} more"
    }

    private val WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet()

    private fun dayLabel(days: Set<DayOfWeek>): String = when {
        days.size == 7 -> "every day"
        days == WEEKDAYS -> "Mon–Fri"
        else -> days.sorted().joinToString(", ") {
            it.getDisplayName(TextStyle.SHORT, Locale.US)
        }
    }
}
