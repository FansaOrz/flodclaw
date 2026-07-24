package com.foldclaw.domain.agent

/**
 * 用户勾选「始终允许此类操作」后记住的工具名。
 * 仅用于非 CRITICAL 之外的审批减免；CRITICAL 工具也可记住，但默认仍会先确认一次。
 */
interface TrustedToolsStore {
    suspend fun isTrusted(toolName: String): Boolean
    suspend fun trust(toolName: String)
    suspend fun revoke(toolName: String)
    suspend fun trustedTools(): Set<String>
}
