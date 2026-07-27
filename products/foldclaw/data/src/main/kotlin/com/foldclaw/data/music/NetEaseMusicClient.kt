package com.foldclaw.data.music

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.MusicPlaybackBackend
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网易云：公开搜索 API 解析 ID + orpheus:// 深链拉起（已在真机验证可 resolve）。
 * 不依赖无障碍点按；也不是截屏识图。
 */
@Singleton
class NetEaseMusicClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) : MusicPlaybackBackend {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun play(query: String, app: String): Result<String> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) {
                return@withContext Result.Failure(DomainError(ErrorKind.PolicyDenied, "播放关键词为空"))
            }
            val normalizedApp = app.trim().lowercase().ifBlank { "netease" }
            if (normalizedApp !in setOf("netease", "网易云", "网易云音乐", "cloudmusic")) {
                return@withContext Result.Failure(
                    DomainError(ErrorKind.PolicyDenied, "暂仅支持网易云（app=netease）"),
                )
            }
            if (!isInstalled(NETEASE_PKG)) {
                return@withContext Result.Failure(
                    DomainError(ErrorKind.ActionFailed, "未安装网易云音乐（$NETEASE_PKG）"),
                )
            }
            try {
                val song = searchSong(q)
                    ?: return@withContext Result.Failure(
                        DomainError(ErrorKind.ActionFailed, "网易云搜不到「$q」相关歌曲"),
                    )
                val uri = "orpheus://song/${song.id}/?autoplay=1"
                openDeepLink(uri)
                Result.Success(
                    "已通过网易云深链播放「${song.name}」${song.artists?.let { " - $it" } ?: ""}（id=${song.id}）",
                )
            } catch (e: Exception) {
                Log.e(TAG, "play failed q=$q", e)
                Result.Failure(DomainError(ErrorKind.ProviderUnavailable, "音乐播放失败: ${e.message}"))
            }
        }

    private fun searchSong(query: String): SongHit? {
        // type=1 单曲；对「陶喆的歌」类 query 先剥后缀再搜
        val cleaned = query
            .replace(Regex("的歌(曲|单)?$"), "")
            .replace(Regex("^(播放|听|放一下|来首|来一首)"), "")
            .trim()
            .ifBlank { query }

        val songs = searchApi(cleaned, type = 1)
        if (songs.isNotEmpty()) return songs.first()

        // 再按歌手搜，取该歌手热门曲目第一条
        val artists = searchArtists(cleaned)
        val artist = artists.firstOrNull() ?: return null
        return artistTopSong(artist.id) ?: songs.firstOrNull()
    }

    private fun searchApi(keyword: String, type: Int): List<SongHit> {
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("csrf_token", "")
            .addQueryParameter("type", type.toString())
            .addQueryParameter("s", keyword)
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "8")
            .build()
        val body = httpGet(url.toString()) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val songs = root["result"]?.jsonObject?.get("songs")?.jsonArray.orEmpty()
        return songs.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val artists = o["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString(" / ")
            SongHit(id = id, name = name, artists = artists)
        }
    }

    private fun searchArtists(keyword: String): List<ArtistHit> {
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("csrf_token", "")
            .addQueryParameter("type", "100")
            .addQueryParameter("s", keyword)
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "5")
            .build()
        val body = httpGet(url.toString()) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val artists = root["result"]?.jsonObject?.get("artists")?.jsonArray.orEmpty()
        return artists.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ArtistHit(id = id, name = name)
        }
    }

    private fun artistTopSong(artistId: Long): SongHit? {
        val url = "https://music.163.com/api/artist/$artistId".toHttpUrl().newBuilder()
            .build()
        val body = httpGet(url.toString()) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hot = root["hotSongs"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val id = hot["id"]?.jsonPrimitive?.longOrNull ?: return null
        val name = hot["name"]?.jsonPrimitive?.contentOrNull ?: "热门歌曲"
        val artists = hot["ar"]?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString(" / ")
            ?: hot["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString(" / ")
        return SongHit(id = id, name = name, artists = artists)
    }

    private fun openDeepLink(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(NETEASE_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val can = intent.resolveActivity(context.packageManager) != null
        if (!can) {
            // 不设 package 再试一次（部分 ROM 深链解析差异）
            intent.setPackage(null)
        }
        context.startActivity(intent)
        Log.i(TAG, "openDeepLink $uri")
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) FoldClaw/0.1")
            .header("Referer", "https://music.163.com")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    private data class SongHit(val id: Long, val name: String, val artists: String?)
    private data class ArtistHit(val id: Long, val name: String)

    private companion object {
        const val TAG = "FoldClaw/NetEase"
        const val NETEASE_PKG = "com.netease.cloudmusic"
    }
}
