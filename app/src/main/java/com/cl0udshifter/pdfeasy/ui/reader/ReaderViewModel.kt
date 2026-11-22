package com.cl0udshifter.pdfeasy.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderPreferencesStore
import com.cl0udshifter.pdfeasy.core.domain.BookmarkRepository
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.domain.ReadingProgressRepository
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.pdf.PdfEngine
import com.cl0udshifter.pdfeasy.core.pdf.PdfSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val documentId: String,
    private val uriString: String,
    initialDisplayName: String,
    private val pdfEngine: PdfEngine,
    private val libraryRepository: LibraryRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readerPreferencesStore: ReaderPreferencesStore,
    private val progressDebounceMs: Long = DEFAULT_PROGRESS_DEBOUNCE_MS,
    private val recycleDelayMs: Long = DEFAULT_RECYCLE_DELAY_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val uiStateMutable =
        MutableStateFlow(
            ReaderUiState(
                documentId = documentId,
                uriString = uriString,
                displayName = initialDisplayName,
            ),
        )
    val uiState: StateFlow<ReaderUiState> = uiStateMutable.asStateFlow()

    val bitmapController = ReaderBitmapController(viewModelScope, recycleDelayMs = recycleDelayMs)

    private var activeSession: PdfSession? = null
    private var dimensionsJob: Job? = null
    private var searchJob: Job? = null
    private var progressDebounceJob: Job? = null
    private var scrollTokenCounter = 0L

    init {
        observeBookmarksForDocument()
        openDocument(password = null)
    }

    fun openDocument(password: String? = null) {
        viewModelScope.launch {
            uiStateMutable.update {
                it.copy(
                    isLoading = password == null,
                    fatalError = null,
                    passwordPrompt =
                        if (password != null) {
                            it.passwordPrompt.copy(isSubmitting = true)
                        } else {
                            PasswordPromptState()
                        },
                )
            }

            when (val result = pdfEngine.openSession(uriString, password)) {
                is CoreResult.Success -> onSessionOpened(result.data, password != null)
                is CoreResult.Failure -> handleOpenFailure(result.error)
            }
        }
    }

    fun submitPassword(password: String) {
        if (password.isEmpty()) return
        openDocument(password = password)
    }

    fun toggleHud() {
        uiStateMutable.update { it.copy(hudVisible = !it.hudVisible) }
    }

    fun setRenderTheme(theme: PageRenderTheme) {
        uiStateMutable.update { it.copy(renderTheme = theme) }
        saveProgressImmediately()
    }

    fun toggleScrollMode() {
        val current = uiStateMutable.value
        val nextMode =
            if (current.scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
                ScrollMode.CONTINUOUS_HORIZONTAL
            } else {
                ScrollMode.CONTINUOUS_VERTICAL
            }
        val token = nextScrollToken()
        uiStateMutable.update {
            it.copy(
                scrollMode = nextMode,
                pendingScrollTarget =
                    ScrollRestoreTarget(
                        pageIndex = it.currentPageIndex,
                        offsetNormalized = it.scrollOffsetNormalized,
                        zoomScale = it.zoomScale,
                        token = token,
                    ),
            )
        }
        saveProgressImmediately()
    }

    fun onViewportScrolled(
        pageIndex: Int,
        offsetNormalized: Float,
        zoomScale: Float,
    ) {
        val state = uiStateMutable.value
        if (state.pageCount <= 0) return
        val safePage = pageIndex.coerceIn(0, state.pageCount - 1)
        val safeOffset = offsetNormalized.coerceIn(0f, 1f)
        val safeZoom = zoomScale.coerceIn(MIN_ZOOM, MAX_ZOOM)

        uiStateMutable.update {
            it.copy(
                currentPageIndex = safePage,
                scrollOffsetNormalized = safeOffset,
                zoomScale = safeZoom,
            )
        }
        scheduleDebouncedProgressSave()
    }

    fun jumpToPage(pageIndex: Int) {
        val state = uiStateMutable.value
        if (state.pageCount <= 0) return
        val safePage = pageIndex.coerceIn(0, state.pageCount - 1)
        val token = nextScrollToken()
        uiStateMutable.update {
            it.copy(
                currentPageIndex = safePage,
                scrollOffsetNormalized = 0f,
                pendingScrollTarget =
                    ScrollRestoreTarget(
                        pageIndex = safePage,
                        offsetNormalized = 0f,
                        zoomScale = it.zoomScale,
                        token = token,
                    ),
            )
        }
        saveProgressImmediately()
    }

    fun consumePendingScrollTarget(token: Long) {
        uiStateMutable.update { state ->
            if (state.pendingScrollTarget?.token == token) {
                state.copy(pendingScrollTarget = null)
            } else {
                state
            }
        }
    }

    fun syncVisibleTiles(
        visibleRange: IntRange,
        viewportWidthPx: Int,
        zoomScale: Float,
        primaryCrop: Pair<Int, NormalizedRect>?,
    ) {
        val session = activeSession ?: return
        bitmapController.syncVisiblePages(
            TileSyncRequest(
                session = session,
                visibleRange = visibleRange,
                pageDimensions = uiStateMutable.value.pageDimensions,
                viewportWidthPx = viewportWidthPx,
                zoomScale = zoomScale,
                primaryCrop = primaryCrop,
            ),
        )
    }

    fun toggleBookmarkOnCurrentPage() {
        val state = uiStateMutable.value
        if (state.pageCount <= 0) return
        viewModelScope.launch {
            bookmarkRepository.toggleBookmark(
                documentId = documentId,
                pageIndex = state.currentPageIndex,
                label = null,
            )
        }
    }

    fun openBookmarksSheet() {
        uiStateMutable.update { it.copy(isBookmarksSheetOpen = true) }
    }

    fun closeBookmarksSheet() {
        uiStateMutable.update { it.copy(isBookmarksSheetOpen = false) }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmarkId)
        }
    }

    fun openSearch() {
        uiStateMutable.update {
            it.copy(
                hudVisible = true,
                search = it.search.copy(isOpen = true),
            )
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        uiStateMutable.update {
            it.copy(search = ReaderSearchState())
        }
    }

    fun updateSearchQuery(query: String) {
        uiStateMutable.update {
            it.copy(search = it.search.copy(query = query))
        }
        runSearch(query)
    }

    fun nextSearchMatch() {
        val search = uiStateMutable.value.search
        if (search.matches.isEmpty()) return
        val nextIndex = (search.currentMatchIndex + 1) % search.matches.size
        selectSearchMatch(nextIndex)
    }

    fun previousSearchMatch() {
        val search = uiStateMutable.value.search
        if (search.matches.isEmpty()) return
        val prevIndex =
            if (search.currentMatchIndex <= 0) search.matches.lastIndex else search.currentMatchIndex - 1
        selectSearchMatch(prevIndex)
    }

    fun flushProgress() {
        saveProgressImmediately()
    }

    override fun onCleared() {
        super.onCleared()
        progressDebounceJob?.cancel()
        dimensionsJob?.cancel()
        searchJob?.cancel()
        bitmapController.releaseSessionBitmaps(activeSession)
        activeSession?.close()
        activeSession = null
    }

    private fun observeBookmarksForDocument() {
        viewModelScope.launch {
            bookmarkRepository.observeBookmarks(documentId).collect { list ->
                uiStateMutable.update { it.copy(bookmarks = list) }
            }
        }
        viewModelScope.launch {
            uiStateMutable
                .map { it.currentPageIndex }
                .distinctUntilChanged()
                .flatMapLatest { page -> bookmarkRepository.observeIsBookmarked(documentId, page) }
                .collect { marked ->
                    uiStateMutable.update { it.copy(isCurrentPageBookmarked = marked) }
                }
        }
    }

    private suspend fun onSessionOpened(
        session: PdfSession,
        passwordUsed: Boolean,
    ) {
        bitmapController.releaseSessionBitmaps(activeSession)
        activeSession?.close()
        activeSession = session

        val savedProgress = readingProgressRepository.getProgress(documentId)
        val defaults = readerPreferencesStore.getDefaults()
        val pageCount = session.pageCount
        val initialPage =
            if (pageCount > 0) {
                (savedProgress?.currentPageIndex ?: 0).coerceIn(0, pageCount - 1)
            } else {
                0
            }
        val initialOffset = (savedProgress?.scrollOffsetNormalized ?: 0f).coerceIn(0f, 1f)
        val initialZoom = (savedProgress?.zoomScale ?: MIN_ZOOM).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val initialTheme = savedProgress?.renderTheme ?: defaults.renderTheme
        val initialScrollMode = savedProgress?.scrollMode ?: defaults.scrollMode

        val initialDims = loadInitialWindowDimensions(session, initialPage)

        libraryRepository.updateDocumentMetadata(
            documentId = documentId,
            pageCount = pageCount,
            isPasswordProtected = passwordUsed,
            lastOpenedAtEpochMillis = nowMillis(),
        )

        val token = nextScrollToken()
        uiStateMutable.update {
            it.copy(
                isLoading = false,
                pageCount = pageCount,
                pageDimensions = initialDims,
                currentPageIndex = initialPage,
                scrollOffsetNormalized = initialOffset,
                zoomScale = initialZoom,
                scrollMode = initialScrollMode,
                renderTheme = initialTheme,
                passwordPrompt = PasswordPromptState(),
                fatalError = null,
                pendingScrollTarget =
                    if (pageCount > 0) {
                        ScrollRestoreTarget(
                            pageIndex = initialPage,
                            offsetNormalized = initialOffset,
                            zoomScale = initialZoom,
                            token = token,
                        )
                    } else {
                        null
                    },
            )
        }

        if (savedProgress == null && pageCount > 0) {
            saveProgressImmediately()
        }
        loadRemainingDimensions(session, initialDims.keys)
    }

    private suspend fun loadInitialWindowDimensions(
        session: PdfSession,
        centerPage: Int,
    ): Map<Int, PageDimensions> {
        if (session.pageCount <= 0) return emptyMap()
        val start = (centerPage - INITIAL_DIM_WINDOW).coerceAtLeast(0)
        val end = (centerPage + INITIAL_DIM_WINDOW).coerceAtMost(session.pageCount - 1)
        val loaded = LinkedHashMap<Int, PageDimensions>()
        for (page in start..end) {
            val result = session.getPageDimensions(page)
            if (result is CoreResult.Success) {
                loaded[page] = result.data
            }
        }
        return loaded
    }

    private fun loadRemainingDimensions(
        session: PdfSession,
        alreadyLoaded: Set<Int>,
    ) {
        dimensionsJob?.cancel()
        if (session.pageCount <= alreadyLoaded.size) return
        dimensionsJob =
            viewModelScope.launch {
                val batch = LinkedHashMap<Int, PageDimensions>()
                for (page in 0 until session.pageCount) {
                    if (page in alreadyLoaded) continue
                    val result = session.getPageDimensions(page)
                    if (result is CoreResult.Success) {
                        batch[page] = result.data
                    }
                    if (batch.size >= DIM_BATCH_SIZE) {
                        val snapshot = batch.toMap()
                        batch.clear()
                        uiStateMutable.update { it.copy(pageDimensions = it.pageDimensions + snapshot) }
                    }
                }
                if (batch.isNotEmpty()) {
                    val snapshot = batch.toMap()
                    uiStateMutable.update { it.copy(pageDimensions = it.pageDimensions + snapshot) }
                }
            }
    }

    private fun handleOpenFailure(error: com.cl0udshifter.pdfeasy.core.model.PdfCoreError) {
        when (error) {
            com.cl0udshifter.pdfeasy.core.model.PdfCoreError.PasswordRequired ->
                uiStateMutable.update {
                    it.copy(
                        isLoading = false,
                        passwordPrompt = PasswordPromptState(isVisible = true, isInvalidAttempt = false),
                    )
                }

            com.cl0udshifter.pdfeasy.core.model.PdfCoreError.InvalidPassword ->
                uiStateMutable.update {
                    it.copy(
                        isLoading = false,
                        passwordPrompt = PasswordPromptState(isVisible = true, isInvalidAttempt = true),
                    )
                }

            else ->
                uiStateMutable.update {
                    it.copy(
                        isLoading = false,
                        passwordPrompt = PasswordPromptState(),
                        fatalError = error,
                    )
                }
        }
    }

    private fun runSearch(rawQuery: String) {
        searchJob?.cancel()
        val trimmed = rawQuery.trim()
        val session = activeSession
        if (trimmed.isEmpty() || session == null) {
            uiStateMutable.update {
                it.copy(
                    search =
                        it.search.copy(
                            matches = emptyList(),
                            currentMatchIndex = -1,
                            isSearching = false,
                        ),
                )
            }
            return
        }

        uiStateMutable.update {
            it.copy(
                search =
                    it.search.copy(
                        matches = emptyList(),
                        currentMatchIndex = -1,
                        isSearching = true,
                    ),
            )
        }

        searchJob =
            viewModelScope.launch {
                session.search(trimmed).collect { match ->
                    var firstMatchArrived = false
                    uiStateMutable.update { state ->
                        val updatedMatches = state.search.matches + match
                        val nextCurrentIndex =
                            if (state.search.currentMatchIndex < 0) {
                                firstMatchArrived = true
                                0
                            } else {
                                state.search.currentMatchIndex
                            }
                        state.copy(
                            search =
                                state.search.copy(
                                    matches = updatedMatches,
                                    currentMatchIndex = nextCurrentIndex,
                                ),
                        )
                    }
                    if (firstMatchArrived) {
                        selectSearchMatch(0)
                    }
                }
                uiStateMutable.update { it.copy(search = it.search.copy(isSearching = false)) }
            }
    }

    private fun selectSearchMatch(index: Int) {
        val state = uiStateMutable.value
        val match = state.search.matches.getOrNull(index) ?: return
        val token = nextScrollToken()
        uiStateMutable.update {
            it.copy(
                currentPageIndex = match.pageIndex,
                search = it.search.copy(currentMatchIndex = index),
                pendingScrollTarget =
                    ScrollRestoreTarget(
                        pageIndex = match.pageIndex,
                        offsetNormalized = 0f,
                        zoomScale = it.zoomScale,
                        targetMatch = match,
                        token = token,
                    ),
            )
        }
        saveProgressImmediately()
    }

    private fun scheduleDebouncedProgressSave() {
        progressDebounceJob?.cancel()
        progressDebounceJob =
            viewModelScope.launch {
                delay(progressDebounceMs)
                persistCurrentProgress()
            }
    }

    private fun saveProgressImmediately() {
        progressDebounceJob?.cancel()
        viewModelScope.launch {
            persistCurrentProgress()
        }
    }

    private suspend fun persistCurrentProgress() {
        val state = uiStateMutable.value
        if (activeSession == null || state.pageCount <= 0) return
        readingProgressRepository.saveProgress(
            ReadingProgress(
                documentId = documentId,
                currentPageIndex = state.currentPageIndex.coerceIn(0, state.pageCount - 1),
                totalPages = state.pageCount,
                scrollOffsetNormalized = state.scrollOffsetNormalized.coerceIn(0f, 1f),
                zoomScale = state.zoomScale.coerceIn(MIN_ZOOM, MAX_ZOOM),
                scrollMode = state.scrollMode,
                renderTheme = state.renderTheme,
                updatedAtEpochMillis = nowMillis(),
            ),
        )
    }

    private fun nextScrollToken(): Long {
        scrollTokenCounter += 1L
        return scrollTokenCounter
    }

    companion object {
        const val MIN_ZOOM = 1.0f
        const val MAX_ZOOM = 4.0f
        private const val INITIAL_DIM_WINDOW = 2
        private const val DIM_BATCH_SIZE = 24
        private const val DEFAULT_PROGRESS_DEBOUNCE_MS = 350L
        private const val DEFAULT_RECYCLE_DELAY_MS = 32L

        fun provideFactory(
            documentId: String,
            uriString: String,
            displayName: String,
            pdfEngine: PdfEngine,
            libraryRepository: LibraryRepository,
            readingProgressRepository: ReadingProgressRepository,
            bookmarkRepository: BookmarkRepository,
            readerPreferencesStore: ReaderPreferencesStore,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(
                        documentId = documentId,
                        uriString = uriString,
                        initialDisplayName = displayName,
                        pdfEngine = pdfEngine,
                        libraryRepository = libraryRepository,
                        readingProgressRepository = readingProgressRepository,
                        bookmarkRepository = bookmarkRepository,
                        readerPreferencesStore = readerPreferencesStore,
                    ) as T
            }
    }
}
