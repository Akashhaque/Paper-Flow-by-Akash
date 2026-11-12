package com.example

import com.example.data.model.DocumentFilter
import com.example.data.model.PageOrientation
import com.example.data.model.PageSizeOption
import com.example.engine.PdfCreatorEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPageSizeDimensions() {
        assertEquals(595, PageSizeOption.A4.widthPt)
        assertEquals(842, PageSizeOption.A4.heightPt)
        assertEquals(612, PageSizeOption.LETTER.widthPt)
        assertEquals(792, PageSizeOption.LETTER.heightPt)
    }

    @Test
    fun testMeasurementUnitConversions() {
        // 1 inch = 72 pt
        assertEquals(72, PdfCreatorEngine.inchToPt(1f))
        // 25.4 mm = 1 inch = 72 pt
        assertEquals(72, PdfCreatorEngine.mmToPt(25.4f))
        // 2.54 cm = 1 inch = 72 pt
        assertEquals(72, PdfCreatorEngine.cmToPt(2.54f))
    }

    @Test
    fun testFilterDefinitions() {
        assertTrue(DocumentFilter.values().contains(DocumentFilter.ORIGINAL))
        assertTrue(DocumentFilter.values().contains(DocumentFilter.AUTO_ENHANCE))
        assertTrue(DocumentFilter.values().contains(DocumentFilter.DOCUMENT))
        assertTrue(DocumentFilter.values().contains(DocumentFilter.BLACK_WHITE))
        assertTrue(DocumentFilter.values().contains(DocumentFilter.MAGIC_COLOR))
    }
}
