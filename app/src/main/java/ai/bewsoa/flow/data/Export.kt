package ai.bewsoa.flow.data

import ai.bewsoa.flow.data.db.CompletionState
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import ai.bewsoa.flow.data.db.TaskWithSubtasks
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import java.time.LocalDate

/**
 * Everything the user is taking with them. Assembled by the caller (which has
 * the DB) and handed to [Export], which stays free of Android and Room lookups
 * so it can be tested on the JVM.
 *
 * @param programJson the active AI/hand-built program, or null when the
 *        built-in schedule is in force.
 */
data class ExportBundle(
    val from: LocalDate,
    val to: LocalDate,
    val completions: List<TaskCompletionEntity>,
    val tasks: List<TaskWithSubtasks>,
    val reviews: List<WeeklyReviewEntity>,
    val programJson: String?
)

/**
 * Serialises an [ExportBundle] to CSV, JSON or Markdown.
 *
 * JSON is hand-built rather than delegated to org.json on purpose: org.json is
 * an Android SDK stub that throws in unit tests, and an export format nobody
 * can test is an export format nobody should trust.
 */
object Export {

    /** The row shape both CSV and JSON flatten to: a completion joined to its block. */
    private data class Row(
        val date: String,
        val taskId: String,
        val title: String,
        val track: String,
        val start: String,
        val end: String,
        val state: String,
        val minutes: Long
    )

    private fun rows(
        bundle: ExportBundle,
        blocksFor: (LocalDate) -> List<TaskBlock>
    ): List<Row> {
        val byDate = bundle.completions.groupBy { it.date }
        val out = mutableListOf<Row>()
        var cursor = bundle.from
        while (!cursor.isAfter(bundle.to)) {
            val iso = cursor.toString()
            val states = byDate[iso].orEmpty().associateBy { it.taskId }
            blocksFor(cursor).forEach { block ->
                out += Row(
                    date = iso,
                    taskId = block.id,
                    title = block.title,
                    track = block.track.name,
                    start = block.start.toString(),
                    end = block.end.toString(),
                    state = stateOf(states[block.id]),
                    minutes = block.durationMinutes
                )
            }
            cursor = cursor.plusDays(1)
        }
        return out
    }

    /** No row at all means the block was simply never touched. */
    private fun stateOf(row: TaskCompletionEntity?): String =
        row?.state ?: CompletionState.PENDING.name

    // CSV --------------------------------------------------------------------

    fun toCsv(bundle: ExportBundle, blocksFor: (LocalDate) -> List<TaskBlock>): String {
        val sb = StringBuilder()
        sb.append("date,taskId,title,track,start,end,state,minutes\n")
        rows(bundle, blocksFor).forEach { r ->
            sb.append(csv(r.date)).append(',')
                .append(csv(r.taskId)).append(',')
                .append(csv(r.title)).append(',')
                .append(csv(r.track)).append(',')
                .append(csv(r.start)).append(',')
                .append(csv(r.end)).append(',')
                .append(csv(r.state)).append(',')
                .append(r.minutes).append('\n')
        }
        return sb.toString()
    }

    /** RFC 4180: quote when the value contains a delimiter, quote or newline; double inner quotes. */
    internal fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // JSON -------------------------------------------------------------------

    fun toJson(bundle: ExportBundle, blocksFor: (LocalDate) -> List<TaskBlock>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"app\": \"Bewsoa Flow\",\n")
        sb.append("  \"formatVersion\": 1,\n")
        sb.append("  \"from\": ").append(json(bundle.from.toString())).append(",\n")
        sb.append("  \"to\": ").append(json(bundle.to.toString())).append(",\n")

        sb.append("  \"program\": {\n")
        sb.append("    \"custom\": ").append(bundle.programJson != null).append(",\n")
        // Already JSON, and already validated by CustomProgram.parse before it
        // was ever stored — embed rather than re-encode it as a string.
        sb.append("    \"days\": ").append(bundle.programJson ?: "null").append("\n")
        sb.append("  },\n")

        sb.append("  \"completions\": [\n")
        rows(bundle, blocksFor).forEachIndexed { i, r ->
            if (i > 0) sb.append(",\n")
            sb.append("    {")
            sb.append("\"date\": ").append(json(r.date)).append(", ")
            sb.append("\"taskId\": ").append(json(r.taskId)).append(", ")
            sb.append("\"title\": ").append(json(r.title)).append(", ")
            sb.append("\"track\": ").append(json(r.track)).append(", ")
            sb.append("\"start\": ").append(json(r.start)).append(", ")
            sb.append("\"end\": ").append(json(r.end)).append(", ")
            sb.append("\"state\": ").append(json(r.state)).append(", ")
            sb.append("\"minutes\": ").append(r.minutes)
            sb.append("}")
        }
        sb.append("\n  ],\n")

