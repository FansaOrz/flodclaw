package com.foldsuite.platform.crossapp

/**
 * 跨 App Intent action / extra 约定。
 * 提供方在 Manifest 中声明 exported 组件并处理这些 action；
 * 调用方只依赖本模块构造 Intent，不得 compile 依赖对方 presentation。
 */
object CrossAppIntents {
    const val ACTION_OPEN_FOLDCLAW = "com.foldsuite.action.OPEN_FOLDCLAW"
    const val ACTION_OPEN_AIRPODS = "com.foldsuite.action.OPEN_AIRPODS"

    const val EXTRA_SOURCE_APP = "com.foldsuite.extra.SOURCE_APP"
    const val EXTRA_DEEPLINK = "com.foldsuite.extra.DEEPLINK"
}
