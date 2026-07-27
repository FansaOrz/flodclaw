package com.foldsuite.platform.crossapp

/**
 * 构造指向另一 App 的显式包名 Intent 参数（纯数据，无 Android 依赖）。
 * App 层用 PackageManager + Intent.setPackage 组装，并把 [sourceApp] 写入 EXTRA_SOURCE_APP。
 */
data class CrossAppLaunch(
    val targetPackage: String,
    val action: String,
    val sourceApp: String,
    val deeplink: String? = null,
)

object CrossAppLauncher {
    fun openFoldClaw(from: String = CrossAppIds.AIRPODS, debug: Boolean = false): CrossAppLaunch =
        CrossAppLaunch(
            targetPackage = if (debug) CrossAppIds.debugId(CrossAppIds.FOLDCLAW) else CrossAppIds.FOLDCLAW,
            action = CrossAppIntents.ACTION_OPEN_FOLDCLAW,
            sourceApp = from,
        )

    fun openAirPods(from: String = CrossAppIds.FOLDCLAW, debug: Boolean = false): CrossAppLaunch =
        CrossAppLaunch(
            targetPackage = if (debug) CrossAppIds.debugId(CrossAppIds.AIRPODS) else CrossAppIds.AIRPODS,
            action = CrossAppIntents.ACTION_OPEN_AIRPODS,
            sourceApp = from,
        )
}
