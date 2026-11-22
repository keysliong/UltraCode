package com.ultracode.app.agent

import com.ultracode.app.ai.LlmClient
import com.ultracode.app.data.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder

/** Internet access for the AI: search + reading pages. Every visited site is reported via [onVisit]. */
class WebTools(private val onVisit: (WebSource) -> Unit) {
    private val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private fun clean(html: String): String = decode(html.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim()

    /** HTML entities -> text (works on phone and desktop). */
    private fun decode(s: String): String = s
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("&#[xX]([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        onVisit(WebSource("Поиск: $query", "https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8"), search = true))
        val html = LlmClient.http.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use {
            if (!it.isSuccessful) return@withContext "Поиск недоступен (HTTP ${it.code})"
            it.body?.string().orEmpty()
        }
        val links = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html).toList()
        val snippets = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL).findAll(html).map { clean(it.groupValues[1]) }.toList()
        if (links.isEmpty()) return@withContext "Ничего не найдено по запросу «$query»"
        links.take(8).mapIndexed { i, m ->
            var href = decode(m.groupValues[1])
            Regex("uddg=([^&]+)").find(href)?.let { href = URLDecoder.decode(it.groupValues[1], "UTF-8") }
            if (href.startsWith("//")) href = "https:$href"
            "${i + 1}. ${clean(m.groupValues[2])}\n   $href\n   ${snippets.getOrNull(i) ?: ""}"
        }.joinToString("\n")
    }

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val u = if (url.startsWith("http")) url else "https://$url"
        LlmClient.http.newCall(Request.Builder().url(u).header("User-Agent", ua).build()).execute().use { r ->
            val type = r.header("Content-Type").orEmpty()
            val raw = r.body?.source()?.let { src -> src.request(2_000_000); src.buffer.readUtf8(minOf(src.buffer.size, 2_000_000)) }.orEmpty()
            val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(raw)?.groupValues?.get(1)?.let { clean(it) }?.takeIf { it.isNotBlank() }
            onVisit(WebSource(title ?: u.substringAfter("://").substringBefore('/'), u))
            if (!r.isSuccessful) return@withContext "HTTP ${r.code} при открытии $u"
            val text = if (type.contains("html") || raw.trimStart().startsWith("<")) {
                clean(
                    raw.replace(Regex("<(script|style|noscript|svg)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                        .replace(Regex("<(br|p|div|li|h[1-6]|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n<$1>"),
                )
            } else raw
            "Страница: ${title ?: u}\nURL: $u\n\n" + text.take(15000)
        }
    }
}
