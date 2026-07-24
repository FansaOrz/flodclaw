package com.foldclaw.device.a11y

import android.graphics.Rect as AndroidRect
import android.view.accessibility.AccessibilityNodeInfo
import com.foldclaw.domain.model.Rect
import com.foldclaw.domain.model.UiNode
import java.util.concurrent.ConcurrentHashMap

/**
 * 把 AccessibilityNodeInfo 树翻译为不可变 [UiNode] 表，并缓存 live 节点供点击/输入。
 * 不决策风险；下次 translate 会清空旧缓存。
 */
class UiTreeTranslator(
    private val maxNodes: Int = 400,
) {
    private val liveNodes = ConcurrentHashMap<String, AccessibilityNodeInfo>()

    fun clear() {
        liveNodes.values.forEach { runCatching { it.recycle() } }
        liveNodes.clear()
    }

    fun getLive(nodeId: String): AccessibilityNodeInfo? = liveNodes[nodeId]

    data class TranslatedTree(
        val rootId: String?,
        val nodes: Map<String, UiNode>,
    )

    fun translate(root: AccessibilityNodeInfo?): TranslatedTree {
        clear()
        if (root == null) return TranslatedTree(null, emptyMap())
        val out = LinkedHashMap<String, UiNode>()
        var counter = 0
        fun walk(node: AccessibilityNodeInfo, parentId: String?): String? {
            if (out.size >= maxNodes) return null
            val id = "n${counter++}"
            // 缓存可操作副本；子节点继续用当前引用遍历
            liveNodes[id] = AccessibilityNodeInfo.obtain(node)
            val childIds = mutableListOf<String>()
            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    walk(child, id)?.let { childIds.add(it) }
                } finally {
                    child.recycle()
                }
            }
            val bounds = AndroidRect().also { node.getBoundsInScreen(it) }
            out[id] = UiNode(
                id = id,
                parentId = parentId,
                packageName = node.packageName?.toString(),
                className = node.className?.toString(),
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isPassword = node.isPassword,
                boundsInScreen = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                children = childIds,
                actions = actionNames(node),
            )
            return id
        }
        val rootId = walk(root, null)
        return TranslatedTree(rootId, out)
    }

    private fun actionNames(node: AccessibilityNodeInfo): Set<String> {
        val names = mutableSetOf<String>()
        node.actionList?.forEach { action ->
            val label = action.label?.toString()
            if (!label.isNullOrBlank()) names.add(label)
            else when (action.id) {
                AccessibilityNodeInfo.ACTION_CLICK -> names.add("CLICK")
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> names.add("LONG_CLICK")
                AccessibilityNodeInfo.ACTION_SET_TEXT -> names.add("SET_TEXT")
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> names.add("SCROLL_FORWARD")
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> names.add("SCROLL_BACKWARD")
                AccessibilityNodeInfo.ACTION_FOCUS -> names.add("FOCUS")
            }
        }
        return names
    }
}
