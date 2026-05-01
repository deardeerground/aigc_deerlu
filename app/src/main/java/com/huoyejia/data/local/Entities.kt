package com.huoyejia.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index("created_at"),
        Index("source_type"),
        Index("processed_status")
    ]
)
data class NoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "raw_text")
    val rawText: String?,
    @ColumnInfo(name = "image_path")
    val imagePath: String?,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "source_title")
    val sourceTitle: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "ocr_text")
    val ocrText: String?,
    @ColumnInfo(name = "url")
    val url: String?,
    @ColumnInfo(name = "note_content")
    val noteContent: String,
    @ColumnInfo(name = "summary")
    val summary: String?,
    @ColumnInfo(name = "tags")
    val tags: String,
    @ColumnInfo(name = "topic")
    val topic: String?,
    @ColumnInfo(name = "importance")
    val importance: Float,
    @ColumnInfo(name = "duplicate_score")
    val duplicateScore: Float,
    @ColumnInfo(name = "processed_status")
    val processedStatus: String,
    @ColumnInfo(name = "read_status")
    val readStatus: Boolean,
    @ColumnInfo(name = "reviewed_count")
    val reviewedCount: Int
)

@Entity(tableName = "note_embeddings")
data class NoteEmbeddingEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "vector_dim")
    val vectorDim: Int,
    @ColumnInfo(name = "vector_blob")
    val vectorBlob: ByteArray,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "note_relations",
    indices = [
        Index("note_id_from"),
        Index("note_id_to"),
        Index("relation_type")
    ]
)
data class NoteRelationEntity(
    @PrimaryKey
    @ColumnInfo(name = "relation_id")
    val relationId: String,
    @ColumnInfo(name = "note_id_from")
    val noteIdFrom: String,
    @ColumnInfo(name = "note_id_to")
    val noteIdTo: String,
    @ColumnInfo(name = "relation_type")
    val relationType: String,
    @ColumnInfo(name = "confidence")
    val confidence: Float,
    @ColumnInfo(name = "evidence")
    val evidence: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "review_cards",
    indices = [
        Index("note_id"),
        Index("status"),
        Index("due_at")
    ]
)
data class ReviewCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "question")
    val question: String,
    @ColumnInfo(name = "explanation")
    val explanation: String,
    @ColumnInfo(name = "related_note_ids")
    val relatedNoteIds: String,
    @ColumnInfo(name = "difficulty")
    val difficulty: String,
    @ColumnInfo(name = "card_type")
    val cardType: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "reviewed_at")
    val reviewedAt: Long?
)

@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "stat_date")
    val statDate: String,
    @ColumnInfo(name = "total_collected")
    val totalCollected: Int,
    @ColumnInfo(name = "total_read")
    val totalRead: Int,
    @ColumnInfo(name = "total_reviewed")
    val totalReviewed: Int,
    @ColumnInfo(name = "duplicate_rate")
    val duplicateRate: Float,
    @ColumnInfo(name = "unprocessed_ratio")
    val unprocessedRatio: Float,
    @ColumnInfo(name = "hoarding_index")
    val hoardingIndex: Int,
    @ColumnInfo(name = "index_reason")
    val indexReason: String
)

data class NoteWithEmbedding(
    @androidx.room.Embedded
    val note: NoteEntity,
    @ColumnInfo(name = "vector_blob")
    val vectorBlob: ByteArray
)
