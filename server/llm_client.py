import json
import re

import httpx

from config import settings


def _extract_json(text: str) -> str:
    trimmed = text.strip()
    trimmed = re.sub(r"^```(?:json)?\s*", "", trimmed)
    trimmed = re.sub(r"\s*```$", "", trimmed)
    start = trimmed.find("{")
    end = trimmed.rfind("}")
    if start == -1 or end == -1 or end <= start:
        raise ValueError(f"Model response is not valid JSON: {text}")
    return trimmed[start : end + 1]


class LlmClient:
    def __init__(self):
        self._http = httpx.AsyncClient(timeout=httpx.Timeout(120.0))

    @property
    def chat_ready(self) -> bool:
        return bool(settings.llm_chat_base_url and settings.llm_chat_api_key and settings.llm_chat_model)

    @property
    def embedding_ready(self) -> bool:
        return bool(settings.llm_embedding_base_url and settings.llm_embedding_api_key and settings.llm_embedding_model)

    @property
    def image_ready(self) -> bool:
        return bool(settings.llm_image_base_url and settings.llm_image_api_key and settings.llm_image_model)

    @property
    def video_ready(self) -> bool:
        return bool(settings.video_base_url and settings.video_api_key and settings.video_model)

    # ----- Chat -----

    async def chat_json(self, system: str, user: str, temperature: float = 0.2, force_json: bool = False) -> dict:
        payload = {
            "model": settings.llm_chat_model,
            "temperature": temperature,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        if force_json:
            payload["response_format"] = {"type": "json_object"}
        resp = await self._post_json(f"{settings.llm_chat_base_url}/chat/completions", payload, settings.llm_chat_api_key)
        content = resp["choices"][0]["message"]["content"]
        return json.loads(_extract_json(content))

    async def chat_text(self, system: str, user: str, temperature: float = 0.3) -> str:
        payload = {
            "model": settings.llm_chat_model,
            "temperature": temperature,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        resp = await self._post_json(f"{settings.llm_chat_base_url}/chat/completions", payload, settings.llm_chat_api_key)
        return resp["choices"][0]["message"]["content"] or "AI 暂时没有返回内容，请重试。"

    # ----- Embedding -----

    async def embed(self, text: str) -> list[float]:
        path = settings.llm_embedding_path or "/embeddings"
        payload = {
            "model": settings.llm_embedding_model,
            "input": text,
        }
        resp = await self._post_json(f"{settings.llm_embedding_base_url}{path}", payload, settings.llm_embedding_api_key)
        return resp["data"][0]["embedding"]

    # ----- Image -----

    async def generate_image(self, prompt: str) -> bytes | None:
        payload = {
            "model": settings.llm_image_model,
            "prompt": prompt,
            "size": settings.llm_image_size,
        }
        if "seedream" in settings.llm_image_model.lower():
            payload.update({
                "sequential_image_generation": "disabled",
                "response_format": "url",
                "stream": False,
                "watermark": False,
            })
        else:
            payload["background"] = "transparent"
        resp = await self._post_json(
            f"{settings.llm_image_base_url}{settings.llm_image_path}",
            payload,
            settings.llm_image_api_key,
        )
        first = (resp.get("data") or [{}])[0]
        b64 = first.get("b64_json", "")
        if b64:
            import base64
            return base64.b64decode(b64)
        url = first.get("url", "")
        if url:
            return await self._download(url)
        return None

    # ----- Video -----

    async def create_video_task(self, prompt: str) -> dict:
        payload = {
            "model": settings.video_model,
            "content": [{"type": "text", "text": prompt}],
            "generate_audio": settings.video_generate_audio,
            "ratio": settings.video_ratio,
            "duration": settings.video_duration,
            "watermark": settings.video_watermark,
        }
        return await self._post_json(
            f"{settings.video_base_url}{settings.video_create_path}",
            payload,
            settings.video_api_key,
        )

    async def get_video_status(self, task_id: str) -> dict:
        path = settings.video_status_path.replace("{id}", task_id)
        return await self._get_json(f"{settings.video_base_url}{path}", settings.video_api_key)

    # ----- Internal helpers -----

    async def _post_json(self, url: str, payload: dict, api_key: str) -> dict:
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        }
        resp = await self._http.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        return resp.json()

    async def _get_json(self, url: str, api_key: str) -> dict:
        headers = {"Authorization": f"Bearer {api_key}"}
        resp = await self._http.get(url, headers=headers)
        resp.raise_for_status()
        return resp.json()

    async def _download(self, url: str) -> bytes:
        resp = await self._http.get(url)
        resp.raise_for_status()
        return resp.content

    async def close(self):
        await self._http.aclose()


_llm: LlmClient | None = None


def get_llm() -> LlmClient:
    global _llm
    if _llm is None:
        _llm = LlmClient()
    return _llm
