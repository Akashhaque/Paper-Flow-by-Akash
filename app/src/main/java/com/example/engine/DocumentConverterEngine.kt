package com.example.engine

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DocRun(val text: String, val isBold: Boolean = false, val isItalic: Boolean = false, val isHeading: Boolean = false)
data class DocParagraph(val runs: List<DocRun>, val isHeading: Boolean = false, val headingLevel: Int = 0)
data class DocTable(val rows: List<List<String>>)

sealed class DocElement {
    data class Paragraph(val p: DocParagraph) : DocElement()
    data class Table(val t: DocTable) : DocElement()
}

object DocumentConverterEngine {

    /**
     * Converts a document (DOCX, TXT, CSV, XLSX, PPTX, HTML) to a styled PDF.
     */
    suspend fun convertDocumentToPdf(
        context: Context,
        sourceUri: Uri,
        sourceFileName: String,
        outputPdfFile: File,
        onProgress: ((progress: Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val extension = sourceFileName.substringAfterLast('.', "").lowercase()

        val elements = mutableListOf<DocElement>()

        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            when (extension) {
                "docx" -> {
                    elements.addAll(parseDocx(inputStream))
                }
                "xlsx", "xls" -> {
                    elements.addAll(parseXlsxOrCsv(inputStream, isXlsx = extension == "xlsx"))
                }
                "csv" -> {
                    elements.addAll(parseCsv(inputStream))
                }
                "pptx" -> {
                    elements.addAll(parsePptx(inputStream))
                }
                "html", "htm" -> {
                    elements.addAll(parseHtml(inputStream))
                }
                else -> {
                    // Treat as plain text or markdown
                    elements.addAll(parsePlainText(inputStream))
                }
            }
        } ?: throw IOException("Could not open input document stream")

        if (elements.isEmpty()) {
            elements.add(DocElement.Paragraph(DocParagraph(listOf(DocRun("Document is empty.")))))
        }

        // Render elements into a multi-page PDF document
        renderElementsToPdf(elements, sourceFileName, outputPdfFile, isLandscape = (extension == "xlsx" || extension == "csv" || extension == "pptx"), onProgress)
    }

    private fun parseDocx(inputStream: InputStream): List<DocElement> {
        val elements = mutableListOf<DocElement>()
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry

        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(InputStreamReader(zis, StandardCharsets.UTF_8))

                var eventType = parser.eventType
                var inTable = false
                var currentRow = mutableListOf<String>()
                var currentTableRows = mutableListOf<List<String>>()
                var currentCellText = StringBuilder()
                var inCell = false

                var currentRuns = mutableListOf<DocRun>()
                var isBold = false
                var isItalic = false
                var isHeading = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "tbl" -> {
                                    inTable = true
                                    currentTableRows = mutableListOf()
                                }
                                "tr" -> {
                                    currentRow = mutableListOf()
                                }
                                "tc" -> {
                                    inCell = true
                                    currentCellText = StringBuilder()
                                }
                                "p" -> {
                                    if (!inTable) {
                                        currentRuns = mutableListOf()
                                        isHeading = false
                                    }
                                }
                                "pStyle" -> {
                                    val styleVal = parser.getAttributeValue(null, "val") ?: ""
                                    if (styleVal.contains("Heading", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                        isHeading = true
                                    }
                                }
                                "b" -> isBold = true
                                "i" -> isItalic = true
                                "t" -> {
                                    val text = parser.nextText()
                                    if (inCell) {
                                        currentCellText.append(text).append(" ")
                                    } else {
                                        currentRuns.add(DocRun(text, isBold = isBold, isItalic = isItalic, isHeading = isHeading))
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (name) {
                                "r" -> {
                                    isBold = false
                                    isItalic = false
                                }
                                "p" -> {
                                    if (!inTable && currentRuns.isNotEmpty()) {
                                        elements.add(DocElement.Paragraph(DocParagraph(currentRuns, isHeading = isHeading)))
                                        currentRuns = mutableListOf()
                                    }
                                }
                                "tc" -> {
                                    inCell = false
                                    currentRow.add(currentCellText.toString().trim())
                                }
                                "tr" -> {
                                    if (currentRow.isNotEmpty()) {
                                        currentTableRows.add(currentRow)
                                    }
                                }
                                "tbl" -> {
                                    inTable = false
                                    if (currentTableRows.isNotEmpty()) {
                                        elements.add(DocElement.Table(DocTable(currentTableRows)))
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return elements
    }

    private fun parseXlsxOrCsv(inputStream: InputStream, isXlsx: Boolean): List<DocElement> {
        if (!isXlsx) return parseCsv(inputStream)

        val elements = mutableListOf<DocElement>()
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry

        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<List<String>>()

        val tempBytes = mutableMapOf<String, ByteArray>()
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") {
                tempBytes[entry.name] = zis.readBytes()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        // 1. Read shared strings if present
        tempBytes["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                    sharedStrings.add(parser.nextText())
                }
                eventType = parser.next()
            }
        }

        // 2. Read sheet rows
        tempBytes["xl/worksheets/sheet1.xml"]?.let { bytes ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8))
            var eventType = parser.eventType
            var currentRow = mutableListOf<String>()
            var isShared = false
            var cellVal = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "row" -> currentRow = mutableListOf()
                            "c" -> {
                                val t = parser.getAttributeValue(null, "t") ?: ""
                                isShared = (t == "s")
                                cellVal = ""
                            }
                            "v" -> {
                                val raw = parser.nextText()
                                if (isShared) {
                                    val idx = raw.toIntOrNull() ?: -1
                                    cellVal = if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                                } else {
                                    cellVal = raw
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (name) {
                            "c" -> currentRow.add(cellVal)
                            "row" -> if (currentRow.isNotEmpty()) sheetRows.add(currentRow)
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        if (sheetRows.isNotEmpty()) {
            elements.add(DocElement.Table(DocTable(sheetRows)))
        } else {
            elements.add(DocElement.Paragraph(DocParagraph(listOf(DocRun("No spreadsheet data found.")))))
        }

        return elements
    }

    private fun parseCsv(inputStream: InputStream): List<DocElement> {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val rows = mutableListOf<List<String>>()
        var line = reader.readLine()
        while (line != null) {
            val delimiter = if (line.contains('\t')) '\t' else if (line.contains(';')) ';' else ','
            val cols = line.split(delimiter).map { it.trim().removeSurrounding("\"") }
            if (cols.isNotEmpty()) rows.add(cols)
            line = reader.readLine()
        }
        return if (rows.isNotEmpty()) listOf(DocElement.Table(DocTable(rows))) else emptyList()
    }

    private fun parsePptx(inputStream: InputStream): List<DocElement> {
        val elements = mutableListOf<DocElement>()
        val zis = ZipInputStream(inputStream)
        var entry: ZipEntry? = zis.nextEntry

        var slideIndex = 1
        val slideTexts = mutableListOf<Pair<String, List<String>>>()

        while (entry != null) {
            if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(InputStreamReader(zis, StandardCharsets.UTF_8))

                var eventType = parser.eventType
                var slideTitle = "Slide $slideIndex"
                val bodyLines = mutableListOf<String>()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "t") {
                        val text = parser.nextText()
                        if (slideTitle == "Slide $slideIndex") {
                            slideTitle = text
                        } else {
                            bodyLines.add(text)
                        }
                    }
                    eventType = parser.next()
                }
                slideTexts.add(slideTitle to bodyLines)
                slideIndex++
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }

        slideTexts.forEachIndexed { idx, (title, lines) ->
            elements.add(DocElement.Paragraph(DocParagraph(listOf(DocRun(title, isBold = true, isHeading = true)))))
            lines.forEach { line ->
                elements.add(DocElement.Paragraph(DocParagraph(listOf(DocRun("• $line")))))
            }
        }

        return elements
    }

    private fun parseHtml(inputStream: InputStream): List<DocElement> {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val text = reader.readText()
        // Strip html tags cleanly
        val cleanText = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        return cleanText.lines().filter { it.isNotBlank() }.map {
            DocElement.Paragraph(DocParagraph(listOf(DocRun(it))))
        }
    }

    private fun parsePlainText(inputStream: InputStream): List<DocElement> {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val elements = mutableListOf<DocElement>()
        var line = reader.readLine()
        while (line != null) {
            val isHeading = line.startsWith("# ") || line.startsWith("## ")
            val clean = line.removePrefix("# ").removePrefix("## ")
            elements.add(DocElement.Paragraph(DocParagraph(listOf(DocRun(clean, isHeading = isHeading)))))
            line = reader.readLine()
        }
        return elements
    }

    /**
     * Renders parsed document elements to a cleanly formatted, paginated PDF.
     */
    private fun renderElementsToPdf(
        elements: List<DocElement>,
        docTitle: String,
        outputPdfFile: File,
        isLandscape: Boolean = false,
        onProgress: ((progress: Float) -> Unit)?
    ): File {
        val pdfDocument = PdfDocument()

        val pageWidth = if (isLandscape) 842 else 595
        val pageHeight = if (isLandscape) 595 else 842

        val margin = 40f
        val contentWidth = pageWidth - (margin * 2)

        var pageIndex = 1
        var currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
        var canvas = currentPage.canvas
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#191919")
            textSize = 12f
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#191919")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F5222D")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EAEAEA")
            strokeWidth = 1f
        }

        fun drawHeaderAndFooter(c: Canvas, pageNum: Int) {
            c.drawText("PAPERFLOW CONVERTED DOCUMENT", margin, 25f, headerPaint)
            c.drawLine(margin, 30f, pageWidth - margin, 30f, dividerPaint)

            val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#888888")
                textSize = 9f
                textAlign = Paint.Align.RIGHT
            }
            c.drawText("Page $pageNum", pageWidth - margin, pageHeight - 15f, footPaint)
        }

        drawHeaderAndFooter(canvas, pageIndex)
        var cursorY = 55f

        fun checkNewPage(neededHeight: Float) {
            if (cursorY + neededHeight > pageHeight - 35f) {
                pdfDocument.finishPage(currentPage)
                pageIndex++
                currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create())
                canvas = currentPage.canvas
                canvas.drawColor(Color.WHITE)
                drawHeaderAndFooter(canvas, pageIndex)
                cursorY = 55f
            }
        }

        elements.forEachIndexed { i, el ->
            onProgress?.invoke(i.toFloat() / elements.size)
            when (el) {
                is DocElement.Paragraph -> {
                    val p = el.p
                    val paint = if (p.isHeading) headingPaint else textPaint
                    val lineHeight = paint.textSize * 1.4f

                    // Wrap text within contentWidth
                    val fullText = p.runs.joinToString("") { it.text }
                    val words = fullText.split(" ")
                    var currentLine = ""

                    for (w in words) {
                        val testLine = if (currentLine.isEmpty()) w else "$currentLine $w"
                        if (paint.measureText(testLine) > contentWidth) {
                            checkNewPage(lineHeight)
                            canvas.drawText(currentLine, margin, cursorY + paint.textSize, paint)
                            cursorY += lineHeight
                            currentLine = w
                        } else {
                            currentLine = testLine
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        checkNewPage(lineHeight + 6f)
                        canvas.drawText(currentLine, margin, cursorY + paint.textSize, paint)
                        cursorY += lineHeight + 6f
                    }
                }

                is DocElement.Table -> {
                    val rows = el.t.rows
                    if (rows.isNotEmpty()) {
                        val colCount = rows.maxOf { it.size }.coerceAtLeast(1)
                        val colWidth = contentWidth / colCount
                        val rowHeight = 22f

                        val cellTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#222222")
                            textSize = 10f
                        }
                        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.WHITE
                            textSize = 10f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        val tableHeaderBg = Paint().apply { color = Color.parseColor("#F5222D") }
                        val altRowBg = Paint().apply { color = Color.parseColor("#F9F9FA") }
                        val borderPaint = Paint().apply {
                            color = Color.parseColor("#E0E0E0")
                            style = Paint.Style.STROKE
                            strokeWidth = 0.8f
                        }

                        rows.forEachIndexed { rIdx, row ->
                            checkNewPage(rowHeight)
                            val isFirstRow = (rIdx == 0)

                            if (isFirstRow) {
                                canvas.drawRect(margin, cursorY, margin + contentWidth, cursorY + rowHeight, tableHeaderBg)
                            } else if (rIdx % 2 == 1) {
                                canvas.drawRect(margin, cursorY, margin + contentWidth, cursorY + rowHeight, altRowBg)
                            }

                            row.forEachIndexed { cIdx, text ->
                                val cellLeft = margin + (cIdx * colWidth)
                                canvas.drawRect(cellLeft, cursorY, cellLeft + colWidth, cursorY + rowHeight, borderPaint)

                                val p = if (isFirstRow) headerTextPaint else cellTextPaint
                                val trimmed = if (p.measureText(text) > colWidth - 8f) {
                                    text.take(8) + "…"
                                } else text
                                canvas.drawText(trimmed, cellLeft + 4f, cursorY + 15f, p)
                            }
                            cursorY += rowHeight
                        }
                        cursorY += 10f
                    }
                }
            }
        }

        pdfDocument.finishPage(currentPage)
        outputPdfFile.parentFile?.mkdirs()
        FileOutputStream(outputPdfFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()
        onProgress?.invoke(1f)
        return outputPdfFile
    }

    /**
     * Extracts pages from PDF and saves them as images (JPG/PNG).
     */
    suspend fun convertPdfToImages(
        context: Context,
        pdfFile: File,
        outputDir: File,
        format: String = "JPG"
    ): List<File> = withContext(Dispatchers.IO) {
        val pageCount = PdfRendererEngine.getPageCount(pdfFile.absolutePath)
        val imageFiles = mutableListOf<File>()
        outputDir.mkdirs()

        for (i in 0 until pageCount) {
            val bitmap = PdfRendererEngine.renderPageToBitmap(pdfFile.absolutePath, i, targetWidth = 1400) ?: continue
            val ext = if (format.equals("PNG", ignoreCase = true)) "png" else "jpg"
            val file = File(outputDir, "${pdfFile.nameWithoutExtension}_page_${i + 1}.$ext")
            FileOutputStream(file).use { out ->
                if (ext == "png") {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
            bitmap.recycle()
            imageFiles.add(file)
        }
        imageFiles
    }

    /**
     * Converts recognized or plain text into a valid DOCX file.
     */
    suspend fun createDocxFromText(text: String, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val zos = ZipOutputStream(FileOutputStream(outputFile))

        // 1. [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        zos.write(contentTypes.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()

        // 2. _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        zos.write(rels.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()

        // 3. word/document.xml
        zos.putNextEntry(ZipEntry("word/document.xml"))
        val paragraphsXml = text.lines().joinToString("") { line ->
            val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<w:p><w:r><w:t>$escaped</w:t></w:r></w:p>"
        }
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $paragraphsXml
  </w:body>
</w:document>"""
        zos.write(documentXml.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()

        zos.close()
        outputFile
    }

    /**
     * Converts text table into CSV format.
     */
    suspend fun createCsvFromText(rows: List<List<String>>, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        BufferedWriter(FileWriter(outputFile)).use { writer ->
            rows.forEach { row ->
                val line = row.joinToString(",") { cell ->
                    val clean = cell.replace("\"", "\"\"")
                    if (clean.contains(",") || clean.contains("\n")) "\"$clean\"" else clean
                }
                writer.write(line)
                writer.newLine()
            }
        }
        outputFile
    }
}
