package com.huoyejia.domain

import com.huoyejia.ai.BlueLMAdapter
import com.huoyejia.data.NoteRepository
import com.huoyejia.data.RelationRepository

class ExplainService(
    private val noteRepository: NoteRepository,
    private val relationRepository: RelationRepository,
    private val blueLM: BlueLMAdapter
) {
    suspend fun generate(noteId: String): ExplainPack? {
        val current = noteRepository.getNote(noteId) ?: return null
        val related = relationRepository.getForNote(noteId)
            .mapNotNull { relation ->
                val otherId = if (relation.noteIdFrom == noteId) relation.noteIdTo else relation.noteIdFrom
                noteRepository.getNote(otherId)
            }
            .distinctBy { it.noteId }
            .take(3)
        return blueLM.generateExplainPack(current, related)
    }
}
