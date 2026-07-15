package ai.bewsoa.flow.data

import android.content.Context
import ai.bewsoa.flow.data.db.AppDatabase
import ai.bewsoa.flow.data.db.XpEventEntity
import ai.bewsoa.flow.data.db.XpKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A moment worth confetti. Emitted by [XpRepository], rendered by CelebrationHost. */
sealed interface Celebration {
    data class LevelUp(val level: LevelInfo) : Celebration
    data class GoalHit(val bonus: Int) : Celebration
    data class ChestOpened(val amount: Int) : Celebration
    data class StreakMilestone(val days: Int, val bonus: Int) : Celebration
}

/**
 * The XP economy's writer. Every award goes through [award], which relies on
 * the ledger's unique (kind, refId, date) index for idempotency: repeats are
 * IGNOREd by Room, and this class detects the no-op by comparing totals, so a
 * duplicate can never trigger a second celebration either.
 */
class XpRepository private constructor(private val db: AppDatabase) {

    private val dao get() = db.xpDao()

    private val _celebrations = MutableSharedFlow<Celebration>(extraBufferCapacity = 8)

    /** App-wide: collected once in AppRoot so a level-up fires confetti no
     *  matter which screen (or notification action) earned it. */
    val celebrations: SharedFlow<Celebration> = _celebrations.asSharedFlow()

    // Reads ----------------------------------------------------------------------

    fun observeTotal(): Flow<Int> = dao.observeTotal()

    fun observeDayTotal(date: LocalDate): Flow<Int> = dao.observeDayTotal(date.toString())

    fun observeWeekXp(weekStart: LocalDate): Flow<Int> =
        dao.observeRange(weekStart.toString(), weekStart.plusDays(6).toString())
            .map { events -> events.sumOf { it.amount } }

    // Awards ----------------------------------------------------------------------

    suspend fun awardBlock(date: LocalDate, block: TaskBlock) =
        award(XpKind.BLOCK_DONE, block.id, date, Xp.blockXp(block))

    suspend fun revokeBlock(date: LocalDate, blockId: String) =
        dao.revoke(XpKind.BLOCK_DONE.name, blockId, date.toString())

    suspend fun awardTask(date: LocalDate, taskId: Long, estimatedMinutes: Int) =
        award(XpKind.TASK_DONE, taskId.toString(), date, Xp.taskXp(estimatedMinutes))

    suspend fun revokeTask(date: LocalDate, taskId: Long) =
        dao.revoke(XpKind.TASK_DONE.name, taskId.toString(), date.toString())

    suspend fun awardFocus(date: LocalDate, sessionId: Long, minutes: Int) =
        award(XpKind.FOCUS_SESSION, sessionId.toString(), date, Xp.focusXp(minutes))

    /**
     * The one shared write path. Inserts, and only if the insert actually
     * landed (totals moved) checks the two follow-ups: did this push the day
     * over its goal, and did it push the account over a level boundary.
     */
    private suspend fun award(kind: XpKind, refId: String, date: LocalDate, amount: Int) {
        if (amount <= 0) return
        val before = dao.getTotal()
        dao.award(
            XpEventEntity(
                date = date.toString(),
                ts = System.currentTimeMillis(),
                kind = kind.name,
                refId = refId,
                amount = amount
            )
        )
        if (dao.getTotal() == before) return // IGNOREd duplicate — nothing happened.
        maybeAwardDayGoal(date)
        val after = Xp.levelFor(dao.getTotal())
        if (after.index > Xp.levelFor(before).index) {
            _celebrations.tryEmit(Celebration.LevelUp(after))
        }
    }

    /**
     * The +25 goal bonus, once per day. The goal compares *base* XP (bonus
     * excluded) against the plan-derived target, so the bonus can't count
     * toward earning itself.
     */
    private suspend fun maybeAwardDayGoal(date: LocalDate) {
        val refId = date.toString()
        if (dao.countOf(XpKind.DAY_GOAL.name, refId) > 0) return
        val base = dao.getRange(refId, refId)
            .filterNot { it.kind == XpKind.DAY_GOAL.name }
            .sumOf { it.amount }
        if (base < Xp.dailyGoal(WeeklyProgram.blocksFor(date))) return
        dao.award(
            XpEventEntity(
                date = refId,
                ts = System.currentTimeMillis(),
                kind = XpKind.DAY_GOAL.name,
                refId = refId,
                amount = Xp.DAY_GOAL_XP
            )
        )
        _celebrations.tryEmit(Celebration.GoalHit(Xp.DAY_GOAL_XP))
    }

