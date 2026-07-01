from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import ReviewCardModel
from schemas import ReviewCardOut

router = APIRouter(prefix="/api/cards", tags=["cards"])


@router.get("", response_model=list[ReviewCardOut])
async def list_cards(
    status: str | None = None,
    limit: int = 50,
    db: AsyncSession = Depends(get_db),
):
    q = select(ReviewCardModel).order_by(ReviewCardModel.status.asc(), ReviewCardModel.created_at.desc())
    if status:
        q = q.where(ReviewCardModel.status == status)
    q = q.limit(limit)
    rows = (await db.execute(q)).scalars().all()
    return [_card_to_out(c) for c in rows]


@router.post("/{card_id}/done", response_model=ReviewCardOut)
async def mark_card_done(card_id: str, db: AsyncSession = Depends(get_db)):
    card = await db.get(ReviewCardModel, card_id)
    if not card:
        raise HTTPException(status_code=404, detail="卡片不存在")
    import datetime
    card.status = "DONE"
    card.reviewed_at = int(datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000)
    card.review_count += 1
    await db.commit()
    await db.refresh(card)
    return _card_to_out(card)


@router.get("/pending-count")
async def pending_count(db: AsyncSession = Depends(get_db)):
    from sqlalchemy import func
    count = (await db.execute(
        select(func.count(ReviewCardModel.card_id)).where(ReviewCardModel.status == "TODO")
    )).scalar() or 0
    return {"count": count}


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
