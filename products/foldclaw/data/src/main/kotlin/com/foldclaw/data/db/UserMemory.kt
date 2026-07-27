package com.foldclaw.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.foldclaw.domain.memory.MemoryItem
import com.foldclaw.domain.memory.MemoryStore
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val epochMs: Long,
)

@Dao
interface UserMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UserMemoryEntity): Long

    @Query("SELECT * FROM user_memories WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): UserMemoryEntity?

    @Query("DELETE FROM user_memories WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM user_memories ORDER BY epochMs DESC LIMIT :limit")
    suspend fun list(limit: Int): List<UserMemoryEntity>
}

@Singleton
class RoomMemoryStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : MemoryStore {
    private val dao get() = FoldClawDatabase.get(context).userMemoryDao()

    override suspend fun upsert(key: String, value: String): MemoryItem {
        val normalizedKey = key.trim().take(64)
        require(normalizedKey.isNotEmpty()) { "key 不能为空" }
        val normalizedValue = value.trim().take(500)
        require(normalizedValue.isNotEmpty()) { "value 不能为空" }
        val existing = dao.findByKey(normalizedKey)
        val entity = UserMemoryEntity(
            id = existing?.id ?: 0,
            key = normalizedKey,
            value = normalizedValue,
            epochMs = System.currentTimeMillis(),
        )
        val id = if (existing != null) {
            dao.insert(entity.copy(id = existing.id))
            existing.id
        } else {
            dao.insert(entity)
        }
        return MemoryItem(id = id, key = normalizedKey, value = normalizedValue, epochMs = entity.epochMs)
    }

    override suspend fun deleteByKey(key: String): Boolean =
        dao.deleteByKey(key.trim()) > 0

    override suspend fun deleteById(id: Long): Boolean =
        dao.deleteById(id) > 0

    override suspend fun list(limit: Int): List<MemoryItem> =
        dao.list(limit.coerceIn(1, 1000)).map {
            MemoryItem(it.id, it.key, it.value, it.epochMs)
        }

    override suspend fun promptBlock(limit: Int): String {
        val items = list(limit)
        if (items.isEmpty()) return ""
        return buildString {
            appendLine("用户已保存的个人记忆（仅来自用户明确要求，可据此个性化，勿编造）：")
            items.forEach { appendLine("- ${it.key}: ${it.value}") }
        }.trimEnd()
    }
}
