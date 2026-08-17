package ai.bewsoa.flow.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** How much an issue matters: an error blocks saving, a warning just informs. */
enum class IssueLevel { ERROR, WARNING }

/**
 * One thing the validator found, addressed at a day and (where it helps) a
 * block, so the editor can show it next to what it is about.
 */
data class ProgramIssue(
    val level: IssueLevel,
    val message: String,
    val day: DayOfWeek? = null,
    val blockId: String? = null
)

/** The structured result — never a boolean, so the UI can say what is wrong. */
data class ProgramValidation(val issues: List<ProgramIssue>) {

    val errors: List<ProgramIssue> get() = issues.filter { it.level == IssueLevel.ERROR }

    val warnings: List<ProgramIssue> get() = issues.filter { it.level == IssueLevel.WARNING }

    /** A program is savable when nothing is broken; warnings are the user's call. */
    val isSavable: Boolean get() = errors.isEmpty()

    val isClean: Boolean get() = issues.isEmpty()

    fun forDay(day: DayOfWeek): List<ProgramIssue> = issues.filter { it.day == day }

    fun errorsForDay(day: DayOfWeek): List<ProgramIssue> =
        errors.filter { it.day == day }

    companion object {
        val CLEAN = ProgramValidation(emptyList())
    }
}

/**
 * The one validation layer for weekly programs — used by the builder before it
 * lets a draft be saved, and by the AI paths before a generated schedule is ever
 * activated. It only reports; it never quietly rewrites the user's week to make
 * a problem disappear.
 *
 * The repository convention that a day ends at 23:59 (never 24:00) is part of
 * the contract here: [BlockCodec.parseTime] folds "24:00" down to 23:59, so a
 * block whose end lands on midnight is a real mistake worth naming.
 */
object WeeklyProgramValidator {

    private val UNCOUNTED_TRACKS = setOf(Track.MEAL, Track.FREE)

    fun validate(draft: WeeklyProgramDraft): ProgramValidation {
        val issues = mutableListOf<ProgramIssue>()

        if (draft.blockCount == 0) {
            issues += ProgramIssue(
                IssueLevel.ERROR,
                "Your week is empty — add at least one block before saving."
            )
        }

        DayOfWeek.entries.forEach { day ->
            val name = label(day)
            if (!draft.days.containsKey(day)) {
                issues += ProgramIssue(IssueLevel.ERROR, "$name is missing from the program.", day)
                return@forEach
            }
            val blocks = draft.blocksFor(day)
            if (blocks.isEmpty()) {
                issues += ProgramIssue(IssueLevel.WARNING, "$name has no blocks.", day)
                return@forEach
            }

            blocks.groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys
                .forEach { id ->
                    issues += ProgramIssue(
                        IssueLevel.ERROR,
                        "$name has two blocks sharing the id “$id”.",
                        day,
                        id
                    )
                }

            blocks.forEach { block -> issues += blockIssues(day, name, block) }

            // Order and overlaps read from the day as sorted, so a bad order is
            // reported once instead of turning every pair into an overlap.
            val sorted = blocks.sortedBy { it.start }
            if (sorted.map { it.id } != blocks.map { it.id }) {
                issues += ProgramIssue(
                    IssueLevel.ERROR,
                    "$name is out of chronological order.",
                    day
                )
            }
            for (i in 0 until sorted.lastIndex) {
                val earlier = sorted[i]
                val later = sorted[i + 1]
                if (later.start < earlier.end) {
                    issues += ProgramIssue(
                        IssueLevel.ERROR,
                        "$name has overlapping blocks — ${earlier.title} ends at " +
                            "${time(earlier.end)} but ${later.title} starts at " +
                            "${time(later.start)}.",
                        day,
                        later.id
                    )
                }
            }
        }

        return ProgramValidation(issues)
    }

    /**
     * Validates a schedule JSON — the gate every AI-generated program passes
     * before it can be activated. A payload that doesn't even parse comes back
     * as one error rather than an exception.
     */
    fun validateJson(json: String): ProgramValidation =
        WeeklyProgramDraft.fromJson(json).fold(
            onSuccess = { validate(it) },
            onFailure = {
                ProgramValidation(
                    listOf(
                        ProgramIssue(
                            IssueLevel.ERROR,
                            it.message ?: "This schedule could not be read."
                        )
                    )
                )
            }
        )

    private fun blockIssues(
        day: DayOfWeek,
        name: String,
        block: TaskBlock
    ): List<ProgramIssue> {
        val issues = mutableListOf<ProgramIssue>()
        val title = block.title.trim().ifEmpty { "Untitled block" }

        if (block.title.isBlank()) {
            issues += ProgramIssue(
                IssueLevel.ERROR,
                "A block on $name has no title.",
                day,
                block.id
            )
        }
        if (block.id.isBlank() || block.id.any { it.isWhitespace() }) {
            issues += ProgramIssue(
                IssueLevel.ERROR,
                "“$title” on $name has an invalid id.",
                day,
                block.id
            )
        }
        when {
            block.end == LocalTime.MIDNIGHT -> issues += ProgramIssue(
                IssueLevel.ERROR,
                "“$title” on $name ends at midnight — a day ends at 23:59 here.",
                day,
                block.id
            )
            block.end == block.start -> issues += ProgramIssue(
                IssueLevel.ERROR,
                "“$title” on $name has no length — it starts and ends at ${time(block.start)}.",
                day,
                block.id
            )
            block.end < block.start -> issues += ProgramIssue(
                IssueLevel.ERROR,
                "“$title” on $name ends before it starts (${time(block.start)}–" +
                    "${time(block.end)}).",
                day,
                block.id
            )
        }
        if (block.counted && block.track in UNCOUNTED_TRACKS) {
            issues += ProgramIssue(
                IssueLevel.WARNING,
                "“$title” on $name counts toward progress — ${block.track.label} " +
                    "blocks usually don't.",
                day,
                block.id
            )
        }
        if (!block.counted && block.track !in UNCOUNTED_TRACKS) {
            issues += ProgramIssue(
                IssueLevel.WARNING,
                "“$title” on $name doesn't count toward progress, so it won't " +
                    "earn XP or protect the streak.",
                day,
                block.id
            )
        }
        return issues
    }

    fun label(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.US)

    private fun time(value: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", value.hour, value.minute)
}
