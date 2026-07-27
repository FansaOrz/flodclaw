package com.foldclaw.domain.tool

import com.foldclaw.domain.model.ToolDescriptor

object GetUiTreeTool {
    const val NAME = "get_ui_tree"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "读取当前前台窗口的 UI 树摘要（已优先保留搜索框/可编辑/播放相关节点）。跨 App 操作前应先调用以获取 nodeId。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "maxNodes": { "type": "integer", "description": "最多返回节点数，默认 120" }
  }
}
        """.trimIndent(),
    )
}

object TapNodeTool {
    const val NAME = "tap_node"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "点击 UI 节点。优先传 nodeId（来自 get_ui_tree）；也可传 text 做模糊匹配。禁止点击发送/支付/删除/卸载等敏感按钮。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "nodeId": { "type": "string", "description": "节点 id，如 n12" },
    "text": { "type": "string", "description": "按可见文本/描述匹配，nodeId 优先" }
  }
}
        """.trimIndent(),
    )
}

object TypeTextTool {
    const val NAME = "type_text"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "向可编辑节点输入文本（搜索框/输入框，带 E 标记）。禁止输入密码/验证码/支付信息。可传 nodeId；不传则使用当前第一个可编辑框。输入后若未出结果，再 tap「搜索」或结果项。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "nodeId": { "type": "string", "description": "可编辑节点 id" },
    "text": { "type": "string", "description": "要输入的文本" }
  },
  "required": ["text"]
}
        """.trimIndent(),
    )
}

object SwipeTool {
    const val NAME = "swipe"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "在屏幕上滑动。direction: up/down/left/right。仅当目标可能在列表更下方时使用；播放歌曲/找歌手时禁止反复上下滑首页，应先点「搜索」。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "direction": { "type": "string", "description": "up|down|left|right" },
    "distanceRatio": { "type": "number", "description": "滑动距离占屏比例，默认 0.45" }
  },
  "required": ["direction"]
}
        """.trimIndent(),
    )
}

object GoBackTool {
    const val NAME = "go_back"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "系统返回键。",
        parametersJsonSchema = """{ "type": "object", "properties": {} }""",
    )
}

object GoHomeTool {
    const val NAME = "go_home"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "回到桌面 Home。",
        parametersJsonSchema = """{ "type": "object", "properties": {} }""",
    )
}

/** tap_node 禁止点击的敏感文案（语义风险，不靠工具名）。 */
object SensitiveTapLabels {
    val PATTERNS: List<Regex> = listOf(
        Regex("发送|send", RegexOption.IGNORE_CASE),
        Regex("支付|付款|pay|purchase|buy now|立即购买", RegexOption.IGNORE_CASE),
        Regex("删除|卸[载除]|delete|uninstall|erase", RegexOption.IGNORE_CASE),
        Regex("转账|transfer", RegexOption.IGNORE_CASE),
        Regex("确认付款|确认支付|submit order", RegexOption.IGNORE_CASE),
        Regex("factory.?reset|恢复出厂", RegexOption.IGNORE_CASE),
    )

    fun isSensitive(label: String?): Boolean {
        if (label.isNullOrBlank()) return false
        return PATTERNS.any { it.containsMatchIn(label) }
    }
}
