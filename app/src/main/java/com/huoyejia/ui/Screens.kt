@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.huoyejia.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.data.local.NoteRelationEntity
import com.huoyejia.data.local.ReviewCardEntity
import com.huoyejia.data.local.UserStatsEntity
import com.huoyejia.data.local.FolderEntity
import com.huoyejia.domain.ExplainUiState
import com.huoyejia.domain.ExplainPack
import com.huoyejia.domain.ScoredNote
import com.huoyejia.util.JsonText
import java.io.File
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun InboxScreen(
    notes: List<NoteEntity>,
    relations: List<NoteRelationEntity>,
    cards: List<ReviewCardEntity>,
    stats: UserStatsEntity?,
    onOpenNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onAddDemo: () -> Unit,
    onStartReview: () -> Unit
) {
    val todayNotes = notes.filter { it.createdAt.isToday() }
    val todayReviewed = todayNotes.count { it.reviewedCount > 0 }
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(
                title = "活页夹",
                subtitle = "把收藏变成可复习的知识关系。新增内容会自动摘要、打标签、找旧笔记并生成回流卡。",
                action = "新增演示网页收藏",
                onAction = onAddDemo
            )
        }
        if ((stats?.hoardingIndex ?: 0) > 60) {
            item {
                InterventionCard(
                    collected = todayNotes.size,
                    understood = todayReviewed,
                    index = stats?.hoardingIndex ?: 0,
                    onClick = onStartReview
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill("收藏", notes.size.toString())
                MetricPill("关联", relations.size.toString())
                MetricPill("待复习", cards.count { it.status == "TODO" }.toString())
            }
        }
        items(notes, key = { it.noteId }) { note ->
            NoteCard(
                note = note,
                relationCount = relations.count { it.noteIdFrom == note.noteId || it.noteIdTo == note.noteId },
                duplicateCount = relations.duplicateCountFor(note.noteId),
                onClick = { onOpenNote(note.noteId) },
                onLongClick = { pendingDelete = note },
                onStartReview = onStartReview
            )
        }
    }
    pendingDelete?.let { note ->
        DeleteNoteDialog(
            noteTitle = note.sourceTitle,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteNote(note.noteId)
                pendingDelete = null
            }
        )
    }
}

@Composable
fun CaptureScreen(
    onSaveText: (String, String, String, String?, String?, String?) -> Unit,
    onMockScreenshot: () -> Unit,
    onOpenFloatingCapture: () -> Unit,
    folders: List<FolderEntity>,
    onCreateFolder: (String) -> Unit,
    pendingCaptureTitle: String? = null,
    pendingCaptureText: String? = null,
    pendingCaptureUrl: String? = null,
    pendingCaptureShowFolderPicker: Boolean = false,
    pendingCaptureRequestId: Long? = null,
    onPendingCaptureConsumed: () -> Unit = {},
    onSaveComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("未命名学习材料") }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<String?>(null) }
    var showFolderPicker by remember { mutableStateOf(false) }
    // 保持初始的showFolderPicker值，用于判断是否是浮窗采集
    var initialShowFolderPicker by remember { mutableStateOf(false) }
    // 控制延迟导航
    var shouldNavigateBack by remember { mutableStateOf(false) }
    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        screenshotUri = copyImageToCaptureStorage(context, uri) ?: uri.toString()
    }

    LaunchedEffect(pendingCaptureRequestId, pendingCaptureText, pendingCaptureUrl, pendingCaptureShowFolderPicker) {
        val incomingText = pendingCaptureText.orEmpty()
        val incomingUrl = pendingCaptureUrl.orEmpty()
        if (incomingText.isNotBlank() || incomingUrl.isNotBlank()) {
            title = pendingCaptureTitle?.takeIf { it.isNotBlank() } ?: "浮窗采集"
            text = incomingText
            url = incomingUrl
            initialShowFolderPicker = pendingCaptureShowFolderPicker
            showFolderPicker = pendingCaptureShowFolderPicker
            onPendingCaptureConsumed()
        }
    }

    // 延迟导航返回，让用户看到保存结果
    LaunchedEffect(shouldNavigateBack) {
        if (shouldNavigateBack) {
            delay(500)
            onSaveComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle("快速采集")
                TinyText("先整理素材，再选择收藏夹并交给 AI 总结。")
            }
            Button(
                onClick = onOpenFloatingCapture,
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Text("浮", fontWeight = FontWeight.Black)
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("粘贴学习内容 / 文章正文") },
            minLines = 8,
            modifier = Modifier.fillMaxWidth()
        )

        CaptureActionCard(
            title = if (screenshotUri != null) "截图已加入" else "加入截图",
            body = screenshotUri?.let { "已选择本地图片：${it.take(42)}..." } ?: "点击从本地相册/截图相册中选择图片，App 会复制一份用于 OCR。",
            action = if (screenshotUri != null) "重新选择" else "选择截图",
            onClick = { screenshotPicker.launch("image/*") }
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("粘贴网址，可选") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = text.isNotBlank() || url.isNotBlank() || screenshotUri != null) { showFolderPicker = true },
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("存入活页夹 + AI总结", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
Text(
                "选择收藏夹后保存，系统会继续执行清洗、摘要、标签、关联旧笔记和复习卡生成。",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
                lineHeight = 20.sp
            )
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders = folders,
            onCreateFolder = { name ->
                onCreateFolder(name)
            },
            onDismiss = { showFolderPicker = false },
            onConfirm = { folderEntity ->
                if (folderEntity != null) {
                    val resolvedUrl = url.trim()
                    val resolvedText = buildCapturePayload(
                        folder = folderEntity.name,
                        title = title,
                        text = text,
                        url = resolvedUrl,
                        imagePath = screenshotUri
                    )
                    val sourceType = when {
                        resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://") -> "web"
                        screenshotUri != null -> "image"
                        else -> "manual"
                    }
                    // 保存数据
                    onSaveText(
                        title.ifBlank { "未命名学习材料" },
                        resolvedText,
                        sourceType,
                        resolvedUrl.ifBlank { null },
                        screenshotUri,
                        folderEntity.folderId
                    )
                    
                    // 不立即关闭对话框，让用户手动返回
                    // showFolderPicker = false
                } else {
                    showFolderPicker = false
                }
            }
        )
    }
}

