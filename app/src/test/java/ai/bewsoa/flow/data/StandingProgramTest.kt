package ai.bewsoa.flow.data

import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The standing-program-vs-one-time-edit line, which the weekly program builder
 * depends on: the builder edits the recurring week and nothing else, and a day
 * override must never be read into it or written out of it.
 */
class StandingProgramTest {

    // Both are process-wide objects; reset so tests can't bleed into each other.
    @After
    fun reset() {
        DayOverrides.load(null)
        CustomProgram.clear()
    }

    private val today: LocalDate = LocalDate.now()

    private fun overrideJson(date: LocalDate): String {
        val block = BlockCodec.toJson(
            TaskBlock(
                id = "x_wedding",
                title = "Wedding",
                track = Track.FREE,
                start = LocalTime.of(19, 0),
                end = LocalTime.of(23, 0),
                counted = false
            )
        )
        return "{\"$date\": ${JSONArray().put(block)}}"
    }

    @Test
    fun `with no custom program the standing week is the built-in one`() {
        val standing = WeeklyProgram.standingWeekMap()
        assertEquals(7, standing.size)
        assertTrue(standing.getValue(DayOfWeek.MONDAY).any { it.id == "wd_yks_morning" })
        assertTrue(standing.getValue(DayOfWeek.SATURDAY).any { it.id == "sa_tyt" })
        assertTrue(standing.getValue(DayOfWeek.SUNDAY).any { it.id == "su_review" })
        assertTrue(WeeklyProgramValidator.validate(WeeklyProgramDraft.of(standing)).isSavable)
    }

    @Test
    fun `the standing week ignores a one-time day override`() {
        DayOverrides.load(overrideJson(today))
        // Today's effective plan follows the override…
        assertEquals(listOf("x_wedding"), WeeklyProgram.blocksFor(today).map { it.id })
        // …but the recurring program the builder edits does not.
        val standing = WeeklyProgram.standingWeekMap()
        assertTrue(standing.getValue(today.dayOfWeek).none { it.id == "x_wedding" })
        assertTrue(standing.getValue(today.dayOfWeek).isNotEmpty())
    }

    @Test
    fun `using the current program as a template reads the custom program`() {
        val custom = ProgramTemplates.gymAndStudy.draft()
        CustomProgram.activate(custom.toJson()).getOrThrow()
        DayOverrides.load(overrideJson(today))

        val template = ProgramTemplates.fromStandingProgram()
        assertEquals(custom, template)
        assertTrue(template.blocksFor(today.dayOfWeek).none { it.id == "x_wedding" })
    }

    @Test
    fun `saving a draft leaves day overrides alone`() {
        DayOverrides.load(overrideJson(today))
        val before = DayOverrides.forDate(today)

        // What ProgramRepository.saveWeeklyProgram does to in-memory state.
        CustomProgram.activate(ProgramTemplates.projectFocus.draft().toJson()).getOrThrow()

        assertEquals(before, DayOverrides.forDate(today))
        assertTrue(DayOverrides.hasOverride(today))
        // The override still wins for its own date, exactly as before.
        assertEquals(listOf("x_wedding"), WeeklyProgram.blocksFor(today).map { it.id })
    }

    @Test
    fun `a saved draft becomes the program every screen reads`() {
        val draft = ProgramTemplates.balanced.draft()
        val versionBefore = CustomProgram.version.value

        CustomProgram.activate(draft.toJson()).getOrThrow()

        // Screens recombine on this bump; alarms and widgets read through
        // WeeklyProgram, so they follow with no extra wiring.
        assertNotEquals(versionBefore, CustomProgram.version.value)
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        DayOfWeek.entries.forEach { day ->
            val date = monday.plusDays((day.value - 1).toLong())
            assertEquals(
                draft.blocksFor(day).map { it.id },
                WeeklyProgram.plannedBlocksFor(date).map { it.id }
            )
        }
        // Reloading the persisted JSON gives back the same week.
        assertEquals(draft, WeeklyProgramDraft.of(WeeklyProgram.standingWeekMap()))
    }

    @Test
    fun `an invalid draft never replaces the active program`() {
        val good = ProgramTemplates.studyFocus.draft()
        CustomProgram.activate(good.toJson()).getOrThrow()

        val broken = "{\"days\": {\"MONDAY\": [{\"id\":\"a\"}]}}"
        assertTrue(CustomProgram.activate(broken).isFailure)
        assertFalse(WeeklyProgramValidator.validateJson(broken).isSavable)
        assertEquals(good.days, CustomProgram.current)
    }

    @Test
    fun `the built-in program is still there after a reset`() {
        CustomProgram.activate(ProgramTemplates.examPrep.draft().toJson()).getOrThrow()
        CustomProgram.clear()
        assertTrue(WeeklyProgram.blocksFor(today).any { it.id.startsWith("wd_") || it.id.startsWith("sa_") || it.id.startsWith("su_") })
        assertTrue(WeeklyProgram.standingWeekMap().getValue(DayOfWeek.SATURDAY).any { it.id == "sa_tyt" })
    }

    @Test
    fun `a week the assistant drafts passes the same save gate as the builder`() {
        // The shape AiAssistant's edit_week schema produces: {"days": {...}}.
        val assistantWeek = ProgramTemplates.balanced.draft().toJson()
        assertTrue(WeeklyProgramValidator.validateJson(assistantWeek).isSavable)
        assertTrue(CustomProgram.parse(assistantWeek).isSuccess)

        // And a day-scoped assistant edit stays a day edit: the same blocks
        // installed as an override never reach the standing program.
        DayOverrides.load(overrideJson(today))
        assertTrue(WeeklyProgram.standingWeekMap().values.flatten().none { it.id == "x_wedding" })
        assertTrue(AiAssistant.dayChanges(emptyList(), emptyList()).isNotEmpty())
    }

    @Test
    fun `the diff between the standing program and a draft reads as changes`() {
        val standing = WeeklyProgram.standingWeekMap()
        val edited = WeeklyProgramDraft.of(standing).let { draft ->
            val gym = draft.block(DayOfWeek.MONDAY, "wd_gym")!!
            draft.updateBlock(
                DayOfWeek.MONDAY,
                gym.copy(start = LocalTime.of(18, 0), end = LocalTime.of(19, 45))
            )
        }
        val lines = ProgramDiff.summarize(standing, edited.days)
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("17:00–18:45 → 18:00–19:45"))
        assertTrue(lines.single().contains("Mon"))
    }
}
