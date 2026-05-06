package com.huoyejia

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huoyejia.NoteProcessingScheduler
import com.huoyejia.data.local.FolderEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.local.UserStatsEntity

import com.huoyejia.service.NotificationScheduler
import com.huoyejia.service.ReminderTime
import com.huoyejia.service.DailyReviewAlarm
import com.huoyejia.domain.CardAssistantState
 import com.huoyejia.domain.ExplainUiState
 import com.huoyejia.domain.ExplainPack
import com.huoyejia.domain.NoteProcessingProgress
 import com.huoyejia.domain.ScoredNote
import com.huoyejia.domain.StatsCalculator
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HuoyejiaViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as HuoyejiaApp).container
    private val notificationScheduler = NotificationScheduler(application.applicationContext)

    val notes: StateFlow<List<NoteEntity>> = container.noteRepository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val relations: StateFlow<List<NoteRelationEntity>> = container.relationRepository.observeRelations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cards: StateFlow<List<ReviewCardEntity>> = container.reviewCardRepository.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<UserStatsEntity?> = container.statsRepository.observeLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders: StateFlow<List<FolderEntity>> = container.folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val processingProgress: StateFlow<List<NoteProcessingProgress>> = container.processingProgress

    private val _searchResults = MutableStateFlow<List<ScoredNote>>(emptyList())
    val searchResults: StateFlow<List<ScoredNote>> = _searchResults.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _cardAssistantState = MutableStateFlow(CardAssistantState())
    val cardAssistantState: StateFlow<CardAssistantState> = _cardAssistantState.asStateFlow()

    private val _explainState = MutableStateFlow(
        ExplainUiState(
            providerLabel = container.blueLM.providerName,
            remoteReady = container.blueLM.remoteReady
        )
    )
    val explainState: StateFlow<ExplainUiState> = _explainState.asStateFlow()

    fun refreshAll() {
        viewModelScope.launch {
            _isBusy.value = true
            container.seedData.ensureSeeded()
            container.processor.schedulePendingNotes()
            refreshStats()
            _isBusy.value = false
        }
    }

    init {
        viewModelScope.launch {
            _isBusy.value = true
            container.seedData.ensureSeeded()
            container.processor.schedulePendingNotes()
            refreshStats()
            if (_explainState.value.selectedNoteId == null) {
                val first = container.noteRepository.loadAllNotes().firstOrNull()
                _explainState.value = _explainState.value.copy(selectedNoteId = first?.noteId)
            }
            _isBusy.value = false
        }
    }

    fun addManualNote(title: String, text: String, sourceType: String = "manual", folderId: String? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                container.processor.captureAndProcess(
                    rawText = text,
                    imagePath = null,
                    sourceType = sourceType,
                    sourceTitle = title.ifBlank { "未命名收藏" },
                    url = null,
                    folderId = folderId
                )
                refreshStats()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val folder = FolderEntity(
                folderId = java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                createdAt = System.currentTimeMillis()
            )
            container.folderRepository.upsert(folder)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            container.folderRepository.deleteFolder(folderId)
            // Keep the cards, but move them out of the deleted folder.
            val notesInFolder = notes.value.filter { it.folderId == folderId }
            notesInFolder.forEach { note ->
                container.noteRepository.upsert(note.copy(folderId = null))
            }
        }
    }

    fun addCaptureNote(
        title: String,
        text: String,
        sourceType: String = "manual",
        url: String? = null,
        imagePath: String? = null,
        folderId: String? = null
    ) {
        if (text.isBlank() && url.isNullOrBlank() && imagePath.isNullOrBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                container.processor.captureAndProcess(
                    rawText = text,
                    imagePath = imagePath,
                    sourceType = sourceType,
                    sourceTitle = title.ifBlank { "未命名收藏" },
                    url = url,
                    folderId = folderId
                )
                refreshStats()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun addDemoLinkedNote() {
        viewModelScope.launch {
            _isBusy.value = true
            container.processor.captureAndProcess(
                rawText = "费曼学习法的关键：用自己话输出，暴露理解漏洞，再回到材料修正。输出是理解的标志。",
                imagePath = null,
                sourceType = "web",
                sourceTitle = "学习方法：费曼输出法",
                url = "https://example.com/feynman"
            )
            refreshStats()
            _isBusy.value = false
        }
    }

    fun addMockScreenshot() {
        viewModelScope.launch {
            _isBusy.value = true
            container.processor.captureAndProcess(
                rawText = "",
                imagePath = "/mock/capture/classroom-map.png",
                sourceType = "image",
                sourceTitle = "截图导入：课堂PPT地图",
                url = null
            )
            refreshStats()
            _isBusy.value = false
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _searchResults.value = container.searchService.search(query)
        }
    }

    fun completeCard(card: ReviewCardEntity) {
        viewModelScope.launch {
            container.reviewCardRepository.markDone(card.cardId, System.currentTimeMillis())
            container.noteRepository.markReviewed(card.noteId)
            refreshStats()
        }
    }

    fun generateReviewCardsForLeastReviewed(count: Int = 3) {
        viewModelScope.launch {
            container.reviewCardGenerator.generateReviewCardsForLeastReviewed(count)
            refreshStats()
        }
    }

    fun noteById(noteId: String): NoteEntity? = notes.value.firstOrNull { it.noteId == noteId }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            NoteProcessingScheduler.cancel(getApplication(), noteId)
            container.relationRepository.deleteForNote(noteId)
            container.reviewCardRepository.deleteForNote(noteId)
            container.noteRepository.deleteNote(noteId)
            refreshStats()
            if (_explainState.value.selectedNoteId == noteId) {
                _explainState.value = _explainState.value.copy(
                    selectedNoteId = notes.value.firstOrNull { it.noteId != noteId }?.noteId,
                    pack = null,
                    errorMessage = null
                )
            }
        }
    }

    fun regenerateNote(noteId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                container.processor.reprocessNote(noteId)
                refreshStats()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun selectExplainNote(noteId: String) {
        _explainState.value = _explainState.value.copy(
            selectedNoteId = noteId,
            errorMessage = null
        )
    }

    fun generateExplainPack(noteId: String? = _explainState.value.selectedNoteId) {
        val resolvedNoteId = noteId ?: return
        viewModelScope.launch {
            _explainState.value = _explainState.value.copy(
                selectedNoteId = resolvedNoteId,
                isGenerating = true,
                errorMessage = null
            )
            val pack: ExplainPack? = runCatching { container.explainService.generate(resolvedNoteId) }.getOrNull()
            _explainState.value = if (pack != null) {
                _explainState.value.copy(
                    isGenerating = false,
                    pack = pack,
                    exportedPptPath = null,
                    exportedAnimationPath = null,
                    exportedVideoPath = null,
                    exportErrorMessage = null,
                    animationExportErrorMessage = null,
                    videoGenerationErrorMessage = null,
                    providerLabel = container.blueLM.providerName,
                    remoteReady = container.blueLM.remoteReady
                )
            } else {
                _explainState.value.copy(
                    isGenerating = false,
                    errorMessage = "讲解生成失败，请检查模型配置或重试。"
                )
            }
        }
    }

     fun exportExplainPpt() {
         val pack = _explainState.value.pack ?: return
         viewModelScope.launch {
             _explainState.value = _explainState.value.copy(
                 isExporting = true,
                 exportErrorMessage = null
             )
            val file: File? = runCatching { container.pptExportService.export(pack) }
                .getOrNull()
             _explainState.value = if (file != null) {
                 _explainState.value.copy(
                     isExporting = false,
                     exportedPptPath = file.absolutePath,
                     providerLabel = container.blueLM.providerName,
                     remoteReady = container.blueLM.remoteReady
                 )
             } else {
                 _explainState.value.copy(
                     isExporting = false,
                     exportErrorMessage = "PPT 导出失败，请重新生成讲解包后再试。"
                 )
             }
         }
     }

    fun exportExplainAnimation() {
        val pack = _explainState.value.pack ?: return
        viewModelScope.launch {
            _explainState.value = _explainState.value.copy(
                isAnimationExporting = true,
                animationExportErrorMessage = null
            )
            val file: File? = runCatching { container.animationExportService.export(pack) }
                .getOrNull()
            _explainState.value = if (file != null) {
                _explainState.value.copy(
                    isAnimationExporting = false,
                    exportedAnimationPath = file.absolutePath
                )
            } else {
                _explainState.value.copy(
                    isAnimationExporting = false,
                    animationExportErrorMessage = "动画讲解导出失败，请重新生成讲解包后再试。"
                )
            }
        }
    }

    fun generateExplainVideo() {
        val pack = _explainState.value.pack ?: return
        viewModelScope.launch {
            _explainState.value = _explainState.value.copy(
                isVideoGenerating = true,
                videoGenerationErrorMessage = null
            )
            val result = runCatching { container.videoGenerationService.generate(pack) }
            val file: File? = result.getOrNull()
            _explainState.value = if (file != null) {
                _explainState.value.copy(
                    isVideoGenerating = false,
                    exportedVideoPath = file.absolutePath,
                    providerLabel = container.blueLM.providerName,
                    remoteReady = container.blueLM.remoteReady
                )
            } else {
                _explainState.value.copy(
                    isVideoGenerating = false,
                    videoGenerationErrorMessage = result.exceptionOrNull()?.message ?: "视频生成失败，请检查视频模型配置。"
                )
            }
        }
    }

    fun updateNoteTitle(noteId: String, title: String) {
        val cleanTitle = title.trim()
        if (noteId.isBlank() || cleanTitle.isBlank()) return
        viewModelScope.launch {
            container.noteRepository.updateTitle(noteId, cleanTitle)
        }
    }

    fun generateCardPpt(noteId: String) {
        viewModelScope.launch {
            val pack = ensureExplainPack(noteId) ?: return@launch
            _explainState.value = _explainState.value.copy(
                isExporting = true,
                exportErrorMessage = null,
                exportedPptPath = null
            )
            val file: File? = runCatching { container.pptExportService.export(pack) }.getOrNull()
            _explainState.value = if (file != null) {
                _explainState.value.copy(
                    isExporting = false,
                    exportedPptPath = file.absolutePath,
                    providerLabel = container.blueLM.providerName,
                    remoteReady = container.blueLM.remoteReady
                )
            } else {
                _explainState.value.copy(
                    isExporting = false,
                    exportErrorMessage = "PPT 生成失败，请检查模型配置后重试。"
                )
            }
        }
    }

    fun generateCardVideo(noteId: String) {
        viewModelScope.launch {
            val pack = ensureExplainPack(noteId) ?: return@launch
            _explainState.value = _explainState.value.copy(
                isVideoGenerating = true,
                videoGenerationErrorMessage = null,
                exportedVideoPath = null
            )
            val result = runCatching { container.videoGenerationService.generate(pack) }
            val file: File? = result.getOrNull()
            _explainState.value = if (file != null) {
                _explainState.value.copy(
                    isVideoGenerating = false,
                    exportedVideoPath = file.absolutePath
                )
            } else {
                _explainState.value.copy(
                    isVideoGenerating = false,
                    videoGenerationErrorMessage = result.exceptionOrNull()?.message ?: "视频生成失败，请配置视频 API 后重试。"
                )
            }
        }
    }

    fun askCardQuestion(noteId: String, question: String) {
        val cleanQuestion = question.trim()
        if (noteId.isBlank() || cleanQuestion.isBlank()) return
        viewModelScope.launch {
            _cardAssistantState.value = CardAssistantState(
                noteId = noteId,
                isAsking = true,
                question = cleanQuestion
            )

            val current = container.noteRepository.getNote(noteId)
            if (current == null) {
                _cardAssistantState.value = CardAssistantState(
                    noteId = noteId,
                    question = cleanQuestion,
                    errorMessage = "卡片不存在，无法回答。"
                )
                return@launch
            }

            val related = container.relationRepository.getForNote(noteId)
                .mapNotNull { relation ->
                    val otherId = if (relation.noteIdFrom == noteId) relation.noteIdTo else relation.noteIdFrom
                    container.noteRepository.getNote(otherId)
                }
                .distinctBy { it.noteId }
                .take(5)

            val answer = runCatching {
                container.blueLM.answerCardQuestion(current, emptyList(), cleanQuestion)
            }
            _cardAssistantState.value = if (answer.isSuccess) {
                CardAssistantState(
                    noteId = noteId,
                    question = cleanQuestion,
                    answer = answer.getOrNull().orEmpty()
                )
            } else {
                CardAssistantState(
                    noteId = noteId,
                    question = cleanQuestion,
                    errorMessage = answer.exceptionOrNull()?.message ?: "AI 回答失败，请稍后重试。"
                )
            }
        }
    }

    private suspend fun ensureExplainPack(noteId: String): ExplainPack? {
        val current = _explainState.value
        if (current.pack?.noteId == noteId) {
            _explainState.value = current.copy(
                selectedNoteId = noteId,
                errorMessage = null,
                providerLabel = container.blueLM.providerName,
                remoteReady = container.blueLM.remoteReady
            )
            return current.pack
        }
        _explainState.value = current.copy(
            selectedNoteId = noteId,
            isGenerating = true,
            errorMessage = null,
            exportErrorMessage = null,
            videoGenerationErrorMessage = null,
            exportedPptPath = null,
            exportedVideoPath = null
        )
        val pack = runCatching { container.explainService.generate(noteId) }.getOrNull()
        _explainState.value = if (pack != null) {
            _explainState.value.copy(
                isGenerating = false,
                pack = pack,
                providerLabel = container.blueLM.providerName,
                remoteReady = container.blueLM.remoteReady
            )
        } else {
            _explainState.value.copy(
                isGenerating = false,
                errorMessage = "讲解结构生成失败，请检查模型配置或稍后重试。"
            )
        }
        return pack
    }

    private suspend fun refreshStats() {
        val current = container.noteRepository.loadAllNotes()
        container.statsRepository.upsert(StatsCalculator.calculate(current))
    }


    // 通知管理方法
    fun enableNotifications() {
        notificationScheduler.enableNotifications()
    }

    fun disableNotifications() {
        notificationScheduler.disableNotifications()
    }

    fun areNotificationsEnabled(): Boolean {
        return notificationScheduler.areNotificationsEnabled()
    }

    fun requestExactAlarmPermission(): Intent {
        return notificationScheduler.requestExactAlarmPermission()
    }

    fun refreshNotificationState() {
        notificationScheduler.refreshState()
    }

    fun setReminderTime(hour: Int, minute: Int) {
        notificationScheduler.setReminderTime(hour, minute)
    }

    val notificationsEnabled: StateFlow<Boolean> = notificationScheduler.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val reminderTime: StateFlow<ReminderTime> = notificationScheduler.reminderTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderTime(21, 13))

}
