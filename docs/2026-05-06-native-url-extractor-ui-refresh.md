# 2026-05-06 原生网页读取与 UI 视觉优化说明

## 本次目标

- 采用方案 B：原生网络请求 + 客户端 HTML 解析，增强只粘贴网址时的正文读取能力。
- 仅优化现有 UI 的颜色、排版、形状、渐变和动效，不改变页面跳转、按钮行为、数据流程等界面逻辑。

## 网页读取改动

### `app/src/main/java/com/huoyejia/domain/WebContentExtractor.kt`

- 新增纯客户端网页提取器。
- 使用 `HttpURLConnection` 发起原生 GET 请求。
- 设置标准 Android Chrome `User-Agent`、`Accept`、`Accept-Language`、`Accept-Encoding`，降低普通站点拒绝访问概率。
- 支持 gzip/deflate 解压。
- 支持从响应头和 HTML `<meta charset>` 推断编码。
- 自动跟随重定向，并保存最终 URL。
- 客户端解析 HTML：
  - 提取 `title`、`description`、`og:title`、`og:description`。
  - 优先解析 `article`、`main`、正文类名容器。
  - 兜底合并 `<p>` 段落。
  - 过滤脚本、样式、导航、页脚、表单等噪声区域。
- 解析失败时保留网址、域名和路径关键词，避免 AI 收到完全空内容。

### `app/src/main/java/com/huoyejia/domain/NoteProcessor.kt`

- 将原来内置在 `NoteProcessor` 里的简单网页抓取逻辑替换为 `WebContentExtractor`。
- 处理卡片和重新生成卡片时都会使用新的原生网页提取结果。

### `app/src/main/java/com/huoyejia/util/UrlTools.kt`

- 扩展正文中的 URL 提取规则。
- 支持 `https://...`、`http://...`、`www.xxx.com`，以及 `example.com/path` 这类裸域名。
- 增强首尾中文括号、引号、标点清理。

## UI 视觉优化

### `app/src/main/java/com/huoyejia/ui/TechStyle.kt`

- 调整整体科技感配色，增强青蓝渐变。
- 背景增加轻量网格、节点连线和光晕层，提升科技感。
- 优化面板边框颜色和卡片透明度。

### `app/src/main/java/com/huoyejia/ui/theme/Theme.kt`

- 调整 Material 主题色板，统一为更清爽的蓝青科技风格。
- 保留原有 shape 尺寸体系，不改变页面结构。

### `app/src/main/java/com/huoyejia/ui/AppScaffold.kt`

- 底部导航栏增加玻璃拟态渐变和更圆润的顶部形状。
- 当前选中的导航符号增加轻量缩放动效和渐变底。
- 保持原有 5 个导航入口和点击逻辑不变。

## 验证

- 已通过 `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace`。

## 注意

- 本次没有引入后端，也没有使用隐藏 WebView。
- 方案 B 仍然无法稳定读取必须登录、验证码、强 JS 渲染或强反爬网页；这些场景需要后续使用隐藏 WebView 方案或提示用户手动粘贴正文。
