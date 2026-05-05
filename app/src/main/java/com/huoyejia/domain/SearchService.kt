package com.huoyejia.domain

import com.huoyejia.ai.BlueLMAdapter
import com.huoyejia.data.NoteRepository

class SearchService(
    private val noteRepository: NoteRepository,
    private val blueLM: BlueLMAdapter
) {
    suspend fun search(query: String, topK: Int = 10): List<ScoredNote> {
        if (query.isBlank()) return emptyList()
        val queryVector = blueLM.embed(query)
        val keywords = extractKeywords(query)
        val imageOnly = query.contains("截图") || query.contains("图片") || query.contains("照片")
        val unreviewedOnly = query.contains("没复习") || query.contains("未复习") || query.contains("还没复习")

        return noteRepository.loadWithEmbeddings()
            .map { note ->
                val text = "${note.sourceTitle} ${note.noteContent} ${note.topic.orEmpty()} ${note.tags}"
                val keywordScore = if (keywords.isEmpty()) 0f else {
                    keywords.count { text.contains(it, ignoreCase = true) }.toFloat() / keywords.size
                }
                val metaScore = buildMetaScore(note.sourceType, note.reviewedCount, imageOnly, unreviewedOnly)
                ScoredNote(
                    note = note,
                    vectorScore = 0f,
                    keywordScore = keywordScore,
                    metaScore = metaScore,
                    finalScore = 0.25f * keywordScore + 0.20f * metaScore
                )
            }
            .filter {
                (!imageOnly || it.note.sourceType == "image") &&
                    (!unreviewedOnly || it.note.reviewedCount == 0)
            }
            .sortedByDescending { it.finalScore }
            .take(topK)
    }

    private fun extractKeywords(query: String): List<String> {
        val dictionaryHits = listOf("二战", "原因", "地图", "截图", "学习方法", "学习", "复习", "费曼", "凡尔赛", "绥靖")
            .filter { query.contains(it) }
        val stopWords = setOf("帮我找", "所有", "关于", "内容", "笔记", "上次", "那张")
        val regexHits = Regex("[\\u4E00-\\u9FA5A-Za-z0-9]+")
            .findAll(query)
            .map { it.value }
            .filter { it.length >= 2 && it !in stopWords }
            .toList()
        return (dictionaryHits + regexHits).distinct()
    }

    private fun buildMetaScore(
        sourceType: String,
        reviewedCount: Int,
        imageOnly: Boolean,
        unreviewedOnly: Boolean
    ): Float {
        var score = 0.2f
        if (imageOnly && sourceType == "image") score += 0.5f
        if (unreviewedOnly && reviewedCount == 0) score += 0.5f
        if (!imageOnly && !unreviewedOnly && reviewedCount == 0) score += 0.2f
        return score.coerceIn(0f, 1f)
    }
}
