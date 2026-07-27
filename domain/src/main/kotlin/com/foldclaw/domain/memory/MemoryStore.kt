package com.foldclaw.domain.memory

/**
 * 用户明确声明的个人偏好/事实。禁止从 UI/通知自动写入。
 */
data class MemoryItem(
    val id: Long,
    val key: String,
    val value: String,
    val epochMs: Long,
)

interface MemoryStore {
    suspend fun upsert(key: String, value: String): MemoryItem
    suspend fun deleteByKey(key: String): Boolean
    suspend fun deleteById(id: Long): Boolean
    suspend fun list(limit: Int = 50): List<MemoryItem> // 备份时可传更大 limit
    suspend fun promptBlock(limit: Int = 20): String
}
