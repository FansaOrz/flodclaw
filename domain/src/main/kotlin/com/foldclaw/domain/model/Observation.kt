package com.foldclaw.domain.model

import kotlinx.serialization.Serializable

/**
 * 不可变 UI 节点快照。业务代码只依赖这个，不直接绑 AccessibilityNodeInfo。
 * 生产实现由 device 模块从真实 A11y 节点翻译；测试用 Fake 构造。
 */
@Serializable
data class UiNode(
    val id: String,
    val parentId: String?,
    val packageName: String?,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isPassword: Boolean,
    /** 开关/复选框等选中态，便于多步 A11y 校验。 */
    val isChecked: Boolean = false,
    val boundsInScreen: Rect?,
    val children: List<String> = emptyList(),
    val actions: Set<String> = emptySet(),
)

@Serializable
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * 一次观察快照。包含窗口归属、包名、UI 树根节点 id 和序列化节点表。
 */
@Serializable
data class ObservationSnapshot(
    val taskId: String,
    val stepIndex: Int,
    val packageName: String?,
    val windowTitle: String?,
    val displayId: Int,
    val isLocked: Boolean,
    val isSecureWindow: Boolean,
    /** 根节点 id。 */
    val rootId: String?,
    /** 节点表，id -> node。 */
    val nodes: Map<String, UiNode>,
    val capturedAtEpochMs: Long,
) {
    fun findNode(predicate: (UiNode) -> Boolean): UiNode? = nodes.values.firstOrNull(predicate)

    /**
     * 把 UI 树裁剪成喂给模型的精简文本。隐藏密码节点，限制深度与文本长度。
     */
    fun toModelContext(maxNodes: Int = 200, maxTextLength: Int = 80): String {
        val sb = StringBuilder()
        sb.append("Package: $packageName | Display: $displayId | Locked: $isLocked\n")
        val visibleNodes = nodes.values
            .filterNot { it.isPassword }
            .take(maxNodes)
        for (node in visibleNodes) {
            val depth = depthOf(node)
            val indent = "  ".repeat(depth.coerceAtMost(8))
            val label = listOfNotNull(
                node.text,
                node.contentDescription,
                node.resourceId,
            ).firstOrNull { it.isNotBlank() }?.take(maxTextLength) ?: ""
            val flags = buildString {
                if (node.isClickable) append("C ")
                if (node.isEditable) append("E ")
                if (node.isChecked) append("ON ")
            }.trim()
            sb.appendLine("$indent- [${node.id}] $label $flags")
        }
        return sb.toString()
    }

    private fun depthOf(node: UiNode): Int {
        var d = 0
        var current = node
        while (current.parentId != null && d < 16) {
            current = nodes[current.parentId] ?: return d
            d++
        }
        return d
    }
}
