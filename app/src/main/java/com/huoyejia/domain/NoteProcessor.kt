package com.huoyejia.domain

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.huoyejia.NoteProcessingScheduler
import com.huoyejia.ai.BlueLMAdapter
import com.huoyejia.data.NoteRepository
import com.huoyejia.data.RelationRepository
import com.huoyejia.data.ReviewCardRepository
import com.huoyejia.data.FolderRepository
import com.huoyejia.data.local.NoteEmbeddingEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.util.JsonText
import com.huoyejia.util.VectorCodec
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

class NoteProcessor(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val relationRepository: RelationRepository,
    private val reviewCardRepository: ReviewCardRepository,
    private val blueLM: BlueLMAdapter,
    private val folderRepository: FolderRepository,
    private val backgroundScope: CoroutineScope
) {
    private val activeNoteIds = ConcurrentHashMap.newKeySet<String>()
    private val webContentExtractor = WebContentExtractor()
    private val _processingProgress = MutableStateFlow<List<NoteProcessingProgress>>(emptyList())
    val processingProgress: StateFlow<List<NoteProcessingProgress>> = _processingProgress.asStateFlow()

    suspend fun captureAndProcess(
        rawText: String,
        imagePath: String?,
        sourceType: String,
        sourceTitle: String,
        url: String?,
        folderId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val noteId = UUID.randomUUID().toString()
        val note = NoteEntity(
            noteId = noteId,
            rawText = rawText.ifBlank { null },
            imagePath = imagePath,
            sourceType = sourceType,
            sourceTitle = sourceTitle.ifBlank { "未命名收藏" },
            createdAt = now,
            ocrText = null,
            url = url,
            noteContent = rawText,
            summary = null,
            tags = "[]",
            topic = null,
            importance = 0f,
            duplicateScore = 0f,
            processedStatus = "QUEUED",
            readStatus = false,
            reviewedCount = 0,
            folderId = folderId
        )
        noteRepository.upsert(note)
        updateProgress(noteId, note.sourceTitle, ProcessingStage.QUEUED, 0.05f, "已保存，等待整理")
        NoteProcessingScheduler.schedule(context, noteId)
        startProcessing(noteId)
        noteId
    }

    suspend fun process(noteId: String) = withContext(Dispatchers.IO) {
        if (!activeNoteIds.add(noteId)) return@withContext
        val note = noteRepository.getNote(noteId)
        if (note == null) {
            activeNoteIds.remove(noteId)
            return@withContext
        }
        try {
            updateProgress(noteId, note.sourceTitle, ProcessingStage.READ_SOURCE, 0.12f, "准备读取素材")
            noteRepository.updateProcessedStatus(noteId, "PROCESSING")
            updateProgress(noteId, note.sourceTitle, ProcessingStage.READ_SOURCE, 0.22f, "正在读取截图和网页内容")
            val material = buildLearningMaterial(note, note.sourceTitle)
            val content = material.content
            updateProgress(noteId, note.sourceTitle, ProcessingStage.UNDERSTAND, 0.42f, "正在理解内容")
            val vector = blueLM.embed(content, note.imagePath)
            val ranked = rankRelatedNotes(noteId, vector)

            val maxSimilarity = ranked.firstOrNull()?.confidence ?: 0f
            updateProgress(noteId, note.sourceTitle, ProcessingStage.UNDERSTAND, 0.62f, "正在生成摘要和标签")
            val ai = blueLM.enrichNote(content, maxSimilarity)
            if (noteRepository.getNote(noteId) == null) {
                removeProgress(noteId)
                return@withContext
            }
            val enriched = note.copy(
                ocrText = material.ocrText.ifBlank { null },
                noteContent = content,
                summary = ai.summary,
                tags = JsonText.encodeList(sanitizeTags(ai.tags, ai.topic)),
                topic = ai.topic.takeIf { it.isNotBlank() },
                importance = ai.importance,
                duplicateScore = ai.duplicateScore,
                processedStatus = "PROCESSED"
            )
            noteRepository.upsert(enriched)
            if (noteRepository.getNote(noteId) == null) {
                removeProgress(noteId)
                return@withContext
            }
            updateProgress(noteId, note.sourceTitle, ProcessingStage.LINK, 0.76f, "正在保存知识索引")
            noteRepository.saveEmbedding(
                NoteEmbeddingEntity(
                    noteId = noteId,
                    modelName = "multimodal-embedding",
                    vectorDim = vector.size,
                    vectorBlob = VectorCodec.encode(vector),
                    updatedAt = System.currentTimeMillis()
                )
            )

            updateProgress(noteId, note.sourceTitle, ProcessingStage.LINK, 0.86f, "正在查找关联卡片")
            val relations = ranked.take(3).mapNotNull { candidate ->
                if (noteRepository.getNote(noteId) == null || noteRepository.getNote(candidate.note.noteId) == null) {
                    null
                } else {
                    blueLM.classifyRelation(enriched, candidate.note, candidate.confidence)?.let { relation ->
                        NoteRelationEntity(
                            relationId = "${noteId}_${candidate.note.noteId}_${relation.relationType}",
                            noteIdFrom = noteId,
                            noteIdTo = candidate.note.noteId,
                            relationType = relation.relationType,
                            confidence = relation.confidence,
                            evidence = relation.evidence,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            if (noteRepository.getNote(noteId) == null) {
                removeProgress(noteId)
                return@withContext
            }
            relationRepository.upsertAll(relations)
            updateProgress(noteId, note.sourceTitle, ProcessingStage.GENERATE_CARD, 0.94f, "正在生成复习卡")
            createReviewCard(enriched, relations, ranked)
            updateProgress(noteId, note.sourceTitle, ProcessingStage.DONE, 1f, "整理完成", done = true)
            backgroundScope.launch {
                delay(2_500)
                removeProgress(noteId)
            }
        } catch (error: Throwable) {
            if (noteRepository.getNote(noteId) != null) {
                noteRepository.updateProcessedStatus(noteId, "FAILED")
                updateProgress(noteId, note.sourceTitle, ProcessingStage.FAILED, 1f, error.processingFailureMessage("生成失败，稍后会重试"), failed = true)
            } else {
                removeProgress(noteId)
            }
            throw error
        } finally {
            activeNoteIds.remove(noteId)
        }
    }

    suspend fun schedulePendingNotes() = withContext(Dispatchers.IO) {
        noteRepository.loadPendingProcessing().forEach { note ->
            updateProgress(note.noteId, note.sourceTitle, ProcessingStage.QUEUED, 0.05f, "等待继续整理")
            NoteProcessingScheduler.schedule(context, note.noteId)
            startProcessing(note.noteId)
        }
    }

    private fun startProcessing(noteId: String) {
        backgroundScope.launch {
            runCatching { process(noteId) }
        }
    }

    private suspend fun createReviewCard(
        current: NoteEntity,
        relations: List<NoteRelationEntity>,
        ranked: List<RelatedNote>
    ) {
        if (noteRepository.getNote(current.noteId) == null) return
        val relationByTarget = relations.associateBy { it.noteIdTo }
        val related = ranked
            .filter { candidate ->
                val relation = relationByTarget[candidate.note.noteId]
                candidate.confidence >= REVIEW_CARD_RELATED_MIN_SIMILARITY &&
                    relation != null &&
                    relation.confidence >= REVIEW_CARD_RELATED_MIN_RELATION
            }
            .take(1)
            .map { it.note }
        val relationHint = related.firstOrNull()
            ?.let { relationByTarget[it.noteId]?.relationType }
            ?: "single_note"
        val draft = blueLM.generateReviewCard(current, related, relationHint)
        if (noteRepository.getNote(current.noteId) == null) return
        val now = System.currentTimeMillis()
        reviewCardRepository.upsert(
            ReviewCardEntity(
                cardId = UUID.randomUUID().toString(),
                noteId = current.noteId,
                question = draft.question,
                explanation = draft.explanation,
                relatedNoteIds = JsonText.encodeList(related.map { it.noteId }),
                difficulty = draft.difficulty,
                cardType = draft.cardType,
                status = "TODO",
                dueAt = now,
                createdAt = now,
                reviewedAt = null,
                reviewCount = 0
            )
        )
    }

    private suspend fun rankRelatedNotes(noteId: String, vector: FloatArray): List<RelatedNote> {
        return noteRepository.loadNotesWithEmbeddingVectors(noteId)
            .map { item ->
                val similarity = ((VectorCodec.cosine(vector, VectorCodec.decode(item.vectorBlob)) + 1f) / 2f)
                    .coerceIn(0f, 1f)
                RelatedNote(item.note, "similar", similarity)
            }
            .filter { it.confidence >= 0.18f }
            .sortedByDescending { it.confidence }
            .take(10)
    }

    private suspend fun recognizeImageText(note: NoteEntity): String {
        val imagePath = note.imagePath?.takeIf { it.isNotBlank() } ?: return ""
        val image = runCatching {
            val uri = when {
                imagePath.startsWith("content://") || imagePath.startsWith("file://") -> Uri.parse(imagePath)
                else -> Uri.fromFile(File(imagePath))
            }
            InputImage.fromFilePath(context, uri)
        }.getOrNull() ?: return ""
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) continuation.resume(result.text.trim())
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume("")
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    suspend fun reprocessNote(noteId: String) = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: return@withContext
        try {
            updateProgress(noteId, note.sourceTitle, ProcessingStage.READ_SOURCE, 0.12f, "准备重新读取素材")
            noteRepository.updateProcessedStatus(noteId, "PROCESSING")
            relationRepository.deleteForNote(noteId)
            reviewCardRepository.deleteForNote(noteId)
            noteRepository.deleteEmbedding(noteId)
            updateProgress(noteId, note.sourceTitle, ProcessingStage.READ_SOURCE, 0.22f, "正在读取截图和网页内容")
            val material = buildLearningMaterial(note, note.sourceTitle)
            val content = material.content
            updateProgress(noteId, note.sourceTitle, ProcessingStage.UNDERSTAND, 0.42f, "正在理解内容")
            val vector = blueLM.embed(content, note.imagePath)
            val ranked = rankRelatedNotes(noteId, vector)

            val maxSimilarity = ranked.firstOrNull()?.confidence ?: 0f
            updateProgress(noteId, note.sourceTitle, ProcessingStage.UNDERSTAND, 0.62f, "正在生成摘要和标签")
            val ai = blueLM.enrichNote(content, maxSimilarity)
            if (noteRepository.getNote(noteId) == null) {
                removeProgress(noteId)
                return@withContext
            }
            val enriched = note.copy(
                ocrText = material.ocrText.ifBlank { null },
                noteContent = content,
                summary = ai.summary,
                tags = JsonText.encodeList(sanitizeTags(ai.tags, ai.topic)),
                topic = ai.topic.takeIf { it.isNotBlank() },
                importance = ai.importance,
                duplicateScore = ai.duplicateScore,
                processedStatus = "PROCESSED"
            )
            noteRepository.upsert(enriched)
            if (noteRepository.getNote(noteId) == null) {
                removeProgress(noteId)
                return@withContext
            }
            updateProgress(noteId, note.sourceTitle, ProcessingStage.LINK, 0.76f, "正在保存知识索引")
            noteRepository.saveEmbedding(
                NoteEmbeddingEntity(
                    noteId = noteId,
                    modelName = "multimodal-embedding",
                    vectorDim = vector.size,
                    vectorBlob = VectorCodec.encode(vector),
                    updatedAt = System.currentTimeMillis()
                )
            )

            updateProgress(noteId, note.sourceTitle, ProcessingStage.LINK, 0.86f, "正在查找关联卡片")
            val relations = ranked.take(3).mapNotNull { candidate ->
                if (noteRepository.getNote(noteId) == null || noteRepository.getNote(candidate.note.noteId) == null) {
                    null
                } else {
                    blueLM.classifyRelation(enriched, candidate.note, candidate.confidence)?.let { relation ->
                        NoteRelationEntity(
                            relationId = "${noteId}_${candidate.note.noteId}_${relation.relationType}",
                            noteIdFrom = noteId,
                            noteIdTo = candidate.note.noteId,
                            relationType = relation.relationType,
                            confidence = relation.confidence,
                            evidence = relation.evidence,
                            createdAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            if (noteRepository.getNote(noteId) == null) {
                removeProgress(noteId)
                return@withContext
            }
            relationRepository.upsertAll(relations)
            updateProgress(noteId, note.sourceTitle, ProcessingStage.GENERATE_CARD, 0.94f, "正在生成复习卡")
            createReviewCard(enriched, relations, ranked)
            updateProgress(noteId, note.sourceTitle, ProcessingStage.DONE, 1f, "重新生成完成", done = true)
            backgroundScope.launch {
                delay(2_500)
                removeProgress(noteId)
            }
        } catch (error: Throwable) {
            if (noteRepository.getNote(noteId) != null) {
                noteRepository.updateProcessedStatus(noteId, "FAILED")
                updateProgress(noteId, note.sourceTitle, ProcessingStage.FAILED, 1f, error.processingFailureMessage("重新生成失败，稍后会重试"), failed = true)
            } else {
                removeProgress(noteId)
            }
            throw error
        }
    }

    private suspend fun extractWebText(url: String?): String {
        val target = url?.takeIf { it.isNotBlank() } ?: return ""
        return webContentExtractor.extract(target).toAiText()
    }

    private suspend fun buildLearningMaterial(note: NoteEntity, fallbackTitle: String): LearningMaterial {
        val ocrText = recognizeImageText(note)
        val webText = extractWebText(note.url)
        val baseContent = normalizeContent(note.rawText.orEmpty(), ocrText, webText)
        val rawImageDesc = if (note.imagePath.hasLocalImage()) {
            blueLM.describeImage(note.imagePath.orEmpty(), baseContent)
        } else {
            ""
        }
        val (imageDescription, reviewHints) = splitReviewHints(rawImageDesc)
        val ocrLen = ocrText.length
        val imgDescLen = imageDescription.length
        val effectiveBase = if (ocrLen > 0 && imgDescLen > 0 && ocrLen < imgDescLen * 0.5f) {
            normalizeContent(note.rawText.orEmpty(), "", webText)
        } else {
            baseContent
        }
        val rawMerged = normalizeContent(effectiveBase, imageDescription, "")
            .ifBlank { fallbackTitle }
        val polished = if (rawMerged.isNotBlank() && rawMerged != fallbackTitle) {
            runCatching { blueLM.polishRecognitionText(rawMerged) }.getOrDefault(rawMerged)
        } else {
            rawMerged
        }
        val contentWithHints = if (reviewHints.isNotBlank()) {
            "$polished\n<!--EXTRAS-->\n$reviewHints"
        } else {
            polished
        }
        return LearningMaterial(
            content = contentWithHints,
            ocrText = ocrText,
            imageDescription = imageDescription
        )
    }

    private fun splitReviewHints(text: String): Pair<String, String> {
        val markers = listOf("【知识点】", "【复习方向】")
        val firstMarkerIdx = markers.mapNotNull { marker ->
            val idx = text.indexOf(marker)
            if (idx >= 0) idx to marker else null
        }.minByOrNull { it.first }
        if (firstMarkerIdx == null) return Pair(text, "")
        val (splitIdx, _) = firstMarkerIdx
        val description = text.substring(0, splitIdx).trim()
        val extras = text.substring(splitIdx).trim()
        return Pair(description.ifBlank { text }, extras)
    }

    private fun normalizeContent(raw: String, ocr: String, web: String): String {
        return listOf(
            raw,
            ocr,
            web
        )
            .map { it.sanitizeRecognizedText() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun String.sanitizeRecognizedText(): String {
        val clean = trim()
        if (clean.isBlank()) return ""
        val garbledMarks = listOf(
            "锟斤拷", "銆", "�", "Ã", "Â", "Ð", "Ã©", "Ã¼",
            "â\u0080\u0099", "â\u0080\u009C", "â\u0080", "ï¼",
            "ã\u0080", "ä¸", "è¿", "æ\u0098¯"
        )
        val garbledHits = garbledMarks.count { clean.contains(it) }
        val visibleChars = clean.count { !it.isWhitespace() }.coerceAtLeast(1)
        val replacementRatio = clean.count { it == '�' }.toFloat() / visibleChars
        val hasHighByteCorruption = clean.any { it in '\u0080'..'\u009F' }
        return if (garbledHits >= 2 || replacementRatio > 0.04f || (hasHighByteCorruption && replacementRatio > 0.02f)) {
            "未识别成功，原因可能为文本编码异常、网页返回乱码、图片质量过低或模型返回内容不可读。"
        } else {
            clean
        }
    }

    private fun String?.hasLocalImage(): Boolean = !isNullOrBlank()

    private data class LearningMaterial(
        val content: String,
        val ocrText: String,
        val imageDescription: String
    )

    private fun sanitizeTags(tags: List<String>, topic: String): List<String> {
        val cleaned = (tags + topic)
            .map { it.trim().removePrefix("#") }
            .map { tag -> tag.replace(Regex("\\s+"), "") }
            .filter { it.isNotBlank() }
            .map { tag -> if (tag.length > 10) "${tag.take(10)}…" else tag }
            .distinct()
            .take(5)
        return cleaned.ifEmpty { listOf("待归类") }
    }

    private fun updateProgress(
        noteId: String,
        title: String,
        stage: ProcessingStage,
        progress: Float,
        message: String,
        done: Boolean = false,
        failed: Boolean = false
    ) {
        val previous = _processingProgress.value.firstOrNull { it.noteId == noteId }
        val item = NoteProcessingProgress(
            noteId = noteId,
            title = title,
            stage = stage.title,
            progress = progress.coerceIn(0f, 1f),
            message = message,
            steps = buildProgressSteps(stage, failed, previous?.steps.orEmpty()),
            done = done,
            failed = failed
        )
        _processingProgress.value = (_processingProgress.value.filterNot { it.noteId == noteId } + item)
            .sortedBy { it.done }
    }

    private fun removeProgress(noteId: String) {
        _processingProgress.value = _processingProgress.value.filterNot { it.noteId == noteId }
    }

    private fun Throwable.processingFailureMessage(defaultMessage: String): String {
        val message = generateSequence(this) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.contains("远程 AI 摘要失败了") }
        return message ?: defaultMessage
    }
}

private const val REVIEW_CARD_RELATED_MIN_SIMILARITY = 0.78f
private const val REVIEW_CARD_RELATED_MIN_RELATION = 0.70f

private enum class ProcessingStage(val title: String) {
    QUEUED("排队"),
    READ_SOURCE("读取素材"),
    UNDERSTAND("理解"),
    LINK("关联"),
    GENERATE_CARD("生成卡片"),
    DONE("完成"),
    FAILED("失败")
}

data class ProcessingStepProgress(
    val title: String,
    val status: String,
    val progress: Float
)

data class NoteProcessingProgress(
    val noteId: String,
    val title: String,
    val stage: String = "",
    val progress: Float,
    val message: String,
    val steps: List<ProcessingStepProgress> = emptyList(),
    val done: Boolean = false,
    val failed: Boolean = false
)

private fun buildProgressSteps(
    stage: ProcessingStage,
    failed: Boolean,
    previous: List<ProcessingStepProgress>
): List<ProcessingStepProgress> {
    val stages = listOf(
        ProcessingStage.READ_SOURCE,
        ProcessingStage.UNDERSTAND,
        ProcessingStage.LINK,
        ProcessingStage.GENERATE_CARD
    )
    if (failed) {
        val failedIndex = stages.indexOf(stage).takeIf { it >= 0 }
            ?: previous.indexOfLast { it.status == "进行中" || it.status == "失败" }.takeIf { it >= 0 }
            ?: 0
        return stages.mapIndexed { index, item ->
            when {
                index < failedIndex -> ProcessingStepProgress(item.title, "完成", 1f)
                index == failedIndex -> ProcessingStepProgress(item.title, "失败", previous.getOrNull(index)?.progress ?: 0.4f)
                else -> ProcessingStepProgress(item.title, "等待", 0f)
            }
        }
    }
    val currentIndex = when (stage) {
        ProcessingStage.QUEUED -> -1
        ProcessingStage.DONE -> stages.lastIndex + 1
        else -> stages.indexOf(stage)
    }
    return stages.mapIndexed { index, item ->
        when {
            currentIndex > stages.lastIndex || index < currentIndex -> ProcessingStepProgress(item.title, "完成", 1f)
            index == currentIndex -> ProcessingStepProgress(item.title, "进行中", 0.55f)
            else -> ProcessingStepProgress(item.title, "等待", 0f)
        }
    }
}
