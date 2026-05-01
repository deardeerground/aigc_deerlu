package com.huoyejia.util

object JsonText {
    fun encodeList(items: List<String>): String {
        return items.joinToString(prefix = "[", postfix = "]") { item ->
            "\"" + item.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }

    fun decodeList(json: String): List<String> {
        return json.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }
}