    // Weekly chest ------------------------------------------------------------------

    fun observeChest(weekStart: LocalDate): Flow<ChestState> = combine(
        db.completionDao().observeRange(weekStart.toString(), weekStart.plusDays(6).toString()),
        dao.observeCountOf(XpKind.CHEST.name, weekStart.toString())
    ) { rows, openedCount ->
        chestState(weekStart, LocalDate.now(), rows, openedCount > 0)
    }

    /**
     * The tap that *is* the dopamine. Returns the amount collected, or null
     * when there was nothing claimable (locked, already opened, expired).
     */
    suspend fun openChest(weekStart: LocalDate): Int? {
        val today = LocalDate.now()
        val rows = db.completionDao()
            .getRange(weekStart.toString(), weekStart.plusDays(6).toString())
        val opened = dao.countOf(XpKind.CHEST.name, weekStart.toString()) > 0
        val state = chestState(weekStart, today, rows, opened)
        if (!state.claimable) return null
        val before = dao.getTotal()
        dao.award(
            XpEventEntity(
                date = today.toString(),
                ts = System.currentTimeMillis(),
                kind = XpKind.CHEST.name,
                refId = weekStart.toString(),
                amount = state.reward
            )
        )
        if (dao.getTotal() == before) return null // raced a double-tap; first one won.
        _celebrations.tryEmit(Celebration.ChestOpened(state.reward))
        val after = Xp.levelFor(dao.getTotal())
        if (after.index > Xp.levelFor(before).index) {
            _celebrations.tryEmit(Celebration.LevelUp(after))
        }
        return state.reward
    }

    private fun chestState(
        weekStart: LocalDate,
        today: LocalDate,
        rows: List<ai.bewsoa.flow.data.db.TaskCompletionEntity>,
        opened: Boolean
    ) = ChestState(
        weekStart = weekStart,
        keptDays = Xp.chestKeptDays(weekStart, today, rows, WeeklyProgram::blocksFor),
        opened = opened,
        expired = today.isAfter(Xp.chestExpiry(weekStart))
    )

    // Streak milestones ---------------------------------------------------------------

    /**
     * Pays any newly-crossed streak milestones. Once ever per milestone (the
     * refId has no date in it) — a streak that breaks and rebuilds to 7 doesn't
     * mint a second bonus. Called from the same foreground moment as
     * settleStreak, and nowhere else.
     */
    suspend fun settleMilestones(streakDays: Int) {
        val today = LocalDate.now().toString()
        Xp.STREAK_MILESTONES.filter { it <= streakDays }.forEach { milestone ->
            val refId = "streak_$milestone"
            if (dao.countOf(XpKind.MILESTONE.name, refId) > 0) return@forEach
            val before = dao.getTotal()
            dao.award(
                XpEventEntity(
                    date = today,
                    ts = System.currentTimeMillis(),
                    kind = XpKind.MILESTONE.name,
                    refId = refId,
                    amount = Xp.milestoneXp(milestone)
                )
            )
            if (dao.getTotal() == before) return@forEach
            _celebrations.tryEmit(Celebration.StreakMilestone(milestone, Xp.milestoneXp(milestone)))
            val after = Xp.levelFor(dao.getTotal())
            if (after.index > Xp.levelFor(before).index) {
                _celebrations.tryEmit(Celebration.LevelUp(after))
            }
        }
    }

    companion object {
        @Volatile
        private var instance: XpRepository? = null

        fun get(context: Context): XpRepository =
            instance ?: synchronized(this) {
                instance ?: XpRepository(AppDatabase.getInstance(context)).also { instance = it }
            }
    }
}
