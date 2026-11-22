package com.cl0udshifter.pdfeasy.core.data.repository

import com.cl0udshifter.pdfeasy.core.data.local.BookmarkDao
import com.cl0udshifter.pdfeasy.core.data.local.BookmarkEntity
import com.cl0udshifter.pdfeasy.core.data.local.DocumentWithProgressRow
import com.cl0udshifter.pdfeasy.core.data.local.LibraryDao
import com.cl0udshifter.pdfeasy.core.data.local.PdfDocumentEntity
import com.cl0udshifter.pdfeasy.core.data.local.ReadingProgressDao
import com.cl0udshifter.pdfeasy.core.data.local.ReadingProgressEntity
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderDefaults
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderPreferencesStore
import com.cl0udshifter.pdfeasy.core.data.saf.InspectedUriDocument
import com.cl0udshifter.pdfeasy.core.data.saf.UriDocumentInspector
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepositoriesTest {
    @Test
    fun `registerOrUpdateDocument catalogs new uri and preserves metadata on re-register`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val libraryDao = FakeLibraryDao()
            val inspector =
                FakeUriInspector(
                    inspectResult = {
                        CoreResult.Success(InspectedUriDocument(displayName = "Guide.pdf", sizeBytes = 2048L))
                    },
                )
            var currentNow = 1_000L
            val repository = LibraryRepositoryImpl(libraryDao, inspector, dispatcher) { currentNow }

            val firstResult = repository.registerOrUpdateDocument("content://docs/guide.pdf")
            assertTrue(firstResult is CoreResult.Success)
            val created = (firstResult as CoreResult.Success).data
            assertEquals(DocumentIdGenerator.fromUri("content://docs/guide.pdf"), created.id)
            assertEquals("Guide.pdf", created.displayName)
            assertEquals(0, created.pageCount)

            repository.updateDocumentMetadata(
                documentId = created.id,
                pageCount = 120,
                isPasswordProtected = true,
                lastOpenedAtEpochMillis = 2_000L,
            )

            currentNow = 3_000L
            inspector.inspectResult = {
                CoreResult.Success(InspectedUriDocument(displayName = "Guide_Renamed.pdf", sizeBytes = 4096L))
            }
            val secondResult = repository.registerOrUpdateDocument("content://docs/guide.pdf")
            val updated = (secondResult as CoreResult.Success).data

            assertEquals("Guide_Renamed.pdf", updated.displayName)
            assertEquals(4096L, updated.sizeBytes)
            assertEquals(120, updated.pageCount)
            assertTrue(updated.isPasswordProtected)
            assertEquals(1_000L, updated.addedAtEpochMillis)
            assertEquals(2_000L, updated.lastOpenedAtEpochMillis)
        }

    @Test
    fun `registerOrUpdateDocument propagates inspector failure without inserting row`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val libraryDao = FakeLibraryDao()
            val inspector = FakeUriInspector(inspectResult = { CoreResult.Failure(PdfCoreError.PermissionDenied) })
            val repository = LibraryRepositoryImpl(libraryDao, inspector, dispatcher)

            val result = repository.registerOrUpdateDocument("content://docs/locked.pdf")
            assertEquals(CoreResult.Failure(PdfCoreError.PermissionDenied), result)
            assertTrue(libraryDao.getAll().isEmpty())
        }

    @Test
    fun `refreshAvailability updates moved and revoked documents and removeFromLibrary releases permission`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val libraryDao = FakeLibraryDao()
            val inspector =
                FakeUriInspector(
                    inspectResult = { CoreResult.Success(InspectedUriDocument("File.pdf", 500L)) },
                )
            val repository = LibraryRepositoryImpl(libraryDao, inspector, dispatcher)

            val doc1 = (repository.registerOrUpdateDocument("content://docs/1.pdf") as CoreResult.Success).data
            val doc2 = (repository.registerOrUpdateDocument("content://docs/2.pdf") as CoreResult.Success).data

            inspector.availabilityByUri["content://docs/1.pdf"] = DocumentAvailability.MISSING_OR_MOVED
            inspector.availabilityByUri["content://docs/2.pdf"] = DocumentAvailability.PERMISSION_REVOKED

            repository.refreshAvailability()
            val items = repository.observeLibrary().first()
            val byId = items.associateBy { it.document.id }
            assertEquals(DocumentAvailability.MISSING_OR_MOVED, byId[doc1.id]?.document?.availability)
            assertEquals(DocumentAvailability.PERMISSION_REVOKED, byId[doc2.id]?.document?.availability)

            repository.removeFromLibrary(doc1.id)
            assertEquals(listOf("content://docs/1.pdf"), inspector.releasedUris)
            assertNull(libraryDao.getById(doc1.id))
        }

    @Test
    fun `observeLibrary escapes SQL LIKE wildcards in search query`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val libraryDao = FakeLibraryDao()
            val inspector =
                FakeUriInspector(
                    inspectResult = { uri ->
                        val name = if (uri.endsWith("1")) "100%_report.pdf" else "normal_report.pdf"
                        CoreResult.Success(InspectedUriDocument(name, 100L))
                    },
                )
            val repository = LibraryRepositoryImpl(libraryDao, inspector, dispatcher)
            repository.registerOrUpdateDocument("content://docs/1")
            repository.registerOrUpdateDocument("content://docs/2")

            val filtered = repository.observeLibrary("100%_").first()
            assertEquals(1, filtered.size)
            assertEquals("100%_report.pdf", filtered.first().document.displayName)
        }

    @Test
    fun `reading progress repository saves immediately and updates global reader defaults`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val progressDao = FakeReadingProgressDao()
            val prefsStore = FakeReaderPreferencesStore()
            val repository = ReadingProgressRepositoryImpl(progressDao, prefsStore, dispatcher)

            assertNull(repository.getProgress("doc-1"))

            val saved =
                ReadingProgress(
                    documentId = "doc-1",
                    currentPageIndex = 7,
                    totalPages = 42,
                    scrollOffsetNormalized = 0.35f,
                    zoomScale = 1.75f,
                    scrollMode = ScrollMode.CONTINUOUS_HORIZONTAL,
                    renderTheme = PageRenderTheme.SEPIA,
                    updatedAtEpochMillis = 5_000L,
                )
            repository.saveProgress(saved)

            assertEquals(saved, repository.getProgress("doc-1"))
            assertEquals(saved, repository.observeProgress("doc-1").first())
            assertEquals(
                ReaderDefaults(PageRenderTheme.SEPIA, ScrollMode.CONTINUOUS_HORIZONTAL),
                prefsStore.getDefaults(),
            )
        }

    @Test
    fun `bookmark repository toggles sorts and deletes bookmarks`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val bookmarkDao = FakeBookmarkDao()
            val repository = BookmarkRepositoryImpl(bookmarkDao, dispatcher) { 9_000L }

            assertTrue(repository.toggleBookmark("doc-1", pageIndex = 10, label = "  Chapter 2  "))
            assertTrue(repository.toggleBookmark("doc-1", pageIndex = 2, label = "   "))
            assertTrue(repository.observeIsBookmarked("doc-1", 10).first())

            val ordered = repository.observeBookmarks("doc-1").first()
            assertEquals(listOf(2, 10), ordered.map { it.pageIndex })
            assertNull(ordered[0].label)
            assertEquals("Chapter 2", ordered[1].label)

            assertFalse(repository.toggleBookmark("doc-1", pageIndex = 10))
            assertFalse(repository.observeIsBookmarked("doc-1", 10).first())

            val remaining = repository.observeBookmarks("doc-1").first()
            assertEquals(1, remaining.size)
            repository.deleteBookmark(remaining.first().id)
            assertTrue(repository.observeBookmarks("doc-1").first().isEmpty())
        }

    private class FakeUriInspector(
        var inspectResult: (String) -> CoreResult<InspectedUriDocument>,
    ) : UriDocumentInspector {
        val availabilityByUri = mutableMapOf<String, DocumentAvailability>()
        val releasedUris = mutableListOf<String>()

        override suspend fun inspectAndPersist(uriString: String): CoreResult<InspectedUriDocument> =
            inspectResult(uriString)

        override suspend fun checkAvailability(uriString: String): DocumentAvailability =
            availabilityByUri[uriString] ?: DocumentAvailability.AVAILABLE

        override suspend fun releasePermission(uriString: String) {
            releasedUris.add(uriString)
        }
    }

    private class FakeReaderPreferencesStore : ReaderPreferencesStore {
        private val state = MutableStateFlow(ReaderDefaults())
        override val defaults: Flow<ReaderDefaults> = state

        override suspend fun getDefaults(): ReaderDefaults = state.value

        override suspend fun updateDefaults(
            renderTheme: PageRenderTheme,
            scrollMode: ScrollMode,
        ) {
            state.value = ReaderDefaults(renderTheme, scrollMode)
        }
    }

    private class FakeLibraryDao : LibraryDao {
        private val docs = MutableStateFlow<Map<String, PdfDocumentEntity>>(emptyMap())
        private val progress = MutableStateFlow<Map<String, ReadingProgressEntity>>(emptyMap())

        override fun observeLibrary(
            hasQuery: Boolean,
            likePattern: String,
        ): Flow<List<DocumentWithProgressRow>> =
            combine(docs, progress) { docsMap, progressMap ->
                val regex = if (hasQuery) likePatternToRegex(likePattern) else null
                docsMap.values
                    .filter { entity -> regex == null || regex.containsMatchIn(entity.displayName) }
                    .sortedByDescending { it.lastOpenedAtEpochMillis ?: it.addedAtEpochMillis }
                    .map { entity -> DocumentWithProgressRow(entity, progressMap[entity.id]) }
            }

        override suspend fun getById(documentId: String): PdfDocumentEntity? = docs.value[documentId]

        override suspend fun getAll(): List<PdfDocumentEntity> = docs.value.values.toList()

        override suspend fun insert(entity: PdfDocumentEntity) {
            docs.value = docs.value + (entity.id to entity)
        }

        override suspend fun update(entity: PdfDocumentEntity) {
            docs.value = docs.value + (entity.id to entity)
        }

        override suspend fun updateMetadata(
            documentId: String,
            pageCount: Int,
            isPasswordProtected: Boolean,
            lastOpenedAtEpochMillis: Long,
        ) {
            val current = docs.value[documentId] ?: return
            docs.value =
                docs.value + (
                    documentId to
                        current.copy(
                            pageCount = pageCount,
                            isPasswordProtected = isPasswordProtected,
                            lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
                        )
                    )
        }

        override suspend fun updateAvailability(
            documentId: String,
            availability: DocumentAvailability,
        ) {
            val current = docs.value[documentId] ?: return
            docs.value = docs.value + (documentId to current.copy(availability = availability))
        }

        override suspend fun deleteById(documentId: String) {
            docs.value = docs.value - documentId
            progress.value = progress.value - documentId
        }

        private fun likePatternToRegex(likePattern: String): Regex {
            val stripped = likePattern.removeSurrounding("%")
            val unescaped =
                stripped
                    .replace("\\%", "%")
                    .replace("\\_", "_")
                    .replace("\\\\", "\\")
            return Regex(Regex.escape(unescaped), RegexOption.IGNORE_CASE)
        }
    }

    private class FakeReadingProgressDao : ReadingProgressDao {
        private val entries = MutableStateFlow<Map<String, ReadingProgressEntity>>(emptyMap())

        override fun observeByDocumentId(documentId: String): Flow<ReadingProgressEntity?> =
            entries.map { it[documentId] }

        override suspend fun getByDocumentId(documentId: String): ReadingProgressEntity? = entries.value[documentId]

        override suspend fun upsert(entity: ReadingProgressEntity) {
            entries.value = entries.value + (entity.documentId to entity)
        }
    }

    private class FakeBookmarkDao : BookmarkDao {
        private val entries = MutableStateFlow<List<BookmarkEntity>>(emptyList())
        private var nextId = 1L

        override fun observeForDocument(documentId: String): Flow<List<BookmarkEntity>> =
            entries.map { list -> list.filter { it.documentId == documentId }.sortedBy { it.pageIndex } }

        override fun observeExists(
            documentId: String,
            pageIndex: Int,
        ): Flow<Boolean> =
            entries.map { list -> list.any { it.documentId == documentId && it.pageIndex == pageIndex } }

        override suspend fun getByDocumentAndPage(
            documentId: String,
            pageIndex: Int,
        ): BookmarkEntity? =
            entries.value.firstOrNull { it.documentId == documentId && it.pageIndex == pageIndex }

        override suspend fun insert(entity: BookmarkEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            val filtered = entries.value.filterNot {
                it.documentId == entity.documentId && it.pageIndex == entity.pageIndex
            }
            entries.value = filtered + entity.copy(id = id)
            return id
        }

        override suspend fun deleteById(bookmarkId: Long) {
            entries.value = entries.value.filterNot { it.id == bookmarkId }
        }
    }
}
