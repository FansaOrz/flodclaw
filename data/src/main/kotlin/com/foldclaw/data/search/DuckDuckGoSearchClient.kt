package com.foldclaw.data.search

import com.foldclaw.domain.model.DomainError
import com.foldclaw.domain.model.ErrorKind
import com.foldclaw.domain.model.Result
import com.foldclaw.domain.tool.WebSearchBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 无需 API Key 的联网搜索：优先 DuckDuckGo Instant Answer，再回退 Lite HTML 摘要。
 */
@Singleton
class DuckDuckGoSearchClient @Inject constructor(
    private val http: OkHttpClient,
) : WebSearchBackend {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String): Result<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) {
            return@withContext Result.Failure(DomainError(ErrorKind.PolicyDenied, "搜索词不能为空"))
        }
        try {
            val instant = instantAnswer(q)
            val htmlSnippets = liteHtmlSnippets(q)
            val parts = buildList {
                if (!instant.isNullOrBlank()) add(instant)
                addAll(htmlSnippets)
            }.distinct().take(8)
            if (parts.isEmpty()) {
                return@withContext Result.Failure(
                    DomainError(ErrorKind.ActionFailed, "未搜到与「$q」相关的结果，请换个关键词或打开浏览器再查"),
                )
            }
            Result.Success(
                buildString {
                    append("搜索「$q」结果：\n")
                    parts.forEachIndexed { i, line ->
                        append("${i + 1}. ")
                        append(line)
                        append('\n')
                    }
                    append("请基于以上结果用中文简洁回答用户；若信息不足请明确说明并建议用户打开浏览器核对。")
                }.trim(),
            )
        } catch (e: Exception) {
            Result.Failure(DomainError(ErrorKind.ProviderUnavailable, "联网搜索失败: ${e.message}"))
        }
    }

    private fun instantAnswer(query: String): String? {
        val url = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("no_html", "1")
            .addQueryParameter("skip_disambig", "1")
            .build()
        val body = httpGet(url.toString()) ?: return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val abstract = obj["AbstractText"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val heading = obj["Heading"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val answer = obj["Answer"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val related = obj["RelatedTopics"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val text = o["Text"]?.jsonPrimitive?.contentOrNull
            text?.takeIf { it.isNotBlank() }
        }.take(3)
        return buildString {
            when {
                !answer.isNullOrBlank() -> append(answer)
                !abstract.isNullOrBlank() -> {
                    if (!heading.isNullOrBlank()) append("$heading：")
                    append(abstract)
                }
            }
            related.forEach {
                if (isNotEmpty()) append('\n')
                append(it)
            }
        }.takeIf { it.isNotBlank() }
    }

    private fun liteHtmlSnippets(query: String): List<String> {
        val url = "https://lite.duckduckgo.com/lite/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val html = httpGet(url.toString()) ?: return emptyList()
        // Lite 结果大致：<a rel="nofollow" href="...">标题</a> 与邻近文本
        val linkRegex = Regex(
            """<a[^>]*rel="nofollow"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val results = mutableListOf<String>()
        for (m in linkRegex.findAll(html)) {
            val href = decodeBasic(m.groupValues[1])
            val title = stripTags(m.groupValues[2]).trim()
            if (title.isBlank()) continue
            if (href.contains("duckduckgo.com", ignoreCase = true) && !href.contains("uddg=")) continue
            val decodedUrl = extractUddg(href) ?: href
            results += "$title — $decodedUrl"
            if (results.size >= 6) break
        }
        return results
    }

    private fun extractUddg(href: String): String? {
        val marker = "uddg="
        val idx = href.indexOf(marker)
        if (idx < 0) return null
        val raw = href.substring(idx + marker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrNull()
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace(Regex("\\s+"), " ")

    private fun decodeBasic(s: String): String =
        s.replace("&amp;", "&")

    private fun httpGet(url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "FoldClaw/0.1 (Android; personal assistant)")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }
}
