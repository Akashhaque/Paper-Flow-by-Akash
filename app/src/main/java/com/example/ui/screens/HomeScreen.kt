package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PDFFile
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.PdfFileListItem
import com.example.ui.components.RenameFileDialog
import com.example.ui.theme.*

data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBg: Color,
    val targetScreen: AppScreen,
    val toolTag: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentFiles by viewModel.recentFiles.collectAsState()
    val allFiles by viewModel.allFiles.collectAsState()

    var fileToRename by remember { mutableStateOf<PDFFile?>(null) }
    var fileForDetails by remember { mutableStateOf<PDFFile?>(null) }

    val photoEnhanceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.startCropAndEnhance(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val quickActions = remember {
        listOf(
            QuickActionItem("Auto-Crop & Scan", "Desk Cutoff & Magic", Icons.Default.CropRotate, Color(0xFFFEF2F2), AppScreen.SCANNER),
            QuickActionItem("Image to PDF", "From Photos", Icons.Default.Image, Color(0xFFEFF6FF), AppScreen.IMAGE_TO_PDF),
            QuickActionItem("Scan Document", "HD Camera", Icons.Default.DocumentScanner, Color(0xFFFDF2F8), AppScreen.SCANNER),
            QuickActionItem("DOC to PDF", "Word Converter", Icons.Default.Description, Color(0xFFF0FDF4), AppScreen.DOCUMENT_CONVERTER),
            QuickActionItem("PDF to Word", "Export DOCX", Icons.Default.TextFields, Color(0xFFFAF5FF), AppScreen.DOCUMENT_CONVERTER),
            QuickActionItem("Excel to PDF", "Spreadsheet", Icons.Default.TableChart, Color(0xFFECFDF5), AppScreen.DOCUMENT_CONVERTER),
            QuickActionItem("PPT to PDF", "Presentation", Icons.Default.Slideshow, Color(0xFFFFFBEB), AppScreen.DOCUMENT_CONVERTER),
            QuickActionItem("Merge PDF", "Combine Files", Icons.Default.CallMerge, Color(0xFFEFF6FF), AppScreen.PDF_ORGANIZER, "MERGE"),
            QuickActionItem("Split PDF", "Extract Pages", Icons.Default.CallSplit, Color(0xFFFEF3C7), AppScreen.PDF_ORGANIZER, "SPLIT"),
            QuickActionItem("Compress PDF", "Reduce Size", Icons.Default.Compress, Color(0xFFF3E8FF), AppScreen.PDF_ORGANIZER, "COMPRESS"),
            QuickActionItem("PDF Editor", "Annotate & Sign", Icons.Default.Edit, Color(0xFFFEE2E2), AppScreen.PDF_READER_EDITOR)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PaperRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PaperFlow",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.FILES) },
                        modifier = Modifier.testTag("home_search_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search Files")
                    }
                    IconButton(onClick = { viewModel.showMessage("All document operations run 100% offline.") }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Hero Welcome Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PaperRed, PaperDarkRed)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Surface(
                            color = Color(0x33FFFFFF),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "OFFLINE & SECURE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Everything You Need for PDF",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan, convert, edit, annotate, compress and secure your documents with one tap.",
                            color = Color(0xFFFFE5E7),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(AppScreen.SCANNER) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PaperRed),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Smart Scan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    photoEnhanceLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(Color.White, Color.White))),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CropRotate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto-Crop Photo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Quick Actions Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Quick Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.navigateTo(AppScreen.TOOLS) }) {
                        Text("View All Tools", color = PaperRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Quick Actions 2-Column Grid
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (i in quickActions.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val item1 = quickActions[i]
                            QuickActionCard(
                                item = item1,
                                onClick = {
                                    if (item1.toolTag.isNotBlank()) viewModel.activeOrganizerTool.value = item1.toolTag
                                    viewModel.navigateTo(item1.targetScreen)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            if (i + 1 < quickActions.size) {
                                val item2 = quickActions[i + 1]
                                QuickActionCard(
                                    item = item2,
                                    onClick = {
                                        if (item2.toolTag.isNotBlank()) viewModel.activeOrganizerTool.value = item2.toolTag
                                        viewModel.navigateTo(item2.targetScreen)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Recently Used Tools Chips
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Recently Used",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ToolChip(icon = Icons.Default.DocumentScanner, label = "Document Scan") {
                            viewModel.navigateTo(AppScreen.SCANNER)
                        }
                    }
                    item {
                        ToolChip(icon = Icons.Default.Compress, label = "Compress PDF") {
                            viewModel.activeOrganizerTool.value = "COMPRESS"
                            viewModel.navigateTo(AppScreen.PDF_ORGANIZER)
                        }
                    }
                    item {
                        ToolChip(icon = Icons.Default.CallMerge, label = "Merge Files") {
                            viewModel.activeOrganizerTool.value = "MERGE"
                            viewModel.navigateTo(AppScreen.PDF_ORGANIZER)
                        }
                    }
                    item {
                        ToolChip(icon = Icons.Default.TextFields, label = "OCR Text") {
                            viewModel.navigateTo(AppScreen.OCR)
                        }
                    }
                    item {
                        ToolChip(icon = Icons.Default.Security, label = "Watermark") {
                            viewModel.navigateTo(AppScreen.WATERMARK_TOOLS)
                        }
                    }
                }
            }

            // Recent Files Section
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.navigateTo(AppScreen.FILES) }) {
                        Text("View All", color = PaperRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            val displayFiles = if (recentFiles.isNotEmpty()) recentFiles else allFiles

            if (displayFiles.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No PDF files yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan or create your first PDF document",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(displayFiles.take(8)) { file ->
                    PdfFileListItem(
                        file = file,
                        onClick = { viewModel.openPdf(file) },
                        onToggleFavorite = { viewModel.toggleFavorite(file) },
                        onRename = { fileToRename = file },
                        onDuplicate = { viewModel.duplicateFile(file) },
                        onDelete = { viewModel.deleteFile(file) },
                        onDetails = { fileForDetails = file },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    fileToRename?.let { file ->
        RenameFileDialog(
            initialName = file.fileName,
            onConfirm = { newName ->
                viewModel.renameFile(file, newName)
                fileToRename = null
            },
            onDismiss = { fileToRename = null }
        )
    }

    fileForDetails?.let { file ->
        FileDetailsDialog(
            file = file,
            onDismiss = { fileForDetails = null }
        )
    }
}

@Composable
private fun QuickActionCard(
    item: QuickActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("action_${item.title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(item.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = PaperRed,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PaperRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
