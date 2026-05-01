package com.huoyejia.domain

import android.content.Context
import android.os.Environment
import com.huoyejia.ai.VideoEndpointConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class VideoGenerationService(
    private val context: Context,
    private val config: VideoEndpointConfig
) {
    suspend fun generate(pack: ExplainPack): File = withContext(Dispatchers.IO) {
        require(config.isComplete) { "视频模型未配置，请先填写 VIDEO_BASE_URL / VIDEO_API_KEY / VIDEO_MODEL。" }
        val prompt = pack.toVideoPrompt()
        val createResponse = postJson(
            path = config.createPath,
            payload = JSONObject()
                .put("model", config.model)
                .put(
                    "content",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", prompt)
                        )
                )
                .put("generate_audio", true)
                .put("ratio", "16:9")
                .put("duration", 11)
                .put("watermark", false)
        )
        val directUrl = createResponse.findVideoUrl()
        val videoUrl = directUrl ?: pollForVideoUrl(createResponse.findTaskId())
        downloadVideo(videoUrl, pack.title)
    }

    private suspend fun pollForVideoUrl(taskId: String): String {
        require(taskId.isNotBlank()) { "视频生成接口没有返回任务 id 或视频 URL。" }
        repeat(144) {
            delay(5_000)
            val response = getJson(config.statusPath.replace("{id}", taskId))
            response.findVideoUrl()?.let { return it }
            val status = response.findStatus()
            if (status in setOf("failed", "error", "cancelled", "canceled")) {
                throw IllegalStateException("视频生成失败：$status")
            }
        }
        throw IllegalStateException("视频生成超时。")
    }

    private fun downloadVideo(url: String, title: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(dir, "${title.safeFileName()}_video_$timestamp.mp4")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.inputStream.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        return file
    }

    private fun postJson(path: String, payload: JSONObject): JSONObject {
        val connection = openConnection(path)
        connection.requestMethod = "POST"
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        return readJson(connection)
    }

    private fun getJson(path: String): JSONObject {
        val connection = openConnection(path)
        connection.requestMethod = "GET"
        return readJson(connection)
    }

    private fun openConnection(path: String): HttpURLConnection {
        val normalizedPath = if (path.startsWith("http")) path else config.baseUrl + path.ensurePrefix("/")
        return (URL(normalizedPath).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(stream.reader(Charsets.UTF_8)).use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Video request failed: ${connection.responseCode} $body")
        }
        return JSONObject(body)
    }
}

private fun ExplainPack.toVideoPrompt(): String {
    return """
        全程生成一段 11 秒、16:9 横屏中文教学讲解视频，风格像高质量课程短视频。
        标题：$title
        开场：$hook
        讲解：$conciseExplanation
        场景：
        ${animationScenes.joinToString("\n") { "- ${it.title}: 画面=${it.visual}；旁白=${it.narration}" }}
        结论：$takeaway
        时间安排：
        0-2秒：用一个强视觉开场引出标题，画面中部出现标题文字，镜头缓慢推进。
        2-5秒：用知识节点、连线或卡片展示核心概念，关键词依次高亮。
        5-8秒：展示因果、对比或迁移关系，镜头轻微横移，画面底部出现同步字幕。
        8-11秒：收束为一句结论，画面定格在结构化知识卡片上。
        视觉要求：清晰中文字幕、知识节点连线、关键词高亮、结构化卡片、适合课堂展示；不要堆满文字；背景声音使用平静清晰的中文女生音色。
    """.trimIndent()
}

private fun JSONObject.findTaskId(): String {
    return optString("id")
        .ifBlank { optString("task_id") }
        .ifBlank { optString("taskId") }
        .ifBlank { optJSONObject("data")?.findTaskId().orEmpty() }
}

private fun JSONObject.findStatus(): String {
    return optString("status")
        .ifBlank { optString("state") }
        .ifBlank { optJSONObject("data")?.findStatus().orEmpty() }
        .lowercase()
}

private fun JSONObject.findVideoUrl(): String? {
    listOf("video_url", "videoUrl", "url", "download_url", "downloadUrl").forEach { key ->
        optString(key).takeIf { it.startsWith("http") }?.let { return it }
        optJSONObject(key)?.optString("url")?.takeIf { it.startsWith("http") }?.let { return it }
    }
    optJSONObject("data")?.findVideoUrl()?.let { return it }
    optJSONObject("result")?.findVideoUrl()?.let { return it }
    optJSONObject("output")?.findVideoUrl()?.let { return it }
    optJSONObject("content")?.findVideoUrl()?.let { return it }
    optJSONArray("output")?.findVideoUrl()?.let { return it }
    optJSONArray("data")?.findVideoUrl()?.let { return it }
    optJSONArray("content")?.findVideoUrl()?.let { return it }
    return null
}

private fun JSONArray.findVideoUrl(): String? {
    for (index in 0 until length()) {
        val item = opt(index)
        if (item is JSONObject) item.findVideoUrl()?.let { return it }
        if (item is String && item.startsWith("http")) return item
    }
    return null
}

private fun String.ensurePrefix(prefix: String): String = if (startsWith(prefix)) this else "$prefix$this"

private fun String.safeFileName(): String = replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(40).ifBlank { "huoyejia" }
