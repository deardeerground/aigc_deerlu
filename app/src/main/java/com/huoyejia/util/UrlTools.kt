package com.huoyejia.util

object UrlTools {
    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'，。；、）)\]】>》」』]+|(?:[a-z0-9-]+\.)+[a-z]{2,}(?:/[^\s<>"'，。；、）)\]】>》」』]*)?)""")

    fun extractFirstUrl(text: String): String? {
        val raw = urlRegex.find(text)?.groupValues?.getOrNull(1)?.trim() ?: return null
        return normalizeUrl(raw)
    }

    fun normalizeUrl(value: String): String? {
        val clean = value.trim()
            .trimStart('(', '（', '[', '【', '<', '《', '「', '『')
            .trimEnd('.', ',', ';', '。', '，', '；', '、', ')', '）', ']', '】', '>', '》', '」', '』')
        if (clean.isBlank()) return null
        return when {
            clean.startsWith("http://", ignoreCase = true) -> clean
            clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("www.", ignoreCase = true) -> "https://$clean"
            clean.contains(".") && !clean.contains(" ") -> "https://$clean"
            else -> null
        }
    }
}
