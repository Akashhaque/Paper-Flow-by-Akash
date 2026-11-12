package com.example.engine

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImagePageItem(
    val bitmap: Bitmap,
    val filter: DocumentFilter = DocumentFilter.ORIGINAL,
    val rotationDegrees: Float = 0f,
    val cropRect: Rect? = null
)

object PdfCreatorEngine {

    /**
     * Converts mm to PDF points (1 pt = 1/72 inch, 1 inch = 25.4 mm => 72 / 25.4 pt/mm)
     */
    fun mmToPt(mm: Float): Int = (mm * 72f / 25.4f).toInt()
    fun cmToPt(cm: Float): Int = mmToPt(cm * 10f)
    fun inchToPt(inch: Float): Int = (inch * 72f).toInt()

    /**
     * Creates a PDF from a list of ImagePageItems with full customization.
     */
    suspend fun createPdfFromImages(
        context: Context,
        pages: List<ImagePageItem>,
        outputFile: File,
        pageSizeOption: PageSizeOption = PageSizeOption.A4,
        customWidthPt: Int = 595,
        customHeightPt: Int = 842,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        marginOption: MarginOption = MarginOption.NONE,
        customMarginPt: Int = 0,
        imageFitOption: ImageFitOption = ImageFitOption.FIT_PAGE,
        imageQualityOption: ImageQualityOption = ImageQualityOption.HIGH,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        val baseWidth = if (pageSizeOption == PageSizeOption.CUSTOM) customWidthPt else pageSizeOption.widthPt
        val baseHeight = if (pageSizeOption == PageSizeOption.CUSTOM) customHeightPt else pageSizeOption.heightPt
        val margin = if (marginOption == MarginOption.CUSTOM) customMarginPt else marginOption.marginPt

        try {
            pages.forEachIndexed { index, item ->
                onProgress?.invoke(index + 1, pages.size)

                // 1. Prepare processed bitmap (crop, rotate, filter)
                var processed = item.bitmap
                if (item.cropRect != null) {
                    processed = ImageProcessor.cropBitmap(processed, item.cropRect)
                }
                if (item.rotationDegrees % 360f != 0f) {
                    processed = ImageProcessor.rotateBitmap(processed, item.rotationDegrees)
                }
                if (item.filter != DocumentFilter.ORIGINAL) {
                    processed = ImageProcessor.applyFilter(processed, item.filter)
                }

                // 2. Determine page orientation
                val isLandscape = when (orientation) {
                    PageOrientation.PORTRAIT -> false
                    PageOrientation.LANDSCAPE -> true
                    PageOrientation.AUTO -> processed.width > processed.height
                }

                val pageWidth = if (isLandscape) maxOf(baseWidth, baseHeight) else minOf(baseWidth, baseHeight)
                val pageHeight = if (isLandscape) minOf(baseWidth, baseHeight) else maxOf(baseWidth, baseHeight)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Fill page background with white
                val bgPaint = Paint().apply { color = Color.WHITE }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

                // 3. Calculate destination rectangle with margins
                val availLeft = margin.toFloat()
                val availTop = margin.toFloat()
                val availWidth = (pageWidth - (margin * 2)).coerceAtLeast(10).toFloat()
                val availHeight = (pageHeight - (margin * 2)).coerceAtLeast(10).toFloat()

                val destRect = RectF()

                when (imageFitOption) {
                    ImageFitOption.FIT_PAGE -> {
                        val imgRatio = processed.width.toFloat() / processed.height.toFloat()
                        val availRatio = availWidth / availHeight
                        if (imgRatio > availRatio) {
                            val targetW = availWidth
                            val targetH = availWidth / imgRatio
                            val top = availTop + (availHeight - targetH) / 2f
                            destRect.set(availLeft, top, availLeft + targetW, top + targetH)
                        } else {
                            val targetH = availHeight
                            val targetW = availHeight * imgRatio
                            val left = availLeft + (availWidth - targetW) / 2f
                            destRect.set(left, availTop, left + targetW, availTop + targetH)
                        }
                    }

                    ImageFitOption.FILL_PAGE -> {
                        val imgRatio = processed.width.toFloat() / processed.height.toFloat()
                        val availRatio = availWidth / availHeight
                        if (imgRatio > availRatio) {
                            val targetH = availHeight
                            val targetW = availHeight * imgRatio
                            val left = availLeft + (availWidth - targetW) / 2f
                            destRect.set(left, availTop, left + targetW, availTop + targetH)
                        } else {
                            val targetW = availWidth
                            val targetH = availWidth / imgRatio
                            val top = availTop + (availHeight - targetH) / 2f
                            destRect.set(availLeft, top, availLeft + targetW, top + targetH)
                        }
                    }

                    ImageFitOption.STRETCH -> {
                        destRect.set(availLeft, availTop, availLeft + availWidth, availTop + availHeight)
                    }

                    ImageFitOption.ORIGINAL_SIZE -> {
                        val targetW = processed.width.toFloat()
                        val targetH = processed.height.toFloat()
                        val left = availLeft + (availWidth - targetW) / 2f
                        val top = availTop + (availHeight - targetH) / 2f
                        destRect.set(left, top, left + targetW, top + targetH)
                    }

                    ImageFitOption.CENTER -> {
                        val scale = minOf(1f, minOf(availWidth / processed.width, availHeight / processed.height))
                        val targetW = processed.width * scale
                        val targetH = processed.height * scale
                        val left = availLeft + (availWidth - targetW) / 2f
                        val top = availTop + (availHeight - targetH) / 2f
                        destRect.set(left, top, left + targetW, top + targetH)
                    }
                }

                // 4. Draw image with quality paint
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(processed, null, destRect, paint)

                pdfDocument.finishPage(page)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }

        outputFile
    }

    /**
     * Creates an ID Card PDF document placing Front and Back images on a single page with card outlines.
     */
    suspend fun createIdCardPdf(
        frontBitmap: Bitmap,
        backBitmap: Bitmap?,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 pt
        val pageHeight = 842 // A4 pt

        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // White background
            canvas.drawColor(Color.WHITE)

            // Title Header
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#191919")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("IDENTITY CARD SCAN", pageWidth / 2f, 60f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#777777")
                textSize = 12f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Generated by PaperFlow – PDF Maker", pageWidth / 2f, 80f, subPaint)

            // Card standard dimensions on paper: 85.6mm x 53.98mm => approx 242 pt x 153 pt
            val cardW = 340f
            val cardH = 215f
            val left = (pageWidth - cardW) / 2f

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D71920")
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E0E0E0")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            // Front Card
            val frontTop = 130f
            canvas.drawText("FRONT SIDE", left, frontTop - 12f, labelPaint)
            val frontRect = RectF(left, frontTop, left + cardW, frontTop + cardH)
            canvas.drawRoundRect(frontRect, 12f, 12f, borderPaint)

            // Clip round rect and draw front bitmap
            canvas.save()
            val frontPath = Path().apply { addRoundRect(frontRect, 12f, 12f, Path.Direction.CW) }
            canvas.clipPath(frontPath)
            canvas.drawBitmap(frontBitmap, null, frontRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()

            // Back Card (if available)
            if (backBitmap != null) {
                val backTop = frontTop + cardH + 70f
                canvas.drawText("BACK SIDE", left, backTop - 12f, labelPaint)
                val backRect = RectF(left, backTop, left + cardW, backTop + cardH)
                canvas.drawRoundRect(backRect, 12f, 12f, borderPaint)

                canvas.save()
                val backPath = Path().apply { addRoundRect(backRect, 12f, 12f, Path.Direction.CW) }
                canvas.clipPath(backPath)
                canvas.drawBitmap(backBitmap, null, backRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            }

            // Footer note
            val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#999999")
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Document verified and scanned via PaperFlow", pageWidth / 2f, pageHeight - 40f, footPaint)

            pdfDocument.finishPage(page)

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }

        outputFile
    }

    /**
     * Creates a blank PDF with specified number of pages and size.
     */
    suspend fun createBlankPdf(
        outputFile: File,
        pageCount: Int = 1,
        pageSizeOption: PageSizeOption = PageSizeOption.A4
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        val width = pageSizeOption.widthPt
        val height = pageSizeOption.heightPt

        try {
            for (i in 1..pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                pdfDocument.finishPage(page)
            }
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }
        outputFile
    }
}
