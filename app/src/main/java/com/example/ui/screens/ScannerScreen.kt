package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.ScanMode
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val scannedPages by viewModel.scannedPages.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()
    val idCardFront by viewModel.idCardFront.collectAsState()
    val idCardBack by viewModel.idCardBack.collectAsState()
    val isBatchAutoEnhance by viewModel.isBatchAutoEnhance.collectAsState()
    val isBatchProcessingPage by viewModel.isBatchProcessingPage.collectAsState()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var showGrid by remember { mutableStateOf(true) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var showFlashEffect by remember { mutableStateOf(false) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveDocName by remember { mutableStateOf("Batch_Scan_${System.currentTimeMillis() % 10000}") }
    var saveAsJpg by remember { mutableStateOf(false) }

    LaunchedEffect(showFlashEffect) {
        if (showFlashEffect) {
            delay(120)
            showFlashEffect = false
        }
    }

    // Single image picker
    val singleGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        if (scanMode == ScanMode.BATCH) {
                            viewModel.addBatchScannedPage(bitmap, autoEnhance = isBatchAutoEnhance)
                        } else {
                            viewModel.startCropAndEnhance(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Multi-image picker for Batch mode
    val batchGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bitmap = BitmapFactory.decodeStream(stream)
                            if (bitmap != null) {
                                viewModel.addBatchScannedPage(bitmap, autoEnhance = isBatchAutoEnhance)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (scanMode == ScanMode.BATCH) "Batch Scanner" else "Smart Scanner",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (scanMode == ScanMode.BATCH) {
                            Text(
                                text = "${scannedPages.size} pages in queue",
                                color = Color(0xFFFFB4BA),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Flash Toggle
                    IconButton(
                        onClick = {
                            flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        }
                    ) {
                        Icon(
                            imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = if (flashMode == ImageCapture.FLASH_MODE_ON) Color.Yellow else Color.White
                        )
                    }

                    // Grid Toggle
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Grid",
                            tint = if (showGrid) PaperRed else Color.White
                        )
                    }

                    // Camera Switch
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        }
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                    }

                    // Clear Batch if pages exist
                    if (scanMode == ScanMode.BATCH && scannedPages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearScannedPages() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Batch", tint = Color(0xFFFF6B72))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera Live View or Permission Placeholder
            if (cameraPermissionState.status.isGranted) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder()
                                .setFlashMode(flashMode)
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()

                            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Camera permission needed", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }

            // Grid Overlay & Document Guide Box
            if (showGrid) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 180.dp)
                        .border(1.5.dp, if (scanMode == ScanMode.ID_CARD) Color.Yellow else Color(0x88FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = when (scanMode) {
                            ScanMode.BATCH -> "BATCH SCANNING • AUTO-EDGE & DESK CUTOFF"
                            ScanMode.ID_CARD -> if (idCardFront == null) "ALIGN ID CARD (FRONT)" else "ALIGN ID CARD (BACK)"
                            ScanMode.PASSPORT -> "ALIGN PASSPORT IN FRAME"
                            else -> "ALIGN DOCUMENT EDGES"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(Color(0x99000000), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Batch Mode Info Banner and Toggle Overlay
            if (scanMode == ScanMode.BATCH) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color(0xCC1E1E1E), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isBatchProcessingPage) Color.Yellow else Color(0xFF10B981))
                    )
                    Text(
                        text = if (isBatchProcessingPage) "Enhancing..." else "Sequential Mode (${scannedPages.size})",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBatchAutoEnhance) PaperRed else Color(0x33FFFFFF))
                            .clickable { viewModel.isBatchAutoEnhance.value = !isBatchAutoEnhance }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isBatchAutoEnhance) "Magic Color: ON" else "Auto-Crop: OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Camera Shutter Flash Effect
            if (showFlashEffect) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.65f))
                )
            }

            // Bottom Controller Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6000000))
                    .padding(bottom = 16.dp)
            ) {
                // Scan Mode Selector Tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    items(ScanMode.values().size) { idx ->
                        val mode = ScanMode.values()[idx]
                        val isSelected = scanMode == mode
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PaperRed else Color.Transparent)
                                .clickable { viewModel.scanMode.value = mode }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Scanned Pages Strip (Sequential preview in Batch or Document mode)
                if (scannedPages.isNotEmpty() || idCardFront != null) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sequential Pages (${scannedPages.size})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (scanMode == ScanMode.BATCH) {
                                Text(
                                    text = "Tap page to edit • Ready to compile",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (scanMode == ScanMode.ID_CARD) {
                                idCardFront?.let { front ->
                                    item {
                                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))) {
                                            Image(bitmap = front.asImageBitmap(), contentDescription = "Front", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            Text("Front", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color(0x99000000)).padding(2.dp))
                                        }
                                    }
                                }
                                idCardBack?.let { back ->
                                    item {
                                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))) {
                                            Image(bitmap = back.asImageBitmap(), contentDescription = "Back", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            Text("Back", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color(0x99000000)).padding(2.dp))
                                        }
                                    }
                                }
                            } else {
                                itemsIndexed(scannedPages) { index, bmp ->
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp, 70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                                            .clickable { viewModel.startCropAndEnhance(bmp) }
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Scanned Page",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        // Page Number Badge
                                        Text(
                                            text = "P${index + 1}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .background(Color(0xCC000000))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                        // Delete Page Button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(18.dp)
                                                .background(Color.Red, CircleShape)
                                                .clickable { viewModel.removeScannedPage(index) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Shutter / Capture Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pick from Gallery (Multi-select in Batch Mode)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = {
                                if (scanMode == ScanMode.BATCH) {
                                    batchGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else {
                                    singleGalleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0x33FFFFFF), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (scanMode == ScanMode.BATCH) Icons.Default.Collections else Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = if (scanMode == ScanMode.BATCH) "Multi-Pick" else "Gallery",
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }

                    // Main Shutter Button with Live Counter Badge
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(3.5.dp, Color.White, CircleShape)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(PaperRed)
                                .clickable {
                                    showFlashEffect = true
                                    imageCapture?.let { capture ->
                                        val executor = Executors.newSingleThreadExecutor()
                                        capture.takePicture(
                                            executor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    val buffer = image.planes[0].buffer
                                                    val bytes = ByteArray(buffer.remaining())
                                                    buffer.get(bytes)
                                                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                                                    val rotation = image.imageInfo.rotationDegrees
                                                    if (rotation != 0) {
                                                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                                    }
                                                    image.close()

                                                    if (scanMode == ScanMode.BATCH) {
                                                        viewModel.addBatchScannedPage(bitmap, autoEnhance = isBatchAutoEnhance)
                                                    } else {
                                                        viewModel.startCropAndEnhance(bitmap)
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    exception.printStackTrace()
                                                    viewModel.showMessage("Capture failed: ${exception.localizedMessage}")
                                                }
                                            }
                                        )
                                    } ?: run {
                                        // Fallback sample bitmap when testing
                                        val simulatedBmp = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888).apply {
                                            eraseColor(android.graphics.Color.WHITE)
                                        }
                                        if (scanMode == ScanMode.BATCH) {
                                            viewModel.addBatchScannedPage(simulatedBmp, autoEnhance = isBatchAutoEnhance)
                                        } else {
                                            viewModel.startCropAndEnhance(simulatedBmp)
                                        }
                                    }
                                }
                                .testTag("scanner_shutter_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(34.dp))
                        }

                        // Batch count badge on shutter
                        if (scanMode == ScanMode.BATCH && scannedPages.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.White, CircleShape)
                                    .border(1.5.dp, PaperRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${scannedPages.size}",
                                    color = PaperRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Save / Compile Button
                    val canSave = if (scanMode == ScanMode.ID_CARD) idCardFront != null else scannedPages.isNotEmpty()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { if (canSave) showSaveDialog = true },
                            enabled = canSave,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canSave) PaperRed else Color(0x33FFFFFF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("scanner_save_btn")
                        ) {
                            Icon(
                                imageVector = if (scanMode == ScanMode.BATCH) Icons.Default.PictureAsPdf else Icons.Default.Check,
                                contentDescription = "Finish & Compile",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (scanMode == ScanMode.BATCH) "Compile" else "Save",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (scanMode == ScanMode.BATCH && scannedPages.isNotEmpty()) {
                            Text(
                                text = "${scannedPages.size} to PDF",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = if (scanMode == ScanMode.BATCH) "Compile Batch to PDF (${scannedPages.size} Pages)" else "Save Document Scan",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (scanMode == ScanMode.BATCH) {
                        Surface(
                            color = PaperLightRed,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PaperRed, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "All ${scannedPages.size} sequentially captured pages will be compiled into one clean, high-resolution PDF document.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = saveDocName,
                        onValueChange = { saveDocName = it },
                        label = { Text("Document Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (scanMode != ScanMode.ID_CARD) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !saveAsJpg, onClick = { saveAsJpg = false })
                            Text("Compile as Single PDF Document", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = saveAsJpg, onClick = { saveAsJpg = true })
                            Text("Save as individual JPG images", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        if (scanMode == ScanMode.ID_CARD) {
                            viewModel.exportIdCard(saveDocName)
                        } else {
                            viewModel.exportScannedDocument(saveDocName, asJpg = saveAsJpg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                ) {
                    Text(if (scanMode == ScanMode.BATCH) "Compile & Open PDF" else "Save Document")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
