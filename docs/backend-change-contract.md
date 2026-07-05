# 后端改动协作说明

这份文档用于约定后续后端改动的接口边界。原则是：改后端之前先更新这份文档，让负责服务器的同学能提前知道 App 端需要什么、字段怎么传、失败时怎么兜底。

## 当前状态

App 端已经接入了 `ServerBlueLMAdapter`，调用顺序是：

1. 优先请求自建后端 `SERVER_BASE_URL`。
2. 后端不可用或接口失败时，降级到 App 端直连模型 `RemoteBlueLMAdapter`。
3. 直连模型也不可用时，降级到本地 `MockBlueLMAdapter`。

当前 App 端配置入口：

- Android: `app/build.gradle.kts`
- 后端地址字段: `SERVER_BASE_URL`
- App 端适配器: `app/src/main/java/com/huoyejia/ai/ServerBlueLMAdapter.kt`
- 后端入口: `server/main.py`
- 后端 AI 路由: `server/routers/ai.py`

## 现有后端接口

App 当前会尝试调用这些接口：

| 功能 | Method | Path | 主要用途 |
|---|---|---|---|
| 摘要/标签 | POST | `/api/process-note-enrich` | 根据内容生成 summary、tags、topic、importance、duplicate_score |
| 向量 | POST | `/api/embed` | 生成文本向量，用于相似度检索 |
| 关系判断 | POST | `/api/classify-relation` | 判断两张卡片关系 |
| 复习卡 | POST | `/api/generate-review-card` | 生成回流复习卡 |
| 讲解包 | POST | `/api/generate-explain-pack` | 生成讲解、PPT、动画脚本结构 |
| 卡片问答 | POST | `/api/answer-question` | 单张卡片 AI 助手问答 |
| PPT 图片 | POST | `/api/generate-slide-image` | 生成 PPT 插图 |
| 动画 HTML | POST | `/api/generate-animation-html` | 生成教学动画 HTML |

## 近期必须补的接口

### 1. 图片理解接口

问题：OCR 只能识别图片里的文字，不能理解纯图片。比如用户上传一张图表、地图、实验图、照片，OCR 可能为空。这时 App 需要后端调用多模态视觉模型，把图片转换为中文学习描述。

建议新增：

`POST /api/describe-image`

请求：

```json
{
  "image_data_url": "data:image/jpeg;base64,...",
  "context_text": "用户粘贴的补充文本，可为空"
}
```

返回：

```json
{
  "description": "120到220字中文描述，说明画面主体、关键信息、可提炼知识点、适合生成复习卡的问题方向"
}
```

后端模型要求：

- 必须是支持图片输入的多模态聊天模型。
- 不是图片生成模型。`seedream` 这类模型负责画图，不负责看图。
- 如果模型不支持 `image_url`，接口要返回明确错误，不要返回空字符串。

App 端当前临时处理：

- `ServerBlueLMAdapter.describeImage()` 目前直接 fallback 到直连模型。
- 后端补上 `/api/describe-image` 后，App 端再改成优先请求后端。

### 2. 多模态向量接口

问题：现在 App 端已经支持 `embed(text, imagePath)`，但后端 `/api/embed` 目前只真正处理 `text`。如果要让“截图图片本身参与相似度关联”，后端需要支持图片向量。

建议把 `/api/embed` 扩展为：

请求：

```json
{
  "text": "文本内容，可为空但建议保留标题/描述",
  "image_data_url": "data:image/jpeg;base64,...，可为空"
}
```

返回：

```json
{
  "embedding": [0.0123, -0.0456]
}
```

兼容要求：

- 只有 `text` 时，按文本向量处理。
- 有 `image_data_url` 时，优先走多模态向量模型。
- 如果后端暂时不支持图片向量，不要静默忽略，建议返回 `supports_image=false` 或明确错误，方便 App 判断是否 fallback。

## 后端提示词隔离要求

之前出现过“AI 污染”：新卡片回答混入历史卡片内容。后端处理问答、摘要、复习卡时要注意：

