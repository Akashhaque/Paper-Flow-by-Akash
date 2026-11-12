package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.data.model.*
import com.example.engine.ImageProcessor
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val imagePages by viewModel.imagePages.collectAsState()
    val pageSize by viewModel.selectedPageSize.collectAsState()
    val orientation by viewModel.selectedOrientation.collectAsState()
    val margin by viewModel.selectedMargin.collectAsState()
    val imageFit by viewModel.selectedImageFit.collectAsState()
    val imageQuality by viewModel.selectedImageQuality.collectAsState()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("PaperFlow_Document_${System.currentTimeMillis() % 10000}") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImagesFromUris(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Images to PDF (${imagePages.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Page Setup", tint = PaperRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (imagePages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add More")
                        }

                        Button(
                            onClick = { showExportDialog = true },
                            modifier = Modifier.weight(1.5f).testTag("generate_pdf_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create PDF", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        if (imagePages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(PaperLightRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = PaperRed,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No images selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select multiple photos from your gallery to create a multi-page PDF.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Photos from Gallery", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Page Setup Summary Chip
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSettingsSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = PaperRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Page Setup", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    "${pageSize.displayName.split(" ").first()} • ${orientation.displayName} • ${margin.displayName}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("Edit", color = PaperRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                itemsIndexed(imagePages) { index, item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header row with page badge and actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    color = PaperLightRed,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Page ${index + 1} of ${imagePages.size}",
                                        color = PaperRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = { viewModel.rotateImagePage(index) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeImage(index) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Image Preview
                            val previewBitmap = remember(item.bitmap, item.filter, item.rotationDegrees) {
                                var bmp = item.bitmap
                                if (item.rotationDegrees % 360f != 0f) {
                                    bmp = ImageProcessor.rotateBitmap(bmp, item.rotationDegrees)
                                }
                                if (item.filter != DocumentFilter.ORIGINAL) {
                                    bmp = ImageProcessor.applyFilter(bmp, item.filter)
                                }
                                bmp
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF3F3F5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Page preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Filter selection row
                            Text("Page Filter:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(DocumentFilter.values().size) { fIdx ->
                                    val f = DocumentFilter.values()[fIdx]
                                    val isSelected = (item.filter == f)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.applyFilterToImage(index, f) },
                                        label = { Text(f.displayName, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PaperLightRed,
                                            selectedLabelColor = PaperRed
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Page Settings BottomSheet
    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("PDF Page Customization", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                // Page Size Selector
                Text("Page Size", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PageSizeOption.values().size) { i ->
                        val opt = PageSizeOption.values()[i]
                        FilterChip(
                            selected = pageSize == opt,
                            onClick = { viewModel.selectedPageSize.value = opt },
                            label = { Text(opt.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                // Page Orientation
                Text("Orientation", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientation.values().forEach { opt ->
                        FilterChip(
                            selected = orientation == opt,
                            onClick = { viewModel.selectedOrientation.value = opt },
                            label = { Text(opt.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                // Margin
                Text("Margins", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarginOption.values().forEach { opt ->
                        FilterChip(
                            selected = margin == opt,
                            onClick = { viewModel.selectedMargin.value = opt },
                            label = { Text(opt.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                // Image Fitting
                Text("Image Fitting", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageFitOption.values().size) { i ->
                        val opt = ImageFitOption.values()[i]
                        FilterChip(
                            selected = imageFit == opt,
                            onClick = { viewModel.selectedImageFit.value = opt },
                            label = { Text(opt.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                Button(
                    onClick = { showSettingsSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply Settings")
                }
            }
        }
    }

    // Export PDF Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Generate PDF", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = exportFileName,
                    onValueChange = { exportFileName = it },
                    label = { Text("PDF File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        viewModel.exportImageToPdf(exportFileName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                ) {
                    Text("Save & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
