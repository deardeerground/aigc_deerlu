package com.huoyejia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.huoyejia.data.local.FolderEntity
import com.huoyejia.data.local.NoteEntity
import com.huoyejia.util.JsonText

/**
 * 标签解码函数
 */
fun decodeTags(tagsJson: String): List<String> {
    return JsonText.decodeList(tagsJson)
        .map { displayTag(it) }
        .filter { it.isNotBlank() }
}

/**
 * 页面2：收藏夹详情页
 * 包含：
 * 1. 顶部标题栏（返回箭头+标题+搜索图标）
 * 2. 标题下方辅助文字（卡片数量统计）
 * 3. 主体内容区（卡片列表，每个卡片含3个圆形图标+回流次数统计）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionDetailScreen(
    navController: NavController,
    folderId: String,
    notes: List<NoteEntity>,
    folders: List<FolderEntity>,
    onDeleteNote: (String) -> Unit = {}
) {
    // 验证输入参数
    if (folderId.isBlank()) {
        // 处理无效的folderId
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "无效的收藏夹ID",
                color = MaterialTheme.colorScheme.error
            )
        }
        return
    }
    
    val folder = folders.firstOrNull { folder -> folder.folderId == folderId }
    val folderName = folder?.name ?: "未知收藏夹"
    val filteredNotes = if (notes.isNotEmpty()) {
        notes.filter { it.folderId == folderId }
    } else {
        emptyList()
    }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = folderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("collections") {
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // 搜索功能已禁用，暂时移除搜索按钮
                    // IconButton(onClick = {
                    //     navController.navigate("search")
                    // }) {
                    //     Icon(
                    //         imageVector = Icons.Default.Search,
                    //         contentDescription = "搜索",
                    //         tint = MaterialTheme.colorScheme.onPrimary
                    //     )
                    // }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White.copy(alpha = 0.84f),
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        TechBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 标题下方辅助文字：卡片数量统计
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "${filteredNotes.size} 张卡片",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 主体内容区：卡片列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredNotes, key = { it.noteId }) { note ->
                    CollectionCardItem(
                        note = note,
                        onClick = { navController.navigate("detail/${note.noteId}") },
                        onDelete = { 
                            noteToDelete = note
                            showDeleteNoteDialog = true
                        }
                    )
                }
            }
        }

        // 删除卡片对话框
        if (showDeleteNoteDialog && noteToDelete != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteNoteDialog = false
                    noteToDelete = null
                },
                title = {
                    Text(
                        text = "删除卡片",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = "确定要删除卡片\"${noteToDelete?.sourceTitle}\"吗？\n\n删除后无法恢复。",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteNote(noteToDelete!!.noteId)
                            showDeleteNoteDialog = false
                            noteToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDeleteNoteDialog = false
                        noteToDelete = null
                    }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * 单个笔记卡片组件
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalFoundationApi::class,
    ExperimentalFoundationApi::class
)
@Composable
internal fun CollectionCardItem(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.90f)
            ),
            border = techPanelBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：笔记标题和标签
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = note.sourceTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 标签显示
                    val tags = decodeTags(note.tags).ifEmpty { listOf(note.topic ?: "未分类") }
                    tags.takeIf { it.isNotEmpty() }?.let {
                        TagRow(tags = it)
                    }
                }

                // 右侧：已回流次数
                Card(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .width(80.dp)
                        .height(40.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "已回流${note.reviewedCount}次",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * 标签行组件
 */
@Composable
private fun TagRow(tags: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        tags.forEach { tag ->
            StatusChip(text = tag.take(8))
        }
    }
}

/**
 * 标签芯片组件
 */
@Composable
private fun StatusChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

private fun displayTag(tag: String): String {
    val clean = tag.trim().replace(Regex("\\s+"), "")
    return if (clean.length > 10) "${clean.take(10)}…" else clean
}
