package com.huoyejia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.huoyejia.ui.CaptureScreen
import com.huoyejia.ui.DashboardScreen
import com.huoyejia.ui.ExplainScreen
import com.huoyejia.ui.HuoyejiaScaffold
import com.huoyejia.ui.InboxScreen
import com.huoyejia.ui.HistoryScreen
import com.huoyejia.ui.NoteDetailScreen
import com.huoyejia.ui.ReviewScreen
import com.huoyejia.ui.SearchScreen
import com.huoyejia.ui.theme.HuoyejiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val isBusy by viewModel.isBusy.collectAsState()

                HuoyejiaScaffold(navController = navController, isBusy = isBusy) {
                    NavHost(navController = navController, startDestination = "inbox") {
                        composable("inbox") {
                            InboxScreen(
                                notes = notes,
                                relations = relations,
                                cards = cards,
                                stats = stats,
                                onOpenNote = { navController.navigate("detail/$it") },
                                onAddDemo = viewModel::addDemoLinkedNote,
                                onStartReview = { navController.navigate("review") }
                            )
                        }
                        composable("capture") {
                            CaptureScreen(
                                onSaveText = viewModel::addManualNote,
                                onMockScreenshot = viewModel::addMockScreenshot,
                                onOpenFloatingCapture = ::openFloatingCapture
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                results = results,
                                onSearch = viewModel::search,
                                onOpenNote = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("review") {
                            ReviewScreen(
                                notes = notes,
                                cards = cards,
                                onDone = viewModel::completeCard
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                notes = notes,
                                cards = cards,
                                onOpenNote = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("explain") {
                            ExplainScreen(
                                notes = notes,
                                explainState = explainState,
                                onSelectNote = viewModel::selectExplainNote,
                                onGenerate = viewModel::generateExplainPack,
                                onExportPpt = viewModel::exportExplainPpt,
                                onExportAnimation = viewModel::exportExplainAnimation,
                                onGenerateVideo = viewModel::generateExplainVideo,
                                onOpenNote = { navController.navigate("detail/$it") }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(stats = stats, notes = notes, cards = cards)
                        }
                        composable(
                            route = "detail/{noteId}",
                            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
                        ) { entry ->
                            val noteId = entry.arguments?.getString("noteId").orEmpty()
                            NoteDetailScreen(
                                note = viewModel.noteById(noteId),
                                notes = notes,
                                relations = relations,
                                cards = cards,
                                onBack = { navController.popBackStack() },
                                onOpenNote = { navController.navigate("detail/$it") },
                                onStartReview = { navController.navigate("review") }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openFloatingCapture() {
        if (Settings.canDrawOverlays(this)) {
            FloatingCaptureService.start(this)
            moveTaskToBack(true)
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
