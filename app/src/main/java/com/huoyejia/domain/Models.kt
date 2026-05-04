package com.huoyejia.domain

import com.huoyejia.data.local.NoteEntity

data class NoteAiResult(
    val summary: String,
    val tags: List<String>,
    val topic: String,
    val importance: Float,
    val duplicateScore: Float
)

data class RelationAiResult(
    val relationType: String,
    val confidence: Float,
    val evidence: String
)

data class ReviewCardDraft(
    val question: String,
    val explanation: String,
    val difficulty: String,
    val cardType: String
)

data class RelatedNote(
    val note: NoteEntity,
    val relationType: String,
    val confidence: Float
)

data class ScoredNote(
    val note: NoteEntity,
    val vectorScore: Float,
    val keywordScore: Float,
    val metaScore: Float,
    val finalScore: Float
)

data class HoardingIndexResult(
    val index: Int,
    val reason: String
)

data class ExplainSlide(
    val title: String,
    val bullets: List<String>,
    val imagePrompt: String = "",
    val icon: String = "spark",
    val animationHint: String = "fade"
)

data class AnimationScene(
    val title: String,
    val visual: String,
    val narration: String
)

data class ExplainPack(
    val noteId: String,
    val title: String,
    val conciseExplanation: String,
    val hook: String,
    val pptOutline: List<ExplainSlide>,
    val animationScenes: List<AnimationScene>,
    val takeaway: String,
    val provider: String
)

data class ExplainUiState(
    val selectedNoteId: String? = null,
    val isGenerating: Boolean = false,
    val isExporting: Boolean = false,
    val isAnimationExporting: Boolean = false,
    val isVideoGenerating: Boolean = false,
    val pack: ExplainPack? = null,
    val errorMessage: String? = null,
    val exportErrorMessage: String? = null,
    val animationExportErrorMessage: String? = null,
    val videoGenerationErrorMessage: String? = null,
    val exportedPptPath: String? = null,
    val exportedAnimationPath: String? = null,
    val exportedVideoPath: String? = null,
    val providerLabel: String = "",
    val remoteReady: Boolean = false
)

data class CardAssistantState(
    val noteId: String? = null,
    val isAsking: Boolean = false,
    val question: String = "",
    val answer: String = "",
    val errorMessage: String? = null
)
