from datetime import datetime, timezone
from typing import Optional

from pydantic import BaseModel, Field


# ===== Folders =====

class FolderCreate(BaseModel):
    name: str = Field(..., max_length=256)
    icon: str | None = None
    color: int | None = None


class FolderUpdate(BaseModel):
    name: str | None = None
    icon: str | None = None
    color: int | None = None


class FolderOut(BaseModel):
    folder_id: str
    name: str
    created_at: int
    icon: str | None = None
    color: int | None = None
    note_count: int = 0


# ===== Notes =====

class NoteCreate(BaseModel):
    raw_text: str | None = None
    image_path: str | None = None
    source_type: str = "text"
    source_title: str = "未命名收藏"
    url: str | None = None
    folder_id: str | None = None


class NoteUpdate(BaseModel):
    source_title: str | None = None
    folder_id: str | None = None
    read_status: bool | None = None


class NoteOut(BaseModel):
    note_id: str
    raw_text: str | None = None
    image_path: str | None = None
    source_type: str
    source_title: str
    created_at: int
    ocr_text: str | None = None
    url: str | None = None
    note_content: str
    summary: str | None = None
    tags: str = "[]"
    topic: str | None = None
    importance: float = 0.0
    duplicate_score: float = 0.0
    processed_status: str
    read_status: bool = False
    reviewed_count: int = 0
    folder_id: str | None = None


# ===== Relations =====

class RelationOut(BaseModel):
    relation_id: str
    note_id_from: str
    note_id_to: str
    relation_type: str
    confidence: float
    evidence: str
    created_at: int


# ===== Review Cards =====

class ReviewCardOut(BaseModel):
    card_id: str
    note_id: str
    question: str
    explanation: str
    related_note_ids: str
    difficulty: str
    card_type: str
    status: str
    due_at: int
    created_at: int
    reviewed_at: int | None = None
    review_count: int = 0


# ===== Stats =====

class StatsOut(BaseModel):
    stat_date: str
    total_collected: int
    total_read: int
    total_reviewed: int
    duplicate_rate: float
    unprocessed_ratio: float
    hoarding_index: int
    index_reason: str


# ===== AI =====

class ExplainPackOut(BaseModel):
    note_id: str
    title: str
    concise_explanation: str
    hook: str
    ppt_outline: list[dict]
    animation_scenes: list[dict]
    takeaway: str
    provider: str


class AnswerRequest(BaseModel):
    question: str


class AnswerResponse(BaseModel):
    answer: str


class ProcessingProgress(BaseModel):
    note_id: str
    stage: str
    progress: float
    message: str
    done: bool = False
    failed: bool = False


class UrlExtractRequest(BaseModel):
    url: str


class UrlExtractResponse(BaseModel):
    input_url: str
    final_url: str
    title: str | None = None
    text: str = ""
    excerpt: str | None = None
    status: str
    failure_reason: str | None = None
    ai_text: str = ""


class SearchResult(BaseModel):
    note_id: str
    source_title: str
    note_content: str
    summary: str | None = None
    topic: str | None = None
    tags: str
    source_type: str
    reviewed_count: int
    final_score: float


class VideoGenerateResponse(BaseModel):
    status: str  # "processing" or "done"
    video_url: str | None = None
    task_id: str | None = None
