package ai.bewsoa.flow.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Transaction
    @Query("SELECT * FROM user_tasks WHERE scheduledDate = :date ORDER BY done, sortOrder, id")
    fun observeForDate(date: String): Flow<List<TaskWithSubtasks>>

    @Query("SELECT * FROM user_tasks WHERE scheduledDate = :date")
    suspend fun getForDate(date: String): List<TaskEntity>

    @Transaction
    @Query(
        "SELECT * FROM user_tasks WHERE scheduledDate BETWEEN :from AND :to " +
            "ORDER BY scheduledDate, sortOrder, id"
    )
    suspend fun getRange(from: String, to: String): List<TaskWithSubtasks>

    @Query("SELECT * FROM user_tasks WHERE id = :id LIMIT 1")
    suspend fun getTask(id: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM user_tasks WHERE reviewParentId = :parentId")
    suspend fun countReviewsOf(parentId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM user_tasks WHERE scheduledDate = :date")
    suspend fun maxSortOrder(date: String): Int

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Insert
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    // Subtasks --------------------------------------------------------------

    @Insert
    suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM task_subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasksOf(taskId: Long)
}
