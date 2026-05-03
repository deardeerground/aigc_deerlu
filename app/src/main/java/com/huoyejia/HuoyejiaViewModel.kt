package com.huoyejia

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huoyejia.data.local.FolderEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.local.UserStatsEntity
 import com.huoyejia.domain.ExplainUiState
 import com.huoyejia.domain.ExplainPack
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

    private val _searchResults = MutableStateFlow<List<ScoredNote>>(emptyList())
    val searchResults: StateFlow<List<ScoredNote>> = _searchResults.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _explainState = MutableStateFlow(
        ExplainUiState(
            providerLabel = container.blueLM.providerName,
            remoteReady = container.blueLM.remoteReady
        )
    )
    val explainState: StateFlow<ExplainUiState> = _explainState.asStateFlow()

    init {
        viewModelScope.launch {
            _isBusy.value = true
            container.seedData.ensureSeeded()
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
            container.processor.captureAndProcess(
                rawText = text,
                imagePath = null,
                sourceType = sourceType,
                sourceTitle = title.ifBlank { "手动粘贴" },
                url = null,
                folderId = folderId
            )
            refreshStats()
            _isBusy.value = false
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
            // 将该文件夹内的笔记 folderId 设为 null
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
            container.processor.captureAndProcess(
                rawText = text,
                imagePath = imagePath,
                sourceType = sourceType,
                sourceTitle = title.ifBlank { "手动粘贴" },
                url = url,
                folderId = folderId
            )
            refreshStats()
            _isBusy.value = false
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
            container.reviewCardRepository.markDone(card.cardId)
            container.noteRepository.markReviewed(card.noteId)
            refreshStats()
        }
    }

    fun noteById(noteId: String): NoteEntity? = notes.value.firstOrNull { it.noteId == noteId }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
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

    private suspend fun refreshStats() {
        val current = container.noteRepository.loadAllNotes()
        container.statsRepository.upsert(StatsCalculator.calculate(current))
    }
}
