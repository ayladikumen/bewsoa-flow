package ai.bewsoa.flow.data

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Holds the AI-generated program override, parsed from the JSON that
 * [AiProgramUpdater] produces. When active, [WeeklyProgram.blocksFor]
 * serves these blocks instead of the built-in schedule.
 *
 * JSON shape: {"days": {"MONDAY": [{id,title,track,start,end,note,counted}], ...}}
 */
object CustomProgram {

    @Volatile
    var current: Map<DayOfWeek, List<TaskBlock>>? = null
        private set

    /** Parses and installs the override; leaves the old one in place on failure. */
    fun activate(json: String): Result<Unit> =
        parse(json).map { program -> current = program }

    fun clear() {
        current = null
    }

    fun parse(json: String): Result<Map<DayOfWeek, List<TaskBlock>>> = runCatching {
        val days = JSONObject(json).getJSONObject("days")
        val program = DayOfWeek.entries.associateWith { day ->
            val arr = days.optJSONArray(day.name)
            if (arr == null) {
                emptyList()
            } else {
                List(arr.length()) { i -> arr.getJSONObject(i).toBlock() }
                    .sortedBy { it.start }
            }
        }
        require(program.values.any { it.isNotEmpty() }) { "The program has no blocks." }
        program
    }

    private fun JSONObject.toBlock(): TaskBlock = TaskBlock(
        id = getString("id"),
        title = getString("title"),
        track = Track.valueOf(getString("track")),
        start = LocalTime.parse(getString("start")),
        end = LocalTime.parse(getString("end")),
        note = optString("note", ""),
        counted = optBoolean("counted", true)
    )
}
