package com.huoyejia.ai

import com.huoyejia.BuildConfig

data class LlmEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val path: String = ""
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class LlmRuntimeConfig(
    val chat: LlmEndpointConfig,
    val embedding: LlmEndpointConfig,
    val image: ImageEndpointConfig,
    val video: VideoEndpointConfig
) {
    val isComplete: Boolean
        get() = chat.isComplete && embedding.isComplete

    val chatReady: Boolean
        get() = chat.isComplete

    val embeddingReady: Boolean
        get() = embedding.isComplete

    val imageReady: Boolean
        get() = image.isComplete

    val videoReady: Boolean
        get() = video.isComplete

    companion object {
        fun fromBuildConfig(): LlmRuntimeConfig {
            return LlmRuntimeConfig(
                chat = LlmEndpointConfig(
                    baseUrl = BuildConfig.LLM_CHAT_BASE_URL.trim().removeSuffix("/"),
                    apiKey = BuildConfig.LLM_CHAT_API_KEY.trim(),
                    model = BuildConfig.LLM_CHAT_MODEL.trim()
                ),
                embedding = LlmEndpointConfig(
                    baseUrl = BuildConfig.LLM_EMBEDDING_BASE_URL.trim().removeSuffix("/"),
                    apiKey = BuildConfig.LLM_EMBEDDING_API_KEY.trim(),
                    model = BuildConfig.LLM_EMBEDDING_MODEL.trim(),
                    path = BuildConfig.LLM_EMBEDDING_PATH.trim().ifBlank { "/embeddings" }
                ),
                image = ImageEndpointConfig(
                    baseUrl = BuildConfig.LLM_IMAGE_BASE_URL.trim().removeSuffix("/"),
                    apiKey = BuildConfig.LLM_IMAGE_API_KEY.trim(),
                    model = BuildConfig.LLM_IMAGE_MODEL.trim(),
                    path = BuildConfig.LLM_IMAGE_PATH.trim().ifBlank { "/images/generations" },
                    size = BuildConfig.LLM_IMAGE_SIZE.trim().ifBlank { "1024x1024" },
                    watermark = BuildConfig.LLM_IMAGE_WATERMARK
                ),
                video = VideoEndpointConfig(
                    baseUrl = BuildConfig.VIDEO_BASE_URL.trim().removeSuffix("/"),
                    apiKey = BuildConfig.VIDEO_API_KEY.trim(),
                    model = BuildConfig.VIDEO_MODEL.trim(),
                    createPath = BuildConfig.VIDEO_CREATE_PATH.trim().ifBlank { "/videos/generations" },
                    statusPath = BuildConfig.VIDEO_STATUS_PATH.trim().ifBlank { "/videos/{id}" },
                    generateAudio = BuildConfig.VIDEO_GENERATE_AUDIO,
                    ratio = BuildConfig.VIDEO_RATIO.trim().ifBlank { "16:9" },
                    duration = BuildConfig.VIDEO_DURATION,
                    watermark = BuildConfig.VIDEO_WATERMARK
                )
            )
        }
    }
}

data class ImageEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val path: String,
    val size: String,
    val watermark: Boolean
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class VideoEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val createPath: String,
    val statusPath: String,
    val generateAudio: Boolean,
    val ratio: String,
    val duration: Int,
    val watermark: Boolean
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}
