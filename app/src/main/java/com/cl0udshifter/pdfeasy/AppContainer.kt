package com.cl0udshifter.pdfeasy

import android.content.Context
import com.cl0udshifter.pdfeasy.core.data.local.PdfEasyDatabase
import com.cl0udshifter.pdfeasy.core.data.preferences.DataStoreReaderPreferences
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderPreferencesStore
import com.cl0udshifter.pdfeasy.core.data.repository.BookmarkRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.repository.LibraryRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.repository.ReadingProgressRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.saf.AndroidUriDocumentInspector
import com.cl0udshifter.pdfeasy.core.data.saf.UriDocumentInspector
import com.cl0udshifter.pdfeasy.core.domain.BookmarkRepository
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.domain.ReadingProgressRepository
import com.cl0udshifter.pdfeasy.core.pdf.PdfEngine
import com.cl0udshifter.pdfeasy.core.pdf.PdfiumPdfEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Container manual de injecao de dependencias com inicializacao preguicosa para zero overhead no cold start.
 */
class AppContainer(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext: Context = context.applicationContext

    private val database: PdfEasyDatabase by lazy {
        PdfEasyDatabase.create(appContext)
    }

    private val uriInspector: UriDocumentInspector by lazy {
        AndroidUriDocumentInspector(appContext)
    }

    val readerPreferences: ReaderPreferencesStore by lazy {
        DataStoreReaderPreferences(appContext)
    }

    val pdfEngine: PdfEngine by lazy {
        PdfiumPdfEngine(appContext, ioDispatcher)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepositoryImpl(
            libraryDao = database.libraryDao(),
            uriInspector = uriInspector,
            ioDispatcher = ioDispatcher,
        )
    }

    val readingProgressRepository: ReadingProgressRepository by lazy {
        ReadingProgressRepositoryImpl(
            readingProgressDao = database.readingProgressDao(),
            preferencesStore = readerPreferences,
            ioDispatcher = ioDispatcher,
        )
    }

    val bookmarkRepository: BookmarkRepository by lazy {
        BookmarkRepositoryImpl(
            bookmarkDao = database.bookmarkDao(),
            ioDispatcher = ioDispatcher,
        )
    }
}
