import json
import re
from datetime import datetime, timezone

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException

from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from llm_client import get_llm, LlmClient
from models import (
    NoteEmbeddingModel,
    NoteModel,
    NoteRelationModel,
    ReviewCardModel,
    UserStatsModel,
)
from schemas import (
    AnswerRequest,
    AnswerResponse,
    ExplainPackOut,
    ProcessingProgress,
    SearchResult,
    VideoGenerateResponse,
)

router = APIRouter(prefix="/api", tags=["ai"])

now_ts = lambda: int(datetime.now(timezone.utc).timestamp() * 1000)


# ---- 独立 AI 操作端点（Android ServerBlueLMAdapter 调用） ----

from pydantic import BaseModel as PydanticBase


class EnrichRequest(PydanticBase):
    note_content: str
    max_similarity: float = 0.0


class EmbedRequest(PydanticBase):
    text: str


class ClassifyRelationRequest(PydanticBase):
    note_a: str
    note_b: str
    similarity: float = 0.0


class GenerateReviewCardRequest(PydanticBase):
    current: str
    related: list[str] = []
    relation_hint: str = "relation"


class GenerateExplainPackRequest(PydanticBase):
    current: str
    current_title: str = ""
    related: list[str] = []


class AnswerQuestionRequest(PydanticBase):
    note_id: str = ""
    current_content: str
    current_title: str = ""
    url: str = ""
    summary: str = ""
    tags: str = "[]"
    topic: str = ""
    related: list[dict] = []
    question: str


class GenerateImageRequest(PydanticBase):
    prompt: str


class GenerateAnimationRequest(PydanticBase):
    note_id: str = ""
    title: str = ""
    explanation: str = ""


