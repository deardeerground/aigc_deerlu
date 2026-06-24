package com.huoyejia.ai

import com.huoyejia.data.local.NoteEntity
import com.huoyejia.domain.AnimationScene
import com.huoyejia.domain.ExplainPack
import com.huoyejia.domain.ExplainSlide
import com.huoyejia.domain.NoteAiResult
import com.huoyejia.domain.RelationAiResult
import com.huoyejia.domain.ReviewCardDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RemoteBlueLMAdapter(
    private val config: LlmRuntimeConfig,
    private val fallback: BlueLMAdapter
) : BlueLMAdapter {
    override val providerName: String
        get() {
            if (!config.chatReady && !config.embeddingReady) return fallback.providerName
            val labels = buildList {
                if (config.chatReady) add("Chat ${config.chat.model}")
                if (config.embeddingReady) add("Embedding ${config.embedding.model}")
                if (config.imageReady) add("Image ${config.image.model}")
                if (config.videoReady) add("Video ${config.video.model}")
            }
            return "Remote · ${labels.joinToString(" | ")}"
        }

    override val remoteReady: Boolean
        get() = config.isComplete

    override suspend fun enrichNote(noteContent: String, maxSimilarity: Float): NoteAiResult {
        if (!config.chatReady) {
            throw IllegalStateException("远程 AI 摘要失败了：聊天模型未配置。")
        }
        return runCatching {
            val response = chatJson(
                system = """
                    你是学习收藏助手。必须只返回一个合法 JSON 对象，不要 markdown，不要解释文字。
                    摘要必须进行归纳提炼，禁止直接复制原文或只截取原文开头。
                """.trimIndent(),
                user = """
                    基于以下 note_content 生成结构化结果。
                    JSON schema:
                    {
                      "summary":"用中文归纳核心观点，40到80字，不要照抄原文",
                      "tags":["最多5个中文短标签"],
                      "topic":"一个中文主题",
                      "importance":0-1,
                      "duplicate_score":0-1
                    }
                    max_similarity=$maxSimilarity
                    note_content=$noteContent
                """.trimIndent(),
                forceJsonObject = true
            )
            val summary = response.optString("summary").trim()
            if (summary.isBlank()) {
                throw IllegalStateException("模型返回 JSON 中缺少 summary。")
            }
            NoteAiResult(
                summary = summary,
                tags = response.optJSONArray("tags").toStringList().ifEmpty { listOf("待归类") },
                topic = response.optString("topic").ifBlank { "待归类" },
                importance = response.optDouble("importance", 0.7).toFloat().coerceIn(0f, 1f),
                duplicateScore = response.optDouble("duplicate_score", maxSimilarity.toDouble()).toFloat().coerceIn(0f, 1f)
            )
        }.getOrElse { error ->
            throw IllegalStateException(
                "远程 AI 摘要失败了：${error.message ?: error::class.java.simpleName}",
                error
            )
        }
    }

    override suspend fun embed(text: String): FloatArray {
        return runRemoteOrFallback(
            ready = config.embeddingReady,
            remoteCall = {
                withContext(Dispatchers.IO) {
                    val embeddingPath = config.embedding.path.ifBlank { "/embeddings" }
                    val payload = JSONObject()
                        .put("model", config.embedding.model)
                        .put(
                            "input",
                            if (embeddingPath.contains("multimodal")) {
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", text)
                                    )
                            } else {
                                text
                            }
                        )
                    val response = postJson(config.embedding, embeddingPath, payload)
                    val array = response.getJSONArray("data").getJSONObject(0).getJSONArray("embedding")
                    FloatArray(array.length()) { index -> array.getDouble(index).toFloat() }
                }
            },
            fallbackCall = { fallback.embed(text) }
        )
    }

    override suspend fun classifyRelation(a: NoteEntity, b: NoteEntity, similarity: Float): RelationAiResult? {
        return runRemoteOrFallback(
            ready = config.chatReady,
            remoteCall = {
                val response = chatJson(
                    system = "你是知识关系判断器。只返回 JSON，不要 markdown。",
                    user = """
                        判断两条笔记的关系。
                        返回 JSON:
                        {
                          "relation_type":"similar|supplement|contrast|cause_effect|same_topic|none",
                          "confidence":0-1,
                          "evidence":"一句中文说明"
                        }
                        similarity=$similarity
                        A=${a.noteContent}
                        B=${b.noteContent}
                    """.trimIndent()
                )
                val type = response.optString("relation_type")
                if (type.isBlank() || type == "none") {
                    null
                } else {
                    RelationAiResult(
                        relationType = type,
                        confidence = response.optDouble("confidence", similarity.toDouble()).toFloat().coerceIn(0f, 1f),
                        evidence = response.optString("evidence").ifBlank { "模型判断这两条内容存在可复习关系。" }
                    )
                }
            },
            fallbackCall = { fallback.classifyRelation(a, b, similarity) }
        )
    }

    override suspend fun generateReviewCard(
        current: NoteEntity,
        related: List<NoteEntity>,
        relationHint: String
    ): ReviewCardDraft {
        return runRemoteOrFallback(
            ready = config.chatReady,
            remoteCall = {
                val response = chatJson(
                    system = "你是学生学习教练。只返回 JSON，不要 markdown。",
                    user = """
                        基于当前笔记和关联笔记生成一张认知回流卡。
                        优先出联系、对比、因果、迁移类问题，不要纯事实背诵题。
                        返回 JSON:
                        {
                          "question":"问题",
                          "explanation":"<=120字",
                          "difficulty":"easy|medium|hard",
                          "card_type":"relation|contrast|cause_transfer"
                        }
                        relationHint=$relationHint
                        current=${current.noteContent}
                        related=${related.joinToString("\n") { it.noteContent }}
                    """.trimIndent()
                )
                ReviewCardDraft(
                    question = response.optString("question").ifBlank { "这条内容能补充你哪一条旧知识？" },
                    explanation = response.optString("explanation").ifBlank { "先说出联系，再解释为什么重要。" },
                    difficulty = response.optString("difficulty").ifBlank { "medium" },
                    cardType = response.optString("card_type").ifBlank { "relation" }
                )
            },
            fallbackCall = { fallback.generateReviewCard(current, related, relationHint) }
        )
    }

    override suspend fun generateExplainPack(current: NoteEntity, related: List<NoteEntity>): ExplainPack {
        return runRemoteOrFallback(
            ready = config.chatReady,
            remoteCall = {
                val response = chatJson(
                    system = "你是教学动画脚本师和PPT讲解助手。只返回 JSON，不要 markdown。",
                    user = """
                        针对学生笔记生成一个知识讲解包，用于 App 内展示和后续 Remotion/PPT 生成。
                        返回 JSON:
                        {
                          "title":"讲解标题",
                          "hook":"开场一句话",
                          "concise_explanation":"120字以内解释",
                          "ppt_outline":[
                            {
                              "title":"页标题",
                              "bullets":["要点1","要点2","要点3"],
                              "image_prompt":"透明背景教育信息图插画提示词",
                              "icon":"spark|network|target|book|timeline",
                              "animation_hint":"fade|push|wipe"
                            }
                          ],
                          "animation_scenes":[
                            {"title":"场景标题","visual":"画面描述","narration":"旁白"}
                          ],
                          "takeaway":"一句结论"
                        }
                        当前笔记=${current.noteContent}
                        相关笔记=${related.joinToString("\n") { it.noteContent }}
                    """.trimIndent()
                )
                ExplainPack(
                    noteId = current.noteId,
                    title = response.optString("title").ifBlank { current.sourceTitle },
                    conciseExplanation = response.optString("concise_explanation").ifBlank { "这是一条需要和旧知识建立联系后再理解的内容。" },
                    hook = response.optString("hook").ifBlank { "先不要背，先理解它和你已有知识的连接。" },
                    pptOutline = response.optJSONArray("ppt_outline").toSlides(),
                    animationScenes = response.optJSONArray("animation_scenes").toScenes(),
                    takeaway = response.optString("takeaway").ifBlank { "把新收藏放进旧知识结构，才算真正学会。" },
                    provider = providerName
                )
            },
            fallbackCall = { fallback.generateExplainPack(current, related) }
        )
    }

    override suspend fun answerCardQuestion(
        current: NoteEntity,
        related: List<NoteEntity>,
        question: String
    ): String {
        return runRemoteOrFallback(
            ready = config.chatReady,
            remoteCall = {
                withContext(Dispatchers.IO) {
                    val messages = JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put(
                                    "content",
                                    """
                                    你是学习卡片 AI 小助手。
                                    只能基于给定的当前卡片、关联卡片、原文、AI摘要、标签和网址回答。
                                    可以使用关联卡片帮助解释当前卡片的因果、对比、补充关系，但不要引入未给出的外部知识。
                                    回答要清楚、简洁、适合学生理解；优先给结构化要点和可执行复习建议。
                                    如果材料不足，不要编造，请说明还需要什么信息。
                                    """.trimIndent()
                                )
                        )
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    """
                                    当前卡片标题：${current.sourceTitle}
                                    原文网址：${current.url.orEmpty()}
                                    原文截图路径：${current.imagePath.orEmpty()}
                                    原文：${current.rawText ?: current.noteContent}
                                    AI摘要：${current.summary.orEmpty()}
                                    标签JSON：${current.tags}
                                    主题：${current.topic.orEmpty()}

                                    关联卡片：
                                    ${related.joinToString("\n\n") { "- 标题：${it.sourceTitle}\n  摘要：${it.summary ?: it.noteContent.take(180)}\n  标签：${it.tags}" }.ifBlank { "无" }}

                                    用户问题：
                                    $question
                                    """.trimIndent()
                                )
                        )
                    val payload = JSONObject()
                        .put("model", config.chat.model)
                        .put("temperature", 0.3)
                        .put("messages", messages)
                    val response = postJson(config.chat, "/chat/completions", payload)
                    response
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content")
                        .ifBlank { "AI 暂时没有返回内容，请重试。" }
                }
            },
            fallbackCall = { fallback.answerCardQuestion(current, related, question) }
        )
    }

    override suspend fun generateSlideImage(prompt: String): ByteArray? {
        if (!config.imageReady || prompt.isBlank()) return fallback.generateSlideImage(prompt)
        return runCatching {
            withContext(Dispatchers.IO) {
                val payload = JSONObject()
                    .put("model", config.image.model)
                    .put("prompt", prompt)
                    .put("size", config.image.size)
                if (config.image.model.contains("seedream", ignoreCase = true)) {
                    payload
                        .put("sequential_image_generation", "disabled")
                        .put("response_format", "url")
                        .put("stream", false)
                        .put("watermark", config.image.watermark)
                } else {
                    payload.put("background", "transparent")
                }
                val response = postJson(config.image.toEndpoint(), config.image.path, payload)
                val first = response.optJSONArray("data")?.optJSONObject(0)
                val b64 = first?.optString("b64_json").orEmpty()
                if (b64.isNotBlank()) {
                    Base64.decode(b64, Base64.DEFAULT)
                } else {
                    val url = first?.optString("url").orEmpty()
                    if (url.isBlank()) null else downloadBytes(url)
                }
            }
        }.getOrElse { fallback.generateSlideImage(prompt) }
    }

    override suspend fun generateAnimationHtml(pack: ExplainPack): String? {
        if (!config.chatReady) return fallback.generateAnimationHtml(pack)
        return runCatching {
            withContext(Dispatchers.IO) {
                val messages = JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                """
                                你是移动端教学动画导演和前端动画工程师。
                                只返回 JSON，不要 markdown。JSON 字段为 {"html":"完整HTML"}。
                                html 必须是单文件 HTML，包含 CSS 和少量原生 JS；禁止外链、禁止远程资源、禁止 iframe。
                                动画要明显：至少 5 个场景，包含时间轴、节点连线、卡片翻转、关键词高亮、进度条、自动播放和重播按钮。
                                风格参考高级课程短视频，不要只是几个方块移动；中文文案要完整可读。
                                必须适配手机竖屏和横屏，避免文字重叠。
                                """.trimIndent()
                            )
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", pack.animationPrompt())
                    )
                val payload = JSONObject()
                    .put("model", config.chat.model)
                    .put("temperature", 0.7)
                    .put("messages", messages)
                val response = postJson(config.chat, "/chat/completions", payload)
                val content = response
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
                val html = JSONObject(extractJsonObject(content)).optString("html")
                html.takeIf { it.contains("<html", ignoreCase = true) && it.contains("</html>", ignoreCase = true) }
            }
        }.getOrElse { fallback.generateAnimationHtml(pack) }
    }

    private suspend fun chatJson(
        system: String,
        user: String,
        forceJsonObject: Boolean = false
    ): JSONObject {
        return withContext(Dispatchers.IO) {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user))
            val payload = JSONObject()
                .put("model", config.chat.model)
                .put("temperature", 0.2)
                .put("messages", messages)
            if (forceJsonObject) {
                payload.put("response_format", JSONObject().put("type", "json_object"))
            }
            val response = postJson(config.chat, "/chat/completions", payload)
            val content = response
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            JSONObject(extractJsonObject(content))
        }
    }

    private fun postJson(endpoint: LlmEndpointConfig, path: String, payload: JSONObject): JSONObject {
        val connection = URL(endpoint.baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${endpoint.apiKey}")
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("LLM request failed: ${connection.responseCode} $body")
        }
        return JSONObject(body)
    }

    private fun downloadBytes(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        return connection.inputStream.use { it.readBytes() }
    }

    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalStateException("Model response is not valid JSON: $text")
        }
        return trimmed.substring(start, end + 1)
    }

    private suspend fun <T> runRemoteOrFallback(
        ready: Boolean,
        remoteCall: suspend () -> T,
        fallbackCall: suspend () -> T
    ): T {
        return if (ready) {
            runCatching { remoteCall() }.getOrElse { fallbackCall() }
        } else {
            fallbackCall()
        }
    }
}

