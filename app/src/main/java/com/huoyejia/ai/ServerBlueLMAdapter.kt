package com.huoyejia.ai

import com.huoyejia.BuildConfig
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.domain.AnimationScene
import com.huoyejia.domain.ExplainPack
import com.huoyejia.domain.ExplainSlide
import com.huoyejia.domain.NoteAiResult
import com.huoyejia.domain.RelationAiResult
import com.huoyejia.domain.ReviewCardDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 后端代理适配器 — 所有 AI 调用都转发到自建服务器，
 * 服务器再转发给大模型 API。客户端不再直接持有 API Key。
 */
class ServerBlueLMAdapter(
    private val fallback: BlueLMAdapter
) : BlueLMAdapter {

    private val baseUrl: String = BuildConfig.SERVER_BASE_URL.trimEnd('/')

    override val providerName: String
        get() = "Server · $baseUrl"

    override val remoteReady: Boolean
        get() = baseUrl.isNotBlank() && !baseUrl.contains("YOUR_SERVER_IP")

    override suspend fun enrichNote(noteContent: String, maxSimilarity: Float): NoteAiResult {
        return runServerOrFallback({
            val json = postJson("/api/process-note-enrich", JSONObject().apply {
                put("note_content", noteContent)
                put("max_similarity", maxSimilarity.toDouble())
            })
            NoteAiResult(
                summary = json.optString("summary").ifBlank { noteContent.take(60) },
                tags = json.optJSONArray("tags")?.toStringList() ?: listOf("待归类"),
                topic = json.optString("topic").ifBlank { "待归类" },
                importance = json.optDouble("importance", 0.7).toFloat().coerceIn(0f, 1f),
                duplicateScore = json.optDouble("duplicate_score", maxSimilarity.toDouble()).toFloat().coerceIn(0f, 1f)
            )
        }, { fallback.enrichNote(noteContent, maxSimilarity) })
    }

    override suspend fun embed(text: String): FloatArray {
        return runServerOrFallback({
            val json = postJson("/api/embed", JSONObject().apply {
                put("text", text)
            })
            val arr = json.getJSONArray("embedding")
            FloatArray(arr.length()) { arr.getDouble(it).toFloat() }
        }, { fallback.embed(text) })
    }

    override suspend fun classifyRelation(a: NoteEntity, b: NoteEntity, similarity: Float): RelationAiResult? {
        return runServerOrFallback({
            val json = postJson("/api/classify-relation", JSONObject().apply {
                put("note_a", a.noteContent)
                put("note_b", b.noteContent)
                put("similarity", similarity.toDouble())
            })
            val type = json.optString("relation_type")
            if (type.isBlank() || type == "none") null
            else RelationAiResult(
                relationType = type,
                confidence = json.optDouble("confidence", similarity.toDouble()).toFloat().coerceIn(0f, 1f),
                evidence = json.optString("evidence").ifBlank { "模型判断存在关联。" }
            )
        }, { fallback.classifyRelation(a, b, similarity) })
    }

    override suspend fun generateReviewCard(
        current: NoteEntity,
        related: List<NoteEntity>,
        relationHint: String
    ): ReviewCardDraft {
        return runServerOrFallback({
            val json = postJson("/api/generate-review-card", JSONObject().apply {
                put("current", current.noteContent)
                put("related", JSONArray(related.map { it.noteContent }))
                put("relation_hint", relationHint)
            })
            ReviewCardDraft(
                question = json.optString("question").ifBlank { "这条内容能补充你哪一条旧知识？" },
                explanation = json.optString("explanation").ifBlank { "先说出联系，再解释为什么重要。" },
                difficulty = json.optString("difficulty").ifBlank { "medium" },
                cardType = json.optString("card_type").ifBlank { "relation" }
            )
        }, { fallback.generateReviewCard(current, related, relationHint) })
    }

    override suspend fun generateExplainPack(current: NoteEntity, related: List<NoteEntity>): ExplainPack {
        return runServerOrFallback({
            val json = postJson("/api/generate-explain-pack", JSONObject().apply {
                put("current", current.noteContent)
                put("current_title", current.sourceTitle)
                put("related", JSONArray(related.map { it.noteContent }))
            })
            ExplainPack(
                noteId = current.noteId,
                title = json.optString("title").ifBlank { current.sourceTitle },
                conciseExplanation = json.optString("concise_explanation").ifBlank { "" },
                hook = json.optString("hook").ifBlank { "" },
                pptOutline = json.optJSONArray("ppt_outline")?.toSlides() ?: emptyList(),
                animationScenes = json.optJSONArray("animation_scenes")?.toScenes() ?: emptyList(),
                takeaway = json.optString("takeaway").ifBlank { "" },
                provider = providerName
            )
        }, { fallback.generateExplainPack(current, related) })
    }

    override suspend fun answerCardQuestion(
        current: NoteEntity,
        related: List<NoteEntity>,
        question: String
    ): String {
        return runServerOrFallback({
            val json = postJson("/api/answer-question", JSONObject().apply {
                put("note_id", current.noteId)
                put("current_content", current.noteContent)
                put("current_title", current.sourceTitle)
                put("url", current.url ?: "")
                put("summary", current.summary ?: "")
                put("tags", current.tags)
                put("topic", current.topic ?: "")
                put("related", JSONArray(related.map {
                    JSONObject().apply {
                        put("title", it.sourceTitle)
                        put("summary", it.summary ?: it.noteContent.take(180))
                        put("tags", it.tags)
                    }
                }))
                put("question", question)
            })
            json.optString("answer").ifBlank { "AI 暂时没有返回内容，请重试。" }
        }, { fallback.answerCardQuestion(current, related, question) })
    }

    override suspend fun generateSlideImage(prompt: String): ByteArray? {
        return runServerOrFallback({
            withContext(Dispatchers.IO) {
                val url = URL("$baseUrl/api/generate-slide-image")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30000
                conn.readTimeout = 120000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("prompt", prompt)
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                stream?.use { it.readBytes() }
            }
        }, { fallback.generateSlideImage(prompt) })
    }

    override suspend fun generateAnimationHtml(pack: ExplainPack): String? {
        return runServerOrFallback({
            val json = postJson("/api/generate-animation-html", JSONObject().apply {
                put("note_id", pack.noteId)
                put("title", pack.title)
                put("explanation", pack.conciseExplanation)
            })
            val html = json.optString("html")
            html.takeIf { it.contains("<html", ignoreCase = true) && it.contains("</html>", ignoreCase = true) }
        }, { fallback.generateAnimationHtml(pack) })
    }

    // ----- internal -----

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("Server request failed: ${conn.responseCode} $body")
        }
        return JSONObject(body)
    }

    private suspend fun <T> runServerOrFallback(serverCall: suspend () -> T, fallbackCall: suspend () -> T): T {
        return if (!remoteReady) {
            fallbackCall()
        } else {
            try {
                serverCall()
            } catch (_: Exception) {
                fallbackCall()
            }
        }
    }
}

private fun JSONArray.toStringList(): List<String> {
    return buildList {
        for (i in 0 until length()) {
            val v = optString(i)
            if (v.isNotBlank()) add(v)
        }
    }
}

private fun JSONArray.toSlides(): List<ExplainSlide> {
    return buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val bullets = item.optJSONArray("bullets")?.let { arr ->
                buildList { for (j in 0 until arr.length()) add(arr.optString(j)) }
            } ?: listOf("补充讲解要点")
            add(ExplainSlide(
                title = item.optString("title").ifBlank { "未命名页" },
                bullets = bullets.ifEmpty { listOf("补充讲解要点") },
                imagePrompt = item.optString("image_prompt"),
                icon = item.optString("icon").ifBlank { "spark" },
                animationHint = item.optString("animation_hint").ifBlank { "fade" }
            ))
        }
    }
}

private fun JSONArray.toScenes(): List<AnimationScene> {
    return buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            add(AnimationScene(
                title = item.optString("title").ifBlank { "场景 ${i + 1}" },
                visual = item.optString("visual").ifBlank { "" },
                narration = item.optString("narration").ifBlank { "" }
            ))
        }
    }
}
