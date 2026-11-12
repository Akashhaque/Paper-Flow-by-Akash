package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.SourceType
import com.example.engine.PdfAdvancedToolsEngine
import com.example.engine.PdfRendererEngine
import com.example.ui.MainViewModel
import com.example.ui.theme.PaperLightRed
import com.example.ui.theme.PaperRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AnnotationTool {
    NONE,
    PEN,
    HIGHLIGHTER,
    TEXT,
    STICKY_NOTE,
    RECTANGLE,
    CIRCLE,
    ARROW,
    ERASER,
    SIGNATURE
}

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false
)

data class TextAnnotation(
    val text: String,
    val position: Offset,
    val color: Color,
    val fontSize: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderAndEditorScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeFile by viewModel.activePdfFile.collectAsState()

    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    var pageBitmaps by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var isLoadingPage by remember { mutableStateOf(true) }

    // Editor state
    var activeTool by remember { mutableStateOf(AnnotationTool.NONE) }
    var strokeColor by remember { mutableStateOf(PaperRed) }
    var strokeWidth by remember { mutableStateOf(5f) }

    // Per-page annotations: page index -> paths
    val pageDrawings = remember { mutableStateMapOf<Int, MutableList<DrawPath>>() }
    val pageTexts = remember { mutableStateMapOf<Int, MutableList<TextAnnotation>>() }

    // Signature dialog
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var inputAnnotationText by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    val file = activeFile

    LaunchedEffect(file) {
        if (file != null) {
            withContext(Dispatchers.IO) {
                val count = PdfRendererEngine.getPageCount(file.filePath).coerceAtLeast(1)
                totalPages = count
                val firstBmp = PdfRendererEngine.renderPageToBitmap(file.filePath, 0, targetWidth = 1400)
                if (firstBmp != null) {
                    pageBitmaps = mapOf(0 to firstBmp)
                }
                isLoadingPage = false
            }
        }
    }

    fun loadPage(index: Int) {
        if (file == null || index !in 0 until totalPages) return
        currentPageIndex = index
        if (!pageBitmaps.containsKey(index)) {
            isLoadingPage = true
            coroutineScope.launch(Dispatchers.IO) {
                val bmp = PdfRendererEngine.renderPageToBitmap(file.filePath, index, targetWidth = 1400)
                if (bmp != null) {
                    pageBitmaps = pageBitmaps + (index to bmp)
                }
                isLoadingPage = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = file?.fileName ?: "PDF Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPages",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            file?.let { PdfAdvancedToolsEngine.sharePdf(context, File(it.filePath)) }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(
                        onClick = {
                            file?.let { PdfAdvancedToolsEngine.printPdf(context, File(it.filePath)) }
                        }
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print")
                    }
                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp).testTag("editor_save_btn")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp)
            ) {
                // Annotation Tools Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        ToolIconButton(icon = Icons.Default.PanTool, label = "View", isSelected = activeTool == AnnotationTool.NONE) {
                            activeTool = AnnotationTool.NONE
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.Edit, label = "Pen", isSelected = activeTool == AnnotationTool.PEN) {
                            activeTool = AnnotationTool.PEN
                            strokeWidth = 6f
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.BorderColor, label = "Highlight", isSelected = activeTool == AnnotationTool.HIGHLIGHTER) {
                            activeTool = AnnotationTool.HIGHLIGHTER
                            strokeColor = Color(0x66FFEB3B)
                            strokeWidth = 24f
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.TextFields, label = "Text", isSelected = activeTool == AnnotationTool.TEXT) {
                            activeTool = AnnotationTool.TEXT
                            showTextDialog = true
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.Draw, label = "Signature", isSelected = activeTool == AnnotationTool.SIGNATURE) {
                            activeTool = AnnotationTool.SIGNATURE
                            showSignatureDialog = true
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.CropSquare, label = "Box", isSelected = activeTool == AnnotationTool.RECTANGLE) {
                            activeTool = AnnotationTool.RECTANGLE
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.Circle, label = "Circle", isSelected = activeTool == AnnotationTool.CIRCLE) {
                            activeTool = AnnotationTool.CIRCLE
                        }
                    }
                    item {
                        ToolIconButton(icon = Icons.Default.Delete, label = "Clear", isSelected = false) {
                            pageDrawings[currentPageIndex]?.clear()
                            pageTexts[currentPageIndex]?.clear()
                            viewModel.showMessage("Cleared annotations on page ${currentPageIndex + 1}")
                        }
                    }
                }

                // Color Palette & Page Switcher Row
                if (activeTool != AnnotationTool.NONE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val colors = listOf(PaperRed, Color.Black, Color(0xFF2563EB), Color(0xFF16A34A), Color(0xFFF59E0B), Color(0xFFFFEB3B))
                            colors.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(if (strokeColor == c) 2.dp else 0.dp, Color.Gray, CircleShape)
                                        .clickable { strokeColor = c }
                                )
                            }
                        }

                        // Undo last stroke
                        TextButton(
                            onClick = {
                                val list = pageDrawings[currentPageIndex]
                                if (!list.isNullOrEmpty()) {
                                    list.removeAt(list.size - 1)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Undo", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Page Navigator Slider & Quick Jump
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { loadPage(currentPageIndex - 1) },
                    enabled = currentPageIndex > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                }

                Slider(
                    value = currentPageIndex.toFloat(),
                    onValueChange = { loadPage(it.toInt()) },
                    valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                    steps = (totalPages - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = { loadPage(currentPageIndex + 1) },
                    enabled = currentPageIndex < totalPages - 1
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                }
            }

            // PDF Page Canvas Viewport with Touch Annotation
            val currentBitmap = pageBitmaps[currentPageIndex]

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFE5E5E7)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPage || currentBitmap == null) {
                    CircularProgressIndicator(color = PaperRed)
                } else {
                    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = currentBitmap.asImageBitmap(),
                            contentDescription = "PDF Page Canvas",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit
                        )

                        // Transparent drawing canvas overlay
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeTool) {
                                    if (activeTool != AnnotationTool.NONE) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPathPoints = listOf(offset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentPathPoints = currentPathPoints + change.position
                                            },
                                            onDragEnd = {
                                                if (currentPathPoints.isNotEmpty()) {
                                                    val isHigh = (activeTool == AnnotationTool.HIGHLIGHTER)
                                                    val draw = DrawPath(
                                                        points = currentPathPoints,
                                                        color = if (isHigh) strokeColor.copy(alpha = 0.35f) else strokeColor,
                                                        strokeWidth = strokeWidth,
                                                        isHighlighter = isHigh
                                                    )
                                                    val list = pageDrawings.getOrPut(currentPageIndex) { mutableListOf() }
                                                    list.add(draw)
                                                    currentPathPoints = emptyList()
                                                }
                                            }
                                        )
                                    }
                                }
                        ) {
                            // Render saved annotations for current page
                            pageDrawings[currentPageIndex]?.forEach { stroke ->
                                if (stroke.points.size > 1) {
                                    val path = Path().apply {
                                        moveTo(stroke.points.first().x, stroke.points.first().y)
                                        for (i in 1 until stroke.points.size) {
                                            lineTo(stroke.points[i].x, stroke.points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = stroke.color,
                                        style = Stroke(
                                            width = stroke.strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Render currently active drag stroke
                            if (currentPathPoints.size > 1) {
                                val path = Path().apply {
                                    moveTo(currentPathPoints.first().x, currentPathPoints.first().y)
                                    for (i in 1 until currentPathPoints.size) {
                                        lineTo(currentPathPoints[i].x, currentPathPoints[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = if (activeTool == AnnotationTool.HIGHLIGHTER) strokeColor.copy(alpha = 0.35f) else strokeColor,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }

                        // Render text annotations on top
                        pageTexts[currentPageIndex]?.forEach { txt ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (txt.position.x / 3).dp, y = (txt.position.y / 3).dp)
                                    .background(Color(0xFFFFFBCC), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFE0C000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(txt.text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Signature Drawer Dialog
    if (showSignatureDialog) {
        Dialog(onDismissRequest = { showSignatureDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                var sigPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Draw Your Signature", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFBFBFC))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { sigPoints = sigPoints + it },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        sigPoints = sigPoints + change.position
                                    }
                                )
                            }
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            if (sigPoints.size > 1) {
                                val p = Path().apply {
                                    moveTo(sigPoints.first().x, sigPoints.first().y)
                                    for (i in 1 until sigPoints.size) lineTo(sigPoints[i].x, sigPoints[i].y)
                                }
                                drawPath(p, color = Color.Black, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                        Text("Sign here with your finger", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { sigPoints = emptyList() }) {
                            Text("Clear")
                        }
                        Button(
                            onClick = {
                                if (sigPoints.isNotEmpty()) {
                                    val list = pageDrawings.getOrPut(currentPageIndex) { mutableListOf() }
                                    list.add(DrawPath(sigPoints, Color.Black, 5f))
                                    viewModel.showMessage("Signature placed on page ${currentPageIndex + 1}")
                                }
                                showSignatureDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                        ) {
                            Text("Insert Signature")
                        }
                    }
                }
            }
        }
    }

    // Add Text Dialog
    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text Annotation") },
            text = {
                OutlinedTextField(
                    value = inputAnnotationText,
                    onValueChange = { inputAnnotationText = it },
                    label = { Text("Annotation Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputAnnotationText.isNotBlank()) {
                            val list = pageTexts.getOrPut(currentPageIndex) { mutableListOf() }
                            list.add(TextAnnotation(inputAnnotationText, Offset(100f, 200f), strokeColor, 14f))
                            inputAnnotationText = ""
                        }
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Save Edited Document Dialog
    if (showSaveDialog && file != null) {
        var saveAsNewCopy by remember { mutableStateOf(true) }
        var editedFileName by remember { mutableStateOf("${file.fileName.removeSuffix(".pdf")}_edited.pdf") }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Document Edits", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = saveAsNewCopy, onClick = { saveAsNewCopy = true })
                        Text("Save as new document copy", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !saveAsNewCopy, onClick = { saveAsNewCopy = false })
                        Text("Overwrite original document", fontSize = 13.sp)
                    }

                    if (saveAsNewCopy) {
                        OutlinedTextField(
                            value = editedFileName,
                            onValueChange = { editedFileName = it },
                            label = { Text("New File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val targetFile = if (saveAsNewCopy) {
                                    val clean = if (editedFileName.endsWith(".pdf")) editedFileName else "$editedFileName.pdf"
                                    File(viewModel.repository.getAppStorageDir(), clean)
                                } else {
                                    File(file.filePath)
                                }

                                val outputPdf = PdfDocument()
                                for (i in 0 until totalPages) {
                                    val baseBmp = PdfRendererEngine.renderPageToBitmap(file.filePath, i, targetWidth = 1400) ?: continue
                                    val pageInfo = PdfDocument.PageInfo.Builder(baseBmp.width, baseBmp.height, i + 1).create()
                                    val page = outputPdf.startPage(pageInfo)
                                    val canvas = page.canvas

                                    canvas.drawColor(android.graphics.Color.WHITE)
                                    canvas.drawBitmap(baseBmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

                                    // Draw strokes
                                    pageDrawings[i]?.forEach { stroke ->
                                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = stroke.color.hashCode()
                                            strokeWidth = stroke.strokeWidth * 1.5f
                                            style = Paint.Style.STROKE
                                            strokeCap = Paint.Cap.ROUND
                                            strokeJoin = Paint.Join.ROUND
                                        }
                                        val path = android.graphics.Path()
                                        if (stroke.points.isNotEmpty()) {
                                            path.moveTo(stroke.points.first().x, stroke.points.first().y)
                                            for (pt in stroke.points) path.lineTo(pt.x, pt.y)
                                            canvas.drawPath(path, p)
                                        }
                                    }

                                    // Draw text annotations
                                    pageTexts[i]?.forEach { t ->
                                        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.BLACK
                                            textSize = 18f
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                        canvas.drawText(t.text, t.position.x, t.position.y, tp)
                                    }

                                    outputPdf.finishPage(page)
                                    baseBmp.recycle()
                                }

                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos -> outputPdf.writeTo(fos) }
                                outputPdf.close()

                                val saved = viewModel.repository.registerNewFile(targetFile, SourceType.EDITED)
                                viewModel.openPdf(saved)
                                viewModel.showMessage("Document saved successfully!")
                            } catch (e: Exception) {
                                viewModel.showMessage("Save error: ${e.localizedMessage}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaperRed)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToolIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) PaperLightRed else Color(0xFFF3F3F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) PaperRed else Color.DarkGray,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PaperRed else Color.DarkGray
        )
    }
}
