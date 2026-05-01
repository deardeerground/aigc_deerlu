package com.huoyejia.domain

import com.huoyejia.ai.BlueLMAdapter
import com.huoyejia.data.NoteRepository
import com.huoyejia.data.RelationRepository
import com.huoyejia.data.ReviewCardRepository
import com.huoyejia.data.local.NoteEmbeddingEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.util.JsonText
import com.huoyejia.util.VectorCodec
import java.util.UUID

class NoteProcessor(
    private val noteRepository: NoteRepository,
    private val relationRepository: RelationRepository,
    private val reviewCardRepository: ReviewCardRepository,
    private val blueLM: BlueLMAdapter
) {
    suspend fun captureAndProcess(
        rawText: String,
        imagePath: String?,
        sourceType: String,
        sourceTitle: String,
        url: String?
    ): String {
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
            reviewedCount = 0
        )
        noteRepository.upsert(note)
        process(noteId)
        return noteId
    }

    suspend fun process(noteId: String) {
        val note = noteRepository.getNote(noteId) ?: return
        val ocrText = mockOcr(note)
        val content = normalizeContent(note.rawText.orEmpty(), ocrText)
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

    private fun mockOcr(note: NoteEntity): String {
        if (note.imagePath.isNullOrBlank()) return ""
        return when (note.sourceType) {
            "image" -> "OCR识别：课堂PPT照片，欧洲势力范围变化示意图，二战前地缘压力。"
            "pdf" -> "OCR识别：PDF页脚摘录，关键概念与例题。"
            else -> "OCR识别：截图文本，等待学生回流复习。"
        }
    }

    private fun normalizeContent(raw: String, ocr: String): String {
        return listOf(raw, ocr)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("[\\u0000-\\u001F]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}
