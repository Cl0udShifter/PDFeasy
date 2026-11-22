package com.cl0udshifter.pdfeasy.ui.reader

import android.graphics.Bitmap
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderDefaults
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderPreferencesStore
import com.cl0udshifter.pdfeasy.core.domain.BookmarkRepository
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.domain.ReadingProgressRepository
import com.cl0udshifter.pdfeasy.core.model.Bookmark
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.core.model.PdfDocumentItem
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import com.cl0udshifter.pdfeasy.core.pdf.PdfEngine
import com.cl0udshifter.pdfeasy.core.pdf.PdfSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `password prompt transitions from required to invalid to unlocked session`() =
        runTest(testDispatcher) {
            val session = FakePdfSession(pageCount = PAGE_COUNT)
            val engine = FakePdfEngine(requiredPassword = "secret", session = session)
            val libraryRepo = FakeLibraryRepo()
            val progressRepo = FakeProgressRepo()
            val viewModel = createViewModel(engine, libraryRepo, progressRepo)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.passwordPrompt.isVisible)
            assertFalse(viewModel.uiState.value.passwordPrompt.isInvalidAttempt)

            viewModel.submitPassword("wrong")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.passwordPrompt.isVisible)
            assertTrue(viewModel.uiState.value.passwordPrompt.isInvalidAttempt)

            viewModel.submitPassword("secret")
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.passwordPrompt.isVisible)
            assertEquals(PAGE_COUNT, viewModel.uiState.value.pageCount)
            assertTrue(libraryRepo.lastPasswordProtected == true)
        }

    @Test
    fun `restores defaults debounces scroll and saves theme and scroll mode immediately`() =
        runTest(testDispatcher) {
            val session = FakePdfSession(pageCount = PAGE_COUNT)
            val engine = FakePdfEngine(requiredPassword = null, session = session)
            val progressRepo = FakeProgressRepo()
            val prefsStore =
                FakePreferencesStore(
                    ReaderDefaults(
                        renderTheme = PageRenderTheme.SEPIA,
                        scrollMode = ScrollMode.CONTINUOUS_HORIZONTAL,
                    ),
                )
            val viewModel =
                createViewModel(
                    engine = engine,
                    progressRepo = progressRepo,
                    prefsStore = prefsStore,
                )
            advanceUntilIdle()

            assertEquals(PageRenderTheme.SEPIA, viewModel.uiState.value.renderTheme)
            assertEquals(ScrollMode.CONTINUOUS_HORIZONTAL, viewModel.uiState.value.scrollMode)

            viewModel.onViewportScrolled(pageIndex = 4, offsetNormalized = 0.6f, zoomScale = 1.8f)
            advanceTimeBy(DEBOUNCE_MS / 2)
            val savesBeforeDebounce = progressRepo.saveCount
            advanceTimeBy(DEBOUNCE_MS)
            advanceUntilIdle()
            assertEquals(savesBeforeDebounce + 1, progressRepo.saveCount)
            assertEquals(4, progressRepo.latest?.currentPageIndex)
            assertEquals(1.8f, progressRepo.latest?.zoomScale ?: 0f, 0.001f)

            viewModel.setRenderTheme(PageRenderTheme.DARK)
            advanceUntilIdle()
            assertEquals(PageRenderTheme.DARK, progressRepo.latest?.renderTheme)

            viewModel.toggleScrollMode()
            advanceUntilIdle()
            assertEquals(ScrollMode.CONTINUOUS_VERTICAL, progressRepo.latest?.scrollMode)
            assertNotNull(viewModel.uiState.value.pendingScrollTarget)
        }

    @Test
    fun `search streams matches wraps navigation and bookmark toggling tracks current page`() =
        runTest(testDispatcher) {
            val matches =
                listOf(
                    TextSearchMatch(1, 0, "...renderizacao...", listOf(NormalizedRect(0.1f, 0.2f, 0.3f, 0.25f))),
                    TextSearchMatch(5, 0, "...renderizacao...", listOf(NormalizedRect(0.2f, 0.4f, 0.4f, 0.45f))),
                )
            val session = FakePdfSession(pageCount = PAGE_COUNT, searchMatches = matches)
            val engine = FakePdfEngine(requiredPassword = null, session = session)
            val bookmarkRepo = FakeBookmarkRepo()
            val viewModel = createViewModel(engine = engine, bookmarkRepo = bookmarkRepo)
            advanceUntilIdle()

            viewModel.openSearch()
            viewModel.updateSearchQuery("renderizacao")
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.search.matches.size)
            assertEquals(0, viewModel.uiState.value.search.currentMatchIndex)
            assertEquals(1, viewModel.uiState.value.currentPageIndex)

            viewModel.nextSearchMatch()
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.search.currentMatchIndex)
            assertEquals(5, viewModel.uiState.value.currentPageIndex)

            viewModel.previousSearchMatch()
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.search.currentMatchIndex)

            viewModel.toggleBookmarkOnCurrentPage()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isCurrentPageBookmarked)
            assertEquals(1, viewModel.uiState.value.bookmarks.size)

            viewModel.deleteBookmark(viewModel.uiState.value.bookmarks.first().id)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isCurrentPageBookmarked)

            viewModel.closeSearch()
            assertFalse(viewModel.uiState.value.search.isOpen)
            assertTrue(viewModel.uiState.value.search.matches.isEmpty())
        }

    private fun createViewModel(
        engine: PdfEngine,
        libraryRepo: LibraryRepository = FakeLibraryRepo(),
        progressRepo: ReadingProgressRepository = FakeProgressRepo(),
        bookmarkRepo: BookmarkRepository = FakeBookmarkRepo(),
        prefsStore: ReaderPreferencesStore = FakePreferencesStore(),
    ): ReaderViewModel =
        ReaderViewModel(
            documentId = "doc-1",
            uriString = "content://docs/1.pdf",
            initialDisplayName = "Arquitetura Limpa.pdf",
            pdfEngine = engine,
            libraryRepository = libraryRepo,
            readingProgressRepository = progressRepo,
            bookmarkRepository = bookmarkRepo,
            readerPreferencesStore = prefsStore,
            progressDebounceMs = DEBOUNCE_MS,
            recycleDelayMs = 0L,
            nowMillis = { 10_000L },
        )

    private class FakePdfEngine(
        private val requiredPassword: String?,
        private val session: PdfSession,
    ) : PdfEngine {
        override suspend fun openSession(
            uriString: String,
            password: String?,
        ): CoreResult<PdfSession> =
            when {
                requiredPassword == null -> CoreResult.Success(session)
                password == null -> CoreResult.Failure(PdfCoreError.PasswordRequired)
                password != requiredPassword -> CoreResult.Failure(PdfCoreError.InvalidPassword)
                else -> CoreResult.Success(session)
            }
    }

    private class FakePdfSession(
        override val pageCount: Int,
        private val searchMatches: List<TextSearchMatch> = emptyList(),
    ) : PdfSession {
        override val documentUri: String = "content://docs/1.pdf"
        var closed = false

        override suspend fun getPageDimensions(pageIndex: Int): CoreResult<PageDimensions> =
            CoreResult.Success(PageDimensions(pageIndex, widthPoints = 600, heightPoints = 800))

        override suspend fun renderTile(request: TileRequest): CoreResult<Bitmap> =
            CoreResult.Failure(PdfCoreError.IoFailure("Not used in JVM unit test"))

        override fun recycleBitmap(bitmap: Bitmap) = Unit

        override fun search(query: String): Flow<TextSearchMatch> =
            flow {
                searchMatches.forEach { emit(it) }
            }

        override fun close() {
            closed = true
        }
    }

    private class FakeLibraryRepo : LibraryRepository {
        var lastPasswordProtected: Boolean? = null

        override fun observeLibrary(searchQuery: String): Flow<List<LibraryItemWithProgress>> =
            MutableStateFlow(emptyList())

        override suspend fun registerOrUpdateDocument(uriString: String): CoreResult<PdfDocumentItem> =
            CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)

        override suspend fun updateDocumentMetadata(
            documentId: String,
            pageCount: Int,
            isPasswordProtected: Boolean,
            lastOpenedAtEpochMillis: Long,
        ) {
            lastPasswordProtected = isPasswordProtected
        }

        override suspend fun refreshAvailability() = Unit

        override suspend fun removeFromLibrary(documentId: String) = Unit
    }

    private class FakeProgressRepo : ReadingProgressRepository {
        private val flowState = MutableStateFlow<ReadingProgress?>(null)
        var latest: ReadingProgress? = null
        var saveCount: Int = 0

        override fun observeProgress(documentId: String): Flow<ReadingProgress?> = flowState

        override suspend fun getProgress(documentId: String): ReadingProgress? = latest

        override suspend fun saveProgress(progress: ReadingProgress) {
            saveCount += 1
            latest = progress
            flowState.value = progress
        }
    }

    private class FakeBookmarkRepo : BookmarkRepository {
        private val entries = MutableStateFlow<List<Bookmark>>(emptyList())
        private var nextId = 1L

        override fun observeBookmarks(documentId: String): Flow<List<Bookmark>> = entries

        override fun observeIsBookmarked(
            documentId: String,
            pageIndex: Int,
        ): Flow<Boolean> = entries.map { list -> list.any { it.pageIndex == pageIndex } }

        override suspend fun toggleBookmark(
            documentId: String,
            pageIndex: Int,
            label: String?,
        ): Boolean {
            val existing = entries.value.firstOrNull { it.pageIndex == pageIndex }
            return if (existing != null) {
                entries.value = entries.value.filterNot { it.id == existing.id }
                false
            } else {
                entries.value =
                    entries.value + Bookmark(nextId++, documentId, pageIndex, label, createdAtEpochMillis = 100L)
                true
            }
        }

        override suspend fun deleteBookmark(bookmarkId: Long) {
            entries.value = entries.value.filterNot { it.id == bookmarkId }
        }
    }

    private class FakePreferencesStore(
        initial: ReaderDefaults = ReaderDefaults(),
    ) : ReaderPreferencesStore {
        private val state = MutableStateFlow(initial)
        override val defaults: Flow<ReaderDefaults> = state

        override suspend fun getDefaults(): ReaderDefaults = state.value

        override suspend fun updateDefaults(
            renderTheme: PageRenderTheme,
            scrollMode: ScrollMode,
        ) {
            state.value = ReaderDefaults(renderTheme, scrollMode)
        }
    }

    private companion object {
        const val PAGE_COUNT = 12
        const val DEBOUNCE_MS = 200L
    }
}