private fun ImageEndpointConfig.toEndpoint(): LlmEndpointConfig {
    return LlmEndpointConfig(
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model
    )
}

private fun ExplainPack.animationPrompt(): String {
    return """
        请根据以下讲解包生成一个更像真实教学小动画的 HTML。
        标题：$title
        开场：$hook
        一分钟解释：$conciseExplanation
        PPT 页：
        ${pptOutline.joinToString("\n") { "- ${it.title}: ${it.bullets.joinToString("；")}" }}
        动画分镜：
        ${animationScenes.joinToString("\n") { "- ${it.title}: 画面=${it.visual}；旁白=${it.narration}" }}
        结论：$takeaway

        具体要求：
        1. 用 CSS keyframes 和 JS 控制场景自动播放，不需要用户手动翻页。
        2. 至少包含：概念登场、因果/关系展开、对比或误区、应用练习、总结收束。
        3. 每个场景要有不同构图，不能重复同一模板。
        4. 画面元素用 CSS 形状、卡片、线条、标签、图表模拟，不要依赖外部图片。
        5. 最终只返回 JSON：{"html":"..."}。
    """.trimIndent()
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun JSONArray?.toSlides(): List<ExplainSlide> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                ExplainSlide(
                    title = item.optString("title").ifBlank { "未命名页" },
                    bullets = item.optJSONArray("bullets").toStringList().ifEmpty { listOf("补充讲解要点") },
                    imagePrompt = item.optString("image_prompt"),
                    icon = item.optString("icon").ifBlank { "spark" },
                    animationHint = item.optString("animation_hint").ifBlank { "fade" }
                )
            )
        }
    }
}

private fun JSONArray?.toScenes(): List<AnimationScene> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                AnimationScene(
                    title = item.optString("title").ifBlank { "场景 ${index + 1}" },
                    visual = item.optString("visual").ifBlank { "用图示解释当前知识点。" },
                    narration = item.optString("narration").ifBlank { "把抽象概念翻成学生能听懂的话。" }
                )
            )
        }
    }
}
