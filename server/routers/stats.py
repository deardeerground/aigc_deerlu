import math
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
    collect_pressure = min(max(math.log(total + 1) / math.log(16), 0.0), 1.0)
    delay = min(max(unproc * min(max(total / 8.0, 0.35), 1.0), 0.0), 1.0)
    factors = [
        ("采集压力", collect_pressure, 0.16),
        ("未读衰减", min(max(unread, 0.0), 1.0), 0.18),
        ("回流缺口", min(max(unreviewed, 0.0), 1.0), 0.26),
        ("重复收藏", min(max(dup, 0.0), 1.0), 0.16),
        ("处理延迟", delay, 0.12),
        ("未处理率", min(max(unproc, 0.0), 1.0), 0.12),
    ]
    weighted = _dynamic_weights(factors)
    score = 100.0 * sum(value * weight for name, value, weight in weighted)
    idx = min(int(score), 100)
    top = "、".join(
        f"{name}{int(value * 100)}%"
        for name, value, weight in sorted(weighted, key=lambda item: item[1] * item[2], reverse=True)[:3]
    )
    if idx >= 80:
        reason = f"高囤积预警：多因子指数={idx}，主要压力来自{top}。建议暂停新增，先清重复并完成今日回流卡。"
    elif idx >= 60:
        reason = f"中度囤积：多因子指数={idx}，主要压力来自{top}。优先处理高重要度未复习内容。"
    elif idx >= 40:
        reason = f"轻度囤积：多因子指数={idx}，主要压力来自{top}。建议坚持收藏后24小时内回流。"
    else:
        reason = f"健康状态：多因子指数={idx}，收藏正在转化为可复习知识。"
    return idx, reason


def _dynamic_weights(factors: list[tuple[str, float, float]]) -> list[tuple[str, float, float]]:
    adjusted = []
    for name, value, base in factors:
        lift = 0.72 + 0.56 * min(max(value, 0.0), 1.0)
        adjusted.append((name, value, base * lift))
    total = max(sum(weight for _, _, weight in adjusted), 0.0001)
    return [(name, value, weight / total) for name, value, weight in adjusted]
