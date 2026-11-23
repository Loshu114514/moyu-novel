package com.moyu.novel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

class WebSearch {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim().take(160)
        if (clean.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(clean, "UTF-8")
        val rssUrls = listOf(
            "https://cn.bing.com/search?q=$encoded&format=rss&setlang=zh-hans",
            "https://www.bing.com/search?q=$encoded&format=rss",
        )
        for (url in rssUrls) {
            val results = runCatching { fetchRss(url) }.getOrNull()
            if (!results.isNullOrEmpty()) return@withContext results
        }
        val htmlUrls = listOf(
            "https://cn.bing.com/search?q=$encoded&setlang=zh-hans",
        )
        for (url in htmlUrls) {
            val results = runCatching { fetchHtml(url) }.getOrNull()
            if (!results.isNullOrEmpty()) return@withContext results
        }
        emptyList()
    }

    private fun fetch(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun fetchRss(url: String): List<SearchResult> {
        val xml = fetch(url) ?: return emptyList()
        val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        val results = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()
        for (item in items) {
            val block = item.groupValues[1]
            val title = tag(block, "title")
            val link = tag(block, "link")
            val description = tag(block, "description")
            if (title.isBlank() || link.isBlank()) continue
            if (!link.startsWith("http") || !seen.add(link)) continue
            results += SearchResult(
                title = unescape(title),
                url = unescape(link),
                snippet = unescape(description).take(500),
            )
            if (results.size >= 5) break
        }
        return results
    }

    private fun fetchHtml(url: String): List<SearchResult> {
        val html = fetch(url) ?: return emptyList()
        val blocks = Regex("<li class=\"b_algo\".*?</li>", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
        val results = mutableListOf<SearchResult>()
        for (block in blocks) {
            val value = block.value
            val anchor = Regex(
                "<h2[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                RegexOption.DOT_MATCHES_ALL,
            ).find(value) ?: continue
            val link = anchor.groupValues[1]
            val title = stripTags(anchor.groupValues[2])
            val snippet = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                .find(value)?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
            if (link.startsWith("http")) {
                results += SearchResult(title, link, snippet)
            }
            if (results.size >= 5) break
        }
        return results
    }

    private fun tag(block: String, name: String): String =
        Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL)
            .find(block)?.groupValues?.get(1).orEmpty()

    private fun stripTags(value: String): String =
        unescape(value.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim()

    private fun unescape(value: String): String {
        var text = value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        text = text.replace(Regex("&#(\\d+);")) { match ->
            runCatching { String(Character.toChars(match.groupValues[1].toInt())) }.getOrDefault("")
        }
        return text
    }
}
