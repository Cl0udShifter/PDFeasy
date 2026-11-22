package com.cl0udshifter.pdfeasy.ui.reader

import com.cl0udshifter.pdfeasy.core.model.Bookmark
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch

data class PasswordPromptState(
    val isVisible: Boolean = false,
    val isInvalidAttempt: Boolean = false,
    val isSubmitting: Boolean = false,
)

data class ReaderSearchState(
    val isOpen: Boolean = false,
    val query: String = "",
    val matches: List<TextSearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val isSearching: Boolean = false,
) {
    val activeMatch: TextSearchMatch?
        get() = matches.getOrNull(currentMatchIndex)
}

data class ScrollRestoreTarget(
    val pageIndex: Int,
    val offsetNormalized: Float,
    val zoomScale: Float,
    val targetMatch: TextSearchMatch? = null,
    val token: Long,
)

data class ReaderUiState(
    val documentId: String,
    val uriString: String,
    val displayName: String,
    val isLoading: Boolean = true,
    val pageCount: Int = 0,
    val pageDimensions: Map<Int, PageDimensions> = emptyMap(),
    val currentPageIndex: Int = 0,
    val scrollOffsetNormalized: Float = 0f,
    val zoomScale: Float = 1f,
    val scrollMode: ScrollMode = ScrollMode.CONTINUOUS_VERTICAL,
    val renderTheme: PageRenderTheme = PageRenderTheme.ORIGINAL,
    val keepScreenOn: Boolean = false,
    val hudVisible: Boolean = true,
    val isCurrentPageBookmarked: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(),
    val isBookmarksSheetOpen: Boolean = false,
    val search: ReaderSearchState = ReaderSearchState(),
    val passwordPrompt: PasswordPromptState = PasswordPromptState(),
    val fatalError: PdfCoreError? = null,
    val pendingScrollTarget: ScrollRestoreTarget? = null,
)
