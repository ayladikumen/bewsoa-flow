package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class InsightsTest {

    // Two counted blocks every day: a short study block and a long project block.
    private val study = TaskBlock(
        "study", "Study", Track.YKS, LocalTime.of(9, 0), LocalTime.of(10, 0)
    )
    private val project = TaskBlock(
        "project", "Project", Track.PROJECT, LocalTime.of(18, 0), LocalTime.of(21, 0)
    )
    private val meal = TaskBlock(
        "meal", "Dinner", Track.MEAL, LocalTime.of(12, 0), LocalTime.of(12, 30),
        counted = false
    )

    private val blocksFor: (LocalDate) -> List<TaskBlock> = { listOf(study, project, meal) }

    /** today is a Monday so "this week" contains exactly today (excluded as partial). */
    private val today: LocalDate = LocalDate.of(2026, 7, 6)

    private fun row(date: LocalDate, id: String, done: Boolean) =
        TaskCompletionEntity(
            date = date.toString(),
            taskId = id,
            state = if (done) CompletionState.DONE.name else CompletionState.PENDING.name,
            completedAt = if (done) 1L else null
        )

    private fun skipped(date: LocalDate, id: String) =
        TaskCompletionEntity(
            date = date.toString(),
            taskId = id,
            state = CompletionState.SKIPPED.name,
            completedAt = null
        )

    @Test
    fun `no history means no insights`() {
        assertTrue(Insights.compute(today, emptyList(), blocksFor).isEmpty())
    }

    @Test
    fun `uncounted blocks are ignored`() {
        // Only meal rows — nothing counted ever recorded, but days still elapse
        // with counted blocks planned, so insights may exist; here we check that
        // the meal id never shows up in any wording.
        val rows = (1L..14L).map { row(today.minusDays(it), "meal", true) }
        val insights = Insights.compute(today, rows, blocksFor)
        assertTrue(insights.none { it.text.contains("Dinner") })
    }

    @Test
    fun `weak spot found for a block skipped on the same weekday`() {
        // 4 weeks of history: everything done except "project" on Thursdays.
        val rows = mutableListOf<TaskCompletionEntity>()
        var date = today.minusWeeks(4)
        while (date < today) {
            rows += row(date, "study", true)
            rows += row(date, "project", date.dayOfWeek != DayOfWeek.THURSDAY)
            date = date.plusDays(1)
        }
        val insights = Insights.compute(today, rows, blocksFor)
        val weak = insights.first { it.kind == Insight.Kind.WEAK_SPOT }
        assertTrue(weak.text.contains("Project"))
        assertTrue(weak.text.contains("Thursday"))
    }

    @Test
    fun `an excused skip is not evidence of a weak spot`() {
        // Same shape as the weak-spot case, except Thursday's project block was
        // explicitly skipped rather than missed. Excused means excused: the app
        // must not turn "I told you I couldn't" into "you always fail at this".
        val rows = mutableListOf<TaskCompletionEntity>()
        var date = today.minusWeeks(4)
        while (date < today) {
            rows += row(date, "study", true)
            if (date.dayOfWeek == DayOfWeek.THURSDAY) {
                rows += skipped(date, "project")
            } else {
                rows += row(date, "project", true)
            }
            date = date.plusDays(1)
        }
        val insights = Insights.compute(today, rows, blocksFor)
        assertTrue(
            "skipped blocks must not surface as weak spots, got: $insights",
            insights.none { it.kind == Insight.Kind.WEAK_SPOT }
        )
    }

    @Test
    fun `slipping trend detected when this week drops`() {
        val rows = mutableListOf<TaskCompletionEntity>()
        // Three perfect prior weeks…
        var date = today.minusWeeks(3)
        while (date < today) {
            rows += row(date, "study", true)
            rows += row(date, "project", true)
            date = date.plusDays(1)
        }
        // …but "this week" needs at least MIN_OCCURRENCES elapsed block-days,
        // so move today to Thursday with Mon–Wed all skipped.
        val thursday = today.plusDays(3)
        listOf(0L, 1L, 2L).forEach { shift ->
            val d = today.plusDays(shift)
            rows.removeAll { it.date == d.toString() }
            rows += row(d, "study", false)
            rows += row(d, "project", false)
        }
        val insights = Insights.compute(thursday, rows, blocksFor)
        val trend = insights.first { it.kind == Insight.Kind.TREND }
        assertTrue(trend.text.startsWith("Slipping"))
    }

    @Test
    fun `overload flagged when only long blocks get skipped`() {
        val rows = mutableListOf<TaskCompletionEntity>()
        var date = today.minusWeeks(2)
        while (date < today) {
            rows += row(date, "study", true)     // 60 min, always done
            rows += row(date, "project", false)  // 180 min, always skipped
            date = date.plusDays(1)
        }
        val insights = Insights.compute(today, rows, blocksFor)
        assertEquals(1, insights.count { it.kind == Insight.Kind.OVERLOAD })
    }

    @Test
    fun `day contrast reported when weekdays differ sharply`() {
        val rows = mutableListOf<TaskCompletionEntity>()
        var date = today.minusWeeks(4)
        while (date < today) {
            val good = date.dayOfWeek == DayOfWeek.MONDAY
            rows += row(date, "study", good)
            rows += row(date, "project", good)
            date = date.plusDays(1)
        }
        val insights = Insights.compute(today, rows, blocksFor)
        val contrast = insights.first { it.kind == Insight.Kind.DAY_CONTRAST }
        assertTrue(contrast.text.contains("Monday"))
    }
}
