package com.foldclaw.agent.tools

import com.foldclaw.domain.device.DeviceController
import com.foldclaw.domain.device.GlobalAction
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.ObservationSnapshot
import com.foldclaw.domain.model.Rect
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor
import com.foldclaw.domain.model.UiNode
import com.foldclaw.domain.tool.GetUiTreeTool
import com.foldclaw.domain.tool.GoBackTool
import com.foldclaw.domain.tool.GoHomeTool
import com.foldclaw.domain.tool.RiskLevel
import com.foldclaw.domain.tool.SensitiveTapLabels
import com.foldclaw.domain.tool.SwipeTool
import com.foldclaw.domain.tool.TapNodeTool
import com.foldclaw.domain.tool.Tool
import com.foldclaw.domain.tool.ToolContext
import com.foldclaw.domain.tool.ToolOutcome
import com.foldclaw.domain.tool.TypeTextTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class GetUiTreeToolImpl(
    private val device: DeviceController,
) : Tool {
    override val descriptor: ToolDescriptor = GetUiTreeTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.READ_ONLY

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        if (!device.isAvailable()) {
            return Result.Success(
                ToolOutcome.Failure(
                    DomainError(ErrorKind.DeviceCapabilityMissing, "请先开启 FoldClaw 无障碍服务"),
                ),
            )
        }
        val maxNodes = parseMaxNodes(argumentsJson)
        return when (val obs = device.observe()) {
            is Result.Failure -> Result.Success(ToolOutcome.Failure(obs.error))
            is Result.Success -> {
                val snap = obs.data
                if (snap.isSecureWindow && snap.nodes.isEmpty()) {
                    Result.Success(ToolOutcome.Text("当前窗口可能受 FLAG_SECURE 保护，无法读取 UI 树。"))
                } else {
                    val text = snap.toModelContext(maxNodes = maxNodes)
                    Result.Success(ToolOutcome.Text(text.ifBlank { "UI 树为空（包名=${snap.packageName}）" }))
                }
            }
        }
    }

    private fun parseMaxNodes(json: String): Int = try {
        val o = Json.decodeFromString<JsonObject>(json.ifBlank { "{}" })
        o["maxNodes"]?.jsonPrimitive?.longOrNull?.toInt()?.coerceIn(20, 300) ?: 120
    } catch (_: Exception) {
        120
    }
}

class TapNodeToolImpl(
    private val device: DeviceController,
) : Tool {
    override val descriptor: ToolDescriptor = TapNodeTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        if (!device.isAvailable()) {
            return Result.Success(
                ToolOutcome.Failure(DomainError(ErrorKind.DeviceCapabilityMissing, "无障碍未连接")),
            )
        }
        val nodeIdArg = stringArg(argumentsJson, "nodeId")
        val textArg = stringArg(argumentsJson, "text")
        val snap = device.observe().getOrNull()
            ?: return Result.Success(
                ToolOutcome.Failure(DomainError(ErrorKind.ActionFailed, "无法观察当前界面")),
            )
        if (ctx.allowedPackages.isNotEmpty() &&
            snap.packageName != null &&
            snap.packageName !in ctx.allowedPackages
        ) {
            return Result.Success(
                ToolOutcome.Failure(
                    DomainError(ErrorKind.PolicyDenied, "当前包 ${snap.packageName} 不在能力信封白名单"),
                ),
            )
        }
        val node = resolveNode(snap, nodeIdArg, textArg)
            ?: return Result.Success(
                ToolOutcome.Failure(DomainError(ErrorKind.ActionFailed, "找不到目标节点")),
            )
        val label = listOfNotNull(node.text, node.contentDescription).firstOrNull { !it.isNullOrBlank() }
        if (SensitiveTapLabels.isSensitive(label)) {
            return Result.Success(
                ToolOutcome.Failure(
                    DomainError(ErrorKind.PolicyDenied, "禁止点击敏感控件「$label」；请用户手动完成"),
                ),
            )
        }
        return when (val res = device.clickNode(node.id)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已点击 [${node.id}] ${label ?: node.resourceId ?: ""}".trim(),
                    expectedPackageNames = snap.packageName?.let { setOf(it) } ?: emptySet(),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = false,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }
}