        sb.append("  \"tasks\": [\n")
        bundle.tasks.forEachIndexed { i, t ->
            if (i > 0) sb.append(",\n")
            sb.append("    {")
            sb.append("\"id\": ").append(t.task.id).append(", ")
            sb.append("\"title\": ").append(json(t.task.title)).append(", ")
            sb.append("\"note\": ").append(json(t.task.note)).append(", ")
            sb.append("\"track\": ").append(t.task.track?.let { json(it) } ?: "null").append(", ")
            sb.append("\"scheduledDate\": ").append(json(t.task.scheduledDate)).append(", ")
            sb.append("\"estimatedMinutes\": ").append(t.task.estimatedMinutes).append(", ")
            sb.append("\"done\": ").append(t.task.done).append(", ")
            sb.append("\"completedAt\": ").append(t.task.completedAt ?: "null").append(", ")
            sb.append("\"createdAt\": ").append(t.task.createdAt).append(", ")
            sb.append("\"needsReview\": ").append(t.task.needsReview).append(", ")
            sb.append("\"reviewStage\": ").append(json(t.task.reviewStage)).append(", ")
            sb.append("\"subtasks\": [")
            t.subtasks.forEachIndexed { j, s ->
                if (j > 0) sb.append(", ")
                sb.append("{\"title\": ").append(json(s.title))
                    .append(", \"done\": ").append(s.done).append("}")
            }
            sb.append("]}")
        }
        sb.append("\n  ],\n")

        sb.append("  \"reviews\": [\n")
        bundle.reviews.forEachIndexed { i, r ->
            if (i > 0) sb.append(",\n")
            sb.append("    {")
            sb.append("\"weekStart\": ").append(json(r.weekStart)).append(", ")
            sb.append("\"yksBlocksKept\": ").append(r.yksBlocksKept).append(", ")
            sb.append("\"tytScore\": ").append(json(r.tytScore)).append(", ")
            sb.append("\"tytPrevScore\": ").append(json(r.tytPrevScore)).append(", ")
            sb.append("\"biggestGap\": ").append(json(r.biggestGap)).append(", ")
            sb.append("\"satHours\": ").append(json(r.satHours)).append(", ")
            sb.append("\"satWeakest\": ").append(json(r.satWeakest)).append(", ")
            sb.append("\"exactHourNotes\": ").append(json(r.exactHourNotes)).append(", ")
            sb.append("\"bewsoaClockNotes\": ").append(json(r.bewsoaClockNotes)).append(", ")
            sb.append("\"commitCount\": ").append(json(r.commitCount)).append(", ")
            sb.append("\"gymSessions\": ").append(r.gymSessions).append(", ")
            sb.append("\"sleepAverage\": ").append(json(r.sleepAverage)).append(", ")
            sb.append("\"energy\": ").append(r.energy).append(", ")
            sb.append("\"slowedMeDown\": ").append(json(r.slowedMeDown)).append(", ")
            sb.append("\"nextWeekTask\": ").append(json(r.nextWeekTask)).append(", ")
            sb.append("\"savedAt\": ").append(r.savedAt)
            sb.append("}")
        }
        sb.append("\n  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    /** A JSON string literal, quotes included. */
    internal fun json(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        value.forEach { c ->
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u").append("%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // Markdown ---------------------------------------------------------------

    /** The human-readable one: what a person would actually want to read back. */
    fun toMarkdown(
        bundle: ExportBundle,
        blocksFor: (LocalDate) -> List<TaskBlock>,
        insights: List<Insight>
    ): String {
        val all = rows(bundle, blocksFor)
        val counted = all.filter { it.track != Track.MEAL.name && it.track != Track.FREE.name }
        val done = counted.count { it.state == "DONE" }
        val sb = StringBuilder()

        sb.append("# Bewsoa Flow — ${bundle.from} to ${bundle.to}\n\n")
        sb.append("- Blocks completed: **$done / ${counted.size}**")
        if (counted.isNotEmpty()) {
            sb.append(" (${(done * 100 / counted.size)}%)")
        }
        sb.append("\n")
        sb.append("- Focused minutes: **${counted.filter { it.state == "DONE" }.sumOf { it.minutes }}**\n")
        sb.append("- Tasks logged: **${bundle.tasks.size}**\n\n")

        if (insights.isNotEmpty()) {
            sb.append("## Patterns\n\n")
            insights.forEach { sb.append("- ${it.text}\n") }
            sb.append("\n")
        }

        sb.append("## By day\n\n")
        sb.append("| Date | Kept | Planned |\n|---|---|---|\n")
        counted.groupBy { it.date }.toSortedMap().forEach { (date, dayRows) ->
            sb.append("| $date | ${dayRows.count { it.state == "DONE" }} | ${dayRows.size} |\n")
        }
        sb.append("\n")

        if (bundle.reviews.isNotEmpty()) {
            sb.append("## Weekly reviews\n\n")
            bundle.reviews.sortedBy { it.weekStart }.forEach { r ->
                sb.append("### Week of ${r.weekStart}\n\n")
                if (r.slowedMeDown.isNotBlank()) sb.append("- Slowed me down: ${r.slowedMeDown}\n")
                if (r.nextWeekTask.isNotBlank()) sb.append("- Next week: ${r.nextWeekTask}\n")
                sb.append("- Energy: ${r.energy}/10\n\n")
            }
        }
        return sb.toString()
    }
}
