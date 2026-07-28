package ai.bewsoa.flow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate

/**
 * Per-day override of the block order — "life happened in a different order today".
 *
 * Every block keeps its own length when dragged: the day is re-laid from its
 * original start time, block after block, preserving the gaps the program had
 * between them. A 2-hour session stays 2 hours wherever it lands; only the
 * clock positions reflow. Completions are keyed by block id, so ticking (and
 * the streak) is unaffected.
 *
 * Loaded synchronously at app start like [CustomProgram]; [version] bumps so
 * screens, widgets and alarms recompute. Orders older than yesterday are pruned —
 * this is a "match today's reality" tool, not a program editor.
 */
object DayBlockOrder {

    @Volatile
    private var orders: Map<String, List<String>> = emptyMap()

    /** Bumped on every change so flows can recombine. */
    val version = MutableStateFlow(0)

    /** Called once from Application.onCreate with the persisted JSON. */
    fun load(json: String?) {
        orders = parse(json)
        version.value++
    }

    /** Re-times [blocks] for [date] if an order is stored; ignores stale orders. */
    fun applyTo(blocks: List<TaskBlock>, date: LocalDate): List<TaskBlock> {
        val order = orders[date.toString()] ?: return blocks
        // Only a full permutation of today's ids is trusted — a program change
        // since the drag invalidates the stored order.
        if (blocks.isEmpty() ||
            order.size != blocks.size ||
            order.toSet() != blocks.mapTo(HashSet()) { it.id }
        ) {
            return blocks
        }
        val byId = blocks.associateBy { it.id }
        val slots = blocks.sortedBy { it.start }
        // Total time is invariant (same durations, same gaps), so the day still
        // starts and ends exactly when the program said — nothing can wrap past
        // midnight that didn't already.
        val gaps = List(slots.size - 1) { i ->
            Duration.between(slots[i].end, slots[i + 1].start)
        }
        var cursor = slots.first().start
        return order.mapIndexed { index, id ->
            val block = byId.getValue(id)
            val end = cursor.plusMinutes(block.durationMinutes)
            block.copy(start = cursor, end = end)
                .also { if (index < gaps.size) cursor = end.plus(gaps[index]) }
        }
    }

    /** Stores [idsInSlotOrder] for [date], prunes old days, persists. */
    suspend fun set(context: Context, date: LocalDate, idsInSlotOrder: List<String>) {
        val keepFrom = date.minusDays(1).toString()
        orders = orders.filterKeys { it >= keepFrom } + (date.toString() to idsInSlotOrder)
        version.value++
        SettingsRepository.get(context).setDayOrderJson(toJson())
    }

    private fun toJson(): String {
        val root = JSONObject()
        orders.forEach { (date, ids) -> root.put(date, JSONArray(ids)) }
        return root.toString()
    }

    private fun parse(json: String?): Map<String, List<String>> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            buildMap {
                root.keys().forEach { date ->
                    val array = root.getJSONArray(date)
                    put(date, List(array.length()) { array.getString(it) })
                }
            }
        }.getOrDefault(emptyMap())
    }
}
