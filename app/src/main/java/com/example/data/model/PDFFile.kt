package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_files")
data class PDFFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val pageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val sourceType: String = SourceType.IMPORTED.name,
    val folderId: Long? = null,
    val thumbnailPath: String? = null,
    val isPasswordProtected: Boolean = false
)
