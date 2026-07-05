package com.huoyejia.domain

import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.ReviewCardRepository
import com.huoyejia.data.NoteRepository
import com.huoyejia.data.RelationRepository
import com.huoyejia.ai.BlueLMAdapter
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
        val leastReviewedCards = reviewCardRepository.getLeastReviewedCards(count)
        
        for (card in leastReviewedCards) {
            val note = noteRepository.getNote(card.noteId)
            if (note != null) {
                generateReviewCardForNote(note)
            }
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
        val relatedNotes = selectedRelation
            ?.let { relation ->
                val relatedId = if (relation.noteIdFrom == note.noteId) relation.noteIdTo else relation.noteIdFrom
                notesById[relatedId]
            }
            ?.let { listOf(it) }
            .orEmpty()
        val relationHint = selectedRelation?.relationType ?: "single_note"
        
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
    
    fun shouldGenerateToday(): Boolean {
        return true
    }
}

private const val REVIEW_CARD_GENERATOR_MIN_RELATION = 0.70f
