package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The single validation layer. It must name what is wrong — the UI shows these
 * messages verbatim — and it must never quietly fix the user's week.
 */
class WeeklyProgramValidatorTest {

    private fun block(
        id: String,
        title: String = "Block $id",
        track: Track = Track.YKS,
        start: Pair<Int, Int> = 9 to 0,
        end: Pair<Int, Int> = 10 to 0,
        counted: Boolean = true
    ) = TaskBlock(
        id = id,
        title = title,
        track = track,
        start = LocalTime.of(start.first, start.second),
        end = LocalTime.of(end.first, end.second),
        counted = counted
    )

    private fun onMonday(vararg blocks: TaskBlock) =
        WeeklyProgramDraft.of(mapOf(DayOfWeek.MONDAY to blocks.toList()))

    private fun messages(draft: WeeklyProgramDraft): List<String> =
        WeeklyProgramValidator.validate(draft).errors.map { it.message }

    @Test
    fun `a template week is savable`() {
        ProgramTemplates.all
            .filter { it.id != "empty" }
            .forEach { template ->
                val result = WeeklyProgramValidator.validate(template.draft())
                assertTrue(
                    "${template.name}: ${result.errors.map { it.message }}",
                    result.isSavable
                )
            }
    }

    @Test
    fun `an empty week cannot be saved`() {
        val result = WeeklyProgramValidator.validate(WeeklyProgramDraft.EMPTY)
        assertFalse(result.isSavable)
        assertTrue(result.errors.any { it.message.contains("empty") })
    }

    @Test
    fun `an empty day is a warning, not a blocker`() {
        val result = WeeklyProgramValidator.validate(onMonday(block("a")))
        assertTrue(result.isSavable)
        assertEquals(6, result.warnings.count { it.message.contains("has no blocks") })
        assertTrue(result.warnings.any { it.day == DayOfWeek.SUNDAY })
    }

    @Test
    fun `overlapping blocks name both sides and the times`() {
        val result = WeeklyProgramValidator.validate(
            WeeklyProgramDraft.of(
                mapOf(
                    DayOfWeek.WEDNESDAY to listOf(
                        block("gym", "Gym", Track.GYM, 18 to 0, 19 to 30),
                        block("yks", "YKS review", Track.YKS, 18 to 45, 20 to 0)
                    )
                )
            )
        )
        assertFalse(result.isSavable)
        val overlap = result.errors.single { it.message.contains("overlapping") }
        assertEquals(DayOfWeek.WEDNESDAY, overlap.day)
        assertTrue(overlap.message.contains("Wednesday"))
        assertTrue(overlap.message.contains("Gym ends at 19:30"))
        assertTrue(overlap.message.contains("YKS review starts at 18:45"))
        assertEquals(1, result.errorsForDay(DayOfWeek.WEDNESDAY).size)
    }

    @Test
    fun `blocks that touch are not an overlap`() {
        val result = WeeklyProgramValidator.validate(
            onMonday(
                block("a", start = 9 to 0, end = 10 to 0),
                block("b", start = 10 to 0, end = 11 to 0)
            )
        )
        assertTrue(result.isSavable)
    }

    @Test
    fun `impossible ranges are rejected`() {
        assertTrue(
            messages(onMonday(block("back", "Backwards", start = 20 to 0, end = 19 to 0)))
                .any { it.contains("ends before it starts") }
        )
        assertTrue(
            messages(onMonday(block("zero", "Zero", start = 20 to 0, end = 20 to 0)))
                .any { it.contains("no length") }
        )
    }

    @Test
    fun `midnight as an end time points at the 23-59 convention`() {
        val errors = messages(onMonday(block("night", "Night owl", start = 22 to 0, end = 0 to 0)))
        assertTrue(errors.any { it.contains("23:59") })
    }

    @Test
    fun `a blank title is an error`() {
        val errors = messages(onMonday(block("a", title = "   ")))
        assertTrue(errors.any { it.contains("no title") })
    }

    @Test
    fun `invalid and duplicate ids are errors`() {
        assertTrue(messages(onMonday(block(""))).any { it.contains("invalid id") })
        assertTrue(messages(onMonday(block("has space"))).any { it.contains("invalid id") })

        val duplicated = WeeklyProgramDraft(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("same", "First", start = 9 to 0, end = 10 to 0),
                    block("same", "Second", start = 11 to 0, end = 12 to 0)
                )
            )
        )
        assertTrue(
            WeeklyProgramValidator.validate(duplicated).errors
                .any { it.message.contains("sharing the id") }
        )
    }

    @Test
    fun `the same id on two different days is legal`() {
        // The built-in program repeats wd_ ids Mon–Fri on purpose.
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(block("wd_gym", "Gym")),
                DayOfWeek.TUESDAY to listOf(block("wd_gym", "Gym"))
            )
        )
        assertTrue(WeeklyProgramValidator.validate(draft).isSavable)
    }

    @Test
    fun `a missing day is reported`() {
        val partial = WeeklyProgramDraft(mapOf(DayOfWeek.MONDAY to listOf(block("a"))))
        val result = WeeklyProgramValidator.validate(partial)
        assertFalse(result.isSavable)
        assertEquals(6, result.errors.count { it.message.contains("missing from the program") })
    }

    @Test
    fun `blocks out of chronological order are reported once`() {
        val unsorted = WeeklyProgramDraft(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("late", start = 20 to 0, end = 21 to 0),
                    block("early", start = 9 to 0, end = 10 to 0)
                )
            )
        )
        val issues = WeeklyProgramValidator.validate(unsorted).errors
            .filter { it.day == DayOfWeek.MONDAY }
        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("out of chronological order"))
    }

    @Test
    fun `counted flags that fight their track are warnings`() {
        val odd = WeeklyProgramValidator.validate(
            onMonday(
                block("meal", "Dinner", Track.MEAL, 19 to 0, 19 to 30, counted = true),
                block("study", "YKS", Track.YKS, 20 to 0, 21 to 0, counted = false)
            )
        )
        assertTrue(odd.isSavable)
        assertTrue(odd.warnings.any { it.message.contains("counts toward progress") })
        assertTrue(odd.warnings.any { it.message.contains("won't") })
    }

    @Test
    fun `an AI schedule is validated before it can be activated`() {
        val good = ProgramTemplates.studyFocus.draft().toJson()
        assertTrue(WeeklyProgramValidator.validateJson(good).isSavable)

        val overlapping = """
            {"days": {"MONDAY": [
              {"id":"a","title":"A","track":"YKS","start":"09:00","end":"12:00","note":"","counted":true},
              {"id":"b","title":"B","track":"YKS","start":"11:00","end":"13:00","note":"","counted":true}
            ]}}
        """.trimIndent()
        val overlapResult = WeeklyProgramValidator.validateJson(overlapping)
        assertFalse(overlapResult.isSavable)
        assertTrue(overlapResult.errors.any { it.message.contains("overlapping") })
    }

    @Test
    fun `unreadable json comes back as one error, not an exception`() {
        val result = WeeklyProgramValidator.validateJson("{not json")
        assertFalse(result.isSavable)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `an unknown track is rejected at the json boundary`() {
        val json = """
            {"days": {"MONDAY": [
              {"id":"a","title":"A","track":"CHESS","start":"09:00","end":"10:00","note":"","counted":true}
            ]}}
        """.trimIndent()
        assertFalse(WeeklyProgramValidator.validateJson(json).isSavable)
    }
}
