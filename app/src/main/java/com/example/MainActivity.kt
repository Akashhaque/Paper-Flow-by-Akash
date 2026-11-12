package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.PaperFlowBottomNavBar
import com.example.ui.components.ProcessingProgressDialog
import com.example.ui.screens.*
import com.example.ui.theme.PaperFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsState()
            val currentScreen by mainViewModel.currentScreen.collectAsState()
            val userMessage by mainViewModel.userMessage.collectAsState()
            val isProcessing by mainViewModel.isProcessing.collectAsState()
            val processingTitle by mainViewModel.processingTitle.collectAsState()
            val processingProgress by mainViewModel.processingProgress.collectAsState()

            val snackbarHostState = remember { SnackbarHostState() }

            // Handle Toast / Snackbar notifications
            LaunchedEffect(userMessage) {
                userMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    mainViewModel.clearMessage()
                }
            }

            // System Back Handler
            BackHandler(enabled = currentScreen != AppScreen.HOME) {
                mainViewModel.navigateBack()
            }

            PaperFlowTheme(darkTheme = isDarkMode) {
                val showBottomBar = currentScreen in listOf(
                    AppScreen.HOME,
                    AppScreen.FILES,
                    AppScreen.TOOLS,
                    AppScreen.SETTINGS
                )

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showBottomBar) {
                            PaperFlowBottomNavBar(
                                currentScreen = currentScreen,
                                onNavigate = { screen ->
                                    mainViewModel.navigateTo(screen)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Unspecified)
                    ) {
                        when (currentScreen) {
                            AppScreen.HOME -> HomeScreen(viewModel = mainViewModel)
                            AppScreen.FILES -> FilesScreen(viewModel = mainViewModel)
                            AppScreen.CREATE_MENU -> CreateMenuSheet(viewModel = mainViewModel)
                            AppScreen.TOOLS -> ToolsScreen(viewModel = mainViewModel)
                            AppScreen.SETTINGS -> SettingsScreen(viewModel = mainViewModel)
                            AppScreen.SCANNER -> ScannerScreen(viewModel = mainViewModel)
                            AppScreen.SMART_CROP_ENHANCE -> SmartCropEnhanceScreen(viewModel = mainViewModel)
                            AppScreen.IMAGE_TO_PDF -> ImageToPdfScreen(viewModel = mainViewModel)
                            AppScreen.PDF_READER_EDITOR -> PdfReaderAndEditorScreen(viewModel = mainViewModel)
                            AppScreen.DOCUMENT_CONVERTER -> DocumentConverterScreen(viewModel = mainViewModel)
                            AppScreen.PDF_ORGANIZER -> PdfOrganizerScreen(viewModel = mainViewModel)
                            AppScreen.OCR -> OcrScreen(viewModel = mainViewModel)
                            AppScreen.WATERMARK_TOOLS -> WatermarkAndPageNumberScreen(viewModel = mainViewModel)
                        }
                    }

                    if (isProcessing) {
                        ProcessingProgressDialog(
                            title = processingTitle,
                            progress = processingProgress
                        )
                    }
                }
            }
        }
    }
}
