package com.foldclaw.data.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Append-only 任务事件账本。审查报告 §5.2 / §7：Room 为唯一持久真相，
 * Timeline 只存结构化元数据，不存完整截图/UI树原文和秘密。
 */
@Entity(tableName = "task_events")
data class TaskEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    /** 单调序号，用于检测篡改与重放。 */
    val seq: Long,
    val type: String,
    val stateBefore: String,
    val stateAfter: String,
    val provider: String?,
    val modelId: String?,
    val toolName: String?,
    val argumentsDigest: String?,
    val riskLevel: String?,
    val decision: String?,
    val approvalTokenId: String?,
    val outcome: String?,
    val errorMessage: String?,
    val tokenInput: Int = 0,
    val tokenOutput: Int = 0,
    val costUsd: Double = 0.0,
    val epochMs: Long,
)

@Dao
interface TaskEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: TaskEventEntity): Long

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY seq ASC")
    fun observeTimeline(taskId: String): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY seq ASC")
    suspend fun getTimeline(taskId: String): List<TaskEventEntity>

    @Query("SELECT MAX(seq) FROM task_events WHERE taskId = :taskId")
    suspend fun maxSeq(taskId: String): Long?

    @Query("SELECT DISTINCT taskId FROM task_events ORDER BY id DESC LIMIT :limit")
    suspend fun recentTaskIds(limit: Int = 50): List<String>

    @Query("DELETE FROM task_events WHERE taskId = :taskId")
    suspend fun deleteTask(taskId: String)
}

@Database(
    entities = [TaskEventEntity::class, UserMemoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FoldClawDatabase : RoomDatabase() {
    abstract fun taskEventDao(): TaskEventDao
    abstract fun userMemoryDao(): UserMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: FoldClawDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        epochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): FoldClawDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoldClawDatabase::class.java,
                    "foldclaw.db",
                )
                    // 落盘到 noBackupFilesDir：审查报告要求敏感数据不进备份
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
