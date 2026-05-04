package com.huoyejia.ai

import com.huoyejia.data.local.NoteEntity
import com.huoyejia.domain.AnimationScene
import com.huoyejia.domain.ExplainPack
import com.huoyejia.domain.ExplainSlide
import com.huoyejia.domain.NoteAiResult
import com.huoyejia.domain.RelationAiResult
import com.huoyejia.domain.ReviewCardDraft
import kotlin.math.abs

class MockBlueLMAdapter : BlueLMAdapter {
    override val providerName: String = "Mock BlueLM"
    override val remoteReady: Boolean = false

    override suspend fun enrichNote(noteContent: String, maxSimilarity: Float): NoteAiResult {
        val topic = detectTopic(noteContent)
        val tags = detectTags(noteContent, topic)
        val summary = noteContent
            .replace(Regex("\\s+"), " ")
            .take(72)
            .let { if (noteContent.length > 72) "$it..." else it }
        val importance = when {
            noteContent.contains("原因") || noteContent.contains("方法") -> 0.86f
            noteContent.contains("地图") || noteContent.contains("课堂") -> 0.74f
            else -> 0.62f
        }
        return NoteAiResult(
            summary = summary.ifBlank { "这条收藏需要结合上下文复习。" },
            tags = tags,
            topic = topic,
            importance = importance,
            duplicateScore = maxSimilarity.coerceIn(0f, 1f)
        )
    }

    override suspend fun embed(text: String): FloatArray {
        val vector = FloatArray(48)
        val tokens = Regex("[\\u4E00-\\u9FA5A-Za-z0-9]+")
            .findAll(text.lowercase())
            .flatMap { match -> expandToken(match.value) }
        tokens.forEach { token ->
            val index = abs(token.hashCode()) % vector.size
            vector[index] += 1f + token.length / 10f
        }
        return vector
    }

    override suspend fun classifyRelation(a: NoteEntity, b: NoteEntity, similarity: Float): RelationAiResult? {
        if (similarity < 0.18f) return null
        val merged = "${a.noteContent} ${b.noteContent}"
        val relation = when {
            merged.contains("相反") || merged.contains("但是") || merged.contains("不同") -> "contrast"
            merged.contains("导致") || merged.contains("原因") || merged.contains("影响") -> "cause_effect"
            a.topic == b.topic -> "same_topic"
            similarity > 0.72f -> "similar"
            else -> "supplement"
        }
        val evidence = when (relation) {
            "cause_effect" -> "两条内容都涉及原因、影响或结果，可形成因果链。"
            "contrast" -> "内容中出现差异或转折信号，适合对比复习。"
            "same_topic" -> "主题一致，可放入同一知识页。"
            "similar" -> "向量相似度较高，可能重复或高度相近。"
            else -> "语义相近但侧重点不同，可互相补充。"
        }
        return RelationAiResult(relation, similarity.coerceIn(0f, 1f), evidence)
    }

    override suspend fun generateReviewCard(
        current: NoteEntity,
        related: List<NoteEntity>,
        relationHint: String
    ): ReviewCardDraft {
        val relatedTopic = related.firstOrNull()?.topic ?: "已有收藏"
        val question = when (relationHint) {
            "contrast" -> "这条内容和「$relatedTopic」在哪个关键判断上不同？这种差异会影响你的结论吗？"
            "cause_effect" -> "如果把这条内容放进因果链，它和「$relatedTopic」分别处在原因、过程还是结果的位置？"
            "same_topic" -> "把这条内容补进「$relatedTopic」主题后，你能得到一个更完整的解释框架吗？"
            else -> "这条新收藏能补充哪一条旧笔记？请用一句话说明它们的联系。"
        }
        val explanation = "不要只复述原文，先找连接点：概念、例子、原因、反例或应用场景。这样收藏会进入可复习的知识结构。"
        val difficulty = if (related.size >= 2) "medium" else "easy"
        return ReviewCardDraft(question, explanation, difficulty, relationHint.ifBlank { "relation" })
    }

    override suspend fun generateExplainPack(current: NoteEntity, related: List<NoteEntity>): ExplainPack {
        val topic = current.topic ?: detectTopic(current.noteContent)
        return ExplainPack(
            noteId = current.noteId,
            title = "${current.sourceTitle} · AI讲解",
            conciseExplanation = "这条内容不是孤立事实，它需要放进 $topic 的旧知识框架里理解，重点看原因、变化和应用。",
            hook = "如果只收藏不回流，这条内容很快就会变成你下次重复收藏的对象。",
            pptOutline = listOf(
                ExplainSlide("先讲它是什么", listOf("一句话定义内容", "指出它对应的学科主题", "说明为什么值得学")),
                ExplainSlide("再讲和旧知识的联系", listOf("它补充了哪条旧笔记", "是因果、对比还是同主题", "为什么不能单独记忆"), icon = "network", animationHint = "push"),
                ExplainSlide("最后讲怎么考怎么用", listOf("可能的题型", "如何迁移到新题目", "一句复习结论"), icon = "target", animationHint = "wipe")
            ),
            animationScenes = listOf(
                AnimationScene("开场引子", "屏幕从收藏列表推进到这条笔记，标出主题和关键词。", "先别急着背，先看它在你知识网里的位置。"),
                AnimationScene("关系展开", "当前笔记和两条旧笔记连线，显示因果或补充关系。", "真正的理解来自连接，而不是多存一条资料。"),
                AnimationScene("记忆落点", "画面收束成三条复习要点卡。", "最后把它压缩成能复述、能迁移的结论。")
            ),
            takeaway = "把新内容放回旧知识网络，再去复习，才会减少数字囤积。",
            provider = providerName
        )
    }

    override suspend fun answerCardQuestion(
        current: NoteEntity,
        related: List<NoteEntity>,
        question: String
    ): String {
        val summary = current.summary ?: current.noteContent.take(120).ifBlank { "这张卡片还没有足够内容。" }
        val relatedText = related.take(2)
            .joinToString("、") { it.sourceTitle }
            .ifBlank { "暂无强关联卡片" }
        return """
            这是本地占位回答：$summary

            你的问题是：$question
            关联参考：$relatedText。

            在 local.properties 配置 LLM_CHAT_BASE_URL、LLM_CHAT_API_KEY、LLM_CHAT_MODEL 后，这里会自动切换为真实 AI 回答。
        """.trimIndent()
    }

    override suspend fun generateSlideImage(prompt: String): ByteArray? = null

    override suspend fun generateAnimationHtml(pack: ExplainPack): String? = null

    private fun detectTopic(content: String): String {
        return when {
            content.contains("二战") || content.contains("凡尔赛") || content.contains("欧洲") -> "历史-二战"
            content.contains("学习") || content.contains("费曼") || content.contains("复习") -> "学习方法"
            content.contains("地图") || content.contains("截图") -> "图像资料"
            content.contains("函数") || content.contains("公式") -> "理科概念"
            else -> "待归类"
        }
    }

    private fun detectTags(content: String, topic: String): List<String> {
        val tags = linkedSetOf(topic)
        listOf("二战", "地图", "学习方法", "费曼", "因果", "对比", "PDF", "课堂").forEach {
            if (content.contains(it)) tags += it
        }
        return tags.take(5)
    }

    private fun expandToken(token: String): Sequence<String> {
        if (token.length <= 2) return sequenceOf(token)
        val grams = (0 until token.length - 1).asSequence().map { token.substring(it, it + 2) }
        return sequenceOf(token) + grams
    }
}
