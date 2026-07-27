package com.foldclaw.domain.tool

import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor

/** 联网搜索后端（实时资讯 / 活动 / 通用问答）。 */
interface WebSearchBackend {
    suspend fun search(query: String): Result<String>
}

/**
 * 联网搜索。演唱会、新闻、实时事实等本地工具无法覆盖的问题用它。
 */
object WebSearchTool {
    const val NAME = "web_search"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "联网搜索实时信息。适用于演唱会/演出、新闻、票务、开放时间、比分、政策等需要最新结果的问题；也适用于你不确定的事实核查。返回若干条标题与摘要，供你整理后用自然语言回答用户。不要用它查天气（请用 get_weather）。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "query": { "type": "string", "description": "搜索关键词，尽量具体，如「北京鸟巢 今天 演唱会」" }
  },
  "required": ["query"]
}
        """.trimIndent(),
    )

    data class Args(val query: String)
}
