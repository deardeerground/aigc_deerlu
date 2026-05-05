package com.huoyejia.data

import com.huoyejia.data.local.EmbeddingDao
import com.huoyejia.data.local.FolderDao
import com.huoyejia.data.local.FolderEntity
import com.huoyejia.data.local.NoteDao
import com.huoyejia.data.local.NoteEmbeddingEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.RelationDao
import com.huoyejia.data.local.ReviewCardDao
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.local.StatsDao
import com.huoyejia.data.local.UserStatsEntity
import kotlinx.coroutines.flow.Flow

class FolderRepository(private val dao: FolderDao) {
    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    suspend fun loadAllFolders(): List<FolderEntity> = dao.loadAllFolders()

    suspend fun getFolder(folderId: String): FolderEntity? = dao.getFolder(folderId)

    suspend fun upsert(folder: FolderEntity) = dao.upsert(folder)

    suspend fun deleteFolder(folderId: String) = dao.deleteFolder(folderId)

    suspend fun countNotesInFolder(folderId: String): Int = dao.countNotesInFolder(folderId)
}

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

    suspend fun updateTitle(noteId: String, title: String) = noteDao.updateTitle(noteId, title)

    suspend fun updateProcessedStatus(noteId: String, status: String) = noteDao.updateProcessedStatus(noteId, status)

    suspend fun loadPendingProcessing(): List<NoteEntity> {
        return noteDao.loadByProcessedStatuses(listOf("NEW", "QUEUED", "PROCESSING", "FAILED"))
    }

    suspend fun saveEmbedding(embedding: NoteEmbeddingEntity) = embeddingDao.upsert(embedding)

    suspend fun deleteNote(noteId: String) {
        embeddingDao.deleteForNote(noteId)
        noteDao.deleteNote(noteId)
    }

    suspend fun loadWithEmbeddings(excludeNoteId: String = ""): List<NoteEntity> {
        return noteDao.loadWithEmbeddings(excludeNoteId)
    }
}

class RelationRepository(private val dao: RelationDao) {
    fun observeRelations(): Flow<List<NoteRelationEntity>> = dao.observeRelations()

    suspend fun getForNote(noteId: String): List<NoteRelationEntity> = dao.findForNote(noteId)

    suspend fun upsertAll(relations: List<NoteRelationEntity>) = dao.upsertAll(relations)

    suspend fun deleteForNote(noteId: String) = dao.deleteForNote(noteId)
}

class ReviewCardRepository(private val dao: ReviewCardDao) {
    fun observeCards(): Flow<List<ReviewCardEntity>> = dao.observeCards()

    suspend fun upsert(card: ReviewCardEntity) = dao.upsert(card)

    suspend fun markDone(cardId: String, reviewedAt: Long) = dao.markDone(cardId, reviewedAt)

    suspend fun incrementReviewCount(cardId: String) = dao.incrementReviewCount(cardId)

    suspend fun getLeastReviewedCards(limit: Int): List<ReviewCardEntity> = dao.getLeastReviewedCards(limit)

    suspend fun getPendingCardCount(): Int = dao.getPendingCardCount()

    suspend fun deleteForNote(noteId: String) = dao.deleteForNote(noteId)
}

class StatsRepository(private val dao: StatsDao) {
    fun observeLatest(): Flow<UserStatsEntity?> = dao.observeLatest()

    suspend fun upsert(stats: UserStatsEntity) = dao.upsert(stats)
}
