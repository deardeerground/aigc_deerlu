# 2026-05-06 卡片与采集问题修复说明

## 修改范围

本次只围绕用户反馈的 5 个问题做最小范围修复，没有重构现有功能和数据结构。

## 修改文件

### `app/src/main/java/com/huoyejia/MainActivity.kt`

- 卡片详情页的返回动作从“强制跳回回流箱并清空栈”改为 `popBackStack()`。
- 如果没有可返回的上一页，才兜底回到 `collections`，避免从收藏夹详情进入卡片后直接丢失原来的页面层级。
- 修复 `OnBackPressedCallback` 在路由变化时没有移除的问题，避免多个旧回调残留影响返回逻辑。
- 分享采集时读取 `Intent.EXTRA_SUBJECT` 作为标题兜底，并把乱码默认标题修正为“分享采集”。

### `app/src/main/java/com/huoyejia/FloatingCaptureService.kt`

- 浮窗采集的 URL 识别改为复用统一的 `UrlTools.extractFirstUrl()`，避免浮窗和主采集页解析规则不一致。

### `app/src/main/java/com/huoyejia/ui/Screens.kt`

- 卡片详情页顶部“返回”按钮改成更轻量的 `<` 按钮。
- 采集成功后的黑色居中提示改成固定 1.5 秒自动消失，不再依赖全局 `isBusy` 状态，避免后台 AI 处理较久时提示挂住。
- AI 小助手的预放置问题不再引用回流卡生成的问题，也不再使用关联卡片内容；现在只基于当前卡片标题、摘要和标签生成问题。

### `app/src/main/java/com/huoyejia/ui/CollectionDetailScreen.kt`

- 收藏夹详情页左上返回也改为优先 `popBackStack()`，没有上一页时才兜底回回流箱。
- 这样从“回流箱 -> 收藏夹详情 -> 卡片详情”逐级返回时层级保持正确。

### `app/src/main/java/com/huoyejia/ui/AppScaffold.kt`

- 底部 5 个主导航页中，如果当前已经在该页，再次点击同一个导航项不会重复执行 `navigate()`。
- 这样可以避免当前页重复出现切页动画。

### `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt`

- 网址采集的读取逻辑改为先统一规范化 URL，再抓取网页。
- 支持按响应头 charset 解码，支持 gzip/deflate 响应，优先提取网页标题、描述和正文。
- 如果网页抓取失败，会写入网址、站点域名和路径关键词作为兜底内容，避免摘要、标签、问答出现“未检测到/空内容”。

### `app/src/main/java/com/huoyejia/util/UrlTools.kt`

- URL 清理时额外移除常见尾部括号和中文引用符，减少从分享文本里提取到脏 URL 的概率。

## 验证记录

- 已执行 `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace`，构建通过。
- 额外尝试执行 `.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace`，但 Gradle wrapper 锁文件 `E:\aigc_zly\.gradle\wrapper\dists\...\gradle-8.7-bin.zip.lck` 返回“拒绝访问”，未能启动单元测试任务。

## 手动核对点

- 从收藏夹详情打开卡片，点击 `<` 应回到该收藏夹详情；从其他入口打开则按原栈返回。
- 只保存网址时，卡片详情中的原文材料和 AI 处理内容不再只剩空文本或默认标题。
- 点击采集保存后，黑色“已保存，正在后台整理”提示约 1.5 秒后消失。
- AI 小助手预置问题均带有“当前卡片/仅根据当前卡片”的语义，不再引导到关联卡片。
- 在底部主导航当前页重复点击同一图标，不触发新的导航动画。
