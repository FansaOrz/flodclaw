package com.foldclaw.presentation.chat

/**
 * 第5周：快捷 Prompt（只保存指令文案，不引入 YAML/JS）。
 * 对齐 docs/compat-matrix.md 的 12 任务入口。
 */
data class QuickPrompt(
    val id: String,
    val label: String,
    val instruction: String,
)

object QuickPrompts {
    val ALL: List<QuickPrompt> = listOf(
        QuickPrompt("S1", "打开设置", "打开设置"),
        QuickPrompt("S2", "设置界面", "打开设置并看看当前界面有什么"),
        QuickPrompt("S3", "设置搜索", "打开设置，找到搜索框并点进去"),
        QuickPrompt("S4", "返回", "返回上一页"),
        QuickPrompt("C1", "设闹钟", "设置一个明天下午三点的闹钟"),
        QuickPrompt("C2", "打开时钟", "打开时钟"),
        QuickPrompt("C3", "时钟界面", "打开时钟并读一下当前界面"),
        QuickPrompt("K1", "建日程", "明天下午三点建一个团队会议日程"),
        QuickPrompt("K2", "打开日历", "打开日历"),
        QuickPrompt("K3", "日历界面", "打开日历并读一下当前界面"),
        QuickPrompt("K4", "查天气", "明天北京的天气怎么样"),
        QuickPrompt("A1", "读界面", "读取当前屏幕界面结构"),
        QuickPrompt("F1", "字体调大", "帮我把系统的字体设置得大一些"),
        QuickPrompt("M1", "静音", "帮我把系统设置成静音模式"),
        QuickPrompt("M2", "恢复响铃", "帮我把手机恢复成响铃模式"),
        QuickPrompt("P1", "关Clash代理", "打开clash并关闭代理"),
    )
}
