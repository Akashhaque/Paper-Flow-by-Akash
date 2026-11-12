package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfRendererEngine {

    /**
     * Gets page count of a PDF file.
     */
    fun getPageCount(filePath: String): Int {
        val file = File(filePath)
        if (!file.exists()) return 0
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Renders a specific page of a PDF to a Bitmap.
     * @param targetWidth if > 0, page is scaled to this width while preserving aspect ratio.
     */
    suspend fun renderPageToBitmap(
        filePath: String,
        pageIndex: Int,
        targetWidth: Int = 1200
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            page = renderer.openPage(pageIndex)
            val originalWidth = page.width
            val originalHeight = page.height

            val (outW, outH) = if (targetWidth > 0) {
                val scale = targetWidth.toFloat() / originalWidth.toFloat()
                targetWidth to (originalHeight * scale).toInt()
            } else {
                originalWidth to originalHeight
            }

            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            // PDF backgrounds default to transparent in PdfRenderer, so fill white first
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                page?.close()
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Generates a cached thumbnail image for the first page of a PDF file.
     */
    suspend fun generateThumbnail(context: Context, pdfFile: File): String? = withContext(Dispatchers.IO) {
        try {
            val thumbName = "thumb_${pdfFile.nameWithoutExtension}_${pdfFile.lastModified()}.jpg"
            val thumbDir = File(context.cacheDir, "pdf_thumbs").apply { mkdirs() }
            val thumbFile = File(thumbDir, thumbName)

            if (thumbFile.exists() && thumbFile.length() > 0) {
                return@withContext thumbFile.absolutePath
            }

            val bitmap = renderPageToBitmap(pdfFile.absolutePath, 0, targetWidth = 400) ?: return@withContext null
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            bitmap.recycle()
            thumbFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
