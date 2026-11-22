package com.cl0udshifter.pdfeasy.ui.library

import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.core.model.PdfDocumentItem
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
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
    fun `search query debounces and filters while preserving total and unavailable counts`() =
        runTest(testDispatcher) {
            val repo = FakeLibraryRepository(sampleItems())
            val viewModel = LibraryViewModel(libraryRepository = repo, debounceMillis = DEBOUNCE_MS)
            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(TOTAL_SAMPLE_DOCS, viewModel.uiState.value.totalDocumentCount)
            assertEquals(1, viewModel.uiState.value.unavailableCount)
            assertEquals(TOTAL_SAMPLE_DOCS, viewModel.uiState.value.items.size)

            viewModel.onSearchQueryChange("Contrato")
            advanceTimeBy(DEBOUNCE_MS + 1L)
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.items.size)
            assertEquals("doc-2", viewModel.uiState.value.items.first().document.id)
            assertEquals(TOTAL_SAMPLE_DOCS, viewModel.uiState.value.totalDocumentCount)

            viewModel.onClearSearch()
            advanceTimeBy(DEBOUNCE_MS + 1L)
            advanceUntilIdle()
            assertEquals(TOTAL_SAMPLE_DOCS, viewModel.uiState.value.items.size)

            collectJob.cancel()
        }

    @Test
    fun `filters reading and unavailable items and resets unavailable filter when count reaches zero`() =
        runTest(testDispatcher) {
            val repo = FakeLibraryRepository(sampleItems())
            val viewModel = LibraryViewModel(libraryRepository = repo, debounceMillis = 0L)
            val collectJob = launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onFilterSelected(LibraryFilter.READING)
            advanceUntilIdle()
            assertEquals(listOf("doc-1", "doc-3"), viewModel.uiState.value.items.map { it.document.id })

            viewModel.onFilterSelected(LibraryFilter.UNAVAILABLE)
            advanceUntilIdle()
            assertEquals(listOf("doc-3"), viewModel.uiState.value.items.map { it.document.id })

            viewModel.removeFromLibrary("doc-3")
            advanceUntilIdle()
            assertEquals(0, viewModel.uiState.value.unavailableCount)
            assertEquals(LibraryFilter.ALL, viewModel.uiState.value.activeFilter)

            collectJob.cancel()
        }

    @Test
    fun `clicking available opens reader while unavailable opens actions sheet`() =
        runTest(testDispatcher) {
            val items = sampleItems()
            val repo = FakeLibraryRepository(items)
            val viewModel = LibraryViewModel(libraryRepository = repo, debounceMillis = 0L)
            val events = mutableListOf<LibraryEvent>()
            val stateJob = launch { viewModel.uiState.collect {} }
            val eventJob = launch { viewModel.events.collect(events::add) }
            advanceUntilIdle()

            viewModel.onDocumentClicked(items[0])
            advanceUntilIdle()
            assertEquals(1, events.size)
            assertEquals("doc-1", (events.first() as LibraryEvent.OpenReader).documentId)

            viewModel.onDocumentClicked(items[2])
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.selectedDocumentForSheet)
            assertEquals("doc-3", viewModel.uiState.value.selectedDocumentForSheet?.document?.id)

            stateJob.cancel()
            eventJob.cancel()
        }

    @Test
    fun `relinking document registers new uri removes stale entry and emits open event`() =
        runTest(testDispatcher) {
            val repo = FakeLibraryRepository(sampleItems())
            val viewModel = LibraryViewModel(libraryRepository = repo, debounceMillis = 0L)
            val events = mutableListOf<LibraryEvent>()
            val stateJob = launch { viewModel.uiState.collect {} }
            val eventJob = launch { viewModel.events.collect(events::add) }
            advanceUntilIdle()

            viewModel.onDocumentPicked(uriString = "content://new/relinked.pdf", replacingDocumentId = "doc-3")
            advanceUntilIdle()

            assertEquals(listOf("doc-3"), repo.removedIds)
            assertTrue(events.any { it is LibraryEvent.OpenReader && it.uriString == "content://new/relinked.pdf" })

            repo.nextRegisterResult = CoreResult.Failure(PdfCoreError.PermissionDenied)
            viewModel.onDocumentPicked(uriString = "content://denied/locked.pdf")
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.errorMessage)

            viewModel.dismissError()
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.errorMessage)

            stateJob.cancel()
            eventJob.cancel()
        }

    private fun sampleItems(): List<LibraryItemWithProgress> =
        listOf(
            LibraryItemWithProgress(
                document =
                    PdfDocumentItem(
                        id = "doc-1",
                        uriString = "content://docs/1.pdf",
                        displayName = "Arquitetura Limpa.pdf",
                        sizeBytes = SIZE_BYTES_MEDIUM,
                        pageCount = PAGES_DOC_1,
                        isPasswordProtected = false,
                        availability = DocumentAvailability.AVAILABLE,
                        addedAtEpochMillis = 100L,
                        lastOpenedAtEpochMillis = 200L,
                    ),
                progress =
                    ReadingProgress(
                        documentId = "doc-1",
                        currentPageIndex = CURRENT_PAGE_DOC_1,
                        totalPages = PAGES_DOC_1,
                        scrollOffsetNormalized = 0.25f,
                        zoomScale = 1f,
                        scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                        renderTheme = PageRenderTheme.ORIGINAL,
                        updatedAtEpochMillis = 200L,
                    ),
            ),
            LibraryItemWithProgress(
                document =
                    PdfDocumentItem(
                        id = "doc-2",
                        uriString = "content://docs/2.pdf",
                        displayName = "Contrato Locacao.pdf",
                        sizeBytes = SIZE_BYTES_SMALL,
                        pageCount = PAGES_DOC_2,
                        isPasswordProtected = true,
                        availability = DocumentAvailability.AVAILABLE,
                        addedAtEpochMillis = 150L,
                        lastOpenedAtEpochMillis = null,
                    ),
                progress = null,
            ),
            LibraryItemWithProgress(
                document =
                    PdfDocumentItem(
                        id = "doc-3",
                        uriString = "content://docs/3.pdf",
                        displayName = "Manual Movido.pdf",
                        sizeBytes = SIZE_BYTES_LARGE,
                        pageCount = PAGES_DOC_3,
                        isPasswordProtected = false,
                        availability = DocumentAvailability.MISSING_OR_MOVED,
                        addedAtEpochMillis = 180L,
                        lastOpenedAtEpochMillis = 250L,
                    ),
                progress = null,
            ),
        )

    private class FakeLibraryRepository(
        initial: List<LibraryItemWithProgress>,
    ) : LibraryRepository {
        private val itemsFlow = MutableStateFlow(initial)
        val removedIds = mutableListOf<String>()
        var nextRegisterResult: CoreResult<PdfDocumentItem>? = null

        override fun observeLibrary(searchQuery: String): Flow<List<LibraryItemWithProgress>> =
            itemsFlow.map { list ->
                val trimmed = searchQuery.trim()
                if (trimmed.isEmpty()) {
                    list
                } else {
                    list.filter { it.document.displayName.contains(trimmed, ignoreCase = true) }
                }
            }

        override suspend fun registerOrUpdateDocument(uriString: String): CoreResult<PdfDocumentItem> {
            val forced = nextRegisterResult
            if (forced != null) return forced
            val created =
                PdfDocumentItem(
                    id = "new-${uriString.hashCode()}",
                    uriString = uriString,
                    displayName = "Relinked.pdf",
                    sizeBytes = SIZE_BYTES_SMALL,
                    pageCount = 0,
                    isPasswordProtected = false,
                    availability = DocumentAvailability.AVAILABLE,
                    addedAtEpochMillis = 500L,
                    lastOpenedAtEpochMillis = null,
                )
            itemsFlow.value = itemsFlow.value + LibraryItemWithProgress(created, null)
            return CoreResult.Success(created)
        }

        override suspend fun updateDocumentMetadata(
            documentId: String,
            pageCount: Int,
            isPasswordProtected: Boolean,
            lastOpenedAtEpochMillis: Long,
        ) = Unit

        override suspend fun refreshAvailability() = Unit

        override suspend fun removeFromLibrary(documentId: String) {
            removedIds.add(documentId)
            itemsFlow.value = itemsFlow.value.filterNot { it.document.id == documentId }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 100L
        const val TOTAL_SAMPLE_DOCS = 3
        const val PAGES_DOC_1 = 200
        const val CURRENT_PAGE_DOC_1 = 80
        const val PAGES_DOC_2 = 18
        const val PAGES_DOC_3 = 90
        const val SIZE_BYTES_SMALL = 840_000L
        const val SIZE_BYTES_MEDIUM = 6_400_000L
        const val SIZE_BYTES_LARGE = 14_800_000L
    }
}
