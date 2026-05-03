package com.huoyejia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.huoyejia.data.local.FolderEntity
import com.huoyejia.data.local.NoteEntity

/**
 * 回流箱主列表页
 * 显示收藏夹分组，每个收藏夹显示其中的笔记数量
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionListScreen(
    navController: NavController,
    notes: List<NoteEntity>,
    folders: List<FolderEntity>,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    onDeleteNote: (String) -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            if (isSearchExpanded) {
                // 展开的搜索框
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    // 返回按钮
                    IconButton(
                        onClick = {
                            isSearchExpanded = false
                            searchQuery = "" // 清空搜索内容
                        },
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // 搜索框
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("搜索收藏夹...", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            focusedBorderColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onPrimary),
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        if (searchQuery.isBlank()) {
                                            isSearchExpanded = false // 如果搜索内容为空，退出搜索模式
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清除搜索",
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    )
                }
            } else {
                // 正常的顶部栏
                TopAppBar(
                    title = {
                        Text(
                            text = "回流箱",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = { isSearchExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "新建收藏夹",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ====================== 关键修改1 ======================
            // 把变量定义 移到 LazyColumn 外部（LazyListScope 不能定义变量）
            val filteredFolders = if (searchQuery.isNotBlank()) {
                if (folders.isNotEmpty() && notes.isNotEmpty()) {
                    folders.filter { folder ->
                        folder.name.contains(searchQuery, ignoreCase = true) ||
                                notes.any { note ->
                                    note.folderId == folder.folderId &&
                                            note.sourceTitle.contains(searchQuery, ignoreCase = true)
                                }
                    }
                } else {
                    folders.filter { folder ->
                        folder.name.contains(searchQuery, ignoreCase = true)
                    }
                }
            } else {
                folders
            }
            // ======================================================

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 显示搜索结果提示
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            text = "搜索结果: ${filteredFolders.size} 个收藏夹",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                // 收藏夹列表
                items(filteredFolders, key = { it.folderId }) { folder ->
                    if (folder != null && folder.folderId.isNotBlank()) {
                        val noteCount = if (notes.isNotEmpty()) {
                            notes.count { it.folderId == folder.folderId }
                        } else {
                            0
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (folder.folderId.isNotBlank()) {
                                            navController.navigate("collection_detail/${folder.folderId}")
                                        }
                                    },
                                    onLongClick = {
                                        folderToDelete = folder
                                        showDeleteFolderDialog = true
                                    }
                                )
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = folder.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${noteCount}个内容",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 卡片标题预览
                                    val folderNotes = notes.filter { it.folderId == folder.folderId }
                                    if (folderNotes.isNotEmpty()) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(folderNotes.take(3), key = { it.noteId }) { note ->
                                                Text(
                                                    text = note.sourceTitle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .combinedClickable(
                                                            onClick = { /* 可以跳转到详情页 */ },
                                                            onLongClick = {
                                                                noteToDelete = note
                                                                showDeleteNoteDialog = true
                                                            }
                                                        )
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ====================== 关键修改2 ======================
                // 空状态：判断过滤后的列表为空，而不是原始列表
                if (filteredFolders.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无收藏夹，点击下方按钮创建",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
                // ======================================================

                // 底部新建收藏夹按钮
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        onClick = { showCreateDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "新建收藏夹",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // 新建收藏夹对话框
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = {
                    Text(
                        text = "新建收藏夹",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("收藏夹名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateFolder(newFolderName)
                                newFolderName = ""
                                showCreateDialog = false
                            }
                        },
                        enabled = newFolderName.isNotBlank()
                    ) {
                        Text("创建")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // 删除收藏夹对话框
        if (showDeleteFolderDialog && folderToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteFolderDialog = false
                    folderToDelete = null
                },
                title = {
                    Text(
                        text = "删除收藏夹",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = "确定要删除收藏夹\"${folderToDelete?.name}\"吗？\n\n删除后无法恢复，该收藏夹内的所有卡片也将被删除。",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteFolder(folderToDelete!!.folderId)
                            showDeleteFolderDialog = false
                            folderToDelete = null
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
                        showDeleteFolderDialog = false
                        folderToDelete = null
                    }) {
                        Text("取消")
                    }
                }
            )
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