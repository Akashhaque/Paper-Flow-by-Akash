package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.DocumentRepository
import com.example.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AppScreen {
    HOME,
    FILES,
    CREATE_MENU,
    TOOLS,
    SETTINGS,
    SCANNER,
    SMART_CROP_ENHANCE,
    IMAGE_TO_PDF,
    PDF_READER_EDITOR,
    DOCUMENT_CONVERTER,
    PDF_ORGANIZER,
    OCR,
    WATERMARK_TOOLS
}

enum class FileSortOrder {
    DATE_MODIFIED,
    NAME,
    SIZE,
    PAGE_COUNT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = DocumentRepository(application, database)

    // Navigation State
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<AppScreen>()

    // Notification / Feedback message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // File Manager State
    val allFiles = repository.allFiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentFiles = repository.recentFiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteFiles = repository.favoriteFiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allFolders = repository.allFolders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val conversionHistory = repository.allHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val sortOrder = MutableStateFlow(FileSortOrder.DATE_MODIFIED)
    val isGridView = MutableStateFlow(false)
    val selectedFolderId = MutableStateFlow<Long?>(null)

    // Active file in Reader/Editor
    private val _activePdfFile = MutableStateFlow<PDFFile?>(null)
    val activePdfFile: StateFlow<PDFFile?> = _activePdfFile.asStateFlow()

    // Image to PDF State
    val imagePages = MutableStateFlow<List<ImagePageItem>>(emptyList())
    val selectedPageSize = MutableStateFlow(PageSizeOption.A4)
    val selectedOrientation = MutableStateFlow(PageOrientation.PORTRAIT)
    val selectedMargin = MutableStateFlow(MarginOption.NONE)
    val selectedImageFit = MutableStateFlow(ImageFitOption.FIT_PAGE)
    val selectedImageQuality = MutableStateFlow(ImageQualityOption.HIGH)

    // Scanner State
    val scannedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val scanMode = MutableStateFlow(ScanMode.BATCH)
    val idCardFront = MutableStateFlow<Bitmap?>(null)
    val idCardBack = MutableStateFlow<Bitmap?>(null)
    val rawCropBitmap = MutableStateFlow<Bitmap?>(null)
    val isBatchAutoEnhance = MutableStateFlow(true)
    val isBatchProcessingPage = MutableStateFlow(false)

    // OCR State
    val ocrResult = MutableStateFlow<OcrResult?>(null)
    val isOcrProcessing = MutableStateFlow(false)

    // Converter / Processing Progress State
    val isProcessing = MutableStateFlow(false)
    val processingProgress = MutableStateFlow(0f)
    val processingTitle = MutableStateFlow("Processing...")

    // Organizer Tool Type
    val activeOrganizerTool = MutableStateFlow("MERGE") // MERGE, SPLIT, ROTATE, DELETE, COMPRESS