class TypeTextToolImpl(
    private val device: DeviceController,
) : Tool {
    override val descriptor: ToolDescriptor = TypeTextTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val text = stringArg(argumentsJson, "text")
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "缺少 text"))
        if (text.isBlank()) {
            return Result.Failure(DomainError(ErrorKind.PolicyDenied, "文本为空"))
        }
        val nodeIdArg = stringArg(argumentsJson, "nodeId")
        val snap = device.observe().getOrNull()
            ?: return Result.Success(
                ToolOutcome.Failure(DomainError(ErrorKind.ActionFailed, "无法观察当前界面")),
            )
        val node = when {
            !nodeIdArg.isNullOrBlank() -> snap.nodes[nodeIdArg]
            else -> snap.nodes.values.firstOrNull { it.isEditable && !it.isPassword }
        } ?: return Result.Success(
            ToolOutcome.Failure(DomainError(ErrorKind.ActionFailed, "找不到可编辑节点")),
        )
        if (node.isPassword) {
            return Result.Success(
                ToolOutcome.Failure(DomainError(ErrorKind.SecretBlocked, "禁止向密码框输入")),
            )
        }
        return when (val res = device.setText(node.id, text)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已在 [${node.id}] 输入 ${text.take(40)}",
                    expectedPackageNames = snap.packageName?.let { setOf(it) } ?: emptySet(),
                    expectedText = text.take(20),
                    irreversible = false,
                    launchedByIntent = false,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }
}

class SwipeToolImpl(
    private val device: DeviceController,
) : Tool {
    override val descriptor: ToolDescriptor = SwipeTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> {
        val direction = stringArg(argumentsJson, "direction")?.lowercase()
            ?: return Result.Failure(DomainError(ErrorKind.ProviderInvalidResponse, "缺少 direction"))
        val ratio = numberArg(argumentsJson, "distanceRatio")?.toFloat()?.coerceIn(0.2f, 0.8f) ?: 0.45f
        // 以常见 Fold 内屏近似中心区域滑动；无精确 DisplayMetrics 时用固定逻辑坐标
        val w = 1080
        val h = 2200
        val cx = w / 2
        val cy = h / 2
        val dx = (w * ratio).toInt()
        val dy = (h * ratio).toInt()
        val (start, end) = when (direction) {
            "up" -> Rect(cx, cy + dy / 2, cx + 1, cy + dy / 2 + 1) to Rect(cx, cy - dy / 2, cx + 1, cy - dy / 2 + 1)
            "down" -> Rect(cx, cy - dy / 2, cx + 1, cy - dy / 2 + 1) to Rect(cx, cy + dy / 2, cx + 1, cy + dy / 2 + 1)
            "left" -> Rect(cx + dx / 2, cy, cx + dx / 2 + 1, cy + 1) to Rect(cx - dx / 2, cy, cx - dx / 2 + 1, cy + 1)
            "right" -> Rect(cx - dx / 2, cy, cx - dx / 2 + 1, cy + 1) to Rect(cx + dx / 2, cy, cx + dx / 2 + 1, cy + 1)
            else -> return Result.Failure(DomainError(ErrorKind.PolicyDenied, "direction 无效: $direction"))
        }
        return when (val res = device.swipe(start, end, 300)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已向 $direction 滑动",
                    expectedPackageNames = emptySet(),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = false,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
    }
}

class GoBackToolImpl(
    private val device: DeviceController,
) : Tool {
    override val descriptor: ToolDescriptor = GoBackTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> =
        when (val res = device.globalAction(GlobalAction.BACK)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已执行返回",
                    expectedPackageNames = emptySet(),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = false,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
}

class GoHomeToolImpl(
    private val device: DeviceController,
) : Tool {
    override val descriptor: ToolDescriptor = GoHomeTool.descriptor
    override val riskLevel: RiskLevel = RiskLevel.REVERSIBLE_SIDE_EFFECT

    override suspend fun execute(ctx: ToolContext, argumentsJson: String): Result<ToolOutcome> =
        when (val res = device.globalAction(GlobalAction.HOME)) {
            is Result.Success -> Result.Success(
                ToolOutcome.SideEffect(
                    summary = "已回到桌面",
                    expectedPackageNames = emptySet(),
                    expectedText = null,
                    irreversible = false,
                    launchedByIntent = false,
                ),
            )
            is Result.Failure -> Result.Success(ToolOutcome.Failure(res.error))
        }
}

private fun stringArg(json: String, key: String): String? = try {
    val o = Json.decodeFromString<JsonObject>(json.ifBlank { "{}" })
    o[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
} catch (_: Exception) {
    null
}

private fun numberArg(json: String, key: String): Double? = try {
    val o = Json.decodeFromString<JsonObject>(json.ifBlank { "{}" })
    o[key]?.jsonPrimitive?.doubleOrNull
} catch (_: Exception) {
    null
}

private fun resolveNode(snap: ObservationSnapshot, nodeId: String?, text: String?): UiNode? {
    if (!nodeId.isNullOrBlank()) {
        snap.nodes[nodeId]?.let { return it }
    }
    if (!text.isNullOrBlank()) {
        val q = text.trim()
        return snap.nodes.values.firstOrNull {
            it.text?.contains(q, ignoreCase = true) == true ||
                it.contentDescription?.contains(q, ignoreCase = true) == true
        } ?: snap.nodes.values.firstOrNull {
            it.resourceId?.contains(q, ignoreCase = true) == true
        }
    }
    return null
}
