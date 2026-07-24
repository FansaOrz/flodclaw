package com.foldclaw.domain.tool

import com.foldclaw.domain.model.ToolDescriptor

/**
 * 打开系统设置子页（比只 open_app「设置」更接近目标，减少多步搜索）。
 */
object OpenSettingsPageTool {
    const val NAME = "open_settings_page"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "打开系统设置的特定页面。page 可选：display（显示）、font（字体与显示大小）、sound（声音/静音相关页）、search（设置搜索）、main（设置首页）。调字体用 font；若只需改铃声模式请优先用 set_ringer_mode，不要猜错误包名 open_app。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "page": {
      "type": "string",
      "description": "display|font|sound|search|main"
    }
  },
  "required": ["page"]
}
        """.trimIndent(),
    )
}
