package com.huoyejia

import android.content.Context
import com.huoyejia.ai.BlueLMAdapter
import com.huoyejia.ai.LlmRuntimeConfig
import com.huoyejia.ai.MockBlueLMAdapter
import com.huoyejia.ai.RemoteBlueLMAdapter
import com.huoyejia.ai.ServerBlueLMAdapter
import com.huoyejia.data.FolderRepository
import com.huoyejia.data.NoteRepository
import com.huoyejia.data.RelationRepository
import com.huoyejia.data.ReviewCardRepository
import com.huoyejia.data.SeedData
import com.huoyejia.data.StatsRepository
import com.huoyejia.data.local.FolderDao
import com.huoyejia.data.local.HuoyejiaDatabase
import com.huoyejia.domain.ExplainService
import com.huoyejia.domain.NoteProcessor
import com.huoyejia.domain.PptExportService
import com.huoyejia.domain.ReviewCardGenerator
import com.huoyejia.domain.SearchService
import com.huoyejia.domain.AnimationExportService
import com.huoyejia.domain.VideoGenerationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val db = HuoyejiaDatabase.create(context)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mockBlueLM = MockBlueLMAdapter()
    // 优先走自建服务器（ServerBlueLMAdapter），失败再降级到直连大模型（RemoteBlueLMAdapter），最终降级到本地 mock
    private val directLM = RemoteBlueLMAdapter(context.applicationContext, LlmRuntimeConfig.fromBuildConfig(), mockBlueLM)
    val blueLM: BlueLMAdapter = ServerBlueLMAdapter(directLM)

    val folderRepository = FolderRepository(db.folderDao())
    val noteRepository = NoteRepository(db.noteDao(), db.embeddingDao())
    val relationRepository = RelationRepository(db.relationDao())
    val reviewCardRepository = ReviewCardRepository(db.reviewCardDao())
    val statsRepository = StatsRepository(db.statsDao())
    val processor = NoteProcessor(context, noteRepository, relationRepository, reviewCardRepository, blueLM, folderRepository, backgroundScope)
    val processingProgress = processor.processingProgress
    val searchService = SearchService(noteRepository, blueLM)
    val explainService = ExplainService(noteRepository, relationRepository, blueLM)
    val pptExportService = PptExportService(context.applicationContext, blueLM)
    val animationExportService = AnimationExportService(context.applicationContext, blueLM)
    val videoGenerationService = VideoGenerationService(context.applicationContext, LlmRuntimeConfig.fromBuildConfig().video)
    val reviewCardGenerator = ReviewCardGenerator(noteRepository, reviewCardRepository, relationRepository, blueLM)
    val seedData = SeedData(noteRepository, processor, folderRepository)
}
