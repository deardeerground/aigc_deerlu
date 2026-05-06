# 2026-05-06 问题排查文档

## 结论先说

我对照了当前工作区 `git diff` 和采集/AI 数据流，结论如下：

- 我这几轮改动没有修改 `NoteProcessor.kt`、`RemoteBlueLMAdapter.kt`、`MockBlueLMAdapter.kt`、`SearchService.kt` 等 AI 摘要、网页抓取、标签归类、相似度计算核心逻辑。
- 已确认是我改动引入或不符合预期的部分：
  - 全局 UI 颜色被我改成了偏亮蓝紫科技风，你现在更希望浅蓝色或蓝黑色，需要再收敛配色。
  - “设置横幅通知权限”跳转目前用的是 Android 通用应用通知设置页，和你希望直接进入的具体系统页不一致。
- 你反馈的 AI 摘要、网址采集、进度条、标签归类、相似比较“降智”等问题，目前从代码证据看，主要来自现有业务逻辑本身，而不是这次 UI/通知改动直接破坏代码结构。
- 但这次 UI 改动确实覆盖了很多显示层文件，所以视觉上可能更容易暴露旧问题，例如 AI 摘要卡片本来就没有使用带展开功能的组件。

## 本次我实际改过哪些文件

当前 `git diff --name-only` 显示我改过这些文件：

- `app/src/main/java/com/huoyejia/HuoyejiaViewModel.kt`
- `app/src/main/java/com/huoyejia/MainActivity.kt`
- `app/src/main/java/com/huoyejia/service/DailyReviewAlarm.kt`
- `app/src/main/java/com/huoyejia/service/NotificationScheduler.kt`
- `app/src/main/java/com/huoyejia/ui/AppScaffold.kt`
- `app/src/main/java/com/huoyejia/ui/CollectionDetailScreen.kt`
- `app/src/main/java/com/huoyejia/ui/CollectionListScreen.kt`
- `app/src/main/java/com/huoyejia/ui/Screens.kt`
- `app/src/main/java/com/huoyejia/ui/SettingsScreen.kt`
- `app/src/main/java/com/huoyejia/ui/theme/Theme.kt`
- `app/src/main/java/com/huoyejia/ui/TechStyle.kt`
- `docs/settings-notification-changes.md`
- `docs/README-changes.md`
- `docs/2026-05-06-regression-investigation.md`

没有改过这些核心 AI/采集处理文件：

- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt`
- `app/src/main/java/com/huoyejia/ai/RemoteBlueLMAdapter.kt`
- `app/src/main/java/com/huoyejia/ai/MockBlueLMAdapter.kt`
- `app/src/main/java/com/huoyejia/domain/SearchService.kt`
- `app/src/main/java/com/huoyejia/data/local/Entities.kt`

## 问题逐项排查

### 1. UI 配色：你希望浅蓝色或蓝黑色

这部分是我改动导致的风格不完全符合预期。

相关文件：

- `app/src/main/java/com/huoyejia/ui/theme/Theme.kt`
- `app/src/main/java/com/huoyejia/ui/TechStyle.kt`
- `app/src/main/java/com/huoyejia/ui/AppScaffold.kt`
- `app/src/main/java/com/huoyejia/ui/Screens.kt`
- `app/src/main/java/com/huoyejia/ui/SettingsScreen.kt`

当前状态：

- 主题主色在 `Theme.kt` 使用了亮蓝 `0xFF2458F2`。
- 辅助色使用了浅紫 `0xFF7C5CFF`。
- `TechPrimaryGradient` 使用蓝、紫、青渐变。

问题：

- 现在整体偏“亮蓝紫”，你反馈更希望“浅蓝色”或“蓝黑色”。

建议修复：

- 方案 A：浅蓝科技风
  - 背景保持白色和极浅蓝。
  - 渐变从蓝紫青改成浅蓝、天蓝、冰蓝。
  - 板块用更浅的蓝灰玻璃色。
- 方案 B：蓝黑科技风
  - 背景改为深蓝黑。
  - 卡片改为半透明深蓝板块。
  - 文字改为白色/浅蓝。
  - 视觉更酷，但阅读压力更大。

### 2. AI 摘要展开功能“没了”

结论：不是我这次改动导致的。当前代码在我改之前就是这样。

证据：

- 当前详情页 AI 摘要使用的是：

```kotlin
AssistCard("AI 摘要", note.summary.orEmpty(), markdown = true)
```

位置：

- `app/src/main/java/com/huoyejia/ui/Screens.kt:1135`

这个 `AssistCard` 本身没有摘要展开/收起逻辑。

同一个文件里确实存在一个带展开逻辑的 `AiSummaryCard`：

- `app/src/main/java/com/huoyejia/ui/Screens.kt:1783`

但详情页没有调用它。

我用 `git show HEAD:...` 对比了修改前版本，修改前也已经是：

```kotlin
AssistCard("AI 摘要", note.summary.orEmpty(), markdown = true)
```

所以这不是 UI 改色时改坏的，而是旧代码里带展开能力的组件没有被接入详情页。

建议修复：

- 把详情页的 `AssistCard("AI 摘要", ...)` 改成 `AiSummaryCard(note.summary.orEmpty())`。
- 如果还要保留 markdown 支持，可以把 `AiSummaryCard` 升级成支持 markdown 的展开卡。

### 3. AI 摘要和原文看起来变成同一个东西

结论：这是现有数据模型和显示逻辑导致的，不是我这次改动直接造成。

相关代码：

- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt`
- `app/src/main/java/com/huoyejia/ui/Screens.kt`

