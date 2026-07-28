package ai.bewsoa.flow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.time.LocalDate

/**
 * Per-date replacement of a whole day's plan — the "do it once" half of the
 * assistant's contract. When the user tells the AI "tonight I'm at a wedding",
 * the changed day lands here and the standing weekly program stays untouched;
 * a permanent request goes through [CustomProgram] instead.
 *
 * [WeeklyProgram.blocksFor] consults this map first, so alarms, widgets,
 * streaks and XP all follow the override automatically. Entries older than
 * yesterday are pruned on every write — this is a diary margin note, not a
 * second program.
 *
 * JSON shape: {"2026-07-28": [{id,title,track,start,end,note,counted}, …]}
 */
object DayOverrides {

    @Volatile
    private var overrides: Map<String, List<TaskBlock>> = emptyMap()

    /** Bumped on every change so screens can recombine. */
    val version = MutableStateFlow(0)

    /** Called once from Application.onCreate with the persisted JSON. */
    fun load(json: String?) {
        overrides = parse(json)
        version.value++
    }

    fun forDate(date: LocalDate): List<TaskBlock>? = overrides[date.toString()]

    fun hasOverride(date: LocalDate): Boolean = overrides.containsKey(date.toString())

    /** Installs [blocks] as [date]'s plan, prunes old days, persists. */
    suspend fun set(context: Context, date: LocalDate, blocks: List<TaskBlock>) {
        val keepFrom = LocalDate.now().minusDays(1).toString()
        overrides = overrides.filterKeys { it >= keepFrom } +
            (date.toString() to blocks.sortedBy { it.start })
        version.value++
        persist(context)
    }

    /** Removes [date]'s override, restoring the standing program for that day. */
    suspend fun clear(context: Context, date: LocalDate) {
        if (!overrides.containsKey(date.toString())) return
        overrides = overrides - date.toString()
        version.value++
        persist(context)
    }

    private suspend fun persist(context: Context) {
        SettingsRepository.get(context).setDayOverridesJson(toJson())
    }

    private fun toJson(): String {
        val root = JSONObject()
        overrides.forEach { (date, blocks) -> root.put(date, BlockCodec.toJson(blocks)) }
        return root.toString()
    }

    private fun parse(json: String?): Map<String, List<TaskBlock>> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            buildMap {
                root.keys().forEach { date ->
                    put(date, BlockCodec.fromJson(root.getJSONArray(date)))
                }
            }
        }.getOrDefault(emptyMap())
    }
}
