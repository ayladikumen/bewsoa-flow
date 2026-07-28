package ai.bewsoa.flow.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/**
 * One JSON shape for a block, shared by [DayOverrides] and the chat assistant.
 * Matches the schedule schema [AiProgramUpdater] already uses:
 * {id,title,track,start,end,note,counted} with 24h HH:MM times.
 */
object BlockCodec {

    fun toJson(block: TaskBlock): JSONObject = JSONObject()
        .put("id", block.id)
        .put("title", block.title)
        .put("track", block.track.name)
        .put("start", format(block.start))
        .put("end", format(block.end))
        .put("note", block.note)
        .put("counted", block.counted)

    fun toJson(blocks: List<TaskBlock>): JSONArray =
        JSONArray().also { arr -> blocks.forEach { arr.put(toJson(it)) } }

    fun fromJson(obj: JSONObject): TaskBlock = TaskBlock(
        id = obj.getString("id"),
        title = obj.getString("title"),
        track = Track.valueOf(obj.getString("track")),
        start = parseTime(obj.getString("start")),
        end = parseTime(obj.getString("end")),
        note = obj.optString("note", ""),
        counted = obj.optBoolean("counted", true)
    )

    fun fromJson(arr: JSONArray): List<TaskBlock> =
        List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }.sortedBy { it.start }

    private fun format(time: LocalTime): String =
        String.format(java.util.Locale.US, "%02d:%02d", time.hour, time.minute)

    /** Models write "24:00" for midnight and sometimes "9:30" — LocalTime accepts neither. */
    fun parseTime(raw: String): LocalTime {
        val text = raw.trim()
        if (text.startsWith("24")) return LocalTime.of(23, 59)
        return LocalTime.parse(if (text.indexOf(':') == 1) "0$text" else text)
    }
}
