package com.huoyejia.domain

import com.huoyejia.util.UrlTools
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebContentExtractor {
    suspend fun extract(url: String): WebExtractResult = withContext(Dispatchers.IO) {
        val target = UrlTools.normalizeUrl(url) ?: return@withContext WebExtractResult.fallback(url)
        val fallback = WebExtractResult.fallback(target)
        runCatching {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", ANDROID_CHROME_UA)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                setRequestProperty("Connection", "close")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return@runCatching fallback.copy(
                    finalUrl = connection.url?.toString() ?: target,
                    status = WebExtractStatus.Failed,
                    failureReason = "HTTP $code"
                )
            }

            val bytes = connection.readResponseBytes(maxBytes = 1_500_000)
            val charset = connection.responseCharset(bytes)
            val html = bytes.toString(charset)
            val parsed = HtmlArticleParser.parse(html, connection.url?.toString() ?: target)
            val status = when {
                parsed.text.length >= 300 -> WebExtractStatus.Success
                parsed.text.length >= 80 || !parsed.title.isNullOrBlank() -> WebExtractStatus.Partial
                else -> WebExtractStatus.Failed
            }
            parsed.copy(
                inputUrl = target,
                method = WebExtractMethod.NativeHttp,
                status = status,
                failureReason = if (status == WebExtractStatus.Failed) "网页正文过短或为空" else null
            )
        }.getOrElse { error ->
            fallback.copy(
                status = WebExtractStatus.Failed,
                failureReason = error.message ?: error::class.java.simpleName
            )
        }.withFallbackIfEmpty(fallback)
    }

    private fun WebExtractResult.withFallbackIfEmpty(fallback: WebExtractResult): WebExtractResult {
        if (text.isNotBlank() || !title.isNullOrBlank()) return this
        return fallback.copy(status = status, failureReason = failureReason)
    }

    private fun HttpURLConnection.readResponseBytes(maxBytes: Int): ByteArray {
        val decoded = when {
            contentEncoding?.equals("gzip", ignoreCase = true) == true -> GZIPInputStream(inputStream)
            contentEncoding?.equals("deflate", ignoreCase = true) == true -> InflaterInputStream(inputStream)
            else -> inputStream
        }
        return decoded.use { input ->
            val buffer = ByteArray(8 * 1024)
            val out = java.io.ByteArrayOutputStream()
            while (out.size() < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun HttpURLConnection.responseCharset(bytes: ByteArray): Charset {
        val headerCharset = contentType
            ?.let { Regex("(?i)charset=([^;]+)").find(it)?.groupValues?.getOrNull(1) }
            ?.trim()
            ?.trim('"', '\'')
            ?.toCharsetOrNull()
        if (headerCharset != null) return headerCharset

        val prefix = bytes.take(4096).toByteArray().toString(Charsets.ISO_8859_1)
        val metaCharset = Regex("(?is)<meta[^>]+charset\\s*=\\s*[\"']?([^\\s\"'/;>]+)")
            .find(prefix)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.toCharsetOrNull()
        return metaCharset ?: Charsets.UTF_8
    }

    private fun String.toCharsetOrNull(): Charset? {
        return runCatching { Charset.forName(this) }.getOrNull()
    }

    private companion object {
        private const val ANDROID_CHROME_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

data class WebExtractResult(
    val inputUrl: String,
    val finalUrl: String,
    val title: String?,
    val text: String,
    val excerpt: String?,
    val imageUrls: List<String>,
    val method: WebExtractMethod,
    val status: WebExtractStatus,
    val failureReason: String? = null
) {
    fun toAiText(): String {
        return listOfNotNull(
            "学习网址：$finalUrl",
            title?.takeIf { it.isNotBlank() }?.let { "网页标题：$it" },
            excerpt?.takeIf { it.isNotBlank() }?.let { "网页描述：$it" },
            text.takeIf { it.isNotBlank() }?.let { "网页正文：\n$it" },
            if (status == WebExtractStatus.Failed && failureReason != null) "网页读取状态：$failureReason" else null
        ).joinToString("\n")
    }

    companion object {
        fun fallback(url: String): WebExtractResult {
            val normalized = UrlTools.normalizeUrl(url) ?: url
            val uri = runCatching { URI(normalized) }.getOrNull()
            val host = uri?.host.orEmpty().removePrefix("www.")
            val pathText = runCatching {
                URLDecoder.decode(uri?.rawPath.orEmpty(), Charsets.UTF_8.name())
            }.getOrDefault("")
                .replace(Regex("[/_\\-.]+"), " ")
                .trim()
            val text = listOfNotNull(
                host.takeIf { it.isNotBlank() }?.let { "网站：$it" },
                pathText.takeIf { it.length > 1 }?.let { "网址路径关键词：$it" }
            ).joinToString("\n")
            return WebExtractResult(
                inputUrl = normalized,
                finalUrl = normalized,
                title = host.takeIf { it.isNotBlank() },
                text = text,
                excerpt = null,
                imageUrls = emptyList(),
                method = WebExtractMethod.Fallback,
                status = WebExtractStatus.Partial,
                failureReason = "仅保留网址信息"
            )
        }
    }
}

enum class WebExtractMethod {
    NativeHttp,
    Fallback
}

enum class WebExtractStatus {
    Success,
    Partial,
    Failed
}

private object HtmlArticleParser {
    fun parse(html: String, finalUrl: String): WebExtractResult {
        val cleanedHtml = html
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?is)<(script|style|noscript|svg|canvas|iframe)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?is)<(nav|footer|aside|form|button|header)[^>]*>.*?</\\1>"), " ")

        val title = extractTitle(cleanedHtml)
        val description = extractMeta(cleanedHtml, setOf("description", "og:description", "twitter:description"))
        val candidates = extractCandidates(cleanedHtml)
        val paragraphText = extractParagraphs(cleanedHtml)
        val bodyText = Regex("(?is)<body[^>]*>(.*?)</body>")
            .find(cleanedHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.toReadableText()
            .orEmpty()

        val best = (candidates + paragraphText + bodyText)
            .map { it.normalizeArticleText() }
            .filter { it.length >= 40 }
            .distinct()
            .maxByOrNull { scoreArticleText(it) }
            .orEmpty()

        return WebExtractResult(
            inputUrl = finalUrl,
            finalUrl = finalUrl,
            title = title,
            text = best.take(12_000),
            excerpt = description,
            imageUrls = extractImages(cleanedHtml),
            method = WebExtractMethod.NativeHttp,
            status = WebExtractStatus.Success
        )
    }

    private fun extractTitle(html: String): String? {
        val ogTitle = extractMeta(html, setOf("og:title", "twitter:title"))
        if (!ogTitle.isNullOrBlank()) return ogTitle
        return Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.toReadableText()
            ?.normalizeArticleText()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractMeta(html: String, names: Set<String>): String? {
        return Regex("(?is)<meta\\s+([^>]+)>")
            .findAll(html)
            .mapNotNull { match ->
                val attrs = parseAttrs(match.groupValues[1])
                val key = attrs["name"] ?: attrs["property"] ?: return@mapNotNull null
                val content = attrs["content"].orEmpty()
                if (key.lowercase(Locale.ROOT) in names && content.isNotBlank()) content.decodeHtmlEntities() else null
            }
            .firstOrNull()
    }

    private fun extractCandidates(html: String): List<String> {
        val selectors = listOf(
            "article",
            "main",
            "article-content",
            "post-content",
            "entry-content",
            "rich_media_content",
            "content",
            "正文"
        )
        val tagBlocks = Regex("(?is)<(article|main|section|div)\\b([^>]*)>(.*?)</\\1>")
            .findAll(html)
            .mapNotNull { match ->
                val attrs = match.groupValues[2].lowercase(Locale.ROOT)
                val tag = match.groupValues[1].lowercase(Locale.ROOT)
                val matched = tag in setOf("article", "main") || selectors.any { attrs.contains(it) }
                if (matched) match.groupValues[3].toReadableText() else null
            }
            .toList()
        return tagBlocks
    }

    private fun extractParagraphs(html: String): String {
        return Regex("(?is)<p\\b[^>]*>(.*?)</p>")
            .findAll(html)
            .map { it.groupValues[1].toReadableText() }
            .filter { it.length >= 12 }
            .joinToString("\n")
    }

    private fun extractImages(html: String): List<String> {
        return Regex("(?is)<img\\s+[^>]*(?:src|data-src)\\s*=\\s*[\"']([^\"']+)[\"']")
            .findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .take(20)
            .toList()
    }

    private fun scoreArticleText(text: String): Int {
        val punctuation = text.count { it in "，。！？；：,.!?;" }
        val lines = text.lines().count { it.trim().length >= 16 }
        return text.length + punctuation * 8 + lines * 24
    }

    private fun parseAttrs(raw: String): Map<String, String> {
        return Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(['"])(.*?)\2""")
            .findAll(raw)
            .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[3] }
    }

    private fun String.toReadableText(): String {
        return replace(Regex("(?is)<br\\s*/?>"), "\n")
            .replace(Regex("(?is)</(p|div|section|article|h[1-6]|li)>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .decodeHtmlEntities()
            .normalizeArticleText()
    }

    private fun String.normalizeArticleText(): String {
        return replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n")
            .trim()
    }

    private fun String.decodeHtmlEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.let { code -> runCatching { code.toChar().toString() }.getOrNull() } ?: match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { code.toChar().toString() }.getOrNull() } ?: match.value
            }
    }
}
