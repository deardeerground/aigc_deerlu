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
        val allNotes = noteRepository.loadAllNotes().filter { it.noteId != note.noteId }
        
        if (allNotes.isEmpty()) return
        
        val relatedNotes = allNotes.take(3)
        val relationHint = relations.firstOrNull()?.relationType ?: "relation"
        
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