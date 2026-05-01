package com.huoyejia.ai

import com.huoyejia.data.local.NoteEntity
import com.huoyejia.domain.ExplainPack
import com.huoyejia.domain.NoteAiResult
import com.huoyejia.domain.RelationAiResult
import com.huoyejia.domain.ReviewCardDraft

interface BlueLMAdapter {
    val providerName: String
    val remoteReady: Boolean

    suspend fun enrichNote(noteContent: String, maxSimilarity: Float): NoteAiResult
    suspend fun embed(text: String): FloatArray
    suspend fun classifyRelation(a: NoteEntity, b: NoteEntity, similarity: Float): RelationAiResult?
    suspend fun generateReviewCard(current: NoteEntity, related: List<NoteEntity>, relationHint: String): ReviewCardDraft
    suspend fun generateExplainPack(current: NoteEntity, related: List<NoteEntity>): ExplainPack
    suspend fun generateSlideImage(prompt: String): ByteArray?
    suspend fun generateAnimationHtml(pack: ExplainPack): String?
}
