package ai.bewsoa.flow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Note: no default arguments on any method here — Room's KSP processor crashes
// on them (see commit 3d5242d).

@Dao
interface XpDao {

    /**
     * IGNORE, paired with the unique (kind, refId, date) index, is what makes
     * awarding idempotent: a repeat award is dropped, not doubled.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun award(event: XpEventEntity)

    @Query("DELETE FROM xp_events WHERE kind = :kind AND refId = :refId AND date = :date")
    suspend fun revoke(kind: String, refId: String, date: String)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events")
    suspend fun getTotal(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = :date")
    fun observeDayTotal(date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE date = :date")
    suspend fun getDayTotal(date: String): Int

    @Query("SELECT * FROM xp_events WHERE date BETWEEN :from AND :to ORDER BY ts DESC")
    fun observeRange(from: String, to: String): Flow<List<XpEventEntity>>

    @Query("SELECT * FROM xp_events WHERE date BETWEEN :from AND :to ORDER BY ts DESC")
    suspend fun getRange(from: String, to: String): List<XpEventEntity>

    @Query("SELECT COUNT(*) FROM xp_events WHERE kind = :kind AND refId = :refId")
    suspend fun countOf(kind: String, refId: String): Int

    @Query("SELECT COUNT(*) FROM xp_events WHERE kind = :kind AND refId = :refId")
    fun observeCountOf(kind: String, refId: String): Flow<Int>
}

@Dao
interface StreakFreezeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun consume(freeze: StreakFreezeEntity)

    @Query("SELECT * FROM streak_freezes")
    suspend fun getAll(): List<StreakFreezeEntity>

    @Query("SELECT * FROM streak_freezes")
    fun observeAll(): Flow<List<StreakFreezeEntity>>

    @Query("SELECT COUNT(*) FROM streak_freezes")
    suspend fun countUsed(): Int
}

// FocusDao lives in FocusDao.kt — see the note in XpEntities.kt.

@Dao
interface ChatDao {

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @androidx.room.Update
    suspend fun update(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages ORDER BY ts, id")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY ts, id")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ChatMessageEntity?

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}
