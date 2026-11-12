package com.example.data.model

enum class PageSizeOption(val displayName: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 (210 × 297 mm)", 595, 842),
    A3("A3 (297 × 420 mm)", 842, 1191),
    A5("A5 (148 × 210 mm)", 420, 595),
    LETTER("Letter (8.5 × 11 in)", 612, 792),
    LEGAL("Legal (8.5 × 14 in)", 612, 1008),
    EXECUTIVE("Executive (7.25 × 10.5 in)", 522, 756),
    B5("B5 (176 × 250 mm)", 499, 709),
    CUSTOM("Custom Size", 595, 842)
}

enum class PageOrientation(val displayName: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
    AUTO("Automatic")
}

enum class MarginOption(val displayName: String, val marginPt: Int) {
    NONE("No Margin", 0),
    SMALL("Small (6mm)", 18),
    MEDIUM("Medium (12mm)", 36),
    LARGE("Large (18mm)", 54),
    CUSTOM("Custom", 24)
}

enum class ImageFitOption(val displayName: String) {
    FIT_PAGE("Fit to Page"),
    FILL_PAGE("Fill Page"),
    ORIGINAL_SIZE("Original Size"),
    STRETCH("Stretch to Page"),
    CENTER("Center Image")
}

enum class ImageQualityOption(val displayName: String, val compressionQuality: Int) {
    LOW("Low (Fastest)", 40),
    MEDIUM("Medium (Balanced)", 70),
    HIGH("High (Sharp)", 90),
    ORIGINAL("Original Quality", 100)
}

enum class DocumentFilter(val displayName: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    DOCUMENT("Document"),
    BLACK_WHITE("Black & White"),
    GRAYSCALE("Grayscale"),
    MAGIC_COLOR("Magic Color"),
    LIGHT("Light"),
    DARK("Dark"),
    HIGH_CONTRAST("High Contrast"),
    PHOTO("Photo"),
    SHARPEN("Sharpen")
}

enum class ScanMode(val displayName: String) {
    BATCH("Batch Mode"),
    DOCUMENT("Single Page"),
    ID_CARD("ID Card"),
    PASSPORT("Passport"),
    BOOK("Book"),
    RECEIPT("Receipt"),
    NOTES("Notes")
}

enum class CompressionLevel(val displayName: String, val scaleFactor: Float, val quality: Int) {
    LOW("Low Compression (Best Quality)", 0.9f, 85),
    MEDIUM("Medium Compression (Recommended)", 0.75f, 65),
    HIGH("High Compression (Smallest Size)", 0.55f, 45)
}

enum class SourceType(val displayName: String) {
    CAMERA("Camera Scan"),
    GALLERY("Image to PDF"),
    CONVERTED("Document Converted"),
    MERGED("Merged PDF"),
    SPLIT("Split PDF"),
    EDITED("PDF Editor"),
    ORGANIZED("Organized PDF"),
    IMPORTED("Imported File")
}
