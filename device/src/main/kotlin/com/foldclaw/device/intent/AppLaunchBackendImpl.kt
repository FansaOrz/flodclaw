package com.foldclaw.device.intent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.AppLaunchBackend
import com.foldclaw.domain.tool.ResolvedApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLaunchBackendImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLaunchBackend {

    override fun resolve(appName: String?, packageName: String?): Result<ResolvedApp> {
        val rawPkg = packageName?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        val pkgHint = rawPkg?.let { PACKAGE_ALIASES[it] ?: it }
        if (pkgHint != null) {
            val label = labelFor(pkgHint) ?: pkgHint
            if (launchIntent(pkgHint) != null) {
                return Result.Success(ResolvedApp(pkgHint, label))
            }
            // 常见错误包名已 remap 仍失败时，尝试「设置」别名
            if (rawPkg != pkgHint || rawPkg.contains("settings", ignoreCase = true)) {
                ALIASES["设置"]?.let { fallback ->
                    if (launchIntent(fallback) != null) {
                        return Result.Success(ResolvedApp(fallback, labelFor(fallback) ?: "设置"))
                    }
                }
            }
            return Result.Failure(DomainError(ErrorKind.ActionFailed, "未安装包名 $pkgHint"))
        }

        val name = appName?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            ?: return Result.Failure(DomainError(ErrorKind.PolicyDenied, "缺少应用名"))

        ALIASES[name]?.let { aliasPkg ->
            if (launchIntent(aliasPkg) != null) {
                return Result.Success(ResolvedApp(aliasPkg, labelFor(aliasPkg) ?: name))
            }
        }
        ALIASES.entries.firstOrNull { (alias, _) -> name.contains(alias) || alias.contains(name) }?.let { (_, aliasPkg) ->
            if (launchIntent(aliasPkg) != null) {
                return Result.Success(ResolvedApp(aliasPkg, labelFor(aliasPkg) ?: name))
            }
        }

        val matched = findByLabel(name)
        if (matched != null) return Result.Success(matched)

        return Result.Failure(
            DomainError(ErrorKind.ActionFailed, "找不到应用「$name」。请确认已安装，或改用包名。"),
        )
    }

    override fun launch(packageName: String): Result<Unit> {
        return try {
            val intent = launchIntent(packageName)
                ?: return Result.Failure(DomainError(ErrorKind.ActionFailed, "无法启动 $packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "launch ok: $packageName")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "launch failed: $packageName", e)
            Result.Failure(DomainError(ErrorKind.ActionFailed, "打开失败: ${e.message}"))
        }
    }

    override fun openSettingsPage(page: String): Result<ResolvedApp> {
        val key = page.trim().lowercase()
        val candidates = when (key) {
            "font", "fonts", "字体", "字体大小" -> listOf(
                // API 33+；用字面量避免部分编译 classpath 缺常量
                Intent("android.settings.TEXT_READING_SETTINGS"),
                Intent(Settings.ACTION_DISPLAY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            "display", "显示" -> listOf(
                Intent(Settings.ACTION_DISPLAY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            "sound", "audio", "mute", "silent", "声音", "静音", "铃声" -> listOf(
                Intent(Settings.ACTION_SOUND_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            "search", "搜索" -> listOf(
                Intent(Settings.ACTION_APP_SEARCH_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
            "main", "settings", "设置" -> listOf(Intent(Settings.ACTION_SETTINGS))
            else -> listOf(Intent(Settings.ACTION_SETTINGS))
        }
        for (raw in candidates) {
            try {
                val intent = Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) continue
                context.startActivity(intent)
                val pkg = intent.resolveActivity(context.packageManager)?.packageName
                    ?: "com.android.settings"
                Log.i(TAG, "openSettingsPage ok page=$key pkg=$pkg")
                return Result.Success(ResolvedApp(pkg, "设置/$key"))
            } catch (e: Exception) {
                Log.w(TAG, "openSettingsPage candidate failed page=$key", e)
            }
        }
        return Result.Failure(DomainError(ErrorKind.ActionFailed, "无法打开设置页 $page"))
    }

    private fun launchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    private fun labelFor(packageName: String): String? = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        null
    }

    private fun findByLabel(name: String): ResolvedApp? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val exact = apps.firstOrNull {
            it.loadLabel(pm).toString().equals(name, ignoreCase = true)
        }
        val partial = exact ?: apps.firstOrNull {
            it.loadLabel(pm).toString().contains(name, ignoreCase = true)
        }
        val ri = partial ?: return null
        val pkg = ri.activityInfo.packageName
        return ResolvedApp(pkg, ri.loadLabel(pm).toString())
    }

    companion object {
        private const val TAG = "FoldClaw/AppLaunch"

        /** 模型常猜错的包名 → 本机真实包名。 */
        private val PACKAGE_ALIASES: Map<String, String> = mapOf(
            "com.samsung.android.settings" to "com.android.settings",
            "com.samsung.settings" to "com.android.settings",
        )

        /** 常见中文名 → 包名（解析加速；未命中再扫桌面）。 */
        val ALIASES: Map<String, String> = mapOf(
            "淘宝" to "com.taobao.taobao",
            "天猫" to "com.tmall.wireless",
            "微信" to "com.tencent.mm",
            "支付宝" to "com.eg.android.AlipayGphone",
            "抖音" to "com.ss.android.ugc.aweme",
            "京东" to "com.jingdong.app.mall",
            "拼多多" to "com.xunmeng.pinduoduo",
            "美团" to "com.sankuai.meituan",
            "饿了么" to "me.ele",
            "地图" to "com.autonavi.minimap",
            "高德地图" to "com.autonavi.minimap",
            "百度地图" to "com.baidu.BaiduMap",
            "设置" to "com.android.settings",
            "Clash" to "com.github.kr328.clash",
            "clash" to "com.github.kr328.clash",
            "相机" to "com.sec.android.app.camera",
            "时钟" to "com.sec.android.app.clockpackage",
            "日历" to "com.samsung.android.calendar",
            "浏览器" to "com.sec.android.app.sbrowser",
            "Chrome" to "com.android.chrome",
            "电话" to "com.samsung.android.dialer",
            "短信" to "com.samsung.android.messaging",
            "相册" to "com.sec.android.gallery3d",
            "文件" to "com.sec.android.app.myfiles",
            "网易云音乐" to "com.netease.cloudmusic",
            "哔哩哔哩" to "tv.danmaku.bili",
            "B站" to "tv.danmaku.bili",
        )
    }
}
