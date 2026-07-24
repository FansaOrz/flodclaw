package com.foldclaw.agent

import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.Tool
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome

/**
 * 工具注册表。集中所有可用工具及其风险等级与描述。
 * 模型拿到的 tools 列表 = 注册表里所有 [descriptor]。
 */
class ToolRegistry {
    private val tools: MutableMap<String, ToolEntry> = mutableMapOf()

    fun register(tool: Tool) {
        tools[tool.descriptor.name] = ToolEntry(tool, tool.riskLevel)
    }

    fun get(name: String): ToolEntry? = tools[name]

    fun descriptors(): List<ToolDescriptor> = tools.values.map { it.tool.descriptor }

    suspend fun execute(ctx: ToolContext, name: String, argumentsJson: String): Result<ToolOutcome> {
        val entry = tools[name]
            ?: return Result.Failure(com.foldclaw.domain.model.DomainError(
                com.foldclaw.domain.model.ErrorKind.Unknown, "未知工具: $name"))
        return entry.tool.execute(ctx, argumentsJson)
    }
}

data class ToolEntry(
    val tool: Tool,
    val riskLevel: RiskLevel,
)
