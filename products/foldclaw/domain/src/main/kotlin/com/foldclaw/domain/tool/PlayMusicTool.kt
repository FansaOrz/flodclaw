package com.foldclaw.domain.tool

import com.foldclaw.domain.model.Result
import com.foldclaw.domain.model.ToolDescriptor

/** 音乐 App 深链/Intent 播放（优先于无障碍点按）。 */
interface MusicPlaybackBackend {
    /**
     * @param query 歌手或歌名，如「陶喆」「普通朋友」
     * @param app 目前支持 netease
     */
    suspend fun play(query: String, app: String = "netease"): Result<String>
}

object PlayMusicTool {
    const val NAME = "play_music"

    val descriptor = ToolDescriptor(
        name = NAME,
        description = "在本地音乐 App 中搜索并播放。优先用于「打开网易云并播放某某的歌」——走官方深链，不要用 tap/swipe 在首页乱点。app 默认 netease（网易云）。query 填歌手或歌名。",
        parametersJsonSchema = """
{
  "type": "object",
  "properties": {
    "query": { "type": "string", "description": "歌手或歌名，如陶喆、普通朋友" },
    "app": { "type": "string", "description": "音乐 App：netease（默认）。暂仅网易云" }
  },
  "required": ["query"]
}
        """.trimIndent(),
    )

    data class Args(
        val query: String,
        val app: String = "netease",
    )
}
