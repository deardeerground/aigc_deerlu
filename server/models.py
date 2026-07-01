import uuid
from datetime import datetime, timezone

from pgvector.sqlalchemy import Vector
from sqlalchemy import Boolean, Float, ForeignKey, Index, Integer, LargeBinary, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from database import Base


def generate_id() -> str:
    return uuid.uuid4().hex


def now_ts() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


class FolderModel(Base):
    __tablename__ = "folders"

    folder_id: Mapped[str] = mapped_column(String(64), primary_key=True, default=generate_id)
    name: Mapped[str] = mapped_column(String(256), nullable=False)
    created_at: Mapped[int] = mapped_column(Integer, default=now_ts, index=True)
    icon: Mapped[str | None] = mapped_column(String(64), nullable=True)
    color: Mapped[int | None] = mapped_column(Integer, nullable=True)

    notes: Mapped[list["NoteModel"]] = relationship(back_populates="folder")


class NoteModel(Base):
    __tablename__ = "notes"

    note_id: Mapped[str] = mapped_column(String(64), primary_key=True, default=generate_id)
    raw_text: Mapped[str | None] = mapped_column(Text, nullable=True)
    image_path: Mapped[str | None] = mapped_column(String(1024), nullable=True)
    source_type: Mapped[str] = mapped_column(String(32), default="text")
    source_title: Mapped[str] = mapped_column(String(512), default="未命名收藏")
    created_at: Mapped[int] = mapped_column(Integer, default=now_ts, index=True)
    ocr_text: Mapped[str | None] = mapped_column(Text, nullable=True)
    url: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    note_content: Mapped[str] = mapped_column(Text, default="")
    summary: Mapped[str | None] = mapped_column(Text, nullable=True)
    tags: Mapped[str] = mapped_column(Text, default="[]")
    topic: Mapped[str | None] = mapped_column(String(256), nullable=True)
    importance: Mapped[float] = mapped_column(Float, default=0.0)
    duplicate_score: Mapped[float] = mapped_column(Float, default=0.0)
    processed_status: Mapped[str] = mapped_column(String(32), default="NEW", index=True)
    read_status: Mapped[bool] = mapped_column(Boolean, default=False)
    reviewed_count: Mapped[int] = mapped_column(Integer, default=0)
    folder_id: Mapped[str | None] = mapped_column(String(64), ForeignKey("folders.folder_id", ondelete="SET NULL"), nullable=True, index=True)

    folder: Mapped[FolderModel | None] = relationship(back_populates="notes")
    embedding: Mapped["NoteEmbeddingModel | None"] = relationship(back_populates="note", uselist=False)

    __table_args__ = (
        Index("ix_notes_source_type", "source_type"),
    )


class NoteEmbeddingModel(Base):
    __tablename__ = "note_embeddings"

    note_id: Mapped[str] = mapped_column(String(64), ForeignKey("notes.note_id", ondelete="CASCADE"), primary_key=True)
    model_name: Mapped[str] = mapped_column(String(128), default="")
    vector_dim: Mapped[int] = mapped_column(Integer, default=0)
    vector_blob: Mapped[bytes] = mapped_column(LargeBinary, nullable=True)
    embedding: Mapped[list[float] | None] = mapped_column(Vector(4096), nullable=True)
    updated_at: Mapped[int] = mapped_column(Integer, default=now_ts)

    note: Mapped[NoteModel] = relationship(back_populates="embedding")


class NoteRelationModel(Base):
    __tablename__ = "note_relations"

    relation_id: Mapped[str] = mapped_column(String(128), primary_key=True, default=generate_id)
    note_id_from: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    note_id_to: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    relation_type: Mapped[str] = mapped_column(String(64), index=True, default="similar")
    confidence: Mapped[float] = mapped_column(Float, default=0.0)
    evidence: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[int] = mapped_column(Integer, default=now_ts)


class ReviewCardModel(Base):
    __tablename__ = "review_cards"

    card_id: Mapped[str] = mapped_column(String(64), primary_key=True, default=generate_id)
    note_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    question: Mapped[str] = mapped_column(Text, default="")
    explanation: Mapped[str] = mapped_column(Text, default="")
    related_note_ids: Mapped[str] = mapped_column(Text, default="[]")
    difficulty: Mapped[str] = mapped_column(String(16), default="medium")
    card_type: Mapped[str] = mapped_column(String(32), default="relation")
    status: Mapped[str] = mapped_column(String(16), default="TODO", index=True)
    due_at: Mapped[int] = mapped_column(Integer, index=True, default=now_ts)
    created_at: Mapped[int] = mapped_column(Integer, default=now_ts)
    reviewed_at: Mapped[int | None] = mapped_column(Integer, nullable=True)
    review_count: Mapped[int] = mapped_column(Integer, default=0)


class UserStatsModel(Base):
    __tablename__ = "user_stats"

    stat_date: Mapped[str] = mapped_column(String(10), primary_key=True)
    total_collected: Mapped[int] = mapped_column(Integer, default=0)
    total_read: Mapped[int] = mapped_column(Integer, default=0)
    total_reviewed: Mapped[int] = mapped_column(Integer, default=0)
    duplicate_rate: Mapped[float] = mapped_column(Float, default=0.0)
    unprocessed_ratio: Mapped[float] = mapped_column(Float, default=0.0)
    hoarding_index: Mapped[int] = mapped_column(Integer, default=0)
    index_reason: Mapped[str] = mapped_column(Text, default="")
