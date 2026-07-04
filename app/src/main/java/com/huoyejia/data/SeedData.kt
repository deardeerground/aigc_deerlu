package com.huoyejia.data

import com.huoyejia.data.local.FolderEntity
import com.huoyejia.domain.NoteProcessor

class SeedData(
    private val noteRepository: NoteRepository,
    private val processor: NoteProcessor,
    private val folderRepository: FolderRepository
) {
    suspend fun ensureSeeded() {
        if (noteRepository.countNotes() > 0) return

        // 创建默认文件夹
        val economyFolder = FolderEntity(
            folderId = "default_economy",
            name = "经济学",
            createdAt = System.currentTimeMillis()
        )
        val languageFolder = FolderEntity(
            folderId = "default_language",
            name = "英语表达",
            createdAt = System.currentTimeMillis()
        )
        val imageFolder = FolderEntity(
            folderId = "default_image",
            name = "实验截图",
            createdAt = System.currentTimeMillis()
        )

        folderRepository.upsert(economyFolder)
        folderRepository.upsert(languageFolder)
        folderRepository.upsert(imageFolder)

        // 创建种子笔记并关联到对应文件夹
        processor.captureAndProcess(
            rawText = "机会成本不是实际花出去的钱，而是做出一个选择时放弃的最佳替代方案。判断一个决策是否划算，要看被放弃选项的价值。",
            imagePath = null,
            sourceType = "web",
            sourceTitle = "经济学：机会成本",
            url = "https://example.com/opportunity-cost",
            folderId = economyFolder.folderId
        )
        processor.captureAndProcess(
            rawText = "",
            imagePath = "/mock/screenshot/physics-experiment.png",
            sourceType = "image",
            sourceTitle = "实验截图：小车运动图像",
            url = null,
            folderId = imageFolder.folderId
        )
        processor.captureAndProcess(
            rawText = "Instead of saying very important every time, academic writing often uses essential, significant, crucial, or fundamental depending on the context.",
            imagePath = null,
            sourceType = "manual",
            sourceTitle = "英语写作：important 的替代表达",
            url = null,
            folderId = languageFolder.folderId
        )
    }
}
