package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val fullText: String,
    val paragraphs: List<String>,
    val blockCount: Int,
    val lineCount: Int
)

object OcrEngine {

    /**
     * Extracts text from a Bitmap using Google ML Kit.
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)

        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val paragraphs = visionText.textBlocks.map { it.text }
                    val totalLines = visionText.textBlocks.sumOf { it.lines.size }
                    continuation.resume(
                        OcrResult(
                            fullText = visionText.text,
                            paragraphs = paragraphs,
                            blockCount = visionText.textBlocks.size,
                            lineCount = totalLines
                        )
                    )
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    /**
     * Extracts text from a PDF file by rendering each page and running OCR.
     */
    suspend fun recognizeTextFromPdf(
        pdfFile: File,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        val pageCount = PdfRendererEngine.getPageCount(pdfFile.absolutePath)
        val fullTextBuilder = StringBuilder()
        val allParagraphs = mutableListOf<String>()
        var totalBlocks = 0
        var totalLines = 0

        for (i in 0 until pageCount) {
            onProgress?.invoke(i + 1, pageCount)
            val pageBitmap = PdfRendererEngine.renderPageToBitmap(pdfFile.absolutePath, i, targetWidth = 1400) ?: continue
            try {
                val pageResult = recognizeText(pageBitmap)
                if (fullTextBuilder.isNotEmpty()) {
                    fullTextBuilder.append("\n\n--- Page ${i + 1} ---\n\n")
                }
                fullTextBuilder.append(pageResult.fullText)
                allParagraphs.addAll(pageResult.paragraphs)
                totalBlocks += pageResult.blockCount
                totalLines += pageResult.lineCount
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pageBitmap.recycle()
            }
        }

        OcrResult(
            fullText = fullTextBuilder.toString(),
            paragraphs = allParagraphs,
            blockCount = totalBlocks,
            lineCount = totalLines
        )
    }
}
