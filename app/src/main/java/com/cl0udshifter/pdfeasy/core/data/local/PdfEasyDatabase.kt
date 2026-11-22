package com.cl0udshifter.pdfeasy.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PdfDocumentEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(PdfEasyTypeConverters::class)
abstract class PdfEasyDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun readingProgressDao(): ReadingProgressDao

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        private const val DATABASE_NAME = "pdfeasy.db"

        fun create(context: Context): PdfEasyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PdfEasyDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