@Composable
private fun CaptureActionCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(body, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f), lineHeight = 18.sp)
            }
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<FolderEntity>,
    onCreateFolder: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (FolderEntity?) -> Unit
) {
    var selectedFolder by remember(folders) { mutableStateOf(folders.firstOrNull()) }
    var creating by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("选择收藏夹", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black)
                Button(
                    onClick = { creating = true },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) { Text("+") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (creating) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("新建收藏夹名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val clean = newFolderName.trim()
                                if (clean.isNotBlank()) {
                                    onCreateFolder(clean)
                                    newFolderName = ""
                                    creating = false
                                    // 重建后刷新列表，让用户重新选择
                                    selectedFolder = null
                                }
                            },
                            enabled = newFolderName.isNotBlank()
                        ) { Text("添加") }
                        TextButton(onClick = { creating = false }) { Text("取消新建") }
                    }
                }
                folders.forEach { folder ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolder = folder },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedFolder?.folderId == folder.folderId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selectedFolder?.folderId == folder.folderId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (selectedFolder?.folderId == folder.folderId) Text("已选", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedFolder) }, enabled = selectedFolder != null) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun buildCapturePayload(
    folder: String,
    title: String,
    text: String,
    url: String,
    imagePath: String?
): String {
    return buildString {
        appendLine("收藏夹：$folder")
        appendLine("标题：${title.ifBlank { "未命名学习材料" }}")
        if (text.isNotBlank()) {
            appendLine()
            appendLine("学习内容：")
            appendLine(text.trim())
        }
        if (url.isNotBlank()) {
            appendLine()
            appendLine("学习网址：$url")
            appendLine("请结合网址内容生成核心摘要、结构化要点和可复习问题。")
        }
        if (!imagePath.isNullOrBlank()) {
            appendLine()
            appendLine("截图：已添加本地图片 $imagePath，请在总结时结合截图线索。")
        }
    }
}

private fun copyImageToCaptureStorage(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        val dir = File(context.filesDir, "capture_images").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file.absolutePath
    }.getOrNull()
}

