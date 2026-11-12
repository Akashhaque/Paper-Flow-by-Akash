package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PageSizeOption
import com.example.data.model.SourceType
import com.example.engine.PdfCreatorEngine
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlinx.coroutines.launch
import java.io.File

data class CreateAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color,
    val bg: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMenuSheet(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showBlankPdfDialog by remember { mutableStateOf(false) }
    var blankDocName by remember { mutableStateOf("Blank_Document") }
    var blankPageCount by remember { mutableStateOf(1) }

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

    val createActions = listOf(
        CreateAction(
            title = "Smart Scan (Camera)",
            description = "Camera scan with smart edge detection & auto-crop",
            icon = Icons.Default.DocumentScanner,
            iconTint = PaperRed,
            bg = PaperLightRed,
            onClick = { viewModel.navigateTo(AppScreen.SCANNER) }
        ),
        CreateAction(
            title = "Auto-Crop & Enhance Photo",
            description = "CamScanner desk cutoff, paper whitening & magic color",
            icon = Icons.Default.CropRotate,
            iconTint = Color(0xFFD97706),
            bg = Color(0xFFFFFBEB),
            onClick = {
                photoEnhanceLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        ),
        CreateAction(
            title = "Image to PDF",
            description = "Select single or multiple photos from gallery",
            icon = Icons.Default.AddPhotoAlternate,
            iconTint = Color(0xFF2563EB),
            bg = Color(0xFFEFF6FF),
            onClick = { viewModel.navigateTo(AppScreen.IMAGE_TO_PDF) }
        ),
        CreateAction(
            title = "DOC / DOCX to PDF",
            description = "Convert Microsoft Word & text documents",
            icon = Icons.Default.Description,
            iconTint = Color(0xFF16A34A),
            bg = Color(0xFFF0FDF4),
            onClick = { viewModel.navigateTo(AppScreen.DOCUMENT_CONVERTER) }
        ),
        CreateAction(
            title = "Excel to PDF",
            description = "Convert XLSX, XLS, and CSV spreadsheets",
            icon = Icons.Default.TableChart,
            iconTint = Color(0xFF059669),
            bg = Color(0xFFECFDF5),
            onClick = { viewModel.navigateTo(AppScreen.DOCUMENT_CONVERTER) }
        ),
        CreateAction(
            title = "PowerPoint to PDF",
            description = "Convert PPT & PPTX presentation slides",
            icon = Icons.Default.Slideshow,
            iconTint = Color(0xFFD97706),
            bg = Color(0xFFFFFBEB),
            onClick = { viewModel.navigateTo(AppScreen.DOCUMENT_CONVERTER) }
        ),
        CreateAction(
            title = "Create Blank PDF",
            description = "Start with a clean blank canvas for drawing & typing",
            icon = Icons.Default.NoteAdd,
            iconTint = Color(0xFF9333EA),
            bg = Color(0xFFFAF5FF),
            onClick = { showBlankPdfDialog = true }
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Document", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Choose a creation method",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            createActions.forEach { action ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { action.onClick() }
                        .testTag("create_${action.title.lowercase().replace(" ", "_")}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(action.bg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.title,
                                tint = action.iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = action.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = action.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showBlankPdfDialog) {
        AlertDialog(
            onDismissRequest = { showBlankPdfDialog = false },
            title = { Text("Create Blank PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = blankDocName,
                        onValueChange = { blankDocName = it },
                        label = { Text("Document Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Number of Pages: $blankPageCount", fontSize = 14.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (blankPageCount > 1) blankPageCount-- }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        IconButton(onClick = { if (blankPageCount < 20) blankPageCount++ }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlankPdfDialog = false
                        coroutineScope.launch {
                            val cleanName = if (blankDocName.endsWith(".pdf")) blankDocName else "$blankDocName.pdf"
                            val outFile = File(viewModel.repository.getAppStorageDir(), cleanName)
                            PdfCreatorEngine.createBlankPdf(outFile, blankPageCount, PageSizeOption.A4)
                            val saved = viewModel.repository.registerNewFile(outFile, SourceType.EDITED)
                            viewModel.openPdf(saved)
                            viewModel.showMessage("Blank PDF created!")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlankPdfDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
