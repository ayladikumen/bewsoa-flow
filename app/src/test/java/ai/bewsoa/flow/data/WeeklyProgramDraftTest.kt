package ai.bewsoa.flow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The draft model the builder, the AI builder and Chat all share. The rules that
 * matter here are the id rules: an untouched block keeps its id byte-for-byte
 * (its completion history is keyed by it), and every copy gets a fresh one.
 */
class WeeklyProgramDraftTest {

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

    private fun draftWith(vararg blocks: TaskBlock) =
        WeeklyProgramDraft.of(mapOf(DayOfWeek.MONDAY to blocks.toList()))

    @Test
    fun `of fills every day and sorts each one`() {
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("late", start = 20 to 0, end = 21 to 0),
                    block("early", start = 9 to 0, end = 10 to 0)
                )
            )
        )
        assertEquals(7, draft.days.size)
        assertEquals(listOf("early", "late"), draft.blocksFor(DayOfWeek.MONDAY).map { it.id })
        assertTrue(draft.blocksFor(DayOfWeek.SUNDAY).isEmpty())
    }

    @Test
    fun `editing a block keeps its id so history survives`() {
        val draft = draftWith(block("wd_gym", "Gym"))
        val edited = draft.updateBlock(
            DayOfWeek.MONDAY,
            draft.block(DayOfWeek.MONDAY, "wd_gym")!!.copy(
                title = "Gym — long session",
                start = LocalTime.of(18, 0),
                end = LocalTime.of(19, 30)
            )
        )
        val after = edited.blocksFor(DayOfWeek.MONDAY).single()
        assertEquals("wd_gym", after.id)
        assertEquals("Gym — long session", after.title)
        assertEquals(LocalTime.of(18, 0), after.start)
    }

    @Test
    fun `updating an id that is not on the day changes nothing`() {
        val draft = draftWith(block("a"))
        assertEquals(draft, draft.updateBlock(DayOfWeek.MONDAY, block("ghost")))
    }

    @Test
    fun `adding a block with a colliding id mints a fresh one`() {
        val draft = draftWith(block("wd_gym", "Gym"))
        val added = draft.addBlock(DayOfWeek.MONDAY, block("wd_gym", "Second gym", start = 18 to 0, end = 19 to 0))
        val ids = added.blocksFor(DayOfWeek.MONDAY).map { it.id }
        assertEquals(2, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains("wd_gym"))
    }

    @Test
    fun `duplicate lands after the original with its own id`() {
        val draft = draftWith(block("wd_gym", "Gym", start = 17 to 0, end = 18 to 0))
        val copied = draft.duplicateBlock(DayOfWeek.MONDAY, "wd_gym")
        val blocks = copied.blocksFor(DayOfWeek.MONDAY)
        assertEquals(2, blocks.size)
        assertEquals("wd_gym", blocks[0].id)
        assertNotEquals("wd_gym", blocks[1].id)
        assertEquals("x_gym", blocks[1].id)
        // Placed in the gap after the source, same length.
        assertEquals(LocalTime.of(18, 0), blocks[1].start)
        assertEquals(LocalTime.of(19, 0), blocks[1].end)
        assertEquals(blocks[0].title, blocks[1].title)
    }

    @Test
    fun `a duplicate that would run past the day keeps the source times`() {
        val draft = draftWith(block("wd_free", "Free time", start = 22 to 0, end = 23 to 30))
        val blocks = draft.duplicateBlock(DayOfWeek.MONDAY, "wd_free")
            .blocksFor(DayOfWeek.MONDAY)
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.start == LocalTime.of(22, 0) && it.end == LocalTime.of(23, 30) })
    }

    @Test
    fun `repeat on other days copies with fresh ids and the same times`() {
        val draft = draftWith(block("wd_gym", "Gym", Track.GYM, 18 to 0, 19 to 30))
        val repeated = draft.copyBlockToDays(
            DayOfWeek.MONDAY,
            "wd_gym",
            setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        )
        val all = DayOfWeek.entries.flatMap { repeated.blocksFor(it) }
        assertEquals(4, all.size)
        assertEquals(4, all.map { it.id }.toSet().size)
        assertEquals("wd_gym", repeated.blocksFor(DayOfWeek.MONDAY).single().id)
        listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY).forEach { day ->
            val copy = repeated.blocksFor(day).single()
            assertNotEquals("wd_gym", copy.id)
            assertEquals(LocalTime.of(18, 0), copy.start)
            assertEquals(LocalTime.of(19, 30), copy.end)
            assertEquals(Track.GYM, copy.track)
        }
        assertTrue(repeated.blocksFor(DayOfWeek.WEDNESDAY).isEmpty())
    }

    @Test
    fun `copying a day replaces the target with fresh ids`() {
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("wd_yks", "YKS deep work", start = 9 to 0, end = 12 to 0),
                    block("wd_gym", "Gym", Track.GYM, 17 to 0, 18 to 0)
                ),
                DayOfWeek.TUESDAY to listOf(block("old", "Something else"))
            )
        )
        val copied = draft.copyDayTo(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        val tuesday = copied.blocksFor(DayOfWeek.TUESDAY)
        assertEquals(listOf("YKS deep work", "Gym"), tuesday.map { it.title })
        assertTrue(tuesday.none { it.id == "old" })
        // Fresh ids: nothing in the week is shared between the two days.
        assertTrue(copied.blocksFor(DayOfWeek.MONDAY).map { it.id }.none { it in tuesday.map { c -> c.id } })
        val allIds = DayOfWeek.entries.flatMap { copied.blocksFor(it) }.map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
        // The source day is untouched, ids included.
        assertEquals(listOf("wd_yks", "wd_gym"), copied.blocksFor(DayOfWeek.MONDAY).map { it.id })
    }

    @Test
    fun `moving a block to another day carries its id`() {
        val draft = draftWith(block("wd_gym", "Gym"))
        val moved = draft.moveBlockToDay(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, "wd_gym")
        assertTrue(moved.blocksFor(DayOfWeek.MONDAY).isEmpty())
        assertEquals("wd_gym", moved.blocksFor(DayOfWeek.FRIDAY).single().id)
    }

    @Test
    fun `a move into a day that already uses the id gets a fresh one`() {
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(block("wd_gym", "Gym", start = 17 to 0, end = 18 to 0)),
                DayOfWeek.FRIDAY to listOf(block("wd_gym", "Gym", start = 9 to 0, end = 10 to 0))
            )
        )
        val moved = draft.moveBlockToDay(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, "wd_gym")
        val friday = moved.blocksFor(DayOfWeek.FRIDAY)
        assertEquals(2, friday.size)
        assertEquals(2, friday.map { it.id }.toSet().size)
    }

    @Test
    fun `deleting and clearing only touch what was named`() {
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(block("a"), block("b", start = 11 to 0, end = 12 to 0)),
                DayOfWeek.TUESDAY to listOf(block("c"))
            )
        )
        val deleted = draft.deleteBlock(DayOfWeek.MONDAY, "a")
        assertEquals(listOf("b"), deleted.blocksFor(DayOfWeek.MONDAY).map { it.id })
        assertEquals(listOf("c"), deleted.blocksFor(DayOfWeek.TUESDAY).map { it.id })
        assertNull(deleted.block(DayOfWeek.MONDAY, "a"))

        val cleared = draft.clearDay(DayOfWeek.MONDAY)
        assertTrue(cleared.blocksFor(DayOfWeek.MONDAY).isEmpty())
        assertEquals(listOf("c"), cleared.blocksFor(DayOfWeek.TUESDAY).map { it.id })
    }

    @Test
    fun `reordering keeps every block's length and the day's span`() {
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("study", "Study", start = 9 to 0, end = 11 to 0),
                    block("gym", "Gym", Track.GYM, 11 to 30, 12 to 0)
                )
            )
        )
        val swapped = draft.moveBlock(DayOfWeek.MONDAY, "gym", -1)
        val blocks = swapped.blocksFor(DayOfWeek.MONDAY)
        assertEquals(listOf("gym", "study"), blocks.map { it.id })
        assertEquals(LocalTime.of(9, 0), blocks[0].start)
        assertEquals(30L, blocks[0].durationMinutes)
        // The gap is preserved, so the day still ends when the program said.
        assertEquals(LocalTime.of(10, 0), blocks[1].start)
        assertEquals(120L, blocks[1].durationMinutes)
        assertEquals(LocalTime.of(12, 0), blocks[1].end)
    }

    @Test
    fun `moving past either end is a no-op`() {
        val draft = WeeklyProgramDraft.of(
            mapOf(
                DayOfWeek.MONDAY to listOf(
                    block("a", start = 9 to 0, end = 10 to 0),
                    block("b", start = 10 to 0, end = 11 to 0)
                )
            )
        )
        assertEquals(draft, draft.moveBlock(DayOfWeek.MONDAY, "a", -1))
        assertEquals(draft, draft.moveBlock(DayOfWeek.MONDAY, "b", 1))
    }

    @Test
    fun `serialization round-trips through the program JSON`() {
        val draft = ProgramTemplates.balanced.draft()
        val json = draft.toJson()
        val reloaded = WeeklyProgramDraft.fromJson(json).getOrThrow()
        assertEquals(draft, reloaded)
        // The same JSON is what CustomProgram installs, so the saved program and
        // the draft are the same week.
        assertEquals(draft.days, CustomProgram.parse(json).getOrThrow())
    }

    @Test
    fun `serialization keeps notes, tracks and the counted flag`() {
        val draft = draftWith(
            block("wd_dinner", "Dinner", Track.MEAL, 19 to 0, 19 to 30, counted = false)
                .copy(note = "Eat, don't linger.")
        )
        val reloaded = WeeklyProgramDraft.fromJson(draft.toJson()).getOrThrow()
        val meal = reloaded.blocksFor(DayOfWeek.MONDAY).single()
        assertEquals(Track.MEAL, meal.track)
        assertFalse(meal.counted)
        assertEquals("Eat, don't linger.", meal.note)
    }

    @Test
    fun `the 23-59 convention survives a round trip`() {
        val draft = draftWith(
            block("wd_free", "Free time", Track.FREE, 23 to 0, 23 to 59, counted = false)
        )
        val reloaded = WeeklyProgramDraft.fromJson(draft.toJson()).getOrThrow()
        assertEquals(LocalTime.of(23, 59), reloaded.blocksFor(DayOfWeek.MONDAY).single().end)
    }

    @Test
    fun `minted ids are snake_case, prefixed and never collide`() {
        assertEquals("x_gym_long_session", WeeklyProgramDraft.mintId("Gym — long session", emptySet()))
        assertEquals("x_block", WeeklyProgramDraft.mintId("—", emptySet()))
        assertEquals(
            "x_gym_2",
            WeeklyProgramDraft.mintId("Gym", setOf("x_gym"))
        )
        assertEquals(
            "x_gym_3",
            WeeklyProgramDraft.mintId("Gym", setOf("x_gym", "x_gym_2"))
        )
        val minted = WeeklyProgramDraft.mintId("YKS — deep work", emptySet())
        assertTrue(minted.startsWith("x_"))
        assertTrue(minted.all { it in 'a'..'z' || it in '0'..'9' || it == '_' })
    }

    @Test
    fun `summary numbers describe the week`() {
        val draft = ProgramTemplates.studyFocus.draft()
        assertEquals(7, draft.configuredDays)
        assertTrue(draft.totalMinutes > draft.countedMinutes)
        assertTrue(draft.countBlocks(Track.YKS, Track.TYT, Track.SAT) > 0)
        assertEquals(0, WeeklyProgramDraft.EMPTY.blockCount)
        assertTrue(WeeklyProgramDraft.EMPTY.isEmpty)
    }
}
