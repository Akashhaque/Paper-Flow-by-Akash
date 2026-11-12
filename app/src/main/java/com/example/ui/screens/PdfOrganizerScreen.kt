package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompressionLevel
import com.example.data.model.PDFFile
import com.example.engine.PdfOrganizerEngine
import com.example.engine.PdfRendererEngine
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOrganizerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val allFiles by viewModel.allFiles.collectAsState()
    val toolType by viewModel.activeOrganizerTool.collectAsState()

    var selectedPdfForPageOps by remember { mutableStateOf<PDFFile?>(null) }
    var pageThumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPageIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pageRotations by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }

    // Merge multi-file selection state
    var selectedFilesForMerge by remember { mutableStateOf<List<PDFFile>>(emptyList()) }
    var mergeDocName by remember { mutableStateOf("Merged_Document_${System.currentTimeMillis() % 10000}") }

    // Compress state
    var selectedCompressionLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }

    LaunchedEffect(selectedPdfForPageOps) {
        val file = selectedPdfForPageOps
        if (file != null) {
            pageThumbnails = emptyList()
            selectedPageIndices = emptySet()
            pageRotations = emptyMap()
            withContext(Dispatchers.IO) {
                val list = mutableListOf<Bitmap>()
                val count = PdfRendererEngine.getPageCount(file.filePath)
                for (i in 0 until count) {
                    val bmp = PdfRendererEngine.renderPageToBitmap(file.filePath, i, targetWidth = 350)
                    if (bmp != null) list.add(bmp)
                }
                pageThumbnails = list
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (toolType) {
                            "MERGE" -> "Merge PDF Documents"
                            "SPLIT" -> "Split & Extract Pages"
                            "ROTATE" -> "Rotate PDF Pages"
                            "DELETE" -> "Delete PDF Pages"
                            "COMPRESS" -> "Compress PDF Size"
                            else -> "PDF Organizer"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
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
            // Mode Switcher Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tools = listOf("MERGE" to "Merge", "SPLIT" to "Split", "ROTATE" to "Rotate", "DELETE" to "Delete", "COMPRESS" to "Compress")
                tools.forEach { (tag, label) ->
                    val isSel = (toolType == tag)
                    FilterChip(
                        selected = isSel,
                        onClick = {
                            viewModel.activeOrganizerTool.value = tag
                            selectedPdfForPageOps = null
                        },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PaperLightRed,
                            selectedLabelColor = PaperRed
                        )
                    )
                }
            }

            if (toolType == "MERGE") {
                // Merge Screen: Select Multiple PDFs
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = "Select PDFs to merge in sequence (${selectedFilesForMerge.size} selected):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allFiles) { file ->
                            val isSelected = selectedFilesForMerge.any { it.id == file.id }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFilesForMerge = if (isSelected) {
                                            selectedFilesForMerge.filterNot { it.id == file.id }
                                        } else {
                                            selectedFilesForMerge + file
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) PaperLightRed else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) BorderStroke(1.5.dp, PaperRed) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { check ->
                                            selectedFilesForMerge = if (check) selectedFilesForMerge + file else selectedFilesForMerge.filterNot { it.id == file.id }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = PaperRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${file.pageCount} pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = mergeDocName,
                        onValueChange = { mergeDocName = it },
                        label = { Text("Merged File Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.mergePdfs(selectedFilesForMerge, mergeDocName) },
                        enabled = selectedFilesForMerge.size >= 2,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                    ) {
                        Icon(Icons.Default.CallMerge, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Merge ${selectedFilesForMerge.size} Files", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (selectedPdfForPageOps == null) {
                // Select a PDF for Split / Rotate / Delete / Compress
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = "Choose a PDF document to ${toolType.lowercase()}:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allFiles) { file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPdfForPageOps = file },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PaperRed, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.fileName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${file.pageCount} pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            } else {
                // Interactive Page Grid for Selected PDF
                val activeDoc = selectedPdfForPageOps!!
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(activeDoc.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${pageThumbnails.size} pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { selectedPdfForPageOps = null }) {
                            Text("Change File", color = PaperRed, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (toolType == "COMPRESS") {
                        // Compress Settings View
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Select Compression Level", fontWeight = FontWeight.Bold)
                                CompressionLevel.values().forEach { level ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { selectedCompressionLevel = level }
                                    ) {
                                        RadioButton(
                                            selected = selectedCompressionLevel == level,
                                            onClick = { selectedCompressionLevel = level }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(level.displayName, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.compressActivePdf(selectedCompressionLevel)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compress PDF Now", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Page Thumbnails Grid for Split / Rotate / Delete
                        Text(
                            text = when (toolType) {
                                "SPLIT" -> "Tap pages to select for extraction:"
                                "ROTATE" -> "Tap rotate button on individual pages or rotate all:"
                                "DELETE" -> "Tap pages you wish to remove:"
                                else -> "Select pages:"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(pageThumbnails) { index, bmp ->
                                val isSelected = selectedPageIndices.contains(index)
                                val rotation = pageRotations[index] ?: 0f

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFEEEEF0))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) PaperRed else Color.LightGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            selectedPageIndices = if (isSelected) {
                                                selectedPageIndices - index
                                            } else {
                                                selectedPageIndices + index
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )

                                    // Badge with page number
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .background(Color(0xAA000000), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )

                                    if (toolType == "ROTATE") {
                                        IconButton(
                                            onClick = {
                                                val next = (rotation + 90f) % 360f
                                                pageRotations = pageRotations + (index to next)
                                            },
                                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color(0x99000000), CircleShape)
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    if (isSelected && toolType == "DELETE") {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Color.Red,
                                            modifier = Modifier.align(Alignment.Center).size(36.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action button depending on tool
                        when (toolType) {
                            "SPLIT" -> {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val outFile = File(viewModel.repository.getAppStorageDir(), "${activeDoc.fileName.removeSuffix(".pdf")}_extracted.pdf")
                                            PdfOrganizerEngine.extractOrSplitPages(File(activeDoc.filePath), selectedPageIndices.toList().sorted(), outFile)
                                            val saved = viewModel.repository.registerNewFile(outFile)
                                            viewModel.openPdf(saved)
                                            viewModel.showMessage("Extracted ${selectedPageIndices.size} pages into new PDF!")
                                        }
                                    },
                                    enabled = selectedPageIndices.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Extract ${selectedPageIndices.size} Pages as PDF", fontWeight = FontWeight.Bold)
                                }
                            }

                            "DELETE" -> {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val outFile = File(viewModel.repository.getAppStorageDir(), "${activeDoc.fileName.removeSuffix(".pdf")}_pruned.pdf")
                                            PdfOrganizerEngine.deletePages(File(activeDoc.filePath), selectedPageIndices, outFile)
                                            val saved = viewModel.repository.registerNewFile(outFile)
                                            viewModel.openPdf(saved)
                                            viewModel.showMessage("Removed ${selectedPageIndices.size} pages!")
                                        }
                                    },
                                    enabled = selectedPageIndices.isNotEmpty() && selectedPageIndices.size < pageThumbnails.size,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Delete ${selectedPageIndices.size} Selected Pages", fontWeight = FontWeight.Bold)
                                }
                            }

                            "ROTATE" -> {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val outFile = File(viewModel.repository.getAppStorageDir(), "${activeDoc.fileName.removeSuffix(".pdf")}_rotated.pdf")
                                            PdfOrganizerEngine.rotatePages(File(activeDoc.filePath), pageRotations, outFile)
                                            val saved = viewModel.repository.registerNewFile(outFile)
                                            viewModel.openPdf(saved)
                                            viewModel.showMessage("Rotated pages saved!")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Save Rotated Document", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
