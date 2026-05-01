package com.huoyejia.ai

import com.huoyejia.BuildConfig

data class LlmEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class LlmRuntimeConfig(
    val chat: LlmEndpointConfig,
    val embedding: LlmEndpointConfig,
    val image: LlmEndpointConfig,
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
                    model = BuildConfig.LLM_EMBEDDING_MODEL.trim()
                ),
                image = LlmEndpointConfig(
                    baseUrl = BuildConfig.LLM_IMAGE_BASE_URL.trim().removeSuffix("/"),
                    apiKey = BuildConfig.LLM_IMAGE_API_KEY.trim(),
                    model = BuildConfig.LLM_IMAGE_MODEL.trim()
                ),
                video = VideoEndpointConfig(
                    baseUrl = BuildConfig.VIDEO_BASE_URL.trim().removeSuffix("/"),
                    apiKey = BuildConfig.VIDEO_API_KEY.trim(),
                    model = BuildConfig.VIDEO_MODEL.trim(),
                    createPath = BuildConfig.VIDEO_CREATE_PATH.trim().ifBlank { "/videos/generations" },
                    statusPath = BuildConfig.VIDEO_STATUS_PATH.trim().ifBlank { "/videos/{id}" }
                )
            )
        }
    }
}

data class VideoEndpointConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val createPath: String,
    val statusPath: String
) {
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}
