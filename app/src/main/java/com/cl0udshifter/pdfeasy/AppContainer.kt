package com.cl0udshifter.pdfeasy

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cl0udshifter.pdfeasy.core.data.local.PdfEasyDatabase
import com.cl0udshifter.pdfeasy.core.data.preferences.DataStoreReaderPreferences
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderPreferencesStore
import com.cl0udshifter.pdfeasy.core.data.preferences.readerPreferencesDataStore
import com.cl0udshifter.pdfeasy.core.data.repository.BookmarkRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.repository.LibraryRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.repository.ReadingProgressRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.repository.SettingsRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.saf.AndroidUriDocumentInspector
import com.cl0udshifter.pdfeasy.core.data.saf.UriDocumentInspector
import com.cl0udshifter.pdfeasy.core.data.update.AndroidApkCacheStorage
import com.cl0udshifter.pdfeasy.core.data.update.AppUpdateRepositoryImpl
import com.cl0udshifter.pdfeasy.core.data.update.HttpGitHubReleaseRemoteDataSource
import com.cl0udshifter.pdfeasy.core.data.update.InstalledAppVersion
import com.cl0udshifter.pdfeasy.core.domain.AppUpdateRepository
import com.cl0udshifter.pdfeasy.core.domain.BookmarkRepository
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.domain.ReadingProgressRepository
import com.cl0udshifter.pdfeasy.core.domain.SettingsRepository
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

    private val preferencesDataStore: DataStore<Preferences> by lazy {
        appContext.readerPreferencesDataStore
    }

    val readerPreferences: ReaderPreferencesStore by lazy {
        DataStoreReaderPreferences(preferencesDataStore)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(
            dataStore = preferencesDataStore,
            ioDispatcher = ioDispatcher,
        )
    }

    val appUpdateRepository: AppUpdateRepository by lazy {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        val versionName = packageInfo.versionName ?: "0.0.0"
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        AppUpdateRepositoryImpl(
            installedVersion = InstalledAppVersion(versionName = versionName, versionCode = versionCode),
            remoteDataSource = HttpGitHubReleaseRemoteDataSource(userAgent = "PDFeasy-Android/$versionName"),
            apkStorage = AndroidApkCacheStorage(appContext),
            settingsRepository = settingsRepository,
            ioDispatcher = ioDispatcher,
        )
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
