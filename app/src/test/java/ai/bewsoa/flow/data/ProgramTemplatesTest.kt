package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Templates are data that produces the same week shape as everything else — no
 * second schedule model, and nothing a user couldn't have built by hand.
 */
class ProgramTemplatesTest {

    @Test
    fun `every template builds seven days`() {
        ProgramTemplates.all.forEach { template ->
            assertEquals(template.name, 7, template.draft().days.size)
        }
    }

    @Test
    fun `templates are savable weeks`() {
        ProgramTemplates.all
            .filter { it.id != ProgramTemplates.emptyWeek.id }
            .forEach { template ->
                val draft = template.draft()
                val result = WeeklyProgramValidator.validate(draft)
                assertTrue(
                    "${template.name}: ${result.errors.map { it.message }}",
                    result.isSavable
                )
                assertEquals("${template.name} covers every day", 7, draft.configuredDays)
                // Ids are unique within each day; sharing one across weekdays is
                // the convention for a block that repeats.
                DayOfWeek.entries.forEach { day ->
                    val ids = draft.blocksFor(day).map { it.id }
                    assertEquals("${template.name} · $day", ids.size, ids.toSet().size)
                }
            }
    }

    @Test
    fun `the empty week really is empty`() {
        val draft = ProgramTemplates.emptyWeek.draft()
        assertTrue(draft.isEmpty)
        assertEquals(0, draft.blockCount)
        assertFalseSavable(draft)
    }

    @Test
    fun `templates keep the weekday convention`() {
        val draft = ProgramTemplates.studyFocus.draft()
        val monday = draft.blocksFor(DayOfWeek.MONDAY)
        val friday = draft.blocksFor(DayOfWeek.FRIDAY)
        assertEquals(monday.map { it.id }, friday.map { it.id })
        assertTrue(monday.all { it.id.startsWith("wd_") })
        assertTrue(draft.blocksFor(DayOfWeek.SATURDAY).all { it.id.startsWith("sa_") })
        assertTrue(draft.blocksFor(DayOfWeek.SUNDAY).all { it.id.startsWith("su_") })
    }

    @Test
    fun `meals and free time do not count toward progress`() {
        ProgramTemplates.all.forEach { template ->
            template.draft().days.values.flatten()
                .filter { it.track == Track.MEAL || it.track == Track.FREE }
                .forEach { assertTrue("${template.name}: ${it.title}", !it.counted) }
        }
    }

    @Test
    fun `a template survives being saved and reloaded`() {
        ProgramTemplates.all
            .filter { it.id != ProgramTemplates.emptyWeek.id }
            .forEach { template ->
                val draft = template.draft()
                assertEquals(draft, WeeklyProgramDraft.fromJson(draft.toJson()).getOrThrow())
            }
    }

    @Test
    fun `templates are addressable by id`() {
        assertNotNull(ProgramTemplates.byId("balanced"))
        assertEquals("Gym + Study", ProgramTemplates.byId("gym_study")?.name)
        assertNull(ProgramTemplates.byId("nope"))
        assertEquals(6, ProgramTemplates.all.size)
    }

    private fun assertFalseSavable(draft: WeeklyProgramDraft) {
        assertTrue(WeeklyProgramValidator.validate(draft).errors.isNotEmpty())
    }
}