/**
 * 搜索页面 - 已禁用
 * 
 * 此搜索功能已被暂时禁用。如果您需要重新启用此功能，
 * 请取消注释以下代码并确保相关的导航路由也已启用。
 * 
 * @Composable
 * fun SearchScreen(
 *     results: List<ScoredNote>,
 *     onSearch: (String) -> Unit,
 *     onOpenNote: (String) -> Unit
 * ) {
 *     var query by remember { mutableStateOf("帮我找关于二战原因的所有内容") }
 *     Column(
 *         modifier = Modifier
 *             .fillMaxSize()
 *             .padding(horizontal = 18.dp, vertical = 24.dp),
 *         verticalArrangement = Arrangement.spacedBy(12.dp)
 *     ) {
 *         SectionTitle("自然语言检索")
 *         OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("输入问题") }, modifier = Modifier.fillMaxWidth())
 *         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
 *             Button(onClick = { onSearch(query) }) { Text("检索") }
 *             OutlinedButton(onClick = { onSearch("找上次那张地图截图") }) { Text("找地图截图") }
 *         }
 *         LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
 *             items(results, key = { it.note.noteId }) { result ->
 *                 ResultCard(result = result, onClick = { onOpenNote(result.note.noteId) })
 *             }
 *         }
 *     }
 * }
 */

@Composable
fun ReviewScreen(
    notes: List<NoteEntity>,
    cards: List<ReviewCardEntity>,
    onDone: (ReviewCardEntity) -> Unit
) {
    val todo = cards.filter { it.status == "TODO" }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("认知回流卡片")
        }
        if (todo.isEmpty()) {
            item { AssistCard("今日完成", "暂无待复习卡片。新增一条收藏后，系统会自动生成联系、对比或因果类问题。") }
        }
        items(todo, key = { it.cardId }) { card ->
            val note = notes.firstOrNull { it.noteId == card.noteId }
            ReviewCardView(card = card, noteTitle = note?.sourceTitle.orEmpty(), onDone = { onDone(card) })
        }
    }
}

@Composable
fun HistoryScreen(
    notes: List<NoteEntity>,
    cards: List<ReviewCardEntity>,
    onOpenNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }
    val reviewedNoteIds = cards.filter { it.status == "DONE" }.map { it.noteId }.toSet()
    val reviewedNotes = notes
        .filter { it.reviewedCount > 0 || it.noteId in reviewedNoteIds || it.readStatus }
        .sortedByDescending { it.createdAt }
    val groups = reviewedNotes
        .flatMap { note ->
            val tags = JsonText.decodeList(note.tags).ifEmpty { listOf(note.topic ?: "未分类") }
            tags.map { tag -> tag to note }
        }
        .groupBy({ it.first }, { it.second })
        .map { (tag, taggedNotes) -> TagHistoryGroup(tag, taggedNotes.distinctBy { it.noteId }) }
        .filter { group ->
            query.isBlank() ||
                group.tag.contains(query, ignoreCase = true) ||
                group.notes.any {
                    it.sourceTitle.contains(query, ignoreCase = true) ||
                        (it.summary ?: "").contains(query, ignoreCase = true) ||
                        it.noteContent.contains(query, ignoreCase = true)
                }
        }
        .sortedWith(compareByDescending<TagHistoryGroup> { it.notes.size }.thenBy { it.tag })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("复习历史")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索标签 / 历史内容") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill("已复习", reviewedNotes.size.toString())
                MetricPill("标签", groups.size.toString())
                MetricPill("卡片", cards.count { it.status == "DONE" }.toString())
            }
        }
        if (reviewedNotes.isEmpty()) {
            item {
                AssistCard("还没有复习历史", "完成复习卡片后，内容会按标签自动收纳到这里。")
            }
        }
        groups.forEach { group ->
            item {
                TagHistorySection(
                    group = group,
                    cards = cards,
                    onOpenNote = onOpenNote,
                    onDeleteNote = { pendingDelete = it }
                )
            }
        }
    }
    pendingDelete?.let { note ->
        DeleteNoteDialog(
            noteTitle = note.sourceTitle,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteNote(note.noteId)
                pendingDelete = null
            }
        )
    }
}

@Composable
fun DashboardScreen(
    stats: UserStatsEntity?,
    notes: List<NoteEntity>,
    cards: List<ReviewCardEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("数字囤积指数")
        val value = stats?.hoardingIndex ?: 0
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(value.toString(), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black)
                ProgressBar(value / 100f)
                Text(stats?.indexReason ?: "正在计算...", color = Color.White.copy(alpha = 0.92f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricPill("收藏数", notes.size.toString())
            MetricPill("已复习", notes.count { it.reviewedCount > 0 }.toString())
            MetricPill("待处理", notes.count { it.processedStatus != "PROCESSED" }.toString())
        }
        AssistCard("行为建议", "不要继续扩大收藏池。优先打开回流卡，完成 1 张后再新增材料，强化“从收藏到理解”的行为闭环。")
        AssistCard("重复收藏率", "${((stats?.duplicateRate ?: 0f) * 100).toInt()}% 高相似收藏会抬高囤积指数。")
        AssistCard("未复习卡片", "${cards.count { it.status == "TODO" }} 张 TODO，完成后会写回笔记状态。")
    }
}

