package ai.bewsoa.flow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskCompletionEntity::class,
        WeeklyReviewEntity::class,
        NotificationLogEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        XpEventEntity::class,
        StreakFreezeEntity::class,
        FocusSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun completionDao(): CompletionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun taskDao(): TaskDao
    abstract fun xpDao(): XpDao
    abstract fun streakFreezeDao(): StreakFreezeDao
    abstract fun focusDao(): FocusDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        note TEXT NOT NULL,
                        track TEXT,
                        scheduledDate TEXT NOT NULL,
                        estimatedMinutes INTEGER NOT NULL,
                        done INTEGER NOT NULL,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        needsReview INTEGER NOT NULL,
                        reviewParentId INTEGER,
                        reviewStage TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_subtasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        taskId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        done INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v4 rewrote task_completions (done: Boolean → state: String) and added
         * the XP, freeze, focus and chat tables.
         *
         * There is deliberately no MIGRATION_3_4. v4 is a clean break: upgrading
         * from v1.2 DROPS all completion history, streaks, tasks and reviews.
         * That is a decision, not an oversight — Settings → Your data exports
         * everything first, and that is the only way to keep any of it.
         */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bewsoa_flow.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
