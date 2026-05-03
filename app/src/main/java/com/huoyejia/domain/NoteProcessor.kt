package com.huoyejia.domain

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume

class NoteProcessor(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val relationRepository: RelationRepository,
    private val reviewCardRepository: ReviewCardRepository,
    private val blueLM: BlueLMAdapter,
    private val folderRepository: FolderRepository
) {
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
            processedStatus = "NEW",
            readStatus = false,
            reviewedCount = 0,
            folderId = folderId
        )
        noteRepository.upsert(note)
        process(noteId)
        noteId
    }

    suspend fun process(noteId: String) = withContext(Dispatchers.IO) {
        val note = noteRepository.getNote(noteId) ?: return@withContext
        val ocrText = recognizeImageText(note)
        val webText = fetchWebText(note.url)
        val content = normalizeContent(note.rawText.orEmpty(), ocrText, webText)
        val vector = blueLM.embed(content)
        val historical = noteRepository.loadWithEmbeddings(noteId)
        val ranked = historical.map {
            val score = VectorCodec.cosine(vector, VectorCodec.decode(it.vectorBlob))
            RelatedNote(it.note, "similar", score)
        }.sortedByDescending { it.confidence }

        val maxSimilarity = ranked.firstOrNull()?.confidence ?: 0f
        val ai = blueLM.enrichNote(content, maxSimilarity)
        val enriched = note.copy(
            ocrText = ocrText.ifBlank { null },
            noteContent = content,
            summary = ai.summary,
            tags = JsonText.encodeList(ai.tags),
            topic = ai.topic,
            importance = ai.importance,
            duplicateScore = ai.duplicateScore,
            processedStatus = "PROCESSED"
        )
        noteRepository.upsert(enriched)
        noteRepository.saveEmbedding(
            NoteEmbeddingEntity(
                noteId = noteId,
                modelName = "mock-bluelm-embedding",
                vectorDim = vector.size,
                vectorBlob = VectorCodec.encode(vector),
                updatedAt = System.currentTimeMillis()
            )
        )

        val relations = ranked.take(3).mapNotNull { candidate ->
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
        relationRepository.upsertAll(relations)
        createReviewCard(enriched, relations, ranked)
    }

    private suspend fun createReviewCard(
        current: NoteEntity,
        relations: List<NoteRelationEntity>,
        ranked: List<RelatedNote>
    ) {
        val related = ranked.take(3).map { it.note }
        val relationHint = relations.firstOrNull()?.relationType ?: "relation"
        val draft = blueLM.generateReviewCard(current, related, relationHint)
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
                reviewedAt = null
            )
        )
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

    private suspend fun fetchWebText(url: String?): String = withContext(Dispatchers.IO) {
        val target = url?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return@withContext ""
        runCatching {
            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 12000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .htmlToReadableText()
                .take(10000)
        }.getOrDefault("")
    }

    private fun String.htmlToReadableText(): String {
        return replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun normalizeContent(raw: String, ocr: String, web: String): String {
        return listOf(
            raw,
            ocr.takeIf { it.isNotBlank() }?.let { "OCR识别结果：\n$it" }.orEmpty(),
            web.takeIf { it.isNotBlank() }?.let { "网页正文抓取：\n$it" }.orEmpty()
        )
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
