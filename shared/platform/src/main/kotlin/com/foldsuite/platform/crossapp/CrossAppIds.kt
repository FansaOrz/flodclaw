package com.foldsuite.platform.crossapp

/**
 * 已发布 App 的 applicationId（release）。
 * debug 变体通常追加 `.debug`（见 fold.android.application 约定）。
 *
 * 禁止在业务代码里散落魔法 package 字符串；跨 App 跳转一律引用此处。
 */
object CrossAppIds {
    const val FOLDCLAW = "com.foldclaw"
    const val AIRPODS = "com.foldpods"

    fun debugId(releaseId: String): String = "$releaseId.debug"
}
