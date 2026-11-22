package com.cl0udshifter.pdfeasy

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cl0udshifter.pdfeasy.core.data.local.PdfEasyDatabase
import com.cl0udshifter.pdfeasy.core.data.local.ReadingProgressEntity
import com.cl0udshifter.pdfeasy.core.data.preferences.DataStoreReaderPreferences
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderDefaults
import com.cl0udshifter.pdfeasy.core.data.update.AndroidApkCacheStorage
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.pdf.AndroidBitmapPool
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfEasyDatabaseAndAndroidTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: PdfEasyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, PdfEasyDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sqliteCascadeDeleteRemovesProgressAndBookmarksWhenDocumentIsDeleted() =
        runBlocking {
            val libraryDao = database.libraryDao()
            val progressDao = database.readingProgressDao()
            val bookmarkDao = database.bookmarkDao()

            val doc =
                libraryDao.upsertPreservingMetadata(
                    id = "doc-cascade",
                    uriString = "content://docs/cascade.pdf",
                    displayName = "Cascade.pdf",
                    sizeBytes = 1024L,
                    addedAtEpochMillis = 100L,
                )
            progressDao.upsert(
                ReadingProgressEntity(
                    documentId = doc.id,
                    currentPageIndex = 3,
                    totalPages = 20,
                    scrollOffsetNormalized = 0.25f,
                    zoomScale = 1.5f,
                    scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                    renderTheme = PageRenderTheme.DARK,
                    updatedAtEpochMillis = 200L,
                ),
            )
            bookmarkDao.toggle(doc.id, pageIndex = 3, label = "Important", createdAtEpochMillis = 300L)

            // Re-upserting existing document must NOT trigger CASCADE DELETE
            libraryDao.upsertPreservingMetadata(
                id = doc.id,
                uriString = doc.uriString,
                displayName = "Cascade_Updated.pdf",
                sizeBytes = 2048L,
                addedAtEpochMillis = 400L,
            )
            assertNotNull(progressDao.getByDocumentId(doc.id))
            assertEquals(1, bookmarkDao.observeForDocument(doc.id).first().size)

            libraryDao.deleteById(doc.id)
            assertNull(progressDao.getByDocumentId(doc.id))
            assertTrue(bookmarkDao.observeForDocument(doc.id).first().isEmpty())
        }

    @Test
    fun sqliteLibraryLikeQueryEscapesWildcardsAndOrdersByRecentRead() =
        runBlocking {
            val libraryDao = database.libraryDao()
            libraryDao.upsertPreservingMetadata(
                id = "doc-a",
                uriString = "content://docs/a",
                displayName = "50%_Specs.pdf",
                sizeBytes = 100L,
                addedAtEpochMillis = 100L,
            )
            libraryDao.upsertPreservingMetadata(
                id = "doc-b",
                uriString = "content://docs/b",
                displayName = "500_Specs.pdf",
                sizeBytes = 100L,
                addedAtEpochMillis = 200L,
            )
            libraryDao.updateMetadata(
                documentId = "doc-a",
                pageCount = 10,
                isPasswordProtected = false,
                lastOpenedAtEpochMillis = 500L,
            )

            val allRows = libraryDao.observeLibrary(hasQuery = false, likePattern = "").first()
            assertEquals(listOf("doc-a", "doc-b"), allRows.map { it.document.id })

            val filtered = libraryDao.observeLibrary(hasQuery = true, likePattern = "%50\\%\\_%").first()
            assertEquals(listOf("doc-a"), filtered.map { it.document.id })
        }

    @Test
    fun androidBitmapPoolAcquiresRecyclesReusesAndClearsBitmaps() {
        val pool = AndroidBitmapPool(maxBytes = 4L * 1024L * 1024L)
        val first = pool.acquire(128, 128)
        assertEquals(Bitmap.Config.ARGB_8888, first.config)
        assertEquals(Color.WHITE, first.getPixel(0, 0))

        first.setPixel(0, 0, Color.BLACK)
        pool.recycle(first)
        assertFalse(first.isRecycled)

        val reused = pool.acquire(128, 128)
        assertSame(first, reused)
        assertEquals(Color.WHITE, reused.getPixel(0, 0))

        pool.recycle(reused)
        pool.clear()
        assertTrue(reused.isRecycled)
    }

    @Test
    fun dataStoreReaderPreferencesPersistsDefaults() =
        runBlocking {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    tempFolder.newFile("test_reader.preferences_pb")
                }
            val store = DataStoreReaderPreferences(dataStore)
            assertEquals(ReaderDefaults(), store.getDefaults())

            store.updateDefaults(PageRenderTheme.DARK, ScrollMode.CONTINUOUS_HORIZONTAL)
            assertEquals(
                ReaderDefaults(PageRenderTheme.DARK, ScrollMode.CONTINUOUS_HORIZONTAL),
                store.getDefaults(),
            )
        }

    @Test
    fun appContainerExposesAllCoreDependenciesLazily() {
        val container = AppContainer(context)
        assertNotNull(container.libraryRepository)
        assertNotNull(container.readingProgressRepository)
        assertNotNull(container.bookmarkRepository)
        assertNotNull(container.readerPreferences)
        assertNotNull(container.settingsRepository)
        assertNotNull(container.appUpdateRepository)
        assertNotNull(container.pdfEngine)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(packageInfo.versionName, container.appUpdateRepository.currentVersionName)
        assertEquals(
            PackageInfoCompat.getLongVersionCode(packageInfo),
            container.appUpdateRepository.currentVersionCode,
        )
    }

    @Test
    fun androidApkCacheStorageSanitizesAndClearsAndManifestRegistersFileProvider() {
        val providerInfo =
            context.packageManager.resolveContentProvider(
                "${context.packageName}.fileprovider",
                PackageManager.GET_META_DATA,
            )
        assertNotNull(providerInfo)
        assertNotNull(providerInfo?.metaData?.getInt("android.support.FILE_PROVIDER_PATHS"))

        val storage =
            AndroidApkCacheStorage(context) { appCtx, file ->
                "content://${appCtx.packageName}.fileprovider/apk_updates/${file.name}"
            }
        val partFile = storage.preparePartFile("../unsafe/PDFeasy-v0.1.2.apk")
        assertEquals("PDFeasy-v0.1.2.apk.part", partFile.name)
        partFile.writeBytes(byteArrayOf(1, 2, 3))

        val finalFile = storage.finalizeApkFile(partFile, "PDFeasy-v0.1.2.apk")
        val uri = storage.contentUriForFile(finalFile)
        assertTrue(uri.startsWith("content://com.cl0udshifter.pdfeasy.fileprovider/apk_updates/"))
        assertTrue(finalFile.exists())

        storage.clearAll()
        assertFalse(finalFile.exists())
    }
}
