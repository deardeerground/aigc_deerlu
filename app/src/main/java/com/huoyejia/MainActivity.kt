package com.huoyejia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.huoyejia.ui.CaptureScreen
import com.huoyejia.ui.CollectionDetailScreen
import com.huoyejia.ui.CollectionListScreen
import com.huoyejia.ui.DashboardScreen
import com.huoyejia.ui.FolderPickerDialog
import com.huoyejia.ui.HuoyejiaScaffold
import com.huoyejia.ui.NoteDetailScreen
import com.huoyejia.ui.ReviewScreen
// import com.huoyejia.ui.SearchScreen  // 搜索页面已禁用
import com.huoyejia.ui.theme.HuoyejiaTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CAPTURE = "com.huoyejia.extra.OPEN_CAPTURE"
        const val EXTRA_CAPTURE_TITLE = "com.huoyejia.extra.CAPTURE_TITLE"
        const val EXTRA_CAPTURE_TEXT = "com.huoyejia.extra.CAPTURE_TEXT"
        const val EXTRA_CAPTURE_URL = "com.huoyejia.extra.CAPTURE_URL"
        const val EXTRA_NAV_CAPTURE = "com.huoyejia.extra.NAV_CAPTURE"
        const val EXTRA_PICK_FOLDER = "com.huoyejia.extra.PICK_FOLDER"
    }

    private var pendingCapture by mutableStateOf<PendingCapture?>(null)
    private var openCaptureRequested by mutableStateOf(false)
    private var floatingFolderPickerPending by mutableStateOf<PendingCapture?>(null)
    private var launchFloatingAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCapture = intent.toPendingCapture()
        floatingFolderPickerPending = pendingCapture?.takeIf { it.showFolderPicker }
        openCaptureRequested = intent.getBooleanExtra(EXTRA_NAV_CAPTURE, false) || intent.isTextShare()
        setContent {
            HuoyejiaTheme {
                val viewModel: HuoyejiaViewModel = viewModel()
                val navController = rememberNavController()
                val notes by viewModel.notes.collectAsState()
                val relations by viewModel.relations.collectAsState()
                val cards by viewModel.cards.collectAsState()
                val stats by viewModel.stats.collectAsState()
                val results by viewModel.searchResults.collectAsState()
                val explainState by viewModel.explainState.collectAsState()
                val cardAssistantState by viewModel.cardAssistantState.collectAsState()
                val isBusy by viewModel.isBusy.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val pending = pendingCapture
                val shouldOpenCapture = pending != null || openCaptureRequested

                LaunchedEffect(shouldOpenCapture) {
                    if (shouldOpenCapture) {
                        navController.navigate("capture") {
                            launchSingleTop = true
                            popUpTo("collections") { saveState = false }
                        }
                    }
                }

                HuoyejiaScaffold(navController = navController, isBusy = isBusy) {
                    NavHost(navController = navController, startDestination = "collections") {
                        composable("collections") {
                            CollectionListScreen(
                                navController = navController,
                                notes = notes,
                                folders = folders,
                                onCreateFolder = viewModel::createFolder,
                                onDeleteFolder = viewModel::deleteFolder,
                                onDeleteNote = viewModel::deleteNote
                            )
                        }
                        composable("capture") {
                            CaptureScreen(
                                onSaveText = viewModel::addCaptureNote,
                                onMockScreenshot = viewModel::addMockScreenshot,
                                onOpenFloatingCapture = ::openFloatingCapture,
                                folders = folders,
                                onCreateFolder = viewModel::createFolder,
                                isBusy = isBusy,
                                pendingCaptureTitle = pending?.title,
                                pendingCaptureText = pending?.text,
                                pendingCaptureUrl = pending?.url,
                                pendingCaptureShowFolderPicker = false,
                                pendingCaptureRequestId = pending?.requestId,
                                onPendingCaptureConsumed = {
                                    pendingCapture = null
                                    openCaptureRequested = false
                                },
                                onSaveComplete = {
                                    // 不自动导航，让用户手动返回
                                }
                            )
                        }
                        // composable("search") {
                            //     SearchScreen(
                            //         results = results,
                            //         onSearch = viewModel::search,
                            //         onOpenNote = { navController.navigate("detail/$it") }
                            //     )
                            // } // 搜索路由已禁用
                        composable("review") {
                            ReviewScreen(
                                notes = notes,
                                cards = cards,
                                onDone = viewModel::completeCard
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(stats = stats, notes = notes, cards = cards)
                        }
                        composable("collection_detail/{folderId}") { entry ->
                            val folderId = entry.arguments?.getString("folderId").orEmpty()
                            CollectionDetailScreen(
                                navController = navController,
                                folderId = folderId,
                                notes = notes,
                                folders = folders,
                                onDeleteNote = viewModel::deleteNote
                            )
                        }
                        composable("detail/{noteId}") { entry ->
                            val noteId = entry.arguments?.getString("noteId").orEmpty()
                            NoteDetailScreen(
                                note = viewModel.noteById(noteId),
                                notes = notes,
                                relations = relations,
                                cards = cards,
                                explainState = explainState,
                                assistantState = cardAssistantState,
                                onBack = { navController.popBackStack() },
                                onOpenNote = { navController.navigate("detail/$it") },
                                onStartReview = { navController.navigate("review") },
                                onGeneratePpt = viewModel::generateCardPpt,
                                onGenerateVideo = viewModel::generateCardVideo,
                                onAskQuestion = viewModel::askCardQuestion
                            )
                        }
                    }
                }
                floatingFolderPickerPending?.let { capture ->
                    FolderPickerDialog(
                        folders = folders,
                        onCreateFolder = viewModel::createFolder,
                        onDismiss = {
                            floatingFolderPickerPending = null
                            pendingCapture = null
                            openCaptureRequested = false
                        },
                        onConfirm = { folder ->
                            val resolvedUrl = capture.url.trim()
                            val resolvedText = capture.text.trim()
                            val sourceType = when {
                                resolvedUrl.startsWith("http://") || resolvedUrl.startsWith("https://") -> "web"
                                else -> "manual"
                            }
                            viewModel.addCaptureNote(
                                capture.title.ifBlank { "浮窗采集" },
                                resolvedText,
                                sourceType,
                                resolvedUrl.ifBlank { null },
                                null,
                                folder.folderId
                            )
                            floatingFolderPickerPending = null
                            pendingCapture = null
                            openCaptureRequested = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingCapture = intent.toPendingCapture()
        floatingFolderPickerPending = pendingCapture?.takeIf { it.showFolderPicker }
        openCaptureRequested = intent.getBooleanExtra(EXTRA_NAV_CAPTURE, false) || intent.isTextShare()
    }

    override fun onResume() {
        super.onResume()
        if (launchFloatingAfterPermission && Settings.canDrawOverlays(this)) {
            launchFloatingAfterPermission = false
            startFloatingCaptureAndSendAppBack()
        }
    }

    private fun openFloatingCapture() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingCaptureAndSendAppBack()
        } else {
            launchFloatingAfterPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startFloatingCaptureAndSendAppBack() {
        FloatingCaptureService.start(this)
        moveTaskToBack(true)
    }

    private fun Intent.toPendingCapture(): PendingCapture? {
        val sharedText = getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        if (!getBooleanExtra(EXTRA_OPEN_CAPTURE, false) && sharedText.isBlank()) return null
        val explicitText = getStringExtra(EXTRA_CAPTURE_TEXT).orEmpty()
        val explicitUrl = getStringExtra(EXTRA_CAPTURE_URL).orEmpty()
        val sharedUrl = extractFirstUrl(sharedText).orEmpty()
        val text = explicitText.ifBlank {
            if (sharedUrl.isNotBlank() && sharedText == sharedUrl) "" else sharedText
        }
        val url = explicitUrl.ifBlank { sharedUrl }
        if (text.isBlank() && url.isBlank()) return null
        return PendingCapture(
            requestId = System.currentTimeMillis(),
            title = getStringExtra(EXTRA_CAPTURE_TITLE).orEmpty().ifBlank { "分享采集" },
            text = text,
            url = url,
            showFolderPicker = getBooleanExtra(EXTRA_PICK_FOLDER, true)
        )
    }

    private fun Intent.isTextShare(): Boolean {
        return action == Intent.ACTION_SEND && type == "text/plain" && !getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()
    }

    private fun extractFirstUrl(text: String): String? {
        return Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ';', '。', '，', '；')
    }
}

private data class PendingCapture(
    val requestId: Long,
    val title: String,
    val text: String,
    val url: String,
    val showFolderPicker: Boolean
)
