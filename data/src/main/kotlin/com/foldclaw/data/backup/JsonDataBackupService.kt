package com.foldclaw.data.backup

import android.content.Context
import com.foldclaw.data.prefs.ProviderSettingsStore
import com.foldclaw.domain.backup.BackupImportResult
import com.foldclaw.domain.backup.DataBackupService
import com.foldclaw.domain.memory.MemoryStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class BackupDto(
    val formatVersion: Int = 1,
    val exportedAtEpochMs: Long,
    val packageHint: String,
    val memories: List<MemoryDto> = emptyList(),
    val settings: SettingsDto? = null,
)

@Serializable
internal data class MemoryDto(
    val key: String,
    val value: String,
    val epochMs: Long = 0L,
)

@Serializable
internal data class SettingsDto(
    val useRealApi: Boolean = false,
    val preset: String = "",
    val workspaceId: String = "",
    val baseUrl: String = "",
    val modelId: String = "",
)

@Singleton
class JsonDataBackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryStore: MemoryStore,
    private val settingsStore: ProviderSettingsStore,
) : DataBackupService {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun exportJson(): String {
        val memories = memoryStore.list(500).map {
            MemoryDto(key = it.key, value = it.value, epochMs = it.epochMs)
        }
        val s = settingsStore.current()
        val dto = BackupDto(
            formatVersion = 1,
            exportedAtEpochMs = System.currentTimeMillis(),
            packageHint = context.packageName,
            memories = memories,
            settings = SettingsDto(
                useRealApi = s.useRealApi,
                preset = s.preset,
                workspaceId = s.workspaceId,
                baseUrl = s.baseUrl,
                modelId = s.modelId,
            ),
        )
        return json.encodeToString(dto)
    }

    override suspend fun importJson(jsonText: String, mergeMemories: Boolean): BackupImportResult {
        val dto = json.decodeFromString<BackupDto>(jsonText)
        if (dto.formatVersion > 1) {
            error("备份格式版本 ${dto.formatVersion} 过新，请升级 App 后再导入")
        }
        var upserted = 0
        for (m in dto.memories) {
            val key = m.key.trim()
            val value = m.value.trim()
            if (key.isEmpty() || value.isEmpty()) continue
            if (!mergeMemories) {
                // 覆盖语义：同 key 直接 upsert；不做全表清空，避免误删
            }
            memoryStore.upsert(key, value)
            upserted++
        }
        var settingsApplied = false
        dto.settings?.let { s ->
            if (s.baseUrl.isNotBlank() && s.modelId.isNotBlank()) {
                settingsStore.save(
                    useRealApi = s.useRealApi,
                    baseUrl = s.baseUrl.trim().trimEnd('/'),
                    modelId = s.modelId.trim(),
                    preset = s.preset.ifBlank { settingsStore.current().preset },
                    workspaceId = s.workspaceId,
                )
                settingsApplied = true
            }
        }
        return BackupImportResult(memoriesUpserted = upserted, settingsApplied = settingsApplied)
    }
}