关键逻辑：

```kotlin
val originalText = cleanOriginalText(note.rawText ?: note.noteContent.ifBlank { "暂无原文内容" })
```

位置：

- `app/src/main/java/com/huoyejia/ui/Screens.kt:1221`

也就是说：

- 如果 `rawText` 有值，原文显示 `rawText`。
- 如果 `rawText` 为空，原文显示 `noteContent`。

对于“只输入网址”的采集：

- `rawText` 可能为空。
- 网页抓取后的正文会被写入 `noteContent`。
- 原文卡片就会显示 `noteContent`。
- 如果远程摘要失败或返回空，`RemoteBlueLMAdapter` 会 fallback 成 `noteContent.take(60)`。
- 这样 AI 摘要和原文开头就会看起来非常像，甚至像同一个东西。

相关 fallback：

```kotlin
summary = response.optString("summary").ifBlank { noteContent.take(60) }
```

位置：

- `app/src/main/java/com/huoyejia/ai/RemoteBlueLMAdapter.kt`

建议修复：

- 数据层分开保存：
  - 用户输入原文：`rawText`
  - 网页抓取正文：`webText` 或 `extractedText`
  - AI 摘要：`summary`
- UI 层分开展示：
  - “用户输入”
  - “网页正文”
  - “AI 摘要”
- 如果 AI 摘要为空，不要直接用原文开头冒充摘要，应该显示“摘要生成失败/待生成”。

### 4. 网址采集不能正常识别

结论：不是我这次改动造成，但当前逻辑确实比较脆弱。

相关代码：

- `app/src/main/java/com/huoyejia/ui/Screens.kt:259`
- `app/src/main/java/com/huoyejia/MainActivity.kt:309`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:253`

当前手动采集页只在 URL 输入框满足下面条件时，才认为是网页采集：

```kotlin
resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://")
```

问题：

- `www.xxx.com` 不会被识别为网址。
- 用户把网址粘贴到正文框，而不是网址框时，手动采集页不会自动提取。
- 一段文字里包含网址时，手动采集页不会自动提取。
- `fetchWebText` 只做简单 HTTP GET 和 HTML 标签剥离：
  - 很多网站需要 JS 渲染，抓不到正文。
  - 很多网站会反爬。
  - 很多网站不是 UTF-8，可能乱码。
  - 有些移动端/公众号/B站页面不能靠简单 GET 拿到正文。

建议修复：

- 新增统一函数 `normalizeUrlOrNull(text)`：
  - 支持 `http://`
  - 支持 `https://`
  - 支持 `www.xxx.com` 自动补 `https://`
  - 支持从一段文本中提取第一个 URL
- 手动采集时：
  - URL 输入框优先。
  - URL 输入框为空时，从正文里提取 URL。
- 网页抓取失败时：
  - 不要直接静默降级。
  - 给用户显示“网页正文抓取失败，仅保存链接和输入内容”。

### 5. 采集进度条一下跳到 100%

结论：不是我这次 UI 改动造成，当前进度逻辑本来就是快速跳变式。

相关代码：

- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt`
- `app/src/main/java/com/huoyejia/ui/Screens.kt`

进度更新点：

- `0.05`：已保存
- `0.12`：开始生成摘要和标签
- `0.22`：读取截图和网页内容
- `0.42`：理解内容
- `0.62`：生成摘要和标签
- `0.76`：保存知识索引
- `0.86`：查找关联卡片
- `0.94`：生成复习卡
- `1.00`：整理完成

位置：

- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:80`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:94`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:96`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:101`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:110`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:131`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:142`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:165`
- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:167`

为什么会“蹦到 100%”：

- 如果远程模型不可用，代码会 fallback 到 mock，本地 mock 很快完成。
- 如果网页抓取失败，也会很快跳过。
- 进度不是基于真实耗时，而是每一步完成后立即设置一个固定百分比。
- UI 显示的是 active task 的平均进度：

```kotlin
val averageProgress = displayItems.map { it.progress }.average()
```

位置：

- `app/src/main/java/com/huoyejia/ui/Screens.kt:388`

建议修复：

- 每个阶段设置最小展示时间，例如至少显示 300-500ms。
- 如果网页抓取失败，停在明确状态，不要直接进入 100%。
- 进度条文案显示真实状态，而不是只显示百分比。
- 完成后可以显示“已完成，正在生成卡片预览”，再延迟消失。

### 6. 标签归类、相似比较像“降智”

