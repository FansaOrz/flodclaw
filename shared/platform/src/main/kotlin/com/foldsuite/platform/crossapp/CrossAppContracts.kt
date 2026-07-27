package com.foldsuite.platform.crossapp

/**
 * 文档化：各 App 对外暴露的契约面。
 * 实现落在各自 apps 模块 Manifest；此处只描述约定，不含 Android 依赖。
 */
object CrossAppContracts {
    /**
     * FoldClaw：主界面可响应 [CrossAppIntents.ACTION_OPEN_FOLDCLAW]。
     * AirPods：主界面可响应 [CrossAppIntents.ACTION_OPEN_AIRPODS]。
     *
     * 侧载场景可用同一调试签名；正式分发时应对导出组件做签名校验。
     */
    const val NOTES = "See docs/multi-app.md for install detection and fallback UX."
}
