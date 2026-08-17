package ai.bewsoa.flow.data

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * The week being edited, before anything is saved.
 *
 * There is no second schedule model: a draft is just [TaskBlock]s per weekday,
 * the same shape [CustomProgram] serves and [ProgramDiff] compares. Chat, the
 * AI builder and the manual builder all produce one of these, and the active
 * program stays untouched until [ProgramRepository.saveWeeklyProgram] is called.
 *
 * Every operation returns a new draft with all seven days present and each day
 * sorted by start time, so preview, validation and diff always see the shape the
 * saved program will have.
 *
 * Ids are load-bearing — completions in the database are keyed by (date, id).
 * Editing a block in place keeps its id, so its history follows it; every copy
 * mints a fresh id, so no copy inherits another block's history and no two
 * blocks in the draft can collide.
 */
data class WeeklyProgramDraft(val days: Map<DayOfWeek, List<TaskBlock>>) {

    fun blocksFor(day: DayOfWeek): List<TaskBlock> = days[day].orEmpty()

    fun block(day: DayOfWeek, id: String): TaskBlock? =
        blocksFor(day).firstOrNull { it.id == id }

    val isEmpty: Boolean get() = days.values.all { it.isEmpty() }

    val blockCount: Int get() = days.values.sumOf { it.size }

    /** Days that have at least one block — the "7 days configured" number. */
    val configuredDays: Int get() = days.values.count { it.isNotEmpty() }

    val totalMinutes: Long get() = days.values.sumOf { day -> day.sumOf { it.durationMinutes } }

    val countedMinutes: Long
        get() = days.values.sumOf { day -> day.filter { it.counted }.sumOf { it.durationMinutes } }

    fun countBlocks(vararg tracks: Track): Int {
        val wanted = tracks.toSet()
        return days.values.sumOf { day -> day.count { it.track in wanted } }
    }

    /** Every id in the draft — the collision set a fresh id is minted against. */
    val ids: Set<String>
        get() = days.values.flatMapTo(HashSet()) { day -> day.map { it.id } }

    // Block editing ------------------------------------------------------------

    /**
     * Adds [block] to [day]. The id is kept when it is free, otherwise a fresh
     * one is minted — a new block never lands on another block's history.
     */
    fun addBlock(day: DayOfWeek, block: TaskBlock): WeeklyProgramDraft =
        withDay(day, blocksFor(day) + keepOrMintId(block, ids))

    /** Adds one copy of [block] to each of [targets], every copy with its own id. */
    fun addBlockToDays(targets: Set<DayOfWeek>, block: TaskBlock): WeeklyProgramDraft =
        targets.fold(this) { draft, day -> draft.addBlock(day, block) }

    /**
     * Replaces the block with the same id on [day], keeping that id byte-for-byte
     * so an edited block keeps its completion history. A block that isn't on that
     * day is left alone.
     */
    fun updateBlock(day: DayOfWeek, block: TaskBlock): WeeklyProgramDraft {
        val blocks = blocksFor(day)
        if (blocks.none { it.id == block.id }) return this
        return withDay(day, blocks.map { if (it.id == block.id) block else it })
    }

    fun deleteBlock(day: DayOfWeek, id: String): WeeklyProgramDraft =
        withDay(day, blocksFor(day).filterNot { it.id == id })

    /**
     * A second copy of a block on the same day, placed in the gap right after
     * the original when it fits — a fresh id, so the original keeps its history.
     */
    fun duplicateBlock(day: DayOfWeek, id: String): WeeklyProgramDraft {
        val source = block(day, id) ?: return this
        return withDay(day, blocksFor(day) + shiftedAfter(source, mintedCopy(source, ids)))
    }

    /**
     * Moves a block to another weekday. The id travels with it — this is the
     * same block on a different day, not a new one — unless the target day
     * already uses that id, in which case the arrival gets a fresh one.
     */
    fun moveBlockToDay(from: DayOfWeek, to: DayOfWeek, id: String): WeeklyProgramDraft {
        if (from == to) return this
        val source = block(from, id) ?: return this
        val without = deleteBlock(from, id)
        val taken = without.ids
        val arriving = if (source.id in taken) mintedCopy(source, taken) else source
        return without.withDay(to, without.blocksFor(to) + arriving)
    }

    /** "Repeat on…": the same block, same times, on other days, each with its own id. */
    fun copyBlockToDays(
        day: DayOfWeek,
        id: String,
        targets: Set<DayOfWeek>
    ): WeeklyProgramDraft {
        val source = block(day, id) ?: return this
        return (targets - day).fold(this) { draft, target ->
            draft.withDay(target, draft.blocksFor(target) + mintedCopy(source, draft.ids))
        }
    }

