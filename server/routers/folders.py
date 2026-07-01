import json

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import FolderModel, NoteModel
from schemas import FolderCreate, FolderOut, FolderUpdate

router = APIRouter(prefix="/api/folders", tags=["folders"])


@router.get("", response_model=list[FolderOut])
async def list_folders(db: AsyncSession = Depends(get_db)):
    rows = (await db.execute(select(FolderModel).order_by(FolderModel.created_at.desc()))).scalars().all()
    result = []
    for f in rows:
        count = (await db.execute(
            select(func.count(NoteModel.note_id)).where(NoteModel.folder_id == f.folder_id)
        )).scalar() or 0
        result.append(FolderOut(
            folder_id=f.folder_id,
            name=f.name,
            created_at=f.created_at,
            icon=f.icon,
            color=f.color,
            note_count=count,
        ))
    return result


@router.post("", response_model=FolderOut, status_code=201)
async def create_folder(body: FolderCreate, db: AsyncSession = Depends(get_db)):
    folder = FolderModel(name=body.name, icon=body.icon, color=body.color)
    db.add(folder)
    await db.commit()
    await db.refresh(folder)
    return FolderOut(
        folder_id=folder.folder_id,
        name=folder.name,
        created_at=folder.created_at,
        icon=folder.icon,
        color=folder.color,
        note_count=0,
    )


@router.put("/{folder_id}", response_model=FolderOut)
async def update_folder(folder_id: str, body: FolderUpdate, db: AsyncSession = Depends(get_db)):
    folder = await db.get(FolderModel, folder_id)
    if not folder:
        raise HTTPException(status_code=404, detail="文件夹不存在")
    if body.name is not None:
        folder.name = body.name
    if body.icon is not None:
        folder.icon = body.icon
    if body.color is not None:
        folder.color = body.color
    await db.commit()
    await db.refresh(folder)
    count = (await db.execute(
        select(func.count(NoteModel.note_id)).where(NoteModel.folder_id == folder.folder_id)
    )).scalar() or 0
    return FolderOut(
        folder_id=folder.folder_id,
        name=folder.name,
        created_at=folder.created_at,
        icon=folder.icon,
        color=folder.color,
        note_count=count,
    )


@router.delete("/{folder_id}")
async def delete_folder(folder_id: str, db: AsyncSession = Depends(get_db)):
    folder = await db.get(FolderModel, folder_id)
    if not folder:
        raise HTTPException(status_code=404, detail="文件夹不存在")
    await db.delete(folder)
    await db.commit()
    return {"ok": True}
