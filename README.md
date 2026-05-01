# 活页夹 Android MVP

最小可演示闭环：

1. 打开 App 后自动写入 3 条启动假数据。
2. 在「采集」页保存文本或模拟截图导入。
3. `MockBlueLMAdapter` 自动生成摘要、标签、主题、重要度和 embedding。
4. `NoteProcessor` 召回 Top 3 历史笔记，生成 `note_relations` 和 `review_cards`。
5. 在「复习」页完成回流卡后，笔记复习状态和囤积指数会更新。
6. 在「讲解」页可调用大模型生成知识讲解、带图 PPT 和动画分镜。
7. 讲解包生成后可一键导出 `.pptx`，页面会按封面、三卡片、时间线、对比、故事板等结构轮换，并包含透明 PNG 图片和基础切换动画。
8. 可导出一个可播放的 HTML 小动画讲解文件；配置聊天模型后会优先由远程 AI 直接生成完整动画页面，手机或浏览器可打开。
9. 可接入异步视频生成模型，把讲解包生成并下载为 MP4。
10. 「采集」页支持悬浮窗模式，可退出 App 后在微信、小红书等其他 App 上方粘贴学习内容。

运行方式：

```powershell
./gradlew.bat :app:assembleDebug
```

当前工程没有提交 Gradle Wrapper 二进制文件；如果本机没有 Gradle，请用 Android Studio 打开项目并同步依赖，或先生成 wrapper。

真实模型配置：

在 `local.properties` 中追加以下字段，重新 Sync / Build 后 App 会自动从 Mock 切到远程大模型。

如果聊天模型和 embedding 模型来自同一平台，可以继续使用统一配置：

```properties
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-...
LLM_CHAT_MODEL=gpt-4.1-mini
LLM_EMBEDDING_MODEL=text-embedding-3-small
```

如果要混用不同平台，可以分别配置聊天和向量接口：

```properties
LLM_CHAT_BASE_URL=https://api.deepseek.com
LLM_CHAT_API_KEY=sk-...
LLM_CHAT_MODEL=deepseek-chat

LLM_EMBEDDING_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
LLM_EMBEDDING_API_KEY=ark-...
LLM_EMBEDDING_MODEL=your-embedding-endpoint-id
```

如果要让 PPT 里的图片来自真实出图接口，可以继续配置图像生成接口。未配置或请求失败时，App 会自动生成本地透明 PNG 图标作为兜底素材。

```properties
LLM_IMAGE_BASE_URL=https://api.openai.com/v1
LLM_IMAGE_API_KEY=sk-...
LLM_IMAGE_MODEL=gpt-image-1
```

如果要接视频生成模型，当前默认按火山方舟 Seedance 的 `contents/generations/tasks` 异步接口适配。App 会向 `VIDEO_CREATE_PATH` 提交 `content: [{type:"text"}]`，读取返回里的 `id` / `task_id`，再按 `VIDEO_STATUS_PATH` 轮询，拿到 `url` / `video_url` / `content.video_url` / `output[].url` 后下载 MP4。

```properties
VIDEO_BASE_URL=https://ark.cn-beijing.volces.com
VIDEO_API_KEY=ark-...
VIDEO_MODEL=ep-20260429125645-qrwkd
VIDEO_CREATE_PATH=/api/v3/contents/generations/tasks
VIDEO_STATUS_PATH=/api/v3/contents/generations/tasks/{id}
```

也可以不写 `VIDEO_API_KEY`，改用环境变量：

```powershell
$env:ARK_API_KEY="ark-..."
.\gradlew.bat :app:assembleDebug
```

国内 API 示例：

- DeepSeek 做聊天/结构化生成：适合笔记摘要、关系判断、回流卡、讲解包和 HTML 动画生成。
- 火山方舟（豆包 / Seedream）做 embedding 或图像能力：适合向量召回和 PPT 插图生成。

说明：

- 适配器按 OpenAI-compatible 接口实现，使用 `/chat/completions`、`/embeddings` 和可选的 `/images/generations`。
- 现在支持聊天接口和 embedding 接口分开配置，因此可以直接使用“DeepSeek 聊天 + 火山方舟 embedding”的组合。
- 未配置或请求失败时，会自动回退到 `MockBlueLMAdapter`，不影响演示。
- 「讲解」页当前会生成知识讲解文案、PPT 大纲、动画分镜，并能导出 `.pptx` 文件。
- PPT 文件会保存到 App 外部私有 Documents 目录，导出后页面会显示完整路径并提供系统分享入口。
- PPT 内置的是 PowerPoint 基础切换动画；小动画讲解会单独导出为 HTML。配置 `LLM_CHAT_*` 后，导出时会请求聊天模型生成包含 CSS keyframes / JS 自动播放 / 多场景构图的完整动画 HTML；模型不可用时才回退到本地模板。
- MP4 视频生成会保存到 App 外部私有 Movies 目录，讲解页会显示完整路径并提供系统分享入口。Seedance 请求默认包含 `generate_audio=true`、`ratio=16:9`、`duration=11`、`watermark=false`。
- 透明 PNG 兜底图标由 App 本地绘制生成；真实图片来源建议优先使用带明确许可证的图标或图像服务，例如 Apache 2.0 许可的 Material Symbols / Material Icons。
- 桌面 `PPTmoban.pptx` 的模板色彩、16:9 尺寸、MiSans 字体倾向和部分背景图已经固化到 App assets 中，手机运行时不依赖桌面文件。
- 悬浮窗模式需要 Android 的“显示在其他应用上层”权限；第一次点击「开启悬浮窗模式」会跳系统设置授权。
