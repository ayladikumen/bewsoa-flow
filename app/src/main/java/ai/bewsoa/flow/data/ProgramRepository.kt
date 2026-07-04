package ai.bewsoa.flow.data

import android.content.Context
import ai.bewsoa.flow.data.db.AppDatabase
import ai.bewsoa.flow.data.db.TaskCompletionEntity
import ai.bewsoa.flow.data.db.WeeklyReviewEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * "Never miss twice" state. A day is kept when at least [ProgramRepository.KEEP_THRESHOLD]
 * of its counted blocks are done.
 */
data class StreakInfo(
    val current: Int,
    val yesterdayKept: Boolean,
    val todayKept: Boolean
)

class ProgramRepository private constructor(private val db: AppDatabase) {

    fun blocksFor(date: LocalDate): List<TaskBlock> = WeeklyProgram.blocksFor(date)

    fun observeDay(date: LocalDate): Flow<List<TaskCompletionEntity>> =
        db.completionDao().observeForDate(date.toString())

    fun observeWeek(weekStart: LocalDate): Flow<List<TaskCompletionEntity>> =
        db.completionDao().observeRange(weekStart.toString(), weekStart.plusDays(6).toString())

    suspend fun getWeek(weekStart: LocalDate): List<TaskCompletionEntity> =
        db.completionDao().getRange(weekStart.toString(), weekStart.plusDays(6).toString())

    suspend fun setDone(date: LocalDate, taskId: String, done: Boolean) {
        db.completionDao().upsert(
            TaskCompletionEntity(
                date = date.toString(),
                taskId = taskId,
                done = done,
                completedAt = if (done) System.currentTimeMillis() else null
            )
        )
    }

    suspend fun isDone(date: LocalDate, taskId: String): Boolean =
        db.completionDao().get(date.toString(), taskId)?.done == true

    /**
     * Walks back from yesterday counting consecutive kept days; today joins the
     * streak as soon as it crosses the threshold too.
     */
    suspend fun computeStreak(today: LocalDate): StreakInfo {
        val from = today.minusDays(LOOKBACK_DAYS)
        val rows = db.completionDao().getRange(from.toString(), today.toString())
        val doneByDate: Map<String, Set<String>> = rows
            .filter { it.done }
            .groupBy({ it.date }, { it.taskId })
            .mapValues { (_, ids) -> ids.toSet() }

        fun kept(date: LocalDate): Boolean {
            val counted = WeeklyProgram.blocksFor(date).filter { it.counted }
            if (counted.isEmpty()) return true
            val doneIds = doneByDate[date.toString()].orEmpty()
            val done = counted.count { doneIds.contains(it.id) }
            return done.toFloat() / counted.size >= KEEP_THRESHOLD
        }

        var streak = 0
        var cursor = today.minusDays(1)
        while (cursor >= from && kept(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val todayKept = kept(today)
        if (todayKept) streak++

        return StreakInfo(
            current = streak,
            yesterdayKept = kept(today.minusDays(1)),
            todayKept = todayKept
        )
    }

    // Weekly reviews -------------------------------------------------------

    suspend fun getReview(weekStart: LocalDate): WeeklyReviewEntity? =
        db.reviewDao().get(weekStart.toString())

    suspend fun saveReview(review: WeeklyReviewEntity) =
        db.reviewDao().upsert(review)

    fun observeReviews(): Flow<List<WeeklyReviewEntity>> =
        db.reviewDao().observeAll()

    companion object {
        const val KEEP_THRESHOLD = 0.6f
        private const val LOOKBACK_DAYS = 90L

        @Volatile
        private var instance: ProgramRepository? = null

        fun get(context: Context): ProgramRepository =
            instance ?: synchronized(this) {
                instance ?: ProgramRepository(AppDatabase.getInstance(context)).also { instance = it }
            }
    }
}
