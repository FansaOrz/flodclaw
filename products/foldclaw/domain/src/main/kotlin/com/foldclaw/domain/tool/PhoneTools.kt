package com.foldclaw.domain.tool

import com.foldclaw.domain.model.ToolDescriptor

/**
 * 打开本机已安装应用。按中文名/包名解析后 startActivity。
 */
object OpenAppTool {
    const val NAME = "open_app"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "打开手机上已安装的应用。优先传 appName（如「淘宝」「微信」），也可直接传 packageName。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "appName": { "type": "string", "description": "应用显示名，如淘宝、微信、设置" },
    "packageName": { "type": "string", "description": "Android 包名，可选；已知时更准确" }
  }
}
        """.trimIndent(),
    )

    data class Args(
        val appName: String?,
        val packageName: String?,
    )
}

/**
 * 查询城市天气（只读）。按城市名 + 相对天数返回摘要文本。
 */
object GetWeatherTool {
    const val NAME = "get_weather"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "查询指定城市的天气预报。dayOffset=0 今天，1 明天，2 后天。返回温度、降水与天气概况，供你回复用户。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "city": { "type": "string", "description": "城市名，如北京、上海、深圳" },
    "dayOffset": { "type": "integer", "description": "相对今天的天数，0=今天，1=明天，默认 0" }
  },
  "required": ["city"]
}
        """.trimIndent(),
    )

    data class Args(
        val city: String,
        val dayOffset: Int = 0,
    )
}
