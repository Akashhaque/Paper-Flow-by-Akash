package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PDFFile
import com.example.engine.DocumentConverterEngine
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allFiles by viewModel.allFiles.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()
    val isProcessing by viewModel.isOcrProcessing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showPdfSelectorDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        viewModel.runOcrOnBitmap(bmp)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Text Recognition", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Scan Image", tint = PaperRed)
                    }
                    IconButton(onClick = { showPdfSelectorDialog = true }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Select PDF", tint = PaperRed)
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
                .padding(16.dp)
        ) {
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PaperRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Extracting text via Google ML Kit...", fontWeight = FontWeight.SemiBold)
                        Text("Reading paragraphs, blocks, and words", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (ocrResult == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(PaperLightRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = PaperRed, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Optical Character Recognition", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Extract machine-readable text from photos, documents, or PDF pages with paragraph preservation.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Image")
                        }

                        OutlinedButton(
                            onClick = { showPdfSelectorDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan PDF")
                        }
                    }
                }
            } else {
                val res = ocrResult!!

                // Actions and Search Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${res.blockCount} blocks • ${res.lineCount} lines",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaperRed
                    )

                    Row {
                        // Copy Text
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Recognized Text", res.fullText))
                                viewModel.showMessage("Text copied to clipboard!")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }

                        // Share Text
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, res.fullText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Extracted Text"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }

                        // Export as TXT
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val txtFile = File(viewModel.repository.getAppStorageDir(), "OCR_Text_${System.currentTimeMillis() % 10000}.txt")
                                    FileWriter(txtFile).use { it.write(res.fullText) }
                                    viewModel.showMessage("Saved as text file: ${txtFile.name}")
                                }
                            }
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Export TXT")
                        }

                        // Export as Word (DOCX)
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val docxFile = File(viewModel.repository.getAppStorageDir(), "OCR_Document_${System.currentTimeMillis() % 10000}.docx")
                                    DocumentConverterEngine.createDocxFromText(res.fullText, docxFile)
                                    viewModel.showMessage("Saved as Word DOCX: ${docxFile.name}")
                                }
                            }
                        ) {
                            Icon(Icons.Default.Article, contentDescription = "Export DOCX")
                        }
                    }
                }

                // Search in extracted text
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search inside extracted text...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PaperRed) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                // Scrollable text display
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = res.fullText,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPdfSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showPdfSelectorDialog = false },
            title = { Text("Select PDF for OCR") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allFiles.size) { i ->
                        val f = allFiles[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showPdfSelectorDialog = false
                                    viewModel.runOcrOnPdf(f)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PaperRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(f.fileName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${f.pageCount} pages", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPdfSelectorDialog = false }) { Text("Cancel") }
            }
        )
    }
}