    // App Preferences
    val appLanguage = MutableStateFlow("en") // "en" or "bn"
    val isDarkMode = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.ensureWelcomeDocument()
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenBackStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        return if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
            true
        } else if (_currentScreen.value != AppScreen.HOME) {
            _currentScreen.value = AppScreen.HOME
            true
        } else {
            false
        }
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun openPdf(file: PDFFile) {
        _activePdfFile.value = file
        viewModelScope.launch {
            repository.recordRecentOpen(file.id)
        }
        navigateTo(AppScreen.PDF_READER_EDITOR)
    }

    fun toggleFavorite(file: PDFFile) {
        viewModelScope.launch {
            repository.toggleFavorite(file)
        }
    }

    fun renameFile(file: PDFFile, newName: String) {
        viewModelScope.launch {
            val updated = repository.renameFile(file, newName)
            if (_activePdfFile.value?.id == file.id) {
                _activePdfFile.value = updated
            }
            showMessage("File renamed to ${updated.fileName}")
        }
    }

    fun duplicateFile(file: PDFFile) {
        viewModelScope.launch {
            val copy = repository.duplicateFile(file)
            showMessage("Created copy: ${copy.fileName}")
        }
    }

    fun deleteFile(file: PDFFile) {
        viewModelScope.launch {
            repository.deleteFile(file)
            if (_activePdfFile.value?.id == file.id) {
                _activePdfFile.value = null
                navigateBack()
            }
            showMessage("Deleted ${file.fileName}")
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(name)
            showMessage("Folder '$name' created")
        }
    }

    // ------------------------------------------------------------------------
    // Image to PDF Actions
    // ------------------------------------------------------------------------
    fun addImagesFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItems = mutableListOf<ImagePageItem>()
            val cr = getApplication<Application>().contentResolver
            for (uri in uris) {
                try {
                    cr.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            newItems.add(ImagePageItem(bitmap))
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            imagePages.value = imagePages.value + newItems
            showMessage("Added ${newItems.size} image(s)")
        }
    }

    fun removeImage(index: Int) {
        val list = imagePages.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            imagePages.value = list
        }
    }

    fun rotateImagePage(index: Int) {
        val list = imagePages.value.toMutableList()
        if (index in list.indices) {
            val current = list[index]
            val newRotation = (current.rotationDegrees + 90f) % 360f
            list[index] = current.copy(rotationDegrees = newRotation)
            imagePages.value = list
        }
    }

    fun applyFilterToImage(index: Int, filter: DocumentFilter) {
        val list = imagePages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(filter = filter)
            imagePages.value = list
        }
    }

    fun exportImageToPdf(pdfName: String) {
        if (imagePages.value.isEmpty()) {
            showMessage("Please select at least one image")
            return
        }

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Creating PDF..."
            try {
                val cleanName = if (pdfName.endsWith(".pdf", ignoreCase = true)) pdfName else "$pdfName.pdf"
                val outFile = File(repository.getAppStorageDir(), cleanName)

                PdfCreatorEngine.createPdfFromImages(
                    context = getApplication(),
                    pages = imagePages.value,
                    outputFile = outFile,
                    pageSizeOption = selectedPageSize.value,
                    orientation = selectedOrientation.value,
                    marginOption = selectedMargin.value,
                    imageFitOption = selectedImageFit.value,
                    imageQualityOption = selectedImageQuality.value,
                    onProgress = { cur, total ->
                        processingProgress.value = cur.toFloat() / total.toFloat()
                    }
                )

                val savedFile = repository.registerNewFile(outFile, SourceType.GALLERY)
                imagePages.value = emptyList()
                openPdf(savedFile)
                showMessage("PDF successfully generated!")
            } catch (e: Exception) {
                showMessage("Failed to create PDF: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // Document Scanner Actions
    // ------------------------------------------------------------------------
    fun startCropAndEnhance(bitmap: Bitmap) {
        rawCropBitmap.value = bitmap
        navigateTo(AppScreen.SMART_CROP_ENHANCE)
    }

    fun finishCropAndEnhance(finalBitmap: Bitmap) {
        if (scanMode.value == ScanMode.ID_CARD) {
            if (idCardFront.value == null) {
                idCardFront.value = finalBitmap
                showMessage("ID Card Front enhanced! Now scan back.")
            } else {
                idCardBack.value = finalBitmap
                showMessage("ID Card Back enhanced! Ready to create PDF.")
            }
        } else {
            scannedPages.value = scannedPages.value + finalBitmap
            showMessage("Page ${scannedPages.value.size} enhanced & added!")
        }
        rawCropBitmap.value = null
        navigateBack()
    }

    fun addScannedPage(bitmap: Bitmap) {
        scannedPages.value = scannedPages.value + bitmap
        showMessage("Page ${scannedPages.value.size} captured")
    }

    fun addBatchScannedPage(bitmap: Bitmap, autoEnhance: Boolean = true) {
        viewModelScope.launch {
            isBatchProcessingPage.value = true
            val finalBitmap = withContext(Dispatchers.Default) {
                if (autoEnhance) {
                    try {
                        val corners = ImageProcessor.detectDocumentCorners(bitmap)
                        val cropped = ImageProcessor.cropAndRectifyDocument(bitmap, corners)
                        ImageProcessor.applyFilter(cropped, DocumentFilter.MAGIC_COLOR)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        bitmap
                    }
                } else {
                    bitmap
                }
            }
            scannedPages.value = scannedPages.value + finalBitmap
            isBatchProcessingPage.value = false
            showMessage("Page ${scannedPages.value.size} captured & added to batch!")
        }
    }

    fun removeScannedPage(index: Int) {
        val list = scannedPages.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            scannedPages.value = list
        }
    }

    fun clearScannedPages() {
        scannedPages.value = emptyList()
        idCardFront.value = null
        idCardBack.value = null
    }

    fun autoCompileBatchPdf(customName: String? = null) {
        if (scannedPages.value.isEmpty()) {
            showMessage("Capture at least 1 page before compiling")
            return
        }
        val defaultName = customName?.takeIf { it.isNotBlank() }
            ?: "Batch_Scan_${System.currentTimeMillis() % 100000}"
        exportScannedDocument(defaultName, asJpg = false)
    }

    fun exportScannedDocument(fileName: String, asJpg: Boolean = false) {
        if (scannedPages.value.isEmpty()) {
            showMessage("No pages scanned")
            return
        }

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Compiling PDF..."
            try {
                if (asJpg) {
                    val exportDir = File(repository.getAppStorageDir(), "Scans_${System.currentTimeMillis()}")
                    exportDir.mkdirs()
                    scannedPages.value.forEachIndexed { i, bmp ->
                        val file = File(exportDir, "${fileName}_page_${i + 1}.jpg")
                        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                    }
                    showMessage("Saved ${scannedPages.value.size} JPG images")
                } else {
                    val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
                    val outFile = File(repository.getAppStorageDir(), cleanName)
                    val pages = scannedPages.value.map { ImagePageItem(it, filter = DocumentFilter.ORIGINAL) }

                    PdfCreatorEngine.createPdfFromImages(
                        context = getApplication(),
                        pages = pages,
                        outputFile = outFile,
                        pageSizeOption = PageSizeOption.A4,
                        orientation = PageOrientation.PORTRAIT,
                        marginOption = MarginOption.NONE,
                        imageFitOption = ImageFitOption.FIT_PAGE
                    )
                    val saved = repository.registerNewFile(outFile, SourceType.CAMERA)
                    scannedPages.value = emptyList()
                    openPdf(saved)
                    showMessage("Successfully compiled ${pages.size} pages into $cleanName!")
                }
            } catch (e: Exception) {
                showMessage("Error saving scan: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun exportIdCard(fileName: String) {
        val front = idCardFront.value
        if (front == null) {
            showMessage("Please capture the front of the ID card")
            return
        }

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Creating ID Card PDF..."
            try {
                val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
                val outFile = File(repository.getAppStorageDir(), cleanName)

                PdfCreatorEngine.createIdCardPdf(front, idCardBack.value, outFile)
                val saved = repository.registerNewFile(outFile, SourceType.CAMERA)
                idCardFront.value = null
                idCardBack.value = null
                openPdf(saved)
                showMessage("ID Card PDF saved successfully!")
            } catch (e: Exception) {
                showMessage("Failed to create ID Card PDF: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // Document Converter Actions
    // ------------------------------------------------------------------------
    fun convertExternalDocument(uri: Uri, fileName: String) {
        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Converting $fileName..."
            processingProgress.value = 0.1f
            try {
                val outBaseName = fileName.substringBeforeLast('.')
                val outFile = File(repository.getAppStorageDir(), "${outBaseName}_converted.pdf")

                DocumentConverterEngine.convertDocumentToPdf(
                    context = getApplication(),
                    sourceUri = uri,
                    sourceFileName = fileName,
                    outputPdfFile = outFile,
                    onProgress = { p -> processingProgress.value = p }
                )

                val saved = repository.registerNewFile(outFile, SourceType.CONVERTED)
                repository.logConversion(fileName, fileName.substringAfterLast('.'), saved.fileName, saved.filePath, true)
                openPdf(saved)
                showMessage("Document converted successfully!")
            } catch (e: Exception) {
                e.printStackTrace()
                repository.logConversion(fileName, fileName.substringAfterLast('.'), "", null, false, e.localizedMessage)
                showMessage("Conversion failed: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // OCR Actions
    // ------------------------------------------------------------------------
    fun runOcrOnBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            isOcrProcessing.value = true
            try {
                val res = OcrEngine.recognizeText(bitmap)
                ocrResult.value = res
                showMessage("Extracted ${res.lineCount} lines of text!")
            } catch (e: Exception) {
                showMessage("OCR recognition error: ${e.localizedMessage}")
            } finally {
                isOcrProcessing.value = false
            }
        }
    }

    fun runOcrOnPdf(file: PDFFile) {
        viewModelScope.launch {
            isOcrProcessing.value = true
            try {
                val res = OcrEngine.recognizeTextFromPdf(File(file.filePath))
                ocrResult.value = res
                showMessage("Extracted ${res.lineCount} lines across ${file.pageCount} page(s)!")
            } catch (e: Exception) {
                showMessage("OCR recognition error: ${e.localizedMessage}")
            } finally {
                isOcrProcessing.value = false
            }
        }
    }

    // ------------------------------------------------------------------------
    // PDF Organizer Actions
    // ------------------------------------------------------------------------
    fun mergePdfs(files: List<PDFFile>, outputName: String) {
        if (files.size < 2) {
            showMessage("Please select at least 2 PDF files to merge")
            return
        }

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Merging ${files.size} PDFs..."
            try {
                val cleanName = if (outputName.endsWith(".pdf", ignoreCase = true)) outputName else "$outputName.pdf"
                val outFile = File(repository.getAppStorageDir(), cleanName)
                val diskFiles = files.map { File(it.filePath) }

                PdfOrganizerEngine.mergePdfs(diskFiles, outFile) { cur, total ->
                    processingProgress.value = cur.toFloat() / total.toFloat()
                }

                val saved = repository.registerNewFile(outFile, SourceType.MERGED)
                openPdf(saved)
                showMessage("Successfully merged into ${saved.fileName}!")
            } catch (e: Exception) {
                showMessage("Merge error: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun compressActivePdf(level: CompressionLevel) {
        val file = _activePdfFile.value ?: return

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Compressing PDF..."
            try {
                val sourceDiskFile = File(file.filePath)
                val outName = "${file.fileName.removeSuffix(".pdf")}_compressed.pdf"
                val outFile = File(repository.getAppStorageDir(), outName)

                val (resFile, savedBytes) = PdfOrganizerEngine.compressPdf(sourceDiskFile, level, outFile)
                val savedMb = savedBytes / (1024f * 1024f)
                val percentSaved = ((savedBytes.toFloat() / sourceDiskFile.length().toFloat()) * 100).toInt()

                val savedPdf = repository.registerNewFile(resFile, SourceType.ORGANIZED)
                openPdf(savedPdf)
                showMessage("Compressed! Saved ${"%.2f".format(savedMb)} MB (${percentSaved}% reduction)")
            } catch (e: Exception) {
                showMessage("Compression error: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun addWatermarkToActivePdf(config: WatermarkConfig) {
        val file = _activePdfFile.value ?: return

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Applying Watermark..."
            try {
                val outName = "${file.fileName.removeSuffix(".pdf")}_watermarked.pdf"
                val outFile = File(repository.getAppStorageDir(), outName)

                PdfAdvancedToolsEngine.addWatermark(File(file.filePath), config, outFile)
                val savedPdf = repository.registerNewFile(outFile, SourceType.EDITED)
                openPdf(savedPdf)
                showMessage("Watermark applied successfully!")
            } catch (e: Exception) {
                showMessage("Watermark error: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun addPageNumbersToActivePdf(config: PageNumberConfig) {
        val file = _activePdfFile.value ?: return

        viewModelScope.launch {
            isProcessing.value = true
            processingTitle.value = "Adding Page Numbers..."
            try {
                val outName = "${file.fileName.removeSuffix(".pdf")}_numbered.pdf"
                val outFile = File(repository.getAppStorageDir(), outName)

                PdfAdvancedToolsEngine.addPageNumbersAndHeader(File(file.filePath), config, null, outFile)
                val savedPdf = repository.registerNewFile(outFile, SourceType.EDITED)
                openPdf(savedPdf)
                showMessage("Page numbers added!")
            } catch (e: Exception) {
                showMessage("Page number error: ${e.localizedMessage}")
            } finally {
                isProcessing.value = false
            }
        }
    }
}
