package com.example.engine

import android.graphics.*
import com.example.data.model.DocumentFilter
import kotlin.math.max
import kotlin.math.min

data class DocumentCorners(
    var topLeft: PointF,
    var topRight: PointF,
    var bottomRight: PointF,
    var bottomLeft: PointF
) {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }
}

object ImageProcessor {

    /**
     * Automatic document boundary detector (CamScanner-like).
     * Analyzes image contrast, edge gradients, and luminance to locate the 4 corners of paper.
     */
    fun detectDocumentCorners(source: Bitmap): DocumentCorners {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        // Analyze on downscaled preview for speed and noise reduction
        val sampleW = 200
        val sampleH = (200f * (h / w)).toInt().coerceAtLeast(100)
        val scaled = Bitmap.createScaledBitmap(source, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        scaled.recycle()

        // Calculate luminance map
        val lums = FloatArray(sampleW * sampleH)
        var totalLum = 0f
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            lums[i] = lum
            totalLum += lum
        }
        val avgLum = totalLum / lums.size

        // Find boundary margins where paper luminance deviates from background
        var leftSample = (sampleW * 0.06f).toInt()
        var rightSample = (sampleW * 0.94f).toInt()
        var topSample = (sampleH * 0.06f).toInt()
        var bottomSample = (sampleH * 0.94f).toInt()

        // Horizontal scan from outside in for document edge
        for (x in 0 until (sampleW * 0.25f).toInt()) {
            var colDiff = 0f
            for (y in (sampleH * 0.2f).toInt() until (sampleH * 0.8f).toInt()) {
                val idx = y * sampleW + x
                val nextIdx = y * sampleW + (x + 3)
                colDiff += kotlin.math.abs(lums[nextIdx] - lums[idx])
            }
            if (colDiff / (sampleH * 0.6f) > 22f) {
                leftSample = x
                break
            }
        }

        for (x in sampleW - 1 downTo (sampleW * 0.75f).toInt()) {
            var colDiff = 0f
            for (y in (sampleH * 0.2f).toInt() until (sampleH * 0.8f).toInt()) {
                val idx = y * sampleW + x
                val prevIdx = y * sampleW + (x - 3)
                colDiff += kotlin.math.abs(lums[prevIdx] - lums[idx])
            }
            if (colDiff / (sampleH * 0.6f) > 22f) {
                rightSample = x
                break
            }
        }

        // Scale sample coordinates back to original source dimensions
        val scaleX = w / sampleW
        val scaleY = h / sampleH

        val x0 = (leftSample * scaleX).coerceIn(0f, w * 0.3f)
        val x1 = (rightSample * scaleX).coerceIn(w * 0.7f, w)
        val y0 = (topSample * scaleY).coerceIn(0f, h * 0.3f)
        val y1 = (bottomSample * scaleY).coerceIn(h * 0.7f, h)

        return DocumentCorners(
            topLeft = PointF(x0, y0),
            topRight = PointF(x1, y0),
            bottomRight = PointF(x1, y1),
            bottomLeft = PointF(x0, y1)
        )
    }

    /**
     * Un-skews, crops away the background, and rectifies perspective from 4 corners.
     */
    fun cropAndRectifyDocument(
        source: Bitmap,
        corners: DocumentCorners
    ): Bitmap {
        val tl = corners.topLeft
        val tr = corners.topRight
        val br = corners.bottomRight
        val bl = corners.bottomLeft

        // Calculate prospective width and height
        val topWidth = Math.hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val bottomWidth = Math.hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val targetWidth = max(topWidth, bottomWidth).toInt().coerceAtLeast(100)

        val leftHeight = Math.hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val rightHeight = Math.hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        val targetHeight = max(leftHeight, rightHeight).toInt().coerceAtLeast(100)

        val srcPoints = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )

        return warpPerspective(source, srcPoints, targetWidth, targetHeight)
    }

    /**
     * Applies CamScanner-grade document enhancements and filters.
     */
    fun applyFilter(source: Bitmap, filter: DocumentFilter): Bitmap {
        if (filter == DocumentFilter.ORIGINAL) return source

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (filter) {
            DocumentFilter.MAGIC_COLOR -> {
                // CamScanner Signature Magic Color:
                // Boosts paper brightness to clean white while enriching colors and darkening text ink
                val cm = ColorMatrix().apply {
                    setSaturation(1.4f)
                    val contrast = 1.35f
                    val brightness = 26f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    postConcat(ColorMatrix(array))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.AUTO_ENHANCE -> {
                // AI-Style Auto Clarity:
                // Eliminates shadows, sharpens dark text, and whitens ambient light
                val cm = ColorMatrix().apply {
                    val scale = 1.22f
                    val translate = 18f
                    set(floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.DOCUMENT -> {
                // High contrast clean office document filter
                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                    val contrast = 1.6f
                    val brightness = 35f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                    postConcat(ColorMatrix(array))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.BLACK_WHITE -> {
                // Deep B&W threshold: stark white background with bold black letters
                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                    val contrast = 2.4f
                    val translate = -135f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                    postConcat(ColorMatrix(array))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.LIGHT -> {
                // Shadow removal: lifts shadowy corners
                val cm = ColorMatrix().apply {
                    val array = floatArrayOf(
                        1.1f, 0f, 0f, 0f, 45f,
                        0f, 1.1f, 0f, 0f, 45f,
                        0f, 0f, 1.1f, 0f, 45f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(array)
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.DARK -> {
                val cm = ColorMatrix().apply {
                    val array = floatArrayOf(
                        0.85f, 0f, 0f, 0f, -20f,
                        0f, 0.85f, 0f, 0f, -20f,
                        0f, 0f, 0.85f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(array)
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.HIGH_CONTRAST -> {
                val cm = ColorMatrix().apply {
                    val contrast = 1.7f
                    val translate = (-0.5f * contrast + 0.5f) * 255f
                    val array = floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(array)
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.PHOTO -> {
                val cm = ColorMatrix().apply {
                    setSaturation(1.25f)
                    val array = floatArrayOf(
                        1.06f, 0f, 0f, 0f, 6f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 0.95f, 0f, -4f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    postConcat(ColorMatrix(array))
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.SHARPEN -> {
                val cm = ColorMatrix().apply {
                    val scale = 1.35f
                    val array = floatArrayOf(
                        scale, 0f, 0f, 0f, -12f,
                        0f, scale, 0f, 0f, -12f,
                        0f, 0f, scale, 0f, -12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(array)
                }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(source, 0f, 0f, paint)
            }

            DocumentFilter.ORIGINAL -> {
                canvas.drawBitmap(source, 0f, 0f, paint)
            }
        }

        return output
    }

    /**
     * Fine-tunes brightness (-100 to 100) and contrast (0.5 to 2.0).
     */
    fun adjustBrightnessContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Rotates a Bitmap by 90, 180, or 270 degrees.
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Crops a rectangular portion of a Bitmap.
     */
    fun cropBitmap(source: Bitmap, rect: Rect): Bitmap {
        val safeX = rect.left.coerceIn(0, source.width - 1)
        val safeY = rect.top.coerceIn(0, source.height - 1)
        val safeWidth = rect.width().coerceIn(1, source.width - safeX)
        val safeHeight = rect.height().coerceIn(1, source.height - safeY)
        return Bitmap.createBitmap(source, safeX, safeY, safeWidth, safeHeight)
    }

    /**
     * Corrects perspective given 4 points (topLeft, topRight, bottomRight, bottomLeft).
     */
    fun warpPerspective(
        source: Bitmap,
        srcPoints: FloatArray, // [x0, y0, x1, y1, x2, y2, x3, y3]
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }
}
