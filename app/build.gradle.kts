import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun readConfig(vararg names: String): String {
    for (name in names) {
        val localValue = localProperties.getProperty(name)
        if (!localValue.isNullOrBlank()) return localValue.cleanConfigValue()
        val envValue = providers.environmentVariable(name).orNull
        if (!envValue.isNullOrBlank()) return envValue.cleanConfigValue()
    }
    return ""
}

fun String.cleanConfigValue(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'").trim()
}

fun String.gradleEscaped(): String {
    return replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.huoyejia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.huoyejia"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "LLM_BASE_URL", "\"${readConfig("LLM_BASE_URL").gradleEscaped()}\"")
        buildConfigField("String", "LLM_API_KEY", "\"${readConfig("LLM_API_KEY").gradleEscaped()}\"")
        buildConfigField("String", "LLM_CHAT_MODEL", "\"${readConfig("LLM_CHAT_MODEL").ifBlank { "gpt-4.1-mini" }.gradleEscaped()}\"")
        buildConfigField("String", "LLM_EMBEDDING_MODEL", "\"${readConfig("LLM_EMBEDDING_MODEL").ifBlank { "text-embedding-3-small" }.gradleEscaped()}\"")
        buildConfigField("String", "LLM_CHAT_BASE_URL", "\"${readConfig("LLM_CHAT_BASE_URL", "LLM_BASE_URL").gradleEscaped()}\"")
        buildConfigField("String", "LLM_CHAT_API_KEY", "\"${readConfig("LLM_CHAT_API_KEY", "LLM_API_KEY").gradleEscaped()}\"")
        buildConfigField("String", "LLM_EMBEDDING_BASE_URL", "\"${readConfig("LLM_EMBEDDING_BASE_URL", "LLM_BASE_URL").gradleEscaped()}\"")
        buildConfigField("String", "LLM_EMBEDDING_API_KEY", "\"${readConfig("LLM_EMBEDDING_API_KEY", "LLM_API_KEY").gradleEscaped()}\"")
        buildConfigField("String", "LLM_IMAGE_BASE_URL", "\"${readConfig("LLM_IMAGE_BASE_URL", "LLM_BASE_URL").gradleEscaped()}\"")
        buildConfigField("String", "LLM_IMAGE_API_KEY", "\"${readConfig("LLM_IMAGE_API_KEY", "LLM_API_KEY").gradleEscaped()}\"")
        buildConfigField("String", "LLM_IMAGE_MODEL", "\"${readConfig("LLM_IMAGE_MODEL").ifBlank { "gpt-image-1" }.gradleEscaped()}\"")
        buildConfigField("String", "VIDEO_BASE_URL", "\"${readConfig("VIDEO_BASE_URL").ifBlank { "https://ark.cn-beijing.volces.com" }.gradleEscaped()}\"")
        buildConfigField("String", "VIDEO_API_KEY", "\"${readConfig("VIDEO_API_KEY", "ARK_API_KEY").gradleEscaped()}\"")
        buildConfigField("String", "VIDEO_MODEL", "\"${readConfig("VIDEO_MODEL").ifBlank { "ep-20260429125645-qrwkd" }.gradleEscaped()}\"")
        buildConfigField("String", "VIDEO_CREATE_PATH", "\"${readConfig("VIDEO_CREATE_PATH").ifBlank { "/api/v3/contents/generations/tasks" }.gradleEscaped()}\"")
        buildConfigField("String", "VIDEO_STATUS_PATH", "\"${readConfig("VIDEO_STATUS_PATH").ifBlank { "/api/v3/contents/generations/tasks/{id}" }.gradleEscaped()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