    /**
     * Reorders [day] by id, keeping every block's own length: the day is re-laid
     * from its first start time, preserving the gaps the program had between
     * blocks. Same rule the Today drag uses, so a 2-hour session stays 2 hours
     * wherever it lands.
     */
    fun reorderDay(day: DayOfWeek, idsInOrder: List<String>): WeeklyProgramDraft {
        val blocks = blocksFor(day)
        if (blocks.size < 2) return this
        if (idsInOrder.size != blocks.size ||
            idsInOrder.toSet() != blocks.mapTo(HashSet()) { it.id }
        ) {
            return this
        }
        val byId = blocks.associateBy { it.id }
        val gaps = List(blocks.size - 1) { i ->
            Duration.between(blocks[i].end, blocks[i + 1].start)
        }
        var cursor = blocks.first().start
        val relaid = idsInOrder.mapIndexed { index, id ->
            val block = byId.getValue(id)
            val end = cursor.plusMinutes(block.durationMinutes)
            block.copy(start = cursor, end = end)
                .also { if (index < gaps.size) cursor = end.plus(gaps[index]) }
        }
        return withDay(day, relaid)
    }

    /** Nudges one block up or down its day's running order. */
    fun moveBlock(day: DayOfWeek, id: String, delta: Int): WeeklyProgramDraft {
        val order = blocksFor(day).map { it.id }.toMutableList()
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to < 0 || to > order.lastIndex) return this
        order.add(to, order.removeAt(from))
        return reorderDay(day, order)
    }

    // Day editing --------------------------------------------------------------

    /** "Copy Monday to Tuesday": [to] becomes fresh-id copies of [from]. */
    fun copyDayTo(from: DayOfWeek, to: DayOfWeek): WeeklyProgramDraft {
        if (from == to) return this
        val cleared = clearDay(to)
        var taken = cleared.ids
        val copies = blocksFor(from).map { source ->
            val copy = mintedCopy(source, taken)
            taken = taken + copy.id
            copy
        }
        return cleared.withDay(to, copies)
    }

    fun clearDay(day: DayOfWeek): WeeklyProgramDraft = withDay(day, emptyList())

    // Serialization ------------------------------------------------------------

    /** The schedule JSON [CustomProgram] and the AI schema both speak. */
    fun toJson(): String {
        val days = JSONObject()
        DayOfWeek.entries.forEach { day -> days.put(day.name, BlockCodec.toJson(blocksFor(day))) }
        return JSONObject().put("days", days).toString()
    }

    // Internals ----------------------------------------------------------------

    private fun withDay(day: DayOfWeek, blocks: List<TaskBlock>): WeeklyProgramDraft =
        copy(days = days + (day to blocks.sortedBy { it.start }))

    /** The same block as a new one: everything kept but the id. */
    private fun mintedCopy(source: TaskBlock, taken: Set<String>): TaskBlock =
        source.copy(id = mintId(source.title.ifBlank { source.id }, taken))

    /** A block keeps an id of its own; a blank or colliding one is replaced. */
    private fun keepOrMintId(block: TaskBlock, taken: Set<String>): TaskBlock =
        if (block.id.isNotBlank() && block.id !in taken) block else mintedCopy(block, taken)

    /** Places [copy] in the gap after [source]; falls back to the same slot at the day's end. */
    private fun shiftedAfter(source: TaskBlock, copy: TaskBlock): TaskBlock {
        val start = source.end
        val end = start.plusMinutes(source.durationMinutes)
        // plusMinutes wraps silently at midnight; the program's convention is
        // that a day ends at 23:59, so a copy that wouldn't fit stays put and
        // the validator flags the overlap instead of the app inventing a time.
        if (end <= start || end > END_OF_DAY) return copy
        return copy.copy(start = start, end = end)
    }

    companion object {
        val END_OF_DAY: LocalTime = LocalTime.of(23, 59)

        /** New blocks are prefixed like the AI schema's new blocks. */
        private const val NEW_PREFIX = "x_"

        val EMPTY: WeeklyProgramDraft = of(emptyMap())

        /** The one way in: fills every weekday and sorts each day by start time. */
        fun of(days: Map<DayOfWeek, List<TaskBlock>>): WeeklyProgramDraft =
            WeeklyProgramDraft(
                DayOfWeek.entries.associateWith { day ->
                    days[day].orEmpty().sortedBy { it.start }
                }
            )

        fun fromJson(json: String): Result<WeeklyProgramDraft> =
            CustomProgram.parse(json).map { of(it) }

        /**
         * A short snake_case id derived from [seed], guaranteed not to be in
         * [taken]. Only ever used for blocks that are new to the week.
         */
        fun mintId(seed: String, taken: Set<String>): String {
            val slug = slug(seed)
            val base = NEW_PREFIX + slug.ifEmpty { "block" }
            if (base !in taken) return base
            var n = 2
            while ("${base}_$n" in taken) n++
            return "${base}_$n"
        }

        /** Lowercase ASCII words, at most three of them — ids stay short and stable. */
        private fun slug(seed: String): String = seed
            .lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9') it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString("_")
            .take(24)
            .trim('_')
    }
}
