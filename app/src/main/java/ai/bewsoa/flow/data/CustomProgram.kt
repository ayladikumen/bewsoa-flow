package ai.bewsoa.flow.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Bumped whenever the active program changes, so screens can re-read blocks. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Parses and installs the override; leaves the old one in place on failure. */
    fun activate(json: String): Result<Unit> =
        parse(json).map { program ->
            current = program
            _version.value++
        }

    fun clear() {
        current = null
        _version.value++
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

    /** Models write "24:00" for midnight and sometimes "9:30" — LocalTime accepts neither. */
    private fun parseTime(raw: String): LocalTime {
        val text = raw.trim()
        if (text.startsWith("24")) return LocalTime.of(23, 59)
        return LocalTime.parse(if (text.indexOf(':') == 1) "0$text" else text)
    }

    private fun JSONObject.toBlock(): TaskBlock = TaskBlock(
        id = getString("id"),
        title = getString("title"),
        track = Track.valueOf(getString("track")),
        start = parseTime(getString("start")),
        end = parseTime(getString("end")),
        note = optString("note", ""),
        counted = optBoolean("counted", true)
    )
}
