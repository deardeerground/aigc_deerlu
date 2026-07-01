import json
import re

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import NoteEmbeddingModel, NoteModel, NoteRelationModel, ReviewCardModel
from schemas import NoteCreate, NoteOut, NoteUpdate, RelationOut, ReviewCardOut

router = APIRouter(prefix="/api/notes", tags=["notes"])


@router.get("", response_model=list[NoteOut])
async def list_notes(
    folder_id: str | None = Query(None),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    q = select(NoteModel).order_by(NoteModel.created_at.desc())
    if folder_id:
        q = q.where(NoteModel.folder_id == folder_id)
    q = q.limit(limit).offset(offset)
    rows = (await db.execute(q)).scalars().all()
    return [_note_to_out(n) for n in rows]


@router.get("/{note_id}", response_model=NoteOut)
async def get_note(note_id: str, db: AsyncSession = Depends(get_db)):
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")
    return _note_to_out(note)


@router.post("", response_model=NoteOut, status_code=201)
async def create_note(body: NoteCreate, db: AsyncSession = Depends(get_db)):
    note = NoteModel(
        raw_text=body.raw_text,
        image_path=body.image_path,
        source_type=body.source_type,
        source_title=body.source_title,
        url=body.url,
        folder_id=body.folder_id,
        note_content=body.raw_text or "",
        tags="[]",
        processed_status="QUEUED",
    )
    db.add(note)
    await db.commit()
    await db.refresh(note)
    return _note_to_out(note)


@router.put("/{note_id}", response_model=NoteOut)
async def update_note(note_id: str, body: NoteUpdate, db: AsyncSession = Depends(get_db)):
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")
    if body.source_title is not None:
        note.source_title = body.source_title
    if body.folder_id is not None:
        note.folder_id = body.folder_id if body.folder_id else None
    if body.read_status is not None:
        note.read_status = body.read_status
    await db.commit()
    await db.refresh(note)
    return _note_to_out(note)


@router.delete("/{note_id}")
async def delete_note(note_id: str, db: AsyncSession = Depends(get_db)):
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")
    await db.delete(note)
    await db.commit()
    return {"ok": True}


@router.post("/{note_id}/mark-reviewed")
async def mark_reviewed(note_id: str, db: AsyncSession = Depends(get_db)):
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")
    note.reviewed_count += 1
    note.read_status = True
    await db.commit()
    return {"ok": True}


# ===== Relations =====

@router.get("/{note_id}/relations", response_model=list[RelationOut])
async def get_relations(note_id: str, db: AsyncSession = Depends(get_db)):
    q = select(NoteRelationModel).where(
        (NoteRelationModel.note_id_from == note_id) | (NoteRelationModel.note_id_to == note_id)
    ).order_by(NoteRelationModel.confidence.desc())
    rows = (await db.execute(q)).scalars().all()
    return [_relation_to_out(r) for r in rows]


# ===== Review Cards =====

@router.get("/{note_id}/review-cards", response_model=list[ReviewCardOut])
async def get_review_cards(note_id: str, db: AsyncSession = Depends(get_db)):
    q = select(ReviewCardModel).where(ReviewCardModel.note_id == note_id).order_by(ReviewCardModel.created_at.desc())
    rows = (await db.execute(q)).scalars().all()
    return [_card_to_out(c) for c in rows]


def _note_to_out(n: NoteModel) -> NoteOut:
    return NoteOut(
        note_id=n.note_id,
        raw_text=n.raw_text,
        image_path=n.image_path,
        source_type=n.source_type,
        source_title=n.source_title,
        created_at=n.created_at,
        ocr_text=n.ocr_text,
        url=n.url,
        note_content=n.note_content,
        summary=n.summary,
        tags=n.tags,
        topic=n.topic,
        importance=n.importance,
        duplicate_score=n.duplicate_score,
        processed_status=n.processed_status,
        read_status=n.read_status,
        reviewed_count=n.reviewed_count,
        folder_id=n.folder_id,
    )


def _relation_to_out(r: NoteRelationModel) -> RelationOut:
    return RelationOut(
        relation_id=r.relation_id,
        note_id_from=r.note_id_from,
        note_id_to=r.note_id_to,
        relation_type=r.relation_type,
        confidence=r.confidence,
        evidence=r.evidence,
        created_at=r.created_at,
    )


def _card_to_out(c: ReviewCardModel) -> ReviewCardOut:
    return ReviewCardOut(
        card_id=c.card_id,
        note_id=c.note_id,
        question=c.question,
        explanation=c.explanation,
        related_note_ids=c.related_note_ids,
        difficulty=c.difficulty,
        card_type=c.card_type,
        status=c.status,
        due_at=c.due_at,
        created_at=c.created_at,
        reviewed_at=c.reviewed_at,
        review_count=c.review_count,
    )
