package com.foldclaw.domain.tool

import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor

/**
 * 设置系统铃声音量模式（响铃 / 振动 / 静音）。比在设置里点开关更可靠。
 */
object SetRingerModeTool {
    const val NAME = "set_ringer_mode"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "设置手机铃声模式。mode=silent 静音（无声）、vibrate 振动、normal 响铃。用户说「静音」「勿扰」「关掉铃声」时优先用本工具，不要去 open_app 错误的设置包名。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "mode": {
      "type": "string",
      "description": "silent | vibrate | normal"
    }
  },
  "required": ["mode"]
}
        """.trimIndent(),
    )

    data class Args(val mode: String)
}

interface RingerModeBackend {
    fun setMode(mode: String): Result<String>
}
