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
        FocusSessionEntity::class,
        XpEventEntity::class,
        StreakFreezeEntity::class,
        ChatMessageEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun completionDao(): CompletionDao
    abstract fun reviewDao(): ReviewDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun taskDao(): TaskDao
    abstract fun focusDao(): FocusDao
    abstract fun xpDao(): XpDao
    abstract fun streakFreezeDao(): StreakFreezeDao
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

        // v4: Eisenhower axes on user tasks + the Deep Focus session log.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_tasks ADD COLUMN urgent INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE user_tasks ADD COLUMN important INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        label TEXT NOT NULL,
                        minutes INTEGER NOT NULL,
                        plannedMinutes INTEGER NOT NULL,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v5: the excused-skip rewrite plus the XP economy.
         *
         * task_completions.done (Boolean) becomes .state (String), because a
         * block now has three outcomes, not two — PENDING, DONE and SKIPPED.
         * SQLite can't retype a column in place, so the table is rebuilt and
         * copied across; every existing row maps to DONE or PENDING, and no
         * history is lost.
         *
         * Index names here must match what Room generates
         * (index_<table>_<col>_<col>) or validation throws at open.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_completions_new (
                        date TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        state TEXT NOT NULL,
                        completedAt INTEGER,
                        skipReason TEXT,
                        PRIMARY KEY(date, taskId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO task_completions_new (date, taskId, state, completedAt, skipReason)
                    SELECT date, taskId,
                           CASE WHEN done = 1 THEN 'DONE' ELSE 'PENDING' END,
                           completedAt, NULL
                    FROM task_completions
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE task_completions")
                db.execSQL("ALTER TABLE task_completions_new RENAME TO task_completions")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS xp_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        ts INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        refId TEXT NOT NULL,
                        amount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_xp_events_date ON xp_events (date)")
                // The idempotency guarantee: one award per (kind, refId, date).
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_xp_events_kind_refId_date " +
                        "ON xp_events (kind, refId, date)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS streak_freezes (
                        usedForDate TEXT PRIMARY KEY NOT NULL,
                        usedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ts INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        text TEXT NOT NULL,
                        cardType TEXT,
                        cardJson TEXT,
                        toolCallsJson TEXT,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_ts ON chat_messages (ts)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bewsoa_flow.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { instance = it }
            }
    }
}