@router.post("/process-note-enrich")
async def api_enrich_note(body: EnrichRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    ai = await llm.chat_json(
        system="你是学习收藏助手。必须只返回一个合法 JSON 对象，不要 markdown。摘要必须进行归纳提炼，禁止直接复制原文。",
        user=f"""基于以下 note_content 生成结构化结果。
JSON schema:
{{
  "summary":"用中文归纳核心观点，40到80字",
  "tags":["最多5个中文短标签"],
  "topic":"一个中文主题",
  "importance":0-1,
  "duplicate_score":0-1
}}
max_similarity={body.max_similarity}
note_content={body.note_content}""",
        force_json=True,
    )
    return {
        "summary": ai.get("summary", ""),
        "tags": ai.get("tags", []),
        "topic": ai.get("topic", ""),
        "importance": ai.get("importance", 0.7),
        "duplicate_score": ai.get("duplicate_score", body.max_similarity),
    }


@router.post("/embed")
async def api_embed(body: EmbedRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.embedding_ready:
        raise HTTPException(status_code=400, detail="嵌入模型未配置")
    vec = await llm.embed(body.text)
    return {"embedding": vec}


@router.post("/classify-relation")
async def api_classify_relation(body: ClassifyRelationRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    ai = await llm.chat_json(
        system="你是知识关系判断器。只返回 JSON，不要 markdown。",
        user=f"""判断两条笔记的关系。
返回 JSON:
{{
  "relation_type":"similar|supplement|contrast|cause_effect|same_topic|none",
  "confidence":0-1,
  "evidence":"一句中文说明"
}}
similarity={body.similarity}
A={body.note_a}
B={body.note_b}""",
    )
    return {
        "relation_type": ai.get("relation_type", "none"),
        "confidence": ai.get("confidence", body.similarity),
        "evidence": ai.get("evidence", "模型判断存在关联。"),
    }


@router.post("/generate-review-card")
async def api_generate_review_card(body: GenerateReviewCardRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    ai = await llm.chat_json(
        system="你是学生学习教练。只返回 JSON，不要 markdown。",
        user=f"""基于当前笔记和关联笔记生成一张认知回流卡。
返回 JSON:
{{
  "question":"问题",
  "explanation":"<=120字",
  "difficulty":"easy|medium|hard",
  "card_type":"relation|contrast|cause_transfer"
}}
relationHint={body.relation_hint}
current={body.current}
related={chr(10).join(body.related)}""",
    )
    return {
        "question": ai.get("question", "这条内容能补充你哪一条旧知识？"),
        "explanation": ai.get("explanation", ""),
        "difficulty": ai.get("difficulty", "medium"),
        "card_type": ai.get("card_type", "relation"),
    }


@router.post("/generate-explain-pack")
async def api_generate_explain_pack(body: GenerateExplainPackRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    ai = await llm.chat_json(
        system="你是教学动画脚本师和PPT讲解助手。只返回 JSON，不要 markdown。",
        user=f"""针对学生笔记生成一个知识讲解包。
返回 JSON:
{{
  "title":"讲解标题",
  "hook":"开场一句话",
  "concise_explanation":"120字以内解释",
  "ppt_outline":[
    {{"title":"页标题","bullets":["要点1","要点2"],"image_prompt":"","icon":"spark","animation_hint":"fade"}}
  ],
  "animation_scenes":[
    {{"title":"场景","visual":"画面描述","narration":"旁白"}}
  ],
  "takeaway":"一句结论"
}}
当前笔记={body.current}
相关笔记={chr(10).join(body.related)}""",
    )
    return {
        "title": ai.get("title", body.current_title),
        "concise_explanation": ai.get("concise_explanation", ""),
        "hook": ai.get("hook", ""),
        "ppt_outline": ai.get("ppt_outline", []),
        "animation_scenes": ai.get("animation_scenes", []),
        "takeaway": ai.get("takeaway", ""),
    }


@router.post("/answer-question")
async def api_answer_question(body: AnswerQuestionRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    related_text = "\n".join(
        f"- {r.get('title', '')}: {r.get('summary', '')}"
        for r in body.related
    ) or "无"
    answer = await llm.chat_text(
        system="""你是学习卡片 AI 小助手。只能基于给定的当前卡片、关联卡片、原文、AI摘要、标签和网址回答。
回答要清楚、简洁、适合学生理解；优先给结构化要点和可执行复习建议。
如果材料不足，不要编造，请说明还需要什么信息。""",
        user=f"""当前卡片标题：{body.current_title}
原文网址：{body.url}
原文：{body.current_content}
AI摘要：{body.summary}
标签JSON：{body.tags}
主题：{body.topic}
关联卡片：{related_text}
用户问题：{body.question}""",
    )
    return {"answer": answer}


@router.post("/generate-slide-image")
async def api_generate_slide_image(body: GenerateImageRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.image_ready:
        raise HTTPException(status_code=400, detail="图片模型未配置")
    from fastapi.responses import Response
    image_bytes = await llm.generate_image(body.prompt)
    if not image_bytes:
        raise HTTPException(status_code=500, detail="图片生成失败")
    return Response(content=image_bytes, media_type="image/png")


@router.post("/generate-animation-html")
async def api_generate_animation_html(body: GenerateAnimationRequest, llm: LlmClient = Depends(get_llm)):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    ai = await llm.chat_json(
        system="你是移动端教学动画导演。只返回 JSON: {\"html\":\"完整HTML\"}。单文件 HTML，包含 CSS 和 JS，禁止外链。至少 5 个场景，适配手机竖屏和横屏。",
        user=f"标题：{body.title}\n解释：{body.explanation}\n要求：概念登场、关系展开、对比误区、应用练习、总结收束。",
    )
    html = ai.get("html", "")
    return {"html": html}


# ----- Processing -----

@router.post("/notes/{note_id}/process", response_model=ProcessingProgress)
async def process_note(
    note_id: str,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db),
    llm: LlmClient = Depends(get_llm),
):
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")
    note.processed_status = "QUEUED"
    await db.commit()
    background_tasks.add_task(_run_pipeline, note_id, llm)
    return ProcessingProgress(
        note_id=note_id,
        stage="QUEUED",
        progress=0.05,
        message="已加入处理队列",
        done=False,
        failed=False,
    )


async def _run_pipeline(note_id: str, llm: LlmClient):
    from database import async_session
    async with async_session() as db:
        note = await db.get(NoteModel, note_id)
        if not note:
            return
        try:
            note.processed_status = "PROCESSING"
            await db.commit()

            content = note.note_content or note.source_title
            if not llm.embedding_ready:
                note.processed_status = "FAILED"
                await db.commit()
                return

            # 1. Embed
            vector = await llm.embed(content)

            # 2. Similarity search using pgvector
            if vector:
                best = await db.execute(
                    select(NoteModel, NoteEmbeddingModel)
                    .join(NoteEmbeddingModel, NoteModel.note_id == NoteEmbeddingModel.note_id)
                    .where(NoteModel.note_id != note_id, NoteEmbeddingModel.embedding.isnot(None))
                    .order_by(NoteEmbeddingModel.embedding.cosine_distance(vector))
                    .limit(10)
                )
                similar_notes = best.all()
            else:
                similar_notes = []

            max_similarity = 0.0
            if similar_notes and similar_notes[0][0]:
                dist = similar_notes[0][1].embedding
                if dist:
                    max_similarity = 0.5  # approx

            # 3. Enrich with LLM
            if not llm.chat_ready:
                note.processed_status = "FAILED"
                await db.commit()
                return

            ai = await llm.chat_json(
                system="你是学习收藏助手。必须只返回一个合法 JSON 对象，不要 markdown，不要解释文字。摘要必须进行归纳提炼，禁止直接复制原文。",
                user=f"""基于以下 note_content 生成结构化结果。
JSON schema:
{{
  "summary":"用中文归纳核心观点，40到80字，不要照抄原文",
  "tags":["最多5个中文短标签"],
  "topic":"一个中文主题",
  "importance":0-1,
  "duplicate_score":0-1
}}
max_similarity={max_similarity}
note_content={content}""",
                force_json=True,
            )

            summary = ai.get("summary", "").strip() or content[:60]
            tags = json.dumps((ai.get("tags") or ["待归类"])[:5], ensure_ascii=False)
            topic = ai.get("topic") or "待归类"
            importance = float(ai.get("importance", 0.7))
            duplicate_score = float(ai.get("duplicate_score", max_similarity))

            note.note_content = content
            note.summary = summary
            note.tags = tags
            note.topic = topic
            note.importance = importance
            note.duplicate_score = duplicate_score
            note.processed_status = "PROCESSED"

            # Save embedding
            emb = await db.get(NoteEmbeddingModel, note_id)
            vec_bytes = _encode_vector(vector) if vector else None
            if emb:
                emb.model_name = "pgvector"
                emb.vector_dim = len(vector) if vector else 0
                emb.vector_blob = vec_bytes
                emb.embedding = vector
                emb.updated_at = now_ts()
            else:
                db.add(NoteEmbeddingModel(
                    note_id=note_id,
                    model_name="pgvector",
                    vector_dim=len(vector) if vector else 0,
                    vector_blob=vec_bytes,
                    embedding=vector,
                    updated_at=now_ts(),
                ))

            # 4. Classify relations
            for sim_note, _ in similar_notes[:3]:
                if not sim_note or sim_note.note_id == note_id:
                    continue
                try:
                    rel = await llm.chat_json(
                        system="你是知识关系判断器。只返回 JSON，不要 markdown。",
                        user=f"""判断两条笔记的关系。
返回 JSON:
{{
  "relation_type":"similar|supplement|contrast|cause_effect|same_topic|none",
  "confidence":0-1,
  "evidence":"一句中文说明"
}}
similarity=0.5
A={note.note_content}
B={sim_note.note_content}""",
                    )
                    rtype = rel.get("relation_type", "")
                    if rtype and rtype != "none":
                        db.add(NoteRelationModel(
                            relation_id=f"{note_id}_{sim_note.note_id}_{rtype}",
                            note_id_from=note_id,
                            note_id_to=sim_note.note_id,
                            relation_type=rtype,
                            confidence=float(rel.get("confidence", 0.5)),
                            evidence=rel.get("evidence", "模型判断存在关联。"),
                            created_at=now_ts(),
                        ))
                except Exception:
                    pass

            # 5. Generate review card
            try:
                card = await llm.chat_json(
                    system="你是学生学习教练。只返回 JSON，不要 markdown。",
                    user=f"""基于当前笔记和关联笔记生成一张认知回流卡。
优先出联系、对比、因果、迁移类问题，不要纯事实背诵题。
返回 JSON:
{{
  "question":"问题",
  "explanation":"<=120字",
  "difficulty":"easy|medium|hard",
  "card_type":"relation|contrast|cause_transfer"
}}
relationHint=relation
current={note.note_content}
related={chr(10).join((n.note_content or "") for n, _ in similar_notes[:3])}""",
                )
                related_ids = json.dumps([n.note_id for n, _ in similar_notes[:3] if n], ensure_ascii=False)
                db.add(ReviewCardModel(
                    note_id=note_id,
                    question=card.get("question", "这条内容能补充你哪一条旧知识？"),
                    explanation=card.get("explanation", "先说出联系，再解释为什么重要。"),
                    related_note_ids=related_ids,
                    difficulty=card.get("difficulty", "medium"),
                    card_type=card.get("card_type", "relation"),
                    status="TODO",
                    due_at=now_ts(),
                    created_at=now_ts(),
                    review_count=0,
                ))
            except Exception:
                pass

            await db.commit()

        except Exception:
            note.processed_status = "FAILED"
            await db.commit()


# ----- Search -----

@router.get("/search", response_model=list[SearchResult])
async def search_notes(
    q: str = "",
    top_k: int = 10,
    db: AsyncSession = Depends(get_db),
    llm: LlmClient = Depends(get_llm),
):
    if not q.strip():
        return []
    if not llm.embedding_ready:
        # Fallback to keyword search
        rows = (await db.execute(
            select(NoteModel).where(
                NoteModel.note_content.ilike(f"%{q}%") | NoteModel.source_title.ilike(f"%{q}%")
            ).limit(top_k)
        )).scalars().all()
        return [SearchResult(
            note_id=n.note_id,
            source_title=n.source_title,
            note_content=n.note_content,
            summary=n.summary,
            topic=n.topic,
            tags=n.tags,
            source_type=n.source_type,
            reviewed_count=n.reviewed_count,
            final_score=0.8,
        ) for n in rows]

    query_vec = await llm.embed(q)
    rows = await db.execute(
        select(NoteModel, NoteEmbeddingModel)
        .join(NoteEmbeddingModel, NoteModel.note_id == NoteEmbeddingModel.note_id)
        .where(NoteEmbeddingModel.embedding.isnot(None))
        .order_by(NoteEmbeddingModel.embedding.cosine_distance(query_vec))
        .limit(top_k)
    )
    results = []
    for note, _ in rows:
        results.append(SearchResult(
            note_id=note.note_id,
            source_title=note.source_title,
            note_content=note.note_content,
            summary=note.summary,
            topic=note.topic,
            tags=note.tags,
            source_type=note.source_type,
            reviewed_count=note.reviewed_count,
            final_score=0.75,
        ))
    return results


# ----- Explain Pack -----

@router.post("/notes/{note_id}/explain", response_model=ExplainPackOut)
async def generate_explain_pack(
    note_id: str,
    db: AsyncSession = Depends(get_db),
    llm: LlmClient = Depends(get_llm),
):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")

    rels = await db.execute(
        select(NoteRelationModel).where(
            (NoteRelationModel.note_id_from == note_id) | (NoteRelationModel.note_id_to == note_id)
        ).order_by(NoteRelationModel.confidence.desc()).limit(3)
    )
    rels = rels.scalars().all()
    related_ids = {r.note_id_from for r in rels} | {r.note_id_to for r in rels}
    related_ids.discard(note_id)
    related = []
    if related_ids:
        related_rows = await db.execute(select(NoteModel).where(NoteModel.note_id.in_(related_ids)))
        related = related_rows.scalars().all()

    ai = await llm.chat_json(
        system="你是教学动画脚本师和PPT讲解助手。只返回 JSON，不要 markdown。",
        user=f"""针对学生笔记生成一个知识讲解包，用于 App 内展示和后续 Remotion/PPT 生成。
返回 JSON:
{{
  "title":"讲解标题",
  "hook":"开场一句话",
  "concise_explanation":"120字以内解释",
  "ppt_outline":[
    {{
      "title":"页标题",
      "bullets":["要点1","要点2","要点3"],
      "image_prompt":"透明背景教育信息图插画提示词",
      "icon":"spark|network|target|book|timeline",
      "animation_hint":"fade|push|wipe"
    }}
  ],
  "animation_scenes":[
    {{"title":"场景标题","visual":"画面描述","narration":"旁白"}}
  ],
  "takeaway":"一句结论"
}}
当前笔记={note.note_content}
相关笔记={chr(10).join(r.note_content for r in related)}""",
    )
    return ExplainPackOut(
        note_id=note_id,
        title=ai.get("title") or note.source_title,
        concise_explanation=ai.get("concise_explanation") or "",
        hook=ai.get("hook") or "",
        ppt_outline=ai.get("ppt_outline") or [],
        animation_scenes=ai.get("animation_scenes") or [],
        takeaway=ai.get("takeaway") or "",
        provider=f"Chat {llm.chat_ready}",
    )


# ----- Slide Image -----

@router.post("/notes/{note_id}/slide-image")
async def generate_slide_image(
    note_id: str,
    prompt: str = "",
    llm: LlmClient = Depends(get_llm),
):
    if not llm.image_ready:
        raise HTTPException(status_code=400, detail="图片模型未配置")
    from fastapi.responses import Response
    image_bytes = await llm.generate_image(prompt)
    if not image_bytes:
        raise HTTPException(status_code=500, detail="图片生成失败")
    return Response(content=image_bytes, media_type="image/png")


# ----- Animation HTML -----

@router.post("/notes/{note_id}/animation-html")
async def generate_animation_html(
    note_id: str,
    db: AsyncSession = Depends(get_db),
    llm: LlmClient = Depends(get_llm),
):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")

    msg = f"""请根据以下讲解包生成一个更像真实教学小动画的 HTML。
标题：{note.source_title}
一分钟解释：{note.summary or note.note_content}
具体要求：
1. 用 CSS keyframes 和 JS 控制场景自动播放，不需要用户手动翻页。
2. 至少包含：概念登场、因果/关系展开、对比或误区、应用练习、总结收束。
3. 每个场景要有不同构图，不能重复同一模板。
4. 画面元素用 CSS 形状、卡片、线条、标签、图表模拟，不要依赖外部图片。
5. 最终只返回 JSON：{{"html":"..."}}。"""

    ai = await llm.chat_json(
        system="你是移动端教学动画导演和前端动画工程师。只返回 JSON，不要 markdown。JSON 字段为 {\"html\":\"完整HTML\"}。html 必须是单文件 HTML，包含 CSS 和少量原生 JS；禁止外链、禁止远程资源、禁止 iframe。动画要明显：至少 5 个场景，中文文案要完整可读。必须适配手机竖屏和横屏。",
        user=msg,
    )
    html = ai.get("html", "")
    if not html or "<html" not in html.lower():
        raise HTTPException(status_code=500, detail="动画生成失败")
    from fastapi.responses import HTMLResponse
    return HTMLResponse(content=html)


# ----- Answer question -----

@router.post("/notes/{note_id}/answer", response_model=AnswerResponse)
async def answer_question(
    note_id: str,
    body: AnswerRequest,
    db: AsyncSession = Depends(get_db),
    llm: LlmClient = Depends(get_llm),
):
    if not llm.chat_ready:
        raise HTTPException(status_code=400, detail="聊天模型未配置")
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")

    answer = await llm.chat_text(
        system="""你是学习卡片 AI 小助手。
只能基于给定的当前卡片、关联卡片、原文、AI摘要、标签和网址回答。
回答要清楚、简洁、适合学生理解；优先给结构化要点和可执行复习建议。
如果材料不足，不要编造，请说明还需要什么信息。""",
        user=f"""当前卡片标题：{note.source_title}
原文网址：{note.url or ''}
原文：{note.note_content}
AI摘要：{note.summary or ''}
标签JSON：{note.tags}
主题：{note.topic or ''}

用户问题：
{body.question}""",
    )
    return AnswerResponse(answer=answer)


# ----- Video Generation -----

@router.post("/notes/{note_id}/video", response_model=VideoGenerateResponse)
async def generate_video(
    note_id: str,
    db: AsyncSession = Depends(get_db),
    llm: LlmClient = Depends(get_llm),
):
    if not llm.video_ready:
        raise HTTPException(status_code=400, detail="视频模型未配置")
    note = await db.get(NoteModel, note_id)
    if not note:
        raise HTTPException(status_code=404, detail="笔记不存在")

    prompt = f"""全程生成一段中文教学讲解视频，风格像高质量课程短视频。
标题：{note.source_title}
讲解：{note.summary or note.note_content}
时间安排：
0-2秒：用一个强视觉开场引出标题。
2-5秒：用知识节点、连线或卡片展示核心概念。
5-8秒：展示因果、对比或迁移关系。
8-11秒：收束为一句结论。
视觉要求：清晰中文字幕、知识节点连线、关键词高亮、结构化卡片、适合课堂展示。"""

    resp = await llm.create_video_task(prompt)
    task_id = resp.get("id") or resp.get("task_id") or resp.get("taskId", "")
    video_url = _find_video_url(resp)
    return VideoGenerateResponse(
        status="done" if video_url else "processing",
        video_url=video_url,
        task_id=task_id,
    )


def _find_video_url(data: dict) -> str | None:
    for key in ("video_url", "videoUrl", "url", "download_url", "downloadUrl"):
        v = data.get(key)
        if isinstance(v, str) and v.startswith("http"):
            return v
        if isinstance(v, dict):
            for sk in ("url", "video_url"):
                sv = v.get(sk)
                if isinstance(sv, str) and sv.startswith("http"):
                    return sv
    for sub in ("data", "result", "output", "content"):
        subv = data.get(sub)
        if isinstance(subv, dict):
            found = _find_video_url(subv)
            if found:
                return found
    return None


def _encode_vector(vec: list[float]) -> bytes | None:
    import struct
    if not vec:
        return None
    return struct.pack(f"{len(vec)}f", *vec)
