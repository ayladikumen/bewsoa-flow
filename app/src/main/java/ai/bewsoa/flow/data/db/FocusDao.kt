package ai.bewsoa.flow.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {

    @Query("SELECT * FROM focus_sessions WHERE date = :date ORDER BY completedAt DESC")
    fun observeForDate(date: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE date BETWEEN :from AND :to ORDER BY completedAt")
    fun observeRange(from: String, to: String): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Delete
    suspend fun delete(session: FocusSessionEntity)
}
