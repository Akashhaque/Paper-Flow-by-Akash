package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_files",
    indices = [Index(value = ["pdfFileId"], unique = true)]
)
data class RecentFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfFileId: Long,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversion_history")
data class ConversionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceFileName: String,
    val sourceFormat: String,
    val outputFileName: String,
    val outputFilePath: String? = null,
    val conversionStatus: String = "SUCCESS",
    val errorReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class PDFSettings(
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultOrientation: PageOrientation = PageOrientation.PORTRAIT,
    val defaultImageQuality: ImageQualityOption = ImageQualityOption.HIGH,
    val defaultMargin: MarginOption = MarginOption.NONE,
    val defaultImageFit: ImageFitOption = ImageFitOption.FIT_PAGE,
    val compressionLevel: CompressionLevel = CompressionLevel.MEDIUM,
    val appLanguage: String = "en",
    val isDarkMode: Boolean = false
)
