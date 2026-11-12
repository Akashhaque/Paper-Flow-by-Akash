package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PDFFile
import com.example.engine.DocumentConverterEngine
import com.example.engine.OcrEngine
import com.example.engine.PdfAdvancedToolsEngine
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentConverterScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allFiles by viewModel.allFiles.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Convert To PDF, 1: Convert From PDF

    // SAF Document Picker for files to convert into PDF
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = queryFileName(context, uri) ?: "document_${System.currentTimeMillis()}"
            viewModel.convertExternalDocument(uri, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Document Converter", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = PaperRed,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PaperRed
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Office/Files to PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("PDF to Other Formats", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            if (selectedTab == 0) {
                // To PDF
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    docPickerLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "text/csv",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "text/plain",
                                            "text/html"
                                        )
                                    )
                                }
                                .testTag("pick_doc_to_convert"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PaperLightRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = PaperRed,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Select Document from Device",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Supports DOC, DOCX, XLSX, CSV, PPTX, TXT, HTML",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        docPickerLauncher.launch(
                                            arrayOf(
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "application/msword",
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "application/vnd.ms-excel",
                                                "text/csv",
                                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                                "text/plain",
                                                "text/html"
                                            )
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse Files")
                                }
                            }
                        }
                    }

                    item {
                        Text("Supported Conversions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PaperRed)
                    }

                    item {
                        FormatInfoRow("Word to PDF", "DOC, DOCX, RTF to PDF with preserved paragraphs and formatting", Icons.Default.Description, Color(0xFF2563EB))
                    }
                    item {
                        FormatInfoRow("Excel to PDF", "XLSX, XLS, CSV to styled spreadsheet table PDF", Icons.Default.TableChart, Color(0xFF16A34A))
                    }
                    item {
                        FormatInfoRow("PowerPoint to PDF", "PPT, PPTX presentation slides converted to landscape PDF", Icons.Default.Slideshow, Color(0xFFD97706))
                    }
                    item {
                        FormatInfoRow("Text & Notes to PDF", "TXT, Markdown, and HTML converted to formatted PDF", Icons.Default.Article, Color(0xFF9333EA))
                    }
                }
            } else {
                // PDF to Other Formats
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = "Select a PDF to convert into other formats:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (allFiles.isEmpty()) {
                        item {
                            Text("No PDF documents available to convert.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        items(allFiles.size) { index ->
                            val file = allFiles[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${file.pageCount} pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // PDF to Images
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.isProcessing.value = true
                                                    viewModel.processingTitle.value = "Extracting Images..."
                                                    try {
                                                        val outDir = File(viewModel.repository.getAppStorageDir(), "${file.fileName.removeSuffix(".pdf")}_images")
                                                        val images = DocumentConverterEngine.convertPdfToImages(context, File(file.filePath), outDir)
                                                        viewModel.showMessage("Extracted ${images.size} JPG image(s) to ${outDir.name}")
                                                    } catch (e: Exception) {
                                                        viewModel.showMessage("Extraction error: ${e.localizedMessage}")
                                                    } finally {
                                                        viewModel.isProcessing.value = false
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("To JPG", fontSize = 11.sp)
                                        }

                                        // PDF to Word (DOCX)
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.isProcessing.value = true
                                                    viewModel.processingTitle.value = "Exporting to Word..."
                                                    try {
                                                        val ocrRes = OcrEngine.recognizeTextFromPdf(File(file.filePath))
                                                        val outWordFile = File(viewModel.repository.getAppStorageDir(), "${file.fileName.removeSuffix(".pdf")}.docx")
                                                        DocumentConverterEngine.createDocxFromText(ocrRes.fullText, outWordFile)
                                                        viewModel.showMessage("Exported DOCX Word document: ${outWordFile.name}")
                                                    } catch (e: Exception) {
                                                        viewModel.showMessage("Word export error: ${e.localizedMessage}")
                                                    } finally {
                                                        viewModel.isProcessing.value = false
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("To Word", fontSize = 11.sp)
                                        }

                                        // PDF to Excel (CSV)
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    viewModel.isProcessing.value = true
                                                    viewModel.processingTitle.value = "Extracting Tables to CSV..."
                                                    try {
                                                        val ocrRes = OcrEngine.recognizeTextFromPdf(File(file.filePath))
                                                        val rows = ocrRes.paragraphs.map { listOf(it) }
                                                        val outCsvFile = File(viewModel.repository.getAppStorageDir(), "${file.fileName.removeSuffix(".pdf")}.csv")
                                                        DocumentConverterEngine.createCsvFromText(rows, outCsvFile)
                                                        viewModel.showMessage("Exported CSV spreadsheet: ${outCsvFile.name}")
                                                    } catch (e: Exception) {
                                                        viewModel.showMessage("Excel export error: ${e.localizedMessage}")
                                                    } finally {
                                                        viewModel.isProcessing.value = false
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("To Excel", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatInfoRow(title: String, desc: String, icon: ImageVector, tint: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx != -1) name = it.getString(idx)
        }
    }
    return name
}
