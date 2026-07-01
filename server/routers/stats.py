from datetime import date

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import NoteModel, UserStatsModel
from schemas import StatsOut

router = APIRouter(prefix="/api/stats", tags=["stats"])


@router.get("", response_model=StatsOut)
async def get_stats(db: AsyncSession = Depends(get_db)):
    notes = (await db.execute(select(NoteModel))).scalars().all()
    total = len(notes)
    read = sum(1 for n in notes if n.read_status)
    reviewed = sum(1 for n in notes if n.reviewed_count > 0)
    dup_rate = sum(1 for n in notes if n.duplicate_score >= 0.72) / max(total, 1)
    unproc = sum(1 for n in notes if n.processed_status != "PROCESSED") / max(total, 1)

    hoarding, reason = _calc_hoarding(total, read, reviewed, dup_rate, unproc)

    today_str = date.today().isoformat()
    existing = await db.get(UserStatsModel, today_str)
    if existing:
        existing.total_collected = total
        existing.total_read = read
        existing.total_reviewed = reviewed
        existing.duplicate_rate = round(dup_rate, 4)
        existing.unprocessed_ratio = round(unproc, 4)
        existing.hoarding_index = hoarding
        existing.index_reason = reason
    else:
        db.add(UserStatsModel(
            stat_date=today_str,
            total_collected=total,
            total_read=read,
            total_reviewed=reviewed,
            duplicate_rate=round(dup_rate, 4),
            unprocessed_ratio=round(unproc, 4),
            hoarding_index=hoarding,
            index_reason=reason,
        ))
    await db.commit()

    return StatsOut(
        stat_date=today_str,
        total_collected=total,
        total_read=read,
        total_reviewed=reviewed,
        duplicate_rate=round(dup_rate, 4),
        unprocessed_ratio=round(unproc, 4),
        hoarding_index=hoarding,
        index_reason=reason,
    )


def _calc_hoarding(total: int, read: int, reviewed: int, dup: float, unproc: float):
    if total <= 0:
        return 0, "暂无囤积，保持输入与理解平衡。"
    unread = max(total - read, 0) / total
    unreviewed = max(total - reviewed, 0) / total
    collect_pressure = min(total / 5.0, 1.0)
    score = 100.0 * (
        0.20 * collect_pressure +
        0.25 * unread +
        0.25 * unreviewed +
        0.15 * min(dup, 1.0) +
        0.15 * min(unproc, 1.0)
    )
    idx = min(int(score), 100)
    if idx >= 80:
        reason = "高囤积预警：收藏远超消化，建议先处理重复内容并完成今日回流卡。"
    elif idx >= 60:
        reason = "中度囤积：输入活跃但复习不足，优先处理高重要度未复习内容。"
    elif idx >= 40:
        reason = "轻度囤积：整体可控，建议坚持收藏后24小时内回流。"
    else:
        reason = "健康状态：收藏正在转化为可复习知识。"
    return idx, reason
