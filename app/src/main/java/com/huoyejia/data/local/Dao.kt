package com.huoyejia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.huoyejia.data.local.UserStatsEntity

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY created_at DESC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY created_at DESC")
    suspend fun loadAllFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE folder_id = :folderId")
    suspend fun getFolder(folderId: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE folder_id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("SELECT COUNT(*) FROM notes WHERE folder_id = :folderId")
    suspend fun countNotesInFolder(folderId: String): Int
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    suspend fun loadAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE note_id = :noteId")
    suspend fun getNote(noteId: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countNotes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("UPDATE notes SET reviewed_count = reviewed_count + 1, read_status = 1 WHERE note_id = :noteId")
    suspend fun markReviewed(noteId: String)

    @Query("UPDATE notes SET source_title = :title WHERE note_id = :noteId")
    suspend fun updateTitle(noteId: String, title: String)

    @Query("UPDATE notes SET processed_status = :status WHERE note_id = :noteId")
    suspend fun updateProcessedStatus(noteId: String, status: String)

    @Query("DELETE FROM notes WHERE note_id = :noteId")
    suspend fun deleteNote(noteId: String)

    @Query("SELECT * FROM notes WHERE processed_status IN (:statuses) ORDER BY created_at ASC")
    suspend fun loadByProcessedStatuses(statuses: List<String>): List<NoteEntity>

    @Query(
        """
        SELECT notes.* FROM notes INNER JOIN note_embeddings ON notes.note_id = note_embeddings.note_id
        WHERE notes.note_id != :excludeNoteId
        """
    )
    suspend fun loadWithEmbeddings(excludeNoteId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE folder_id = :folderId ORDER BY created_at DESC")
    fun observeNotesByFolder(folderId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE processed_status != 'PROCESSED' ORDER BY created_at ASC")
    suspend fun loadPendingProcessing(): List<NoteEntity>

    @Query("UPDATE notes SET read_status = 1 WHERE note_id = :noteId")
    suspend fun markAsRead(noteId: String)

    @Query("DELETE FROM notes WHERE folder_id = :folderId")
    suspend fun deleteAllInFolder(folderId: String)
}

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: NoteEmbeddingEntity)

    @Query("DELETE FROM note_embeddings WHERE note_id = :noteId")
    suspend fun deleteForNote(noteId: String)
}

@Dao
interface RelationDao {
    @Query("SELECT * FROM note_relations ORDER BY confidence DESC")
    fun observeRelations(): Flow<List<NoteRelationEntity>>

    @Query(
        """
        SELECT * FROM note_relations
        WHERE note_id_from = :noteId OR note_id_to = :noteId
        ORDER BY confidence DESC
        """
    )
    suspend fun findForNote(noteId: String): List<NoteRelationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(relations: List<NoteRelationEntity>)

    @Query("DELETE FROM note_relations WHERE note_id_from = :noteId OR note_id_to = :noteId")
    suspend fun deleteForNote(noteId: String)
}

@Dao
interface ReviewCardDao {
    @Query("SELECT * FROM review_cards ORDER BY status ASC, created_at DESC")
    fun observeCards(): Flow<List<ReviewCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: ReviewCardEntity)

    @Query("UPDATE review_cards SET status = 'DONE', reviewed_at = :reviewedAt, review_count = review_count + 1 WHERE card_id = :cardId")
    suspend fun markDone(cardId: String, reviewedAt: Long)

    @Query("UPDATE review_cards SET review_count = review_count + 1 WHERE card_id = :cardId")
    suspend fun incrementReviewCount(cardId: String)

    @Query("DELETE FROM review_cards WHERE note_id = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Query("SELECT * FROM review_cards WHERE status = 'TODO' ORDER BY review_count ASC, created_at ASC LIMIT :limit")
    suspend fun getLeastReviewedCards(limit: Int): List<ReviewCardEntity>

    @Query("SELECT COUNT(*) FROM review_cards WHERE status = 'TODO'")
    suspend fun getPendingCardCount(): Int
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM user_stats ORDER BY stat_date DESC LIMIT 1")
    fun observeLatest(): Flow<UserStatsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: UserStatsEntity)
}
