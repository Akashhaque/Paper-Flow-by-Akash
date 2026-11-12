package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.ConversionHistory
import com.example.data.model.Folder
import com.example.data.model.PDFFile
import com.example.data.model.RecentFile

@Database(
    entities = [PDFFile::class, RecentFile::class, Folder::class, ConversionHistory::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfFileDao(): PDFFileDao
    abstract fun folderDao(): FolderDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun conversionHistoryDao(): ConversionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paperflow_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