@Composable
fun ExplainScreen(
    notes: List<NoteEntity>,
    explainState: ExplainUiState,
    onSelectNote: (String) -> Unit,
    onGenerate: (String?) -> Unit,
    onExportPpt: () -> Unit,
    onExportAnimation: () -> Unit,
    onGenerateVideo: () -> Unit,
    onOpenNote: (String) -> Unit
) {
    LaunchedEffect(notes, explainState.selectedNoteId) {
        if (notes.isNotEmpty() && explainState.selectedNoteId == null) {
            onSelectNote(notes.first().noteId)
        }
    }
    val selectedNote = notes.firstOrNull { it.noteId == explainState.selectedNoteId } ?: notes.firstOrNull()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("AI讲解工坊")
        AssistCard(
            "模型状态",
            if (explainState.remoteReady) {
                "当前已接入真实模型：${explainState.providerLabel}。可生成知识讲解、带图 PPT 和动画分镜。"
            } else {
                "当前未配置真实模型，正在使用 ${explainState.providerLabel}。在 local.properties 中配置 LLM_BASE_URL、LLM_API_KEY、LLM_CHAT_MODEL、LLM_EMBEDDING_MODEL 后会自动切换。"
            }
        )
        AssistCard(
            "比赛用途",
            "这个板块不是做普通摘要，而是把一条知识点转成“能讲给别人听”的解释结构，同时输出带图 PPT 和可做视频的小动画分镜。"
        )
        SectionTitle("选择要讲解的笔记")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notes.take(8).forEach { note ->
                FilterChip(
                    selected = explainState.selectedNoteId == note.noteId,
                    onClick = { onSelectNote(note.noteId) },
                    label = { Text(note.sourceTitle.take(10)) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onGenerate(selectedNote?.noteId) },
                enabled = !explainState.isGenerating && selectedNote != null
            ) {
                Text(if (explainState.isGenerating) "生成中..." else "生成 AI 讲解")
            }
            OutlinedButton(
                onClick = { selectedNote?.noteId?.let(onOpenNote) },
                enabled = selectedNote != null
            ) {
                Text("查看原笔记")
            }
        }
        selectedNote?.let {
            AssistCard("当前选中", "${it.sourceTitle}\n主题：${it.topic ?: "待归类"}")
        }
        explainState.errorMessage?.let { AssistCard("生成失败", it) }
        explainState.pack?.let { pack ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExportPpt,
                    enabled = !explainState.isExporting
                ) {
                    Text(if (explainState.isExporting) "导出中..." else "导出带图 PPT")
                }
                OutlinedButton(
                    onClick = onExportAnimation,
                    enabled = !explainState.isAnimationExporting
                ) {
                    Text(if (explainState.isAnimationExporting) "动画生成中..." else "导出小动画")
                }
                OutlinedButton(
                    onClick = onGenerateVideo,
                    enabled = !explainState.isVideoGenerating
                ) {
                    Text(if (explainState.isVideoGenerating) "视频生成中..." else "生成MP4视频")
                }
                explainState.exportedPptPath?.let { path ->
                    OutlinedButton(
                        onClick = { shareFile(context, path, "application/vnd.openxmlformats-officedocument.presentationml.presentation", "分享PPT") }
                    ) {
                        Text("分享 PPT")
                    }
                }
                explainState.exportedAnimationPath?.let { path ->
                    OutlinedButton(
                        onClick = { shareFile(context, path, "text/html", "分享动画讲解") }
                    ) {
                        Text("分享动画")
                    }
                }
                explainState.exportedVideoPath?.let { path ->
                    OutlinedButton(
                        onClick = { shareFile(context, path, "video/mp4", "分享视频讲解") }
                    ) {
                        Text("分享视频")
                    }
                }
            }
            explainState.exportErrorMessage?.let { AssistCard("导出失败", it) }
            explainState.animationExportErrorMessage?.let { AssistCard("动画导出失败", it) }
            explainState.videoGenerationErrorMessage?.let { AssistCard("视频生成失败", it) }
            explainState.exportedPptPath?.let { AssistCard("已生成 PPT", it) }
            explainState.exportedAnimationPath?.let { AssistCard("已生成小动画", it) }
            explainState.exportedVideoPath?.let { AssistCard("已生成视频", it) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(pack.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(pack.hook, color = Color.White.copy(alpha = 0.94f), lineHeight = 20.sp)
                    StatusChip(pack.provider)
                }
            }
            AssistCard("一分钟解释", pack.conciseExplanation)
            AnimationPreview(pack)
            SectionTitle("PPT 大纲")
            pack.pptOutline.forEachIndexed { index, slide ->
                AssistCard(
                    "第 ${index + 1} 页 · ${slide.title}",
                    slide.bullets.joinToString("\n") { "• $it" }
                )
            }
            SectionTitle("动画分镜")
            pack.animationScenes.forEachIndexed { index, scene ->
                AssistCard(
                    "Scene ${index + 1} · ${scene.title}",
                    "画面：${scene.visual}\n旁白：${scene.narration}"
                )
            }
            AssistCard("讲解收束", pack.takeaway)
        }
    }
}

