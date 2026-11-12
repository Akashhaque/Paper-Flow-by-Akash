package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.engine.PdfRendererEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val pdfFileDao = database.pdfFileDao()
    private val folderDao = database.folderDao()
    private val recentFileDao = database.recentFileDao()
    private val conversionHistoryDao = database.conversionHistoryDao()

    val allFiles: Flow<List<PDFFile>> = pdfFileDao.getAllFiles()
    val favoriteFiles: Flow<List<PDFFile>> = pdfFileDao.getFavoriteFiles()
    val recentFiles: Flow<List<PDFFile>> = pdfFileDao.getRecentFiles()
    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()
    val allHistory: Flow<List<ConversionHistory>> = conversionHistoryDao.getAllHistory()

    fun searchFiles(query: String): Flow<List<PDFFile>> = pdfFileDao.searchFiles(query)
    fun getFilesByFolder(folderId: Long): Flow<List<PDFFile>> = pdfFileDao.getFilesByFolder(folderId)

    suspend fun getFileById(id: Long): PDFFile? = pdfFileDao.getFileById(id)

    suspend fun registerNewFile(
        file: File,
        sourceType: SourceType = SourceType.IMPORTED,
        folderId: Long? = null
    ): PDFFile = withContext(Dispatchers.IO) {
        val pageCount = PdfRendererEngine.getPageCount(file.absolutePath).coerceAtLeast(1)
        val thumbPath = PdfRendererEngine.generateThumbnail(context, file)

        val existing = pdfFileDao.getFileByPath(file.absolutePath)
        val pdfFile = existing?.copy(
            fileSize = file.length(),
            pageCount = pageCount,
            modifiedAt = System.currentTimeMillis(),
            thumbnailPath = thumbPath ?: existing.thumbnailPath
        ) ?: PDFFile(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            pageCount = pageCount,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            sourceType = sourceType.name,
            folderId = folderId,
            thumbnailPath = thumbPath
        )

        val id = pdfFileDao.insertFile(pdfFile)
        val saved = pdfFile.copy(id = if (existing != null) existing.id else id)
        recordRecentOpen(saved.id)
        saved
    }

    suspend fun toggleFavorite(file: PDFFile) = withContext(Dispatchers.IO) {
        pdfFileDao.updateFile(file.copy(isFavorite = !file.isFavorite))
    }

    suspend fun renameFile(file: PDFFile, newNameWithoutExt: String): PDFFile = withContext(Dispatchers.IO) {
        val cleanName = newNameWithoutExt.trim()
        val finalName = if (cleanName.endsWith(".pdf", ignoreCase = true)) cleanName else "$cleanName.pdf"

        val original = File(file.filePath)
        val newTarget = File(original.parentFile, finalName)

        if (original.exists() && original.renameTo(newTarget)) {
            val updated = file.copy(
                fileName = finalName,
                filePath = newTarget.absolutePath,
                modifiedAt = System.currentTimeMillis()
            )
            pdfFileDao.updateFile(updated)
            updated
        } else {
            file
        }
    }

    suspend fun duplicateFile(file: PDFFile): PDFFile = withContext(Dispatchers.IO) {
        val original = File(file.filePath)
        val baseName = file.fileName.removeSuffix(".pdf")
        val copyName = "${baseName}_copy_${System.currentTimeMillis() % 10000}.pdf"
        val copyTarget = File(original.parentFile, copyName)

        original.copyTo(copyTarget, overwrite = true)
        registerNewFile(copyTarget, SourceType.valueOf(file.sourceType), file.folderId)
    }

    suspend fun deleteFile(file: PDFFile) = withContext(Dispatchers.IO) {
        val diskFile = File(file.filePath)
        if (diskFile.exists()) {
            diskFile.delete()
        }
        file.thumbnailPath?.let {
            val thumb = File(it)
            if (thumb.exists()) thumb.delete()
        }
        recentFileDao.removeRecent(file.id)
        pdfFileDao.deleteFile(file)
    }

    suspend fun recordRecentOpen(fileId: Long) = withContext(Dispatchers.IO) {
        recentFileDao.insertRecent(RecentFile(pdfFileId = fileId, lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        folderDao.insertFolder(Folder(folderName = name.trim()))
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(folder)
    }

    suspend fun logConversion(
        sourceFileName: String,
        sourceFormat: String,
        outputFileName: String,
        outputFilePath: String?,
        isSuccess: Boolean,
        error: String? = null
    ) = withContext(Dispatchers.IO) {
        conversionHistoryDao.insertHistory(
            ConversionHistory(
                sourceFileName = sourceFileName,
                sourceFormat = sourceFormat,
                outputFileName = outputFileName,
                outputFilePath = outputFilePath,
                conversionStatus = if (isSuccess) "SUCCESS" else "FAILED",
                errorReason = error
            )
        )
    }

    fun getAppStorageDir(): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "PaperFlowDocs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Creates a high quality initial welcome document so the user has immediate rich data.
     */
    suspend fun ensureWelcomeDocument() = withContext(Dispatchers.IO) {
        val docsDir = getAppStorageDir()
        val welcomeFile = File(docsDir, "PaperFlow_User_Guide.pdf")
        if (!welcomeFile.exists()) {
            val doc = PdfDocument()

            // Page 1: Cover & Features
            val page1Info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val p1 = doc.startPage(page1Info)
            val c1 = p1.canvas

            c1.drawColor(Color.WHITE)

            // Header Banner
            val bannerPaint = Paint().apply { color = Color.parseColor("#F5222D") }
            c1.drawRect(0f, 0f, 595f, 180f, bannerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c1.drawText("PaperFlow – PDF Maker", 40f, 75f, titlePaint)

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFECEE")
                textSize = 14f
            }
            c1.drawText("All-in-One PDF Maker, Document Scanner & Editor", 40f, 105f, subtitlePaint)

            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            c1.drawText("OFFICIAL USER MANUAL • VERSION 1.0", 40f, 145f, badgePaint)

            // Body text
            var y = 220f
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#191919")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#444444")
                textSize = 12f
            }

            c1.drawText("1. Document Scanner & Perspective Warping", 40f, y, sectionPaint)
            y += 24f
            c1.drawText("Scan multi-page documents, ID cards, receipts, and passports with", 40f, y, bodyPaint)
            y += 18f
            c1.drawText("automatic boundary detection and 11 professional document filters.", 40f, y, bodyPaint)

            y += 35f
            c1.drawText("2. Image to PDF with Page Layout Customization", 40f, y, sectionPaint)
            y += 24f
            c1.drawText("Select gallery photos, reorder pages, rotate, crop, and configure", 40f, y, bodyPaint)
            y += 18f
            c1.drawText("page sizes (A4, A3, Letter, Custom), margins, and image fitting.", 40f, y, bodyPaint)

            y += 35f
            c1.drawText("3. Universal Office Document Converter", 40f, y, sectionPaint)
            y += 24f
            c1.drawText("Convert DOC, DOCX, XLS, XLSX, CSV, PPTX, TXT, and HTML files", 40f, y, bodyPaint)
            y += 18f
            c1.drawText("directly on your device with preserved tables, formatting, and layout.", 40f, y, bodyPaint)

            y += 35f
            c1.drawText("4. PDF Editor, Annotation & Signatures", 40f, y, sectionPaint)
            y += 24f
            c1.drawText("Draw with pen, highlight lines, place text boxes, add sticky notes,", 40f, y, bodyPaint)
            y += 18f
            c1.drawText("insert geometric shapes, and place finger-drawn signatures anywhere.", 40f, y, bodyPaint)

            y += 35f
            c1.drawText("5. Security, OCR, and Organization Tools", 40f, y, sectionPaint)
            y += 24f
            c1.drawText("Merge, split, rotate, delete pages, compress size, add watermarks,", 40f, y, bodyPaint)
            y += 18f
            c1.drawText("page numbers, and extract machine-readable text using Google ML Kit OCR.", 40f, y, bodyPaint)

            // Footer
            val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#888888")
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
            c1.drawText("Created with PaperFlow – 100% Offline & Private", 595f / 2f, 800f, footPaint)

            doc.finishPage(p1)

            // Page 2: Quick Reference Table
            val page2Info = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val p2 = doc.startPage(page2Info)
            val c2 = p2.canvas
            c2.drawColor(Color.WHITE)

            c2.drawText("PaperFlow – Feature Reference Matrix", 40f, 60f, sectionPaint)

            val tableHeader = Paint().apply { color = Color.parseColor("#F5222D") }
            val borderPaint = Paint().apply {
                color = Color.parseColor("#D0D0D0")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val textHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#222222")
                textSize = 10f
            }

            var ty = 90f
            c2.drawRect(40f, ty, 555f, ty + 26f, tableHeader)
            c2.drawText("Category", 50f, ty + 17f, textHead)
            c2.drawText("Feature", 180f, ty + 17f, textHead)
            c2.drawText("Output / Capability", 370f, ty + 17f, textHead)
            ty += 26f

            val rows = listOf(
                Triple("Scanner", "Camera / Gallery", "Smart edge detection & 11 filters"),
                Triple("Scanner", "ID Card & Passport", "Double-sided layout on single page"),
                Triple("Converter", "DOCX, XLSX, PPTX", "Native formatting & tables to PDF"),
                Triple("Export", "PDF to Word / Excel", "DOCX and CSV table exports"),
                Triple("Organizer", "Merge & Split", "Batch multi-file combine & page extraction"),
                Triple("Organizer", "Compress PDF", "Up to 60% file size reduction"),
                Triple("Editor", "Annotations", "Pen, Highlighter, Text, Shapes"),
                Triple("Editor", "Signature", "Digital finger signature placement"),
                Triple("OCR", "Text Recognition", "ML Kit Latin & multi-language OCR"),
                Triple("Security", "Watermark & Numbers", "Custom diagonal stamps & page headers")
            )

            rows.forEachIndexed { rIdx, (cat, feat, cap) ->
                val bg = if (rIdx % 2 == 1) Color.parseColor("#F8F9FA") else Color.WHITE
                c2.drawRect(40f, ty, 555f, ty + 24f, Paint().apply { color = bg })
                c2.drawRect(40f, ty, 555f, ty + 24f, borderPaint)

                c2.drawText(cat, 50f, ty + 16f, cellText)
                c2.drawText(feat, 180f, ty + 16f, cellText)
                c2.drawText(cap, 370f, ty + 16f, cellText)
                ty += 24f
            }

            c2.drawText("Page 2 of 2", 595f - 40f, 800f, Paint(footPaint).apply { textAlign = Paint.Align.RIGHT })

            doc.finishPage(p2)

            FileOutputStream(welcomeFile).use { fos ->
                doc.writeTo(fos)
            }
            doc.close()

            registerNewFile(welcomeFile, SourceType.IMPORTED)
        }
    }
}
