import html
import re
from dataclasses import dataclass
from urllib.parse import unquote, urlparse

import httpx


MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
)


@dataclass
class WebExtractResult:
    input_url: str
    final_url: str
    title: str | None
    text: str
    excerpt: str | None
    status: str
    failure_reason: str | None = None

    def to_ai_text(self) -> str:
        parts = [
            f"学习网址：{self.final_url}",
            f"网页标题：{self.title}" if self.title else "",
            f"网页描述：{self.excerpt}" if self.excerpt else "",
            f"网页正文：\n{self.text}" if self.text else "",
            f"网页读取状态：{self.failure_reason}" if self.status != "success" and self.failure_reason else "",
        ]
        return "\n".join(p for p in parts if p.strip())


async def extract_url_content(url: str) -> WebExtractResult:
    target = normalize_url(url)
    fallback = fallback_result(target)
    if not target:
        return fallback

    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(18.0, connect=8.0),
            follow_redirects=True,
            headers={
                "User-Agent": MOBILE_UA,
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7",
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            },
        ) as client:
            resp = await client.get(target)
        if resp.status_code < 200 or resp.status_code >= 300:
            return fallback_result(str(resp.url), f"HTTP {resp.status_code}", status="failed")

        content_type = resp.headers.get("content-type", "")
        if "text" not in content_type and "html" not in content_type and "json" not in content_type:
            return fallback_result(str(resp.url), f"非文本响应：{content_type}", status="partial")

        parsed = parse_html(resp.text, str(resp.url))
        if len(parsed.text) >= 300:
            return parsed
        if parsed.title or len(parsed.text) >= 80:
            parsed.status = "partial"
            parsed.failure_reason = "网页正文较短，可能是动态渲染或登录限制"
            return parsed
        return fallback_result(str(resp.url), "网页正文为空，可能被反爬或需要登录", status="partial")
    except Exception as exc:
        return fallback_result(target, str(exc), status="partial")


def parse_html(raw_html: str, final_url: str) -> WebExtractResult:
    cleaned = re.sub(r"(?is)<!--.*?-->", " ", raw_html)
    cleaned = re.sub(r"(?is)<(script|style|noscript|svg|canvas|iframe)[^>]*>.*?</\1>", " ", cleaned)
    cleaned = re.sub(r"(?is)<(nav|footer|aside|form|button|header)[^>]*>.*?</\1>", " ", cleaned)

    title = extract_title(cleaned)
    excerpt = extract_meta(cleaned, {"description", "og:description", "twitter:description"})
    candidates = extract_article_candidates(cleaned)
    paragraphs = "\n".join(
        to_text(match.group(1))
        for match in re.finditer(r"(?is)<p\b[^>]*>(.*?)</p>", cleaned)
        if len(to_text(match.group(1))) >= 12
    )
    body = ""
    body_match = re.search(r"(?is)<body[^>]*>(.*?)</body>", cleaned)
    if body_match:
        body = to_text(body_match.group(1))

    best = max(
        [normalize_text(x) for x in candidates + [paragraphs, body] if len(normalize_text(x)) >= 40],
        key=score_text,
        default="",
    )
    return WebExtractResult(
        input_url=final_url,
        final_url=final_url,
        title=title,
        text=best[:12000],
        excerpt=excerpt,
        status="success",
    )


def extract_article_candidates(raw_html: str) -> list[str]:
    selectors = (
        "article",
        "main",
        "content",
        "article-content",
        "post-content",
        "entry-content",
        "rich_media_content",
        "正文",
        "detail",
    )
    out: list[str] = []
    for match in re.finditer(r"(?is)<(article|main|section|div)\b([^>]*)>(.*?)</\1>", raw_html):
        tag = match.group(1).lower()
        attrs = match.group(2).lower()
        if tag in {"article", "main"} or any(s in attrs for s in selectors):
            out.append(to_text(match.group(3)))
    return out


def extract_title(raw_html: str) -> str | None:
    meta_title = extract_meta(raw_html, {"og:title", "twitter:title"})
    if meta_title:
        return meta_title
    match = re.search(r"(?is)<title[^>]*>(.*?)</title>", raw_html)
    return normalize_text(to_text(match.group(1))) if match else None


def extract_meta(raw_html: str, names: set[str]) -> str | None:
    for match in re.finditer(r"(?is)<meta\s+([^>]+)>", raw_html):
        attrs = parse_attrs(match.group(1))
        key = (attrs.get("name") or attrs.get("property") or "").lower()
        value = attrs.get("content", "")
        if key in names and value.strip():
            return normalize_text(html.unescape(value))
    return None


def parse_attrs(raw: str) -> dict[str, str]:
    return {
        m.group(1).lower(): html.unescape(m.group(3))
        for m in re.finditer(r"""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(['"])(.*?)\2""", raw)
    }


def to_text(raw_html: str) -> str:
    text = re.sub(r"(?is)<br\s*/?>", "\n", raw_html)
    text = re.sub(r"(?is)</(p|div|section|article|h[1-6]|li)>", "\n", text)
    text = re.sub(r"(?is)<[^>]+>", " ", text)
    return normalize_text(html.unescape(text))


def normalize_text(text: str) -> str:
    return re.sub(r"\n\s*\n+", "\n", re.sub(r"[ \t\r\f\v]+", " ", text)).strip()


def score_text(text: str) -> int:
    punctuation = sum(text.count(ch) for ch in "，。！？；：,.!?;")
    lines = sum(1 for line in text.splitlines() if len(line.strip()) >= 16)
    return len(text) + punctuation * 8 + lines * 24


def normalize_url(url: str | None) -> str:
    value = (url or "").strip()
    if not value:
        return ""
    if not value.startswith(("http://", "https://")):
        value = "https://" + value
    return value


def fallback_result(url: str, reason: str = "仅保留网址信息", status: str = "partial") -> WebExtractResult:
    normalized = normalize_url(url)
    parsed = urlparse(normalized)
    host = (parsed.netloc or "").removeprefix("www.")
    path = normalize_text(re.sub(r"[/_\-.]+", " ", unquote(parsed.path or "")))
    text = "\n".join(p for p in (f"网站：{host}" if host else "", f"网址路径关键词：{path}" if len(path) > 1 else "") if p)
    return WebExtractResult(
        input_url=normalized,
        final_url=normalized,
        title=host or None,
        text=text,
        excerpt=None,
        status=status,
        failure_reason=reason,
    )
