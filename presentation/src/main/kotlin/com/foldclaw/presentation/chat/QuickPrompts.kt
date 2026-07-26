package com.foldclaw.presentation.chat

/**
 * 快捷任务芯片。刻意不包含 Wi‑Fi/手电筒等控制台已有能力。
 */
data class QuickPrompt(
    val id: String,
    val label: String,
    val instruction: String,
)

object QuickPrompts {
    val ALL: List<QuickPrompt> = listOf(
        QuickPrompt("S1", "打开设置", "打开设置"),
        QuickPrompt("S4", "返回", "返回上一页"),
        QuickPrompt("C1", "设闹钟", "设置一个明天下午三点的闹钟"),
        QuickPrompt("K1", "建日程", "明天下午三点建一个团队会议日程"),
        QuickPrompt("K4", "查天气", "明天北京的天气怎么样"),
        QuickPrompt("A1", "读界面", "读取当前屏幕界面结构"),
        QuickPrompt("D1", "设备状态", "读一下当前设备状态摘要"),
        QuickPrompt("N1", "通知摘要", "摘要一下最近通知"),
        QuickPrompt("F1", "字体调大", "帮我把系统的字体设置得大一些"),
        QuickPrompt("M1", "静音", "帮我把系统设置成静音模式"),
        QuickPrompt("M2", "恢复响铃", "帮我把手机恢复成响铃模式"),
        QuickPrompt("P1", "关Clash代理", "打开clash并关闭代理"),
        QuickPrompt("R1", "记住城市", "记住我常住城市是北京"),
    )
}