@Composable
private fun QuickPasteDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速学习总结") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题，可不填") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("粘贴正文或链接") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(title, content.trim()) },
                enabled = content.isNotBlank()
            ) {
                Text("生成学习总结")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun AnimationPreview(pack: ExplainPack) {
    val transition = rememberInfiniteTransition(label = "explain-preview")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "preview-scale"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("小动画预览", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pack.animationScenes.take(3).forEachIndexed { index, scene ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.graphicsLayer {
                            scaleX = if (index == 1) pulse else 1f
                            scaleY = if (index == 1) pulse else 1f
                        }
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Scene ${index + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Text(scene.title, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun shareFile(context: android.content.Context, path: String, mimeType: String, title: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

@Composable
private fun DeleteNoteDialog(
    noteTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除知识点") },
        text = { Text("确定删除「$noteTitle」吗？相关的复习卡、关联关系和向量也会一起清理。") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private data class TagHistoryGroup(
    val tag: String,
    val notes: List<NoteEntity>
)

@Composable
private fun TagHistorySection(
    group: TagHistoryGroup,
    cards: List<ReviewCardEntity>,
    onOpenNote: (String) -> Unit,
    onDeleteNote: (NoteEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.tag, fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                StatusChip("${group.notes.size} 条")
            }
            group.notes.take(4).forEach { note ->
                val doneCards = cards.filter { it.noteId == note.noteId && it.status == "DONE" }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenNote(note.noteId) },
                            onLongClick = { onDeleteNote(note) }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(note.sourceTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(note.summary ?: note.noteContent, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                        if (doneCards.isNotEmpty()) {
                            TinyText("已完成 ${doneCards.size} 张回流卡 · 最近问题：${doneCards.first().question.take(34)}")
                        } else {
                            TinyText("已复习 ${note.reviewedCount} 次")
                        }
                    }
                }
            }
            if (group.notes.size > 4) {
                TinyText("还有 ${group.notes.size - 4} 条历史，输入更具体的标题或标签可继续筛选。")
            }
        }
    }
}

@Composable
fun NoteDetailScreen(
    note: NoteEntity?,
    notes: List<NoteEntity>,
    relations: List<NoteRelationEntity>,
    cards: List<ReviewCardEntity>,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onStartReview: () -> Unit
) {
    if (note == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("笔记不存在")
        }
        return
    }
    val relatedRelations = relations.filter { it.noteIdFrom == note.noteId || it.noteIdTo == note.noteId }
    val relatedNotes = relatedRelations.mapNotNull { relation ->
        val otherId = if (relation.noteIdFrom == note.noteId) relation.noteIdTo else relation.noteIdFrom
        notes.firstOrNull { it.noteId == otherId }?.let { relation to it }
    }
    val card = cards.firstOrNull { it.noteId == note.noteId }
    val duplicateCount = relatedRelations.duplicateCountFor(note.noteId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("返回") }
        SectionTitle(note.sourceTitle)
        if (note.duplicateScore >= 0.45f || duplicateCount > 0) {
            DuplicateWarningCard(
                duplicateCount = maxOf(duplicateCount, 1),
                onViewExisting = {
                    relatedNotes.firstOrNull()?.second?.noteId?.let(onOpenNote)
                },
                onStartReview = onStartReview
            )
        }
        AssistCard("AI 摘要", note.summary.orEmpty())
        TagRow(JsonText.decodeList(note.tags))
        AssistCard("统一内容", note.noteContent)
        AssistCard("回流问题", card?.question ?: "暂无卡片")
        SectionTitle("关联推荐")
        relatedNotes.forEach { (relation, related) ->
            RelationCard(relation = relation, note = related, onClick = { onOpenNote(related.noteId) })
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), lineHeight = 20.sp)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    relationCount: Int,
    duplicateCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStartReview: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.sourceTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                StatusChip(note.processedStatus)
            }
            Text(note.summary ?: "等待处理", maxLines = 2, overflow = TextOverflow.Ellipsis)
            TagRow(JsonText.decodeList(note.tags))
            if (note.duplicateScore >= 0.45f || duplicateCount > 0) {
                DuplicateInlineWarning(
                    duplicateCount = maxOf(duplicateCount, 1),
                    onViewExisting = onClick,
                    onStartReview = onStartReview
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TinyText("topic: ${note.topic ?: "-"}")
                TinyText("relation: $relationCount")
                TinyText("dup: ${(note.duplicateScore * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun ReviewCardView(card: ReviewCardEntity, noteTitle: String, onDone: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(noteTitle.ifBlank { "关联复习" }, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            Text(card.question, fontSize = 18.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp)
            Text(card.explanation)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(card.cardType)
                StatusChip(card.difficulty)
                Spacer(Modifier.weight(1f))
                Button(onClick = onDone) { Text("完成回流") }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ScoredNote, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(result.note.sourceTitle, fontWeight = FontWeight.Bold)
            Text(result.note.summary ?: result.note.noteContent, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TinyText("score ${(result.finalScore * 100).toInt()} | vector ${(result.vectorScore * 100).toInt()} | keyword ${(result.keywordScore * 100).toInt()}")
        }
    }
}

@Composable
private fun RelationCard(relation: NoteRelationEntity, note: NoteEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${relation.relationType} ${(relation.confidence * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("${note.sourceTitle}\n${relation.evidence}", lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AssistCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(body.ifBlank { "暂无内容" }, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag -> StatusChip(tag) }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 12.sp)
    }
}

@Composable
private fun TinyText(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .then(Modifier)
    ) {
        Surface(color = Color.White.copy(alpha = 0.22f), modifier = Modifier.fillMaxSize()) {}
        Surface(
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(10.dp)
        ) {}
    }
}

@Composable
private fun InterventionCard(collected: Int, understood: Int, index: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF8A2D20)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("必须处理提醒", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("你今天收藏 $collected 条，仅理解 $understood 条", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("囤积指数 $index 已超过安全线。点击直接进入回流卡片，把收藏转成理解。", color = Color.White.copy(alpha = 0.9f), lineHeight = 20.sp)
            Surface(shape = RoundedCornerShape(50), color = Color.White) {
                Text("现在处理", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = Color(0xFF8A2D20), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DuplicateInlineWarning(
    duplicateCount: Int,
    onViewExisting: () -> Unit,
    onStartReview: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFF2D8),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("你已经收藏过类似内容 $duplicateCount 次", color = Color(0xFF7A4A00), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewExisting) { Text("查看已有笔记") }
                Button(onClick = onStartReview) { Text("进入复习卡片") }
            }
        }
    }
}

@Composable
private fun DuplicateWarningCard(
    duplicateCount: Int,
    onViewExisting: () -> Unit,
    onStartReview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2D8)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFFE59F2A))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("重复收藏提示", color = Color(0xFF7A4A00), fontWeight = FontWeight.Black)
            Text("你已经收藏过类似内容 $duplicateCount 次。不要继续堆材料，先看已有笔记或进入回流卡片。", lineHeight = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewExisting) { Text("查看已有笔记") }
                Button(onClick = onStartReview) { Text("进入复习卡片") }
            }
        }
    }
}

private fun List<NoteRelationEntity>.duplicateCountFor(noteId: String): Int {
    return count {
        (it.noteIdFrom == noteId || it.noteIdTo == noteId) &&
            (it.confidence >= 0.45f || it.relationType == "similar" || it.relationType == "same_topic")
    }
}

private fun captureTagSuggestions(text: String): List<String> {
    return when {
        text.contains("二战") || text.contains("凡尔赛") || text.contains("绥靖") -> listOf("二战", "因果", "历史")
        text.contains("学习") || text.contains("费曼") || text.contains("复习") -> listOf("学习方法", "输出", "复习")
        text.contains("地图") || text.contains("截图") -> listOf("地图", "课堂", "图像资料")
        else -> listOf("待归类", "补充材料", "复习线索")
    }
}

private fun Long.isToday(): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(this).atZone(zone).toLocalDate() == LocalDate.now(zone)
}
