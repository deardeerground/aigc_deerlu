package com.huoyejia.domain

import android.content.Context
import android.os.Environment
import com.huoyejia.ai.BlueLMAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AnimationExportService(
    private val context: Context,
    private val blueLM: BlueLMAdapter
) {
    suspend fun export(pack: ExplainPack): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(dir, "${pack.title.safeFileName()}_animation_$timestamp.html")
        val remoteHtml = blueLM.generateAnimationHtml(pack)?.normalizeHtml()
        file.writeText(remoteHtml ?: pack.toHtml(), Charsets.UTF_8)
        file
    }
}

private fun String.normalizeHtml(): String? {
    val cleaned = trim()
        .removePrefix("```html")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf("<!doctype", ignoreCase = true).takeIf { it >= 0 }
        ?: cleaned.indexOf("<html", ignoreCase = true)
    val end = cleaned.lastIndexOf("</html>", ignoreCase = true)
    if (start < 0 || end < start) return null
    val html = cleaned.substring(start, end + "</html>".length)
    if (!html.contains("@keyframes") || !html.contains("<style", ignoreCase = true)) return null
    return html
}

private fun ExplainPack.toHtml(): String {
    val scenes = animationScenes.ifEmpty {
        listOf(
            AnimationScene("开场", hook, conciseExplanation),
            AnimationScene("结构", "把知识点拆成原因、联系和应用。", takeaway),
            AnimationScene("收束", "画面聚焦到一句结论。", takeaway)
        )
    }.take(5)
    val sceneHtml = scenes.mapIndexed { index, scene ->
        val delay = index * 5
        """
        <section class="scene scene-${index + 1}" style="animation-delay:${delay}s">
          <div class="kicker">Scene ${index + 1}</div>
          <h2>${scene.title.html()}</h2>
          <div class="stage">
            <div class="node node-a"></div>
            <div class="line"></div>
            <div class="node node-b"></div>
            <div class="caption">${scene.visual.html()}</div>
          </div>
          <p>${scene.narration.html()}</p>
        </section>
        """.trimIndent()
    }.joinToString("\n")
    val totalDuration = maxOf(scenes.size * 5, 15)
    return """
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title.html()} - 动画讲解</title>
  <style>
    *{box-sizing:border-box} body{margin:0;background:#0e1116;color:#fff;font-family:MiSans,"Microsoft YaHei",sans-serif;overflow:hidden}
    .deck{position:relative;width:100vw;height:100vh;background:radial-gradient(circle at 18% 22%,#3a1e20 0,#151820 38%,#090b10 100%)}
    .brand{position:absolute;left:28px;top:22px;z-index:20;color:#ffc000;font-weight:800;letter-spacing:.08em}
    .progress{position:absolute;left:0;bottom:0;height:6px;background:#c0584d;animation:progress ${totalDuration}s linear forwards;z-index:30}
    .title{position:absolute;left:7vw;top:11vh;width:58vw;z-index:10;animation:intro 1.4s ease both}
    .title h1{font-size:clamp(32px,6vw,78px);line-height:1.02;margin:0 0 16px;font-weight:900}
    .title p{font-size:clamp(15px,2vw,24px);line-height:1.55;color:#eef2f5;max-width:760px}
    .scene{position:absolute;inset:0;padding:18vh 8vw 9vh;opacity:0;transform:translateX(60px);animation:scene 5s ease-in-out forwards}
    .scene h2{font-size:clamp(28px,4.8vw,64px);margin:0 0 24px;max-width:760px}
    .scene p{font-size:clamp(16px,2.1vw,25px);line-height:1.55;max-width:820px;color:#eef2f5}
    .kicker{display:inline-block;background:#ffc000;color:#000;padding:8px 16px;border-radius:999px;font-weight:900;margin-bottom:18px}
    .stage{position:absolute;right:7vw;top:20vh;width:min(32vw,420px);height:min(32vw,420px)}
    .node{position:absolute;width:120px;height:120px;border-radius:28px;background:#c0584d;box-shadow:0 24px 70px rgba(192,88,77,.45)}
    .node-a{left:0;top:40px;animation:float 2.4s ease-in-out infinite}
    .node-b{right:0;bottom:40px;background:#66648b;animation:float 2.4s ease-in-out .4s infinite}
    .line{position:absolute;left:85px;top:160px;width:230px;height:10px;background:#ffc000;transform:rotate(34deg);transform-origin:left center;animation:grow 1.2s ease both}
    .caption{position:absolute;left:8%;right:8%;bottom:-36px;background:rgba(255,255,255,.92);color:#111;padding:14px 18px;border-radius:18px;line-height:1.45;font-weight:700}
    .scene-2 .node-a{border-radius:999px}.scene-2 .node-b{border-radius:999px}.scene-3 .stage{transform:rotate(-4deg)}.scene-4 .stage{transform:scale(.92)}.scene-5 .stage{transform:rotate(3deg)}
    @keyframes intro{from{opacity:0;transform:translateY(24px)}to{opacity:1;transform:none}}
    @keyframes scene{0%{opacity:0;transform:translateX(60px)}12%,82%{opacity:1;transform:none}100%{opacity:0;transform:translateX(-60px)}}
    @keyframes float{0%,100%{transform:translateY(0)}50%{transform:translateY(-18px)}}
    @keyframes grow{from{transform:rotate(34deg) scaleX(0)}to{transform:rotate(34deg) scaleX(1)}}
    @keyframes progress{from{width:0}to{width:100vw}}
    @media(max-width:720px){.title{width:86vw}.stage{right:9vw;top:54vh;width:70vw;height:32vh}.node{width:78px;height:78px}.line{left:58px;top:106px;width:180px}.scene{padding:13vh 7vw}.caption{font-size:13px}}
  </style>
</head>
<body>
  <main class="deck">
    <div class="brand">HUOYEJIA ANIMATION</div>
    <div class="title"><h1>${title.html()}</h1><p>${hook.html()}</p></div>
    $sceneHtml
    <div class="progress"></div>
  </main>
</body>
</html>
    """.trimIndent()
}

private fun String.safeFileName(): String = replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40).ifBlank { "huoyejia" }

private fun String.html(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