结论：不是我这次改动导致。当前代码里相似度逻辑本身就是弱实现。

核心证据：

```kotlin
val ranked = historical.take(10).map {
    RelatedNote(it, "similar", 0.5f)
}.sortedByDescending { it.confidence }
```

位置：

- `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt:105`

问题：

- 虽然前面生成了 embedding：

```kotlin
val vector = blueLM.embed(content)
```

但后续没有拿这个 vector 和历史 embedding 做真实 cosine similarity。

结果：

- 所有历史候选都被赋值成固定 `0.5f`。
- `maxSimilarity` 很多时候也是固定 0.5。
- 后续 duplicate score、relation 分类都基于这个弱输入。
- 标签和相似度看起来会很“假”或“降智”。

另外，如果远程模型没有配置好，`RemoteBlueLMAdapter` 会 fallback 到 `MockBlueLMAdapter`，标签和摘要就会变成关键词启发式逻辑。

建议修复：

- 用当前 embedding 和历史 embedding 计算 cosine similarity。
- 按真实相似度排序。
- 只对超过阈值的候选调用 `classifyRelation`。
- 记录模型是否 fallback 到 mock，并在 UI 上提示“当前使用离线 mock 能力”。

### 7. 设置横幅通知权限跳转不对

结论：这是我改动中确实需要调整的地方。

当前代码：

```kotlin
Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
```

位置：

- `app/src/main/java/com/huoyejia/ui/SettingsScreen.kt:393`

问题：

- 这个 Intent 是 Android 通用“应用通知设置”。
- 不同厂商会跳到不同层级。
- vivo 等机型未必能直接进入“顶部横幅/悬浮通知”那个页面。
- 你说“直接跳转到这个界面更好”，说明当前跳转层级不符合你测试机上的目标页面。

建议修复：

- 如果你希望跳到应用详情页，用：

```kotlin
Settings.ACTION_APPLICATION_DETAILS_SETTINGS
```

- 如果希望跳到具体通知渠道页，Android 8+ 可用：

```kotlin
Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
Settings.EXTRA_APP_PACKAGE
Settings.EXTRA_CHANNEL_ID
```

- 但 vivo 的“横幅通知/顶部预览”页面通常不是 Android 标准 Intent，可能只能跳到应用通知设置或应用详情页，再用文案引导用户手动点进去。

## 我这次改动和问题的对应关系

| 问题 | 是否由我这次改动直接导致 | 说明 |
|---|---:|---|
| UI 想换浅蓝/蓝黑 | 是 | 我把主题改成了亮蓝紫科技风，但不完全符合你新的偏好。 |
| 横幅通知权限跳转不对 | 是 | 我用了 Android 通用应用通知设置页，目标层级需要按你的测试机调整。 |
| AI 摘要展开功能没有 | 否 | 修改前详情页就没有调用 `AiSummaryCard`，而是用无展开逻辑的 `AssistCard`。 |
| 摘要和原文像同一个 | 否 | 现有数据模型会在 URL-only 场景用 `noteContent` 作为原文 fallback，摘要失败时又用 `noteContent.take(60)` fallback。 |
| 网址采集识别差 | 否 | 手动采集只识别 `http://`/`https://` URL 输入框，不会自动处理 `www.` 或正文中的 URL。 |
| 采集进度跳 100% | 否 | 进度逻辑本来就是固定节点快速更新，mock/fallback 或抓取失败会非常快完成。 |
| 标签归类/相似比较降智 | 否 | 当前相似度没有真实使用 embedding，历史候选固定 `0.5f`。 |

## 建议下一步修复顺序

1. 先修“真实功能”：
   - AI 摘要详情页接入可展开摘要卡。
   - URL 自动识别和 normalize。
   - 原文、网页正文、AI 摘要分开展示。
   - 进度条按真实阶段展示，避免瞬间 100%。
   - 相似度改为真实 embedding cosine similarity。
2. 再修“设置跳转”：
   - 根据你的测试机目标页面，改成应用详情页或通知渠道页。
3. 最后统一视觉：
   - 主题改为你更喜欢的浅蓝或蓝黑。
   - 保留动态背景，但降低紫色占比。

## 我建议优先改的代码点

- `Screens.kt:1135`
  - `AssistCard("AI 摘要", ...)` 改为可展开摘要组件。
- `Screens.kt:259`
  - 手动采集的 URL 判断改为统一 URL 识别函数。
- `MainActivity.kt:390`
  - 抽出 URL 提取函数，给手动采集页复用。
- `NoteProcessor.kt:98`
  - 网页抓取失败时返回明确状态。
- `NoteProcessor.kt:105`
  - 用真实 embedding 相似度替代固定 `0.5f`。
- `NoteProcessor.kt:253`
  - 增强网页抓取，支持 charset、失败提示、常见站点策略。
- `SettingsScreen.kt:391`
  - 横幅通知设置跳转改成你测试机更接近的目标页面。
- `Theme.kt` 和 `TechStyle.kt`
  - 把当前蓝紫配色调整为浅蓝或蓝黑。