- 每次请求都必须独立构造 messages。
- 不要在全局变量里保存上一次用户问题或模型回答。
- `answer-question` 只能使用本次请求传入的 `current_content`、`summary`、`tags`、`url`、`related`。
- 如果材料不足，应返回“材料不足”，不要自己补外部知识。

## 需要队友优先确认的配置

后端 `.env` 建议只放服务器本地，不要提交真实密钥。仓库里应该只保留：

- `server/.env.example`

不建议提交：

- `server/.env`

推荐后端新增配置：

```env
VISION_BASE_URL=
VISION_API_KEY=
VISION_MODEL=

MULTIMODAL_EMBEDDING_BASE_URL=
MULTIMODAL_EMBEDDING_API_KEY=
MULTIMODAL_EMBEDDING_MODEL=
MULTIMODAL_EMBEDDING_PATH=/api/v3/embeddings/multimodal
```

如果暂时想复用已有 Ark 配置，也要明确哪个模型用于：

- 看图理解
- 图文向量
- 图片生成
- 视频生成

这四类不能混用。

## 后端改动前 checklist

后续我改后端前，先按这个流程走：

1. 在这份文档里写清楚新增/修改接口。
2. 标明 App 端调用文件和字段。
3. 写请求 JSON 和返回 JSON 示例。
4. 写失败时 App 端如何 fallback。
5. 再改 `server/` 代码。
6. 最后跑基本接口测试和 Android 构建。

## 建议的最小下一步

先只做一个小闭环：

1. 后端新增 `/api/describe-image`。
2. App 端 `ServerBlueLMAdapter.describeImage()` 改为优先请求后端。
3. 用户上传纯图片时，卡片详情页的“识别结果”能显示后端返回的图片描述。
4. 摘要、标签、复习卡基于这段图片描述继续生成。

这样最容易在答辩里展示：“纯图片输入也能被理解并转成学习卡片”。

## 2026-07-04 后端改动日志

本次已经实际改动后端，队友接手时重点看下面几处。

### 1. 新增 URL 正文抽取工具

新增文件：

- `server/web_extractor.py`

能力：

- 使用移动端 Chrome UA 请求网页。
- 抽取 `title`、`description`、正文候选块。
- 支持 `article/main/content/rich_media_content` 等常见正文容器。
- 遇到微信、小红书、B站等动态/反爬页面时，不让任务失败，会回退为“网址域名 + 路径关键词 + 失败原因”。

### 2. 新增后端 URL 抽取接口

新增接口：

`POST /api/extract-url`

请求：

```json
{
  "url": "https://example.com/article"
}
```

返回：

```json
{
  "input_url": "https://example.com/article",
  "final_url": "https://example.com/article",
  "title": "网页标题",
  "text": "网页正文",
  "excerpt": "网页描述",
  "status": "success|partial|failed",
  "failure_reason": null,
  "ai_text": "可直接拼进大模型 prompt 的整理文本"
}
```

### 3. 后端处理流水线接入网页正文

修改文件：

- `server/routers/ai.py`

影响：

- `/api/notes/{note_id}/process` 现在会在 note 有 `url` 时自动调用 `extract_url_content()`。
- 生成摘要、标签、向量前，会合并 `raw_text + ocr_text + web_text`。
- 如果网页标题可用，并且卡片标题还是默认“未命名收藏”，会用网页标题回填 `source_title`。

### 4. 服务端囤积指数公式同步升级

修改文件：

- `server/routers/stats.py`

新公式使用 6 个因子：

- 采集压力
- 未读衰减
- 回流缺口
- 重复收藏
- 处理延迟
- 未处理率

并加入“压力越高权重略微抬升”的动态权重归一逻辑。返回的 `index_reason` 会包含前三个主要压力来源，便于答辩解释。

### 5. 注意事项

