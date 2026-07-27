package com.foldclaw.domain.backup

import com.foldclaw.domain.memory.MemoryItem

/**
 * 用户侧可迁移备份（不含 API Key 密文：Keystore 重装后无法解密）。
 */
data class AppBackupPayload(
    val formatVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val packageHint: String,
    val memories: List<MemoryItem>,
    val settings: BackupSettings?,
)

data class BackupSettings(
    val useRealApi: Boolean,
    val preset: String,
    val workspaceId: String,
    val baseUrl: String,
    val modelId: String,
)

interface DataBackupService {
    suspend fun exportJson(): String
    suspend fun importJson(json: String, mergeMemories: Boolean = true): BackupImportResult
}

data class BackupImportResult(
    val memoriesUpserted: Int,
    val settingsApplied: Boolean,
)
