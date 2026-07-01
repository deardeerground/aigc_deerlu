from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from database import init_db
from routers import ai, cards, folders, notes, stats


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(
    title="Huoyejia API",
    description="活页夹 - AI 学习收藏助手后端服务",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(folders.router)
app.include_router(notes.router)
app.include_router(ai.router)
app.include_router(cards.router)
app.include_router(stats.router)


@app.get("/api/health")
async def health():
    return {"status": "ok", "service": "huoyejia"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.server_host, port=settings.server_port, reload=True)