- 本次没有新增 Python 依赖，`requirements.txt` 不需要变化。
- URL 抽取不是万能浏览器渲染，无法绕过登录、强 JS 渲染和强反爬；这类页面会安全降级为 partial。
- 如果后续要进一步增强小红书/微信正文提取，建议单独加 Playwright 或平台分享解析接口，但这会明显增加服务器资源压力。

## 2026-07-04 App 学习闭环增强日志

本次没有新增或修改后端接口，队友不需要调整服务器。

本次撤掉上一版偏展示包装的面板，改为保留更贴近真实学习场景的三项功能：

### 1. 一键生成答辩式学习报告

位置：App “指数/学习统计”页。

能力：

- 基于本地 Room 数据自动生成学习报告。
- 汇总知识点数量、AI 已处理数量、复习完成情况、高频标签、知识关系、囤积指数和下一步建议。
- 支持一键复制，方便答辩或写项目总结。

### 2. 错题 / 易混点自动整理

位置：App “指数/学习统计”页。

能力：

- 从高相似关系、对比关系、重复收藏、高难度未完成复习卡中自动提炼易混点。
- 为每个易混点给出风险分、原因和复习建议。
- 只依赖本地已有卡片、关系和复习卡数据，不新增后端压力。

### 3. AI 回答来源标注增强

位置：卡片详情页的 AI 小助手回答区。

能力：

- AI 回答后显示来源证据卡。
- 来源包括当前卡片、原始网址、截图/OCR、关联卡片。
- 每个来源显示类型、可信度和摘要/OCR/原文片段，方便解释“AI 为什么这么回答”。

后端注意：

- 目前来源证据由 App 端基于本地数据生成，不需要新增接口。
- 如果后续后端接管问答，建议 `/api/answer-question` 返回结构化 `sources` 字段，格式可参考：`title/type/confidence/evidence`。

## 2026-07-04 视觉理解配置日志

本次 App 端新增了 `LLM_CHAT_PATH` 配置，用于兼容火山方舟 Responses API。

### 1. 为什么要加

火山方舟部分视觉理解模型的官方示例使用：

`POST /api/v3/responses`

请求体里使用：

- `type=input_image`
- `type=input_text`

而项目旧版直连接口默认使用 OpenAI Chat Completions 风格：

`POST /chat/completions`

请求体里使用：

- `messages`
- `type=image_url`

两种格式不一样，所以需要通过 `LLM_CHAT_PATH` 区分。

### 2. App 端配置示例

如果使用火山方舟视觉理解模型识别纯图片，`local.properties` 建议配置：

```properties
LLM_CHAT_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
LLM_CHAT_API_KEY=你的火山方舟APIKey
LLM_CHAT_MODEL=doubao-seed-2-1-pro-260628
LLM_CHAT_PATH=/responses
```

注意：

- 不要把真实 API Key 提交到仓库。
- 如果继续使用 OpenAI-compatible `/chat/completions` 服务，则 `LLM_CHAT_PATH` 可以不填，默认仍是 `/chat/completions`。
- `LLM_IMAGE_MODEL` 是图片生成模型，不负责看图；纯图片理解走 `LLM_CHAT_MODEL`。

### 3. 当前影响范围

修改文件：

- `app/build.gradle.kts`
- `app/src/main/java/com/huoyejia/ai/LlmRuntimeConfig.kt`
- `app/src/main/java/com/huoyejia/ai/RemoteBlueLMAdapter.kt`

影响：

- `describeImage()` 会在 `LLM_CHAT_PATH=/responses` 时按火山 Responses API 格式发送图片。
- 文本摘要、问答、动画 HTML 等直连聊天能力也可以走 `/responses`，并自动解析 `output_text` 或 `output[].content[].text`。
- 后端暂未新增 `/api/describe-image`，所以如果启用了 `SERVER_BASE_URL`，App 的图片理解仍会 fallback 到直连视觉模型。

## 2026-07-04 识别失败兜底日志

本次优化“网页抓取、图片识别、文字识别”失败时的返回内容，避免把乱码、域名路径或 mock 文案当成正常知识点。

### 1. 统一失败文案

