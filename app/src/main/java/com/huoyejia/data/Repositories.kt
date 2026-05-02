package com.huoyejia.data

import com.huoyejia.data.local.EmbeddingDao
import com.huoyejia.data.local.NoteDao
import com.huoyejia.data.local.NoteEmbeddingEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.NoteWithEmbedding
import com.huoyejia.data.local.RelationDao
import com.huoyejia.data.local.ReviewCardDao
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.local.StatsDao
import com.huoyejia.data.local.UserStatsEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val embeddingDao: EmbeddingDao
) {
    fun observeNotes(): Flow<List<NoteEntity>> = noteDao.observeNotes()

    suspend fun getNote(noteId: String): NoteEntity? = noteDao.getNote(noteId)

    suspend fun loadAllNotes(): List<NoteEntity> = noteDao.loadAllNotes()

    suspend fun countNotes(): Int = noteDao.countNotes()

    suspend fun upsert(note: NoteEntity) = noteDao.upsert(note)

    suspend fun markReviewed(noteId: String) = noteDao.markReviewed(noteId)

    suspend fun saveEmbedding(embedding: NoteEmbeddingEntity) = embeddingDao.upsert(embedding)

    suspend fun deleteNote(noteId: String) {
        embeddingDao.deleteForNote(noteId)
        noteDao.deleteNote(noteId)
    }

    suspend fun loadWithEmbeddings(excludeNoteId: String = ""): List<NoteWithEmbedding> {
        return noteDao.loadWithEmbeddings(excludeNoteId)
    }
}

class RelationRepository(private val dao: RelationDao) {
    fun observeRelations(): Flow<List<NoteRelationEntity>> = dao.observeRelations()

    suspend fun getForNote(noteId: String): List<NoteRelationEntity> = dao.getForNote(noteId)

    suspend fun upsertAll(relations: List<NoteRelationEntity>) = dao.upsertAll(relations)

    suspend fun deleteForNote(noteId: String) = dao.deleteForNote(noteId)
}

class ReviewCardRepository(private val dao: ReviewCardDao) {
    fun observeCards(): Flow<List<ReviewCardEntity>> = dao.observeCards()

    suspend fun upsert(card: ReviewCardEntity) = dao.upsert(card)

    suspend fun markDone(cardId: String) = dao.markDone(cardId, System.currentTimeMillis())

    suspend fun deleteForNote(noteId: String) = dao.deleteForNote(noteId)
}

class StatsRepository(private val dao: StatsDao) {
    fun observeLatest(): Flow<UserStatsEntity?> = dao.observeLatest()

    suspend fun upsert(stats: UserStatsEntity) = dao.upsert(stats)
}
