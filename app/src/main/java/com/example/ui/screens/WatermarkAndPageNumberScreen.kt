package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PDFFile
import com.example.engine.PageNumberConfig
import com.example.engine.WatermarkConfig
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkAndPageNumberScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val allFiles by viewModel.allFiles.collectAsState()
    val activePdf by viewModel.activePdfFile.collectAsState()

    var selectedFile by remember { mutableStateOf(activePdf ?: allFiles.firstOrNull()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Watermark, 1: Page Numbers

    // Watermark State
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkColorHex by remember { mutableStateOf("#F5222D") }
    var watermarkAlpha by remember { mutableStateOf(0.25f) }
    var watermarkFontSize by remember { mutableStateOf(44f) }
    var watermarkRotation by remember { mutableStateOf(-45f) }

    // Page Number State
    var isBottomNumber by remember { mutableStateOf(true) }
    var alignmentOption by remember { mutableStateOf(Paint.Align.CENTER) }
    var pageNumberFormat by remember { mutableStateOf("Page {n} of {total}") }
    var startNumber by remember { mutableStateOf(1) }

    var showPdfSelectorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watermark & Numbers", fontWeight = FontWeight.Bold) },
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
                    text = { Text("Add Watermark", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Add Page Numbers", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Target Document Selector Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPdfSelectorDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PaperRed, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFile?.fileName ?: "Select a PDF Document",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (selectedFile != null) "${selectedFile?.pageCount} pages • Tap to change" else "Tap to choose target PDF",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("Change", color = PaperRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (selectedTab == 0) {
                    // Watermark Configuration
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Watermark Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = { watermarkText = it },
                                    label = { Text("Watermark Text") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Preset words
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("CONFIDENTIAL", "DRAFT", "APPROVED", "SAMPLE").forEach { preset ->
                                        SuggestionChip(
                                            onClick = { watermarkText = preset },
                                            label = { Text(preset, fontSize = 10.sp) }
                                        )
                                    }
                                }

                                // Color Selector
                                Text("Color", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val colors = listOf("#F5222D" to "Red", "#777777" to "Gray", "#2563EB" to "Blue", "#16A34A" to "Green", "#000000" to "Black")
                                    colors.forEach { (hex, name) ->
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                .border(if (watermarkColorHex == hex) 2.5.dp else 0.dp, Color.Black, CircleShape)
                                                .clickable { watermarkColorHex = hex }
                                        )
                                    }
                                }

                                // Opacity Slider
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Transparency (Opacity)", fontSize = 12.sp)
                                        Text("${(watermarkAlpha * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = watermarkAlpha,
                                        onValueChange = { watermarkAlpha = it },
                                        valueRange = 0.1f..0.8f
                                    )
                                }

                                // Rotation Slider
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Rotation Angle", fontSize = 12.sp)
                                        Text("${watermarkRotation.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = watermarkRotation,
                                        onValueChange = { watermarkRotation = it },
                                        valueRange = -90f..90f
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (selectedFile != null) {
                                            viewModel.openPdf(selectedFile!!)
                                            viewModel.addWatermarkToActivePdf(
                                                WatermarkConfig(
                                                    text = watermarkText,
                                                    colorHex = watermarkColorHex,
                                                    alpha = watermarkAlpha,
                                                    fontSize = watermarkFontSize,
                                                    rotationDegrees = watermarkRotation
                                                )
                                            )
                                        } else {
                                            viewModel.showMessage("Please select a target PDF first")
                                        }
                                    },
                                    enabled = selectedFile != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Watermark Stamp", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Page Number Configuration
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("Page Number Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                // Position
                                Text("Position", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = isBottomNumber,
                                        onClick = { isBottomNumber = true },
                                        label = { Text("Bottom Footer") }
                                    )
                                    FilterChip(
                                        selected = !isBottomNumber,
                                        onClick = { isBottomNumber = false },
                                        label = { Text("Top Header") }
                                    )
                                }

                                // Alignment
                                Text("Alignment", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = alignmentOption == Paint.Align.LEFT,
                                        onClick = { alignmentOption = Paint.Align.LEFT },
                                        label = { Text("Left") }
                                    )
                                    FilterChip(
                                        selected = alignmentOption == Paint.Align.CENTER,
                                        onClick = { alignmentOption = Paint.Align.CENTER },
                                        label = { Text("Center") }
                                    )
                                    FilterChip(
                                        selected = alignmentOption == Paint.Align.RIGHT,
                                        onClick = { alignmentOption = Paint.Align.RIGHT },
                                        label = { Text("Right") }
                                    )
                                }

                                // Format options
                                Text("Format", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                val formats = listOf("Page {n} of {total}", "- {n} -", "{n} / {total}", "Page {n}")
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    formats.forEach { fmt ->
                                        FilterChip(
                                            selected = pageNumberFormat == fmt,
                                            onClick = { pageNumberFormat = fmt },
                                            label = { Text(fmt.replace("{n}", "1").replace("{total}", "5"), fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (selectedFile != null) {
                                            viewModel.openPdf(selectedFile!!)
                                            viewModel.addPageNumbersToActivePdf(
                                                PageNumberConfig(
                                                    isBottom = isBottomNumber,
                                                    alignment = alignmentOption,
                                                    format = pageNumberFormat,
                                                    startNumber = startNumber
                                                )
                                            )
                                        } else {
                                            viewModel.showMessage("Please select a target PDF first")
                                        }
                                    },
                                    enabled = selectedFile != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Page Numbers to PDF", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPdfSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showPdfSelectorDialog = false },
            title = { Text("Select Document") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allFiles.size) { i ->
                        val f = allFiles[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedFile = f
                                    showPdfSelectorDialog = false
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