当网页抓取、图片理解、OCR 或模型返回内容不可读时，统一返回类似：

```text
未识别成功，原因可能为网页需要登录、动态渲染、反爬限制、网络不可用、图片质量过低、未配置视觉理解模型或模型返回内容不可读。
```

### 2. App 端改动

修改文件：

- `app/src/main/java/com/huoyejia/domain/WebContentExtractor.kt`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt`
- `app/src/main/java/com/huoyejia/ai/MockBlueLMAdapter.kt`
- `app/src/main/java/com/huoyejia/ui/Screens.kt`
- `app/src/main/java/com/huoyejia/data/SeedData.kt`

影响：

- 网页无法抓取时，不再把“域名/路径关键词”伪装成正文。
- 图片理解没有真实视觉模型时，不再返回 mock 描述，而是明确提示未识别成功。
- 识别结果中出现明显乱码标记时，会替换成未识别成功提示。
- 卡片详情页如果图片没有识别结果，会展示失败原因而不是空白。
- 默认示例已更换为经济学、实验截图、英语写作三类案例。

### 3. 后端改动

修改文件：

- `server/web_extractor.py`
- `server/routers/ai.py`

影响：

- 后端 URL 抽取失败时返回明确失败原因。
- 后端处理流水线会过滤明显乱码识别结果。

## 2026-07-05 回流卡片隔离与图片识别诊断日志

本次优化复习卡“串题 / AI 污染”问题，并补充纯图片识别排查约定。

### 1. 回流卡片隔离

App 端生成复习卡时：

- 默认只围绕当前卡片生成问题。
- 只有当向量相似度 `>= 0.78` 且关系置信度 `>= 0.70` 时，才会带入 1 条强相关卡片。
- 关联卡片只能作为轻量对比/补充线索，不允许成为题目主体。

后端 `/api/generate-review-card` 已同步支持：

```json
{
  "current": "当前卡片正文",
  "current_title": "当前卡片标题",
  "related": ["最多一条强相关卡片正文"],
  "relation_hint": "single_note|contrast|cause_effect|same_topic|...",
  "isolation_policy": "only_current_note; related_optional_max_one; do_not_mix_topics"
}
```

后端实现要求：

- prompt 必须明确“题目必须围绕当前笔记标题或核心概念”。
- 不要把 `related` 当成连续对话上下文。
- 每次请求必须独立调用模型，不复用上一张卡片的 messages/history。

### 2. 纯图片识别排查

如果“上传纯图片不能理解”，优先检查：

- 优先配置独立视觉理解模型：`LLM_VISION_BASE_URL`、`LLM_VISION_API_KEY`、`LLM_VISION_MODEL`、`LLM_VISION_PATH`。
- 如果没有配置 `LLM_VISION_*`，App 才会退回使用 `LLM_CHAT_*` 做图片理解。
- 使用火山方舟 Responses API 时，`LLM_VISION_PATH` 或 `LLM_CHAT_PATH` 应为 `/responses`。
- `LLM_VISION_MODEL` 或兜底的 `LLM_CHAT_MODEL` 必须为支持 `input_image` 的视觉理解模型。
- `LLM_IMAGE_MODEL` 只负责生成图片，不负责看图。
- 如果启用了 `SERVER_BASE_URL`，当前后端仍需要新增 `/api/describe-image` 才能完全后端代理图片理解；否则 App 会 fallback 到直连视觉模型。

App 端独立视觉理解配置示例：

```properties
LLM_VISION_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
LLM_VISION_API_KEY=你的火山方舟APIKey
LLM_VISION_MODEL=doubao-seed-2-1-pro-260628
LLM_VISION_PATH=/responses
```

后端后续如要接管图片理解，建议新增：

```http
POST /api/describe-image
```

请求体建议：

```json
{
  "image_base64": "data:image/jpeg;base64,...",
  "context_text": "用户补充文本，可为空"
}
```

返回体建议：

```json
{
  "description": "图片可提炼出的中文学习内容；失败时返回“未识别成功，原因可能为...”"
}
```
