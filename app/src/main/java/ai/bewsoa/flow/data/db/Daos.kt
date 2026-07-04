package ai.bewsoa.flow.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionDao {

    @Query("SELECT * FROM task_completions WHERE date = :date")
    fun observeForDate(date: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE date BETWEEN :from AND :to")
    fun observeRange(from: String, to: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE date BETWEEN :from AND :to")
    suspend fun getRange(from: String, to: String): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions WHERE date = :date AND taskId = :taskId LIMIT 1")
    suspend fun get(date: String, taskId: String): TaskCompletionEntity?

    @Upsert
    suspend fun upsert(completion: TaskCompletionEntity)
}

@Dao
interface ReviewDao {

    @Upsert
    suspend fun upsert(review: WeeklyReviewEntity)

    @Query("SELECT * FROM weekly_reviews WHERE weekStart = :weekStart LIMIT 1")
    suspend fun get(weekStart: String): WeeklyReviewEntity?

    @Query("SELECT * FROM weekly_reviews ORDER BY weekStart DESC")
    fun observeAll(): Flow<List<WeeklyReviewEntity>>
}
