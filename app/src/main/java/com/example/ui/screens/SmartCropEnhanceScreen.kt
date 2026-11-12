package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentFilter
import com.example.engine.DocumentCorners
import com.example.engine.ImageProcessor
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCropEnhanceScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val rawBitmap by viewModel.rawCropBitmap.collectAsState()

    if (rawBitmap == null) {
        LaunchedEffect(Unit) {
            viewModel.navigateBack()
        }
        return
    }

    var currentStep by remember { mutableStateOf(1) } // 1: Crop & Corner Adjustment, 2: Enhance & Filter

    // Working bitmap that can be rotated
    var workingBitmap by remember { mutableStateOf(rawBitmap!!) }
    var detectedCorners by remember { mutableStateOf(ImageProcessor.detectDocumentCorners(workingBitmap)) }

    // Rectified cropped bitmap for step 2
    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(DocumentFilter.MAGIC_COLOR) }
    var brightnessOffset by remember { mutableStateOf(0f) }
    var contrastScale by remember { mutableStateOf(1.15f) }

    // UI layout measurement for corner mapping
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Helper to reset detection
    fun autoDetect() {
        detectedCorners = ImageProcessor.detectDocumentCorners(workingBitmap)
        viewModel.showMessage("Document edges auto-detected!")
    }

    fun selectFullImage() {
        val w = workingBitmap.width.toFloat()
        val h = workingBitmap.height.toFloat()
        detectedCorners = DocumentCorners(
            topLeft = PointF(0f, 0f),
            topRight = PointF(w, 0f),
            bottomRight = PointF(w, h),
            bottomLeft = PointF(0f, h)
        )
    }

    fun rotate90() {
        val rotated = ImageProcessor.rotateBitmap(workingBitmap, 90f)
        workingBitmap = rotated
        autoDetect()
    }

    fun processCropAndProceed() {
        val rectified = ImageProcessor.cropAndRectifyDocument(workingBitmap, detectedCorners)
        croppedBitmap = rectified
        currentStep = 2
        viewModel.showMessage("Document straightened & background removed!")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (currentStep == 1) "Smart Document Crop" else "Document Enhancement",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (currentStep == 1) "CamScanner Edge Detection & Perspective" else "Magic Color & Clarity Tuning",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep == 2) {
                                currentStep = 1
                            } else {
                                viewModel.navigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentStep == 1) {
                        IconButton(onClick = { rotate90() }) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                        }
                        IconButton(onClick = { autoDetect() }) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Detect", tint = PaperRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        if (currentStep == 1) {
            // STEP 1: CAMSCANNER 4-CORNER SMART CROP
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Top control chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Drag the 4 corners to fit your document:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { autoDetect() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = PaperRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Detect", color = PaperRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { selectFullImage() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Full Image", fontSize = 11.sp)
                        }
                    }
                }

                // Interactive Document Canvas with Draggable Handles
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E22))
                        .onGloballyPositioned { coordinates ->
                            canvasSize = coordinates.size
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmpW = workingBitmap.width.toFloat()
                    val bmpH = workingBitmap.height.toFloat()

                    // Compute display scale & offsets to center image in Box
                    val containerW = canvasSize.width.toFloat().coerceAtLeast(1f)
                    val containerH = canvasSize.height.toFloat().coerceAtLeast(1f)

                    val scale = minOf(containerW / bmpW, containerH / bmpH)
                    val dispW = bmpW * scale
                    val dispH = bmpH * scale
                    val offsetX = (containerW - dispW) / 2f
                    val offsetY = (containerH - dispH) / 2f

                    // Display base image
                    Image(
                        bitmap = workingBitmap.asImageBitmap(),
                        contentDescription = "Document capture",
                        modifier = Modifier
                            .size((dispW / 2.7f).dp, (dispH / 2.7f).dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )

                    // Helper to convert bitmap coords <-> screen coords
                    fun toScreen(pt: PointF): Offset = Offset(offsetX + pt.x * scale, offsetY + pt.y * scale)
                    fun toBitmap(screenOffset: Offset): PointF {
                        val bx = ((screenOffset.x - offsetX) / scale).coerceIn(0f, bmpW)
                        val by = ((screenOffset.y - offsetY) / scale).coerceIn(0f, bmpH)
                        return PointF(bx, by)
                    }

                    val tlScreen = toScreen(detectedCorners.topLeft)
                    val trScreen = toScreen(detectedCorners.topRight)
                    val brScreen = toScreen(detectedCorners.bottomRight)
                    val blScreen = toScreen(detectedCorners.bottomLeft)

                    // Draw Polygon boundary & Semi-transparent mask over excluded desk
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        // Document boundary path
                        val docPath = Path().apply {
                            moveTo(tlScreen.x, tlScreen.y)
                            lineTo(trScreen.x, trScreen.y)
                            lineTo(brScreen.x, brScreen.y)
                            lineTo(blScreen.x, blScreen.y)
                            close()
                        }

                        // Boundary outline (Bright glowing green / PaperRed border like CamScanner)
                        drawPath(
                            path = docPath,
                            color = PaperRed,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Draw Grid lines inside polygon for alignment
                        val midTop = Offset((tlScreen.x + trScreen.x) / 2f, (tlScreen.y + trScreen.y) / 2f)
                        val midBottom = Offset((blScreen.x + brScreen.x) / 2f, (blScreen.y + brScreen.y) / 2f)
                        val midLeft = Offset((tlScreen.x + blScreen.x) / 2f, (tlScreen.y + blScreen.y) / 2f)
                        val midRight = Offset((trScreen.x + brScreen.x) / 2f, (trScreen.y + brScreen.y) / 2f)

                        drawLine(Color(0x55FFFFFF), midTop, midBottom, strokeWidth = 1.2f)
                        drawLine(Color(0x55FFFFFF), midLeft, midRight, strokeWidth = 1.2f)
                    }

                    // Touch Drag Area for 4 Corners
                    var activeCornerIndex by remember { mutableStateOf(-1) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(detectedCorners) {
                                detectDragGestures(
                                    onDragStart = { startPos ->
                                        // Find closest corner handle
                                        val distTL = hypot(startPos.x - tlScreen.x, startPos.y - tlScreen.y)
                                        val distTR = hypot(startPos.x - trScreen.x, startPos.y - trScreen.y)
                                        val distBR = hypot(startPos.x - brScreen.x, startPos.y - brScreen.y)
                                        val distBL = hypot(startPos.x - blScreen.x, startPos.y - blScreen.y)

                                        val minDist = minOf(distTL, distTR, distBR, distBL)
                                        activeCornerIndex = when (minDist) {
                                            distTL -> 0
                                            distTR -> 1
                                            distBR -> 2
                                            else -> 3
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val newPt = toBitmap(change.position)
                                        val updated = detectedCorners
                                        when (activeCornerIndex) {
                                            0 -> updated.topLeft = newPt
                                            1 -> updated.topRight = newPt
                                            2 -> updated.bottomRight = newPt
                                            3 -> updated.bottomLeft = newPt
                                        }
                                        detectedCorners = DocumentCorners(updated.topLeft, updated.topRight, updated.bottomRight, updated.bottomLeft)
                                    },
                                    onDragEnd = {
                                        activeCornerIndex = -1
                                    }
                                )
                            }
                    ) {
                        // Render 4 Draggable Pin Handles on screen
                        listOf(
                            tlScreen to "TL",
                            trScreen to "TR",
                            brScreen to "BR",
                            blScreen to "BL"
                        ).forEachIndexed { idx, (pos, label) ->
                            val isActive = (activeCornerIndex == idx)
                            Box(
                                modifier = Modifier
                                    .offset(x = (pos.x / 2.7f - 14).dp, y = (pos.y / 2.7f - 14).dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) Color.Yellow else Color.White)
                                    .border(3.dp, PaperRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PaperRed)
                                )
                            }
                        }
                    }
                }

                // Bottom Action Bar: Proceed to Enhancement
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
                            onClick = { autoDetect() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp), tint = PaperRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Detect")
                        }

                        Button(
                            onClick = { processCropAndProceed() },
                            modifier = Modifier.weight(1.8f).testTag("confirm_crop_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Crop & Enhance ➔", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // STEP 2: CAMSCANNER MAGIC ENHANCEMENT & TUNING
            val baseCropped = croppedBitmap ?: workingBitmap

            // Apply active filter and brightness/contrast
            val enhancedBitmap = remember(baseCropped, selectedFilter, brightnessOffset, contrastScale) {
                var bmp = baseCropped
                if (selectedFilter != DocumentFilter.ORIGINAL) {
                    bmp = ImageProcessor.applyFilter(bmp, selectedFilter)
                }
                if (brightnessOffset != 0f || contrastScale != 1.0f) {
                    bmp = ImageProcessor.adjustBrightnessContrast(bmp, brightnessOffset, contrastScale)
                }
                bmp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Enhanced Document Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFEFEFF2)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = enhancedBitmap.asImageBitmap(),
                        contentDescription = "Enhanced Document",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFD0D0D5), RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )

                    // Active Preset Badge
                    Surface(
                        color = Color(0xCC000000),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "✨ ${selectedFilter.displayName}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Enhancement Presets & Sliders Controller
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Select Document Enhancement Preset:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        // Horizontal Filter Selector
                        val filters = listOf(
                            DocumentFilter.MAGIC_COLOR to "Magic Color",
                            DocumentFilter.AUTO_ENHANCE to "Auto Enhance",
                            DocumentFilter.DOCUMENT to "Document",
                            DocumentFilter.BLACK_WHITE to "Deep B&W",
                            DocumentFilter.LIGHT to "Remove Shadows",
                            DocumentFilter.SHARPEN to "Super Sharp",
                            DocumentFilter.ORIGINAL to "Original"
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filters.size) { idx ->
                                val (f, name) = filters[idx]
                                val isSelected = selectedFilter == f
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedFilter = f },
                                    label = { Text(name, fontSize = 11.sp) },
                                    leadingIcon = if (f == DocumentFilter.MAGIC_COLOR) {
                                        { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = PaperRed) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PaperLightRed,
                                        selectedLabelColor = PaperRed
                                    )
                                )
                            }
                        }

                        // Fine Tune Sliders
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Clarity / Contrast", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.1f".format(contrastScale)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = contrastScale,
                            onValueChange = { contrastScale = it },
                            valueRange = 0.8f..1.8f
                        )

                        // Save & Continue Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { currentStep = 1 },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-Crop", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.finishCropAndEnhance(enhancedBitmap)
                                },
                                modifier = Modifier.weight(1.8f).testTag("save_enhanced_doc_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Done & Add Page", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
