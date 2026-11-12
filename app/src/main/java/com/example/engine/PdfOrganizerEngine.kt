package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.model.CompressionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfOrganizerEngine {

    /**
     * Merges multiple PDF files in order into one output PDF.
     */
    suspend fun mergePdfs(
        pdfFiles: List<File>,
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val totalPages = pdfFiles.sumOf { PdfRendererEngine.getPageCount(it.absolutePath) }
        val outputPdf = PdfDocument()
        var pageCounter = 0

        try {
            pdfFiles.forEach { file ->
                val count = PdfRendererEngine.getPageCount(file.absolutePath)
                for (pageIdx in 0 until count) {
                    pageCounter++
                    onProgress?.invoke(pageCounter, totalPages)

                    val bitmap = PdfRendererEngine.renderPageToBitmap(file.absolutePath, pageIdx, targetWidth = 1400) ?: continue
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageCounter).create()
                    val page = outputPdf.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    outputPdf.finishPage(page)
                    bitmap.recycle()
                }
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
     * Splits or extracts pages from a PDF given a list of 0-based page indices.
     */
    suspend fun extractOrSplitPages(
        sourcePdf: File,
        pageIndices: List<Int>,
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val outputPdf = PdfDocument()
        try {
            pageIndices.forEachIndexed { outIdx, pageIdx ->
                onProgress?.invoke(outIdx + 1, pageIndices.size)
                val bitmap = PdfRendererEngine.renderPageToBitmap(sourcePdf.absolutePath, pageIdx, targetWidth = 1400) ?: return@forEachIndexed
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, outIdx + 1).create()
                val page = outputPdf.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
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
     * Rotates selected pages by a given rotation angle (90, 180, 270) and re-saves the PDF.
     */
    suspend fun rotatePages(
        sourcePdf: File,
        pageRotations: Map<Int, Float>, // page index -> rotation angle
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val totalPages = PdfRendererEngine.getPageCount(sourcePdf.absolutePath)
        val outputPdf = PdfDocument()

        try {
            for (i in 0 until totalPages) {
                var bitmap = PdfRendererEngine.renderPageToBitmap(sourcePdf.absolutePath, i, targetWidth = 1400) ?: continue
                val degrees = pageRotations[i] ?: 0f
                if (degrees % 360f != 0f) {
                    val rotated = ImageProcessor.rotateBitmap(bitmap, degrees)
                    bitmap.recycle()
                    bitmap = rotated
                }

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = outputPdf.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
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
     * Deletes selected pages from PDF.
     */
    suspend fun deletePages(
        sourcePdf: File,
        pagesToDelete: Set<Int>, // 0-based page indices
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val totalPages = PdfRendererEngine.getPageCount(sourcePdf.absolutePath)
        val keepIndices = (0 until totalPages).filterNot { pagesToDelete.contains(it) }
        extractOrSplitPages(sourcePdf, keepIndices, outputFile)
    }

    /**
     * Rearranges pages according to a new index order.
     */
    suspend fun rearrangePages(
        sourcePdf: File,
        newOrder: List<Int>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        extractOrSplitPages(sourcePdf, newOrder, outputFile)
    }

    /**
     * Compresses PDF by scaling down page resolutions and applying quality compression.
     */
    suspend fun compressPdf(
        sourcePdf: File,
        compressionLevel: CompressionLevel,
        outputFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Pair<File, Long> = withContext(Dispatchers.IO) {
        val totalPages = PdfRendererEngine.getPageCount(sourcePdf.absolutePath)
        val outputPdf = PdfDocument()

        val targetWidth = when (compressionLevel) {
            CompressionLevel.LOW -> 1200
            CompressionLevel.MEDIUM -> 900
            CompressionLevel.HIGH -> 650
        }

        try {
            for (i in 0 until totalPages) {
                onProgress?.invoke(i + 1, totalPages)
                val rawBitmap = PdfRendererEngine.renderPageToBitmap(sourcePdf.absolutePath, i, targetWidth = targetWidth) ?: continue

                val pageInfo = PdfDocument.PageInfo.Builder(rawBitmap.width, rawBitmap.height, i + 1).create()
                val page = outputPdf.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(rawBitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                outputPdf.finishPage(page)
                rawBitmap.recycle()
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                outputPdf.writeTo(fos)
            }
        } finally {
            outputPdf.close()
        }

        val originalSize = sourcePdf.length()
        val compressedSize = outputFile.length()
        outputFile to (originalSize - compressedSize)
    }
}
