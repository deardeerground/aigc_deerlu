package com.huoyejia.domain

import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteWithEmbedding
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.ReviewCardRepository
import com.huoyejia.data.NoteRepository
import com.huoyejia.data.RelationRepository
import com.huoyejia.ai.BlueLMAdapter
import com.huoyejia.util.VectorCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import com.huoyejia.util.JsonText

class ReviewCardGenerator(
    private val noteRepository: NoteRepository,
    private val reviewCardRepository: ReviewCardRepository,
    private val relationRepository: RelationRepository,
    private val blueLM: BlueLMAdapter
) {
    
    suspend fun generateReviewCardsForLeastReviewed(count: Int = 3) = withContext(Dispatchers.IO) {
        val leastReviewedNotes = noteRepository.loadAllNotes()
            .filter { it.noteContent.isNotBlank() }
            .sortedBy { it.reviewedCount }
            .take(count)
        
        for (note in leastReviewedNotes) {
            generateReviewCardForNote(note)
        }
    }
    
    private suspend fun generateReviewCardForNote(note: NoteEntity) {
        val relations = relationRepository.getForNote(note.noteId)
        val notesById = noteRepository.loadAllNotes()
            .filter { it.noteId != note.noteId }
            .associateBy { it.noteId }
        val selectedRelation = relations
            .filter { it.confidence >= REVIEW_CARD_GENERATOR_MIN_RELATION }
            .maxByOrNull { it.confidence }
        var relatedNotes = selectedRelation
            ?.let { relation ->
                val relatedId = if (relation.noteIdFrom == note.noteId) relation.noteIdTo else relation.noteIdFrom
                notesById[relatedId]
            }
            ?.let { listOf(it) }
            .orEmpty()
        var relationHint = selectedRelation?.relationType ?: "single_note"

        if (relatedNotes.isNotEmpty()) {
            val relatedNote = relatedNotes.first()
            if (!isRelatedByTopic(note, relatedNote) || !isRelatedByVector(note, relatedNote)) {
                relatedNotes = emptyList()
                relationHint = "single_note"
            }
        }

        val draft = blueLM.generateReviewCard(note, relatedNotes, relationHint)
        
        val now = System.currentTimeMillis()
        val newCard = ReviewCardEntity(
            cardId = UUID.randomUUID().toString(),
            noteId = note.noteId,
            question = draft.question,
            explanation = draft.explanation,
            relatedNoteIds = JsonText.encodeList(relatedNotes.map { it.noteId }),
            difficulty = draft.difficulty,
            cardType = draft.cardType,
            status = "TODO",
            dueAt = now,
            createdAt = now,
            reviewedAt = null,
            reviewCount = 0
        )
        
        reviewCardRepository.upsert(newCard)
    }

    private suspend fun isRelatedByTopic(current: NoteEntity, related: NoteEntity): Boolean {
        val currentTopic = current.topic.orEmpty().trim()
        val relatedTopic = related.topic.orEmpty().trim()
        if (currentTopic.isNotEmpty() && relatedTopic.isNotEmpty()) {
            if (currentTopic != relatedTopic) return false
        }
        return true
    }

    private suspend fun isRelatedByVector(current: NoteEntity, related: NoteEntity): Boolean {
        val allEmbeddings = noteRepository.loadNotesWithEmbeddingVectors("")
        val currentVec = allEmbeddings.find { it.note.noteId == current.noteId }?.vectorBlob ?: return true
        val relatedVec = allEmbeddings.find { it.note.noteId == related.noteId }?.vectorBlob ?: return true
        val similarity = ((VectorCodec.cosine(VectorCodec.decode(currentVec), VectorCodec.decode(relatedVec)) + 1f) / 2f)
            .coerceIn(0f, 1f)
        return similarity >= REVIEW_CARD_VECTOR_MIN_SIMILARITY
    }
    
    fun shouldGenerateToday(): Boolean {
        return true
    }
}

private const val REVIEW_CARD_GENERATOR_MIN_RELATION = 0.78f
private const val REVIEW_CARD_VECTOR_MIN_SIMILARITY = 0.75f
