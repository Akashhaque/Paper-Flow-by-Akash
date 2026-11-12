package com.example.engine

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class WatermarkConfig(
    val text: String = "CONFIDENTIAL",
    val colorHex: String = "#F5222D",
    val alpha: Float = 0.25f, // 0.1f to 1.0f
    val fontSize: Float = 42f,
    val rotationDegrees: Float = -45f
)

data class PageNumberConfig(
    val isBottom: Boolean = true,
    val alignment: Paint.Align = Paint.Align.CENTER,
    val format: String = "Page {n} of {total}",
    val startNumber: Int = 1,
    val fontSize: Float = 10f,
    val colorHex: String = "#777777"
)

data class HeaderFooterConfig(
    val headerText: String? = null,
    val footerText: String? = null,
    val includeDate: Boolean = false,
    val documentTitle: String? = null
)

object PdfAdvancedToolsEngine {

    /**
     * Applies a watermark diagonally or centered on every page of a PDF.
     */
    suspend fun addWatermark(
        sourcePdf: File,
        config: WatermarkConfig,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val pageCount = PdfRendererEngine.getPageCount(sourcePdf.absolutePath)
        val outputPdf = PdfDocument()

        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(config.colorHex)
            alpha = (config.alpha * 255).toInt().coerceIn(10, 255)
            textSize = config.fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        try {
            for (i in 0 until pageCount) {
                val bitmap = PdfRendererEngine.renderPageToBitmap(sourcePdf.absolutePath, i, targetWidth = 1400) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = outputPdf.startPage(pageInfo)
                val canvas = page.canvas

                // Draw existing page
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

                // Draw watermark rotated around center
                canvas.save()
                canvas.rotate(config.rotationDegrees, bitmap.width / 2f, bitmap.height / 2f)
                canvas.drawText(config.text, bitmap.width / 2f, bitmap.height / 2f, watermarkPaint)
                canvas.restore()

                outputPdf.finishPage(page)
                bitmap.recycle()
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                outputPdf.writeTo(fos)
            }
        } finally {
            outputPdf.close()
        }
        outputFile
    }

    /**
     * Stamps page numbers and optional header/footers on all pages.
     */
    suspend fun addPageNumbersAndHeader(
        sourcePdf: File,
        numberConfig: PageNumberConfig,
        headerFooterConfig: HeaderFooterConfig? = null,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val totalPages = PdfRendererEngine.getPageCount(sourcePdf.absolutePath)
        val outputPdf = PdfDocument()

        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(numberConfig.colorHex)
            textSize = numberConfig.fontSize * 1.5f
            textAlign = numberConfig.alignment
        }

        val hfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            textSize = 14f
        }

        val dateString = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())

        try {
            for (i in 0 until totalPages) {
                val pageNum = i + numberConfig.startNumber
                val bitmap = PdfRendererEngine.renderPageToBitmap(sourcePdf.absolutePath, i, targetWidth = 1400) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = outputPdf.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))

                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()

                // Draw page number
                val numText = numberConfig.format
                    .replace("{n}", pageNum.toString())
                    .replace("{total}", totalPages.toString())

                val numY = if (numberConfig.isBottom) h - 30f else 45f
                val numX = when (numberConfig.alignment) {
                    Paint.Align.LEFT -> 50f
                    Paint.Align.RIGHT -> w - 50f
                    else -> w / 2f
                }
                canvas.drawText(numText, numX, numY, numPaint)

                // Draw header / footer text if requested
                headerFooterConfig?.let { hf ->
                    if (!hf.headerText.isNullOrBlank()) {
                        canvas.drawText(hf.headerText, 50f, 45f, hfPaint)
                    }
                    if (hf.includeDate) {
                        val dPaint = Paint(hfPaint).apply { textAlign = Paint.Align.RIGHT }
                        canvas.drawText(dateString, w - 50f, 45f, dPaint)
                    }
                    if (!hf.footerText.isNullOrBlank() && !numberConfig.isBottom) {
                        canvas.drawText(hf.footerText, 50f, h - 30f, hfPaint)
                    }
                }

                outputPdf.finishPage(page)
                bitmap.recycle()
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                outputPdf.writeTo(fos)
            }
        } finally {
            outputPdf.close()
        }
        outputFile
    }

    /**
     * Inserts a blank page into the PDF at a specific position (0-based).
     */
    suspend fun insertBlankPage(
        sourcePdf: File,
        positionIndex: Int,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val totalPages = PdfRendererEngine.getPageCount(sourcePdf.absolutePath)
        val outputPdf = PdfDocument()

        try {
            var outIdx = 0
            for (i in 0 until totalPages) {
                if (i == positionIndex) {
                    // Insert blank page
                    outIdx++
                    val blankInfo = PdfDocument.PageInfo.Builder(595, 842, outIdx).create()
                    val blankPage = outputPdf.startPage(blankInfo)
                    blankPage.canvas.drawColor(Color.WHITE)
                    outputPdf.finishPage(blankPage)
                }

                outIdx++
                val bitmap = PdfRendererEngine.renderPageToBitmap(sourcePdf.absolutePath, i, targetWidth = 1400) ?: continue
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, outIdx).create()
                val page = outputPdf.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                outputPdf.finishPage(page)
                bitmap.recycle()
            }

            if (positionIndex >= totalPages) {
                outIdx++
                val blankInfo = PdfDocument.PageInfo.Builder(595, 842, outIdx).create()
                val blankPage = outputPdf.startPage(blankInfo)
                blankPage.canvas.drawColor(Color.WHITE)
                outputPdf.finishPage(blankPage)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                outputPdf.writeTo(fos)
            }
        } finally {
            outputPdf.close()
        }
        outputFile
    }

    /**
     * Prints a PDF document using the Android PrintManager.
     */
    fun printPdf(context: Context, pdfFile: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val pInfo = android.print.PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PdfRendererEngine.getPageCount(pdfFile.absolutePath))
                    .build()
                callback?.onLayoutFinished(pInfo, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (destination == null) return
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print("PaperFlow – ${pdfFile.name}", printAdapter, PrintAttributes.Builder().build())
    }

    /**
     * Shares a PDF file via standard Android share sheet with FileProvider URI.
     */
    fun sharePdf(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF Document"))
    }
}
