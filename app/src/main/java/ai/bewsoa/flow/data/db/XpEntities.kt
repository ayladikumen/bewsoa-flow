package ai.bewsoa.flow.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Why XP was awarded. [XpEventEntity.refId] means something different for each. */
enum class XpKind {
    /** refId = TaskBlock.id */
    BLOCK_DONE,

    /** refId = TaskEntity.id */
    TASK_DONE,

    /** refId = FocusSessionEntity.id */
    FOCUS_SESSION,

    /** refId = the ISO date — the once-a-day goal bonus */
    DAY_GOAL,

    /** refId = the ISO Monday of the week */
    CHEST,

    /** refId = the milestone reached */
    MILESTONE
}

/**
 * The XP ledger — append-only, one row per award, never a running total. A
 * total that drifts from its causes is unauditable; this can always be re-added.
 *
 * The unique index on (kind, refId, date) is the whole idempotency guarantee:
 * un-checking a block deletes its row, re-checking re-inserts exactly one, and
 * an insert that races or repeats is ignored rather than doubled. Without it,
 * check/uncheck becomes an XP farm.
 */
@Entity(
    tableName = "xp_events",
    indices = [
        Index(value = ["date"]),
        Index(value = ["kind", "refId", "date"], unique = true)
    ]
)
data class XpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO yyyy-MM-dd — the day the XP is credited to, not necessarily when it was earned. */
    val date: String,
    val ts: Long,
    val kind: String,
    val refId: String,
    val amount: Int
)

/**
 * One spent streak freeze. Earning is derived (one per 7 kept days, held cap 2)
 * rather than stored — only the spend is a fact worth persisting, and the
 * primary key makes consuming a freeze for a given day idempotent.
 */
@Entity(tableName = "streak_freezes")
data class StreakFreezeEntity(
    /** ISO yyyy-MM-dd of the day this freeze rescued. */
    @PrimaryKey val usedForDate: String,
    val usedAt: Long
)

// Focus sessions live in FocusEntities.kt / FocusDao.kt — the Deep Focus
// feature already owns that table, and XP reads it rather than duplicating it.

/**
 * One turn in the AI chat. Persisted so the transcript survives process death.
 *
 * @param cardType which rich card this message renders as, if any.
 * @param status APPLIED/DISCARDED matter for program diffs — a persisted
 *        transcript must never offer to re-apply a stale proposal.
 */
@Entity(tableName = "chat_messages", indices = [Index(value = ["ts"])])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    /** user | assistant | tool */
    val role: String,
    val text: String,
    val cardType: String? = null,
    val cardJson: String? = null,
    val toolCallsJson: String? = null,
    val status: String = "OK"
)
