package com.foldclaw.domain.tool

import com.foldclaw.domain.model.Result

data class ResolvedApp(
    val packageName: String,
    val label: String,
)

/** 应用解析与启动。 */
interface AppLaunchBackend {
    fun resolve(appName: String?, packageName: String?): Result<ResolvedApp>
    fun launch(packageName: String): Result<Unit>

    /** 打开系统设置子页：display / font / search / main。 */
    fun openSettingsPage(page: String): Result<ResolvedApp>
}
