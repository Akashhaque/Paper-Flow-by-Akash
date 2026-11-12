package com.example.data.local

import androidx.room.*
import com.example.data.model.ConversionHistory
import com.example.data.model.Folder
import com.example.data.model.PDFFile
import com.example.data.model.RecentFile
import kotlinx.coroutines.flow.Flow

@Dao
interface PDFFileDao {
    @Query("SELECT * FROM pdf_files ORDER BY modifiedAt DESC")
    fun getAllFiles(): Flow<List<PDFFile>>

    @Query("SELECT * FROM pdf_files WHERE isFavorite = 1 ORDER BY modifiedAt DESC")
    fun getFavoriteFiles(): Flow<List<PDFFile>>

    @Query("SELECT * FROM pdf_files WHERE folderId = :folderId ORDER BY modifiedAt DESC")
    fun getFilesByFolder(folderId: Long): Flow<List<PDFFile>>

    @Query("SELECT p.* FROM pdf_files p INNER JOIN recent_files r ON p.id = r.pdfFileId ORDER BY r.lastOpenedAt DESC LIMIT 20")
    fun getRecentFiles(): Flow<List<PDFFile>>

    @Query("SELECT * FROM pdf_files WHERE fileName LIKE '%' || :query || '%' ORDER BY modifiedAt DESC")
    fun searchFiles(query: String): Flow<List<PDFFile>>

    @Query("SELECT * FROM pdf_files WHERE id = :id")
    suspend fun getFileById(id: Long): PDFFile?

    @Query("SELECT * FROM pdf_files WHERE filePath = :path LIMIT 1")
    suspend fun getFileByPath(path: String): PDFFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: PDFFile): Long

    @Update
    suspend fun updateFile(file: PDFFile)

    @Delete
    suspend fun deleteFile(file: PDFFile)

    @Query("DELETE FROM pdf_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY folderName ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)
}

@Dao
interface RecentFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentFile)

    @Query("DELETE FROM recent_files WHERE pdfFileId = :pdfFileId")
    suspend fun removeRecent(pdfFileId: Long)

    @Query("DELETE FROM recent_files")
    suspend fun clearRecents()
}

@Dao
interface ConversionHistoryDao {
    @Query("SELECT * FROM conversion_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<ConversionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ConversionHistory): Long

    @Query("DELETE FROM conversion_history")
    suspend fun clearHistory()
}
