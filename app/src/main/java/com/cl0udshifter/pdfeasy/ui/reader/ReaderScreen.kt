package com.cl0udshifter.pdfeasy.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens
import com.cl0udshifter.pdfeasy.ui.theme.ReaderRenderColorFilters

private object ReaderScreenDimens {
    val LoadingGap = 14.dp
    val LoadingIndicatorSize = 28.dp
    val LoadingStroke = 2.5.dp
}

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }

    ObserveReaderLifecycle(viewModel = viewModel, keepScreenOn = uiState.keepScreenOn)
    BackHandler(enabled = uiState.search.isOpen) { viewModel.closeSearch() }

    val hudCallbacks =
        remember(viewModel, onNavigateBack, onOpenSettings) {
            buildReaderHudCallbacks(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack,
                onOpenSettings = onOpenSettings,
            )
        }

    val extendedColors = PdfEasyThemeTokens.colors
    val backdropColor = ReaderRenderColorFilters.backdropColorFor(uiState.renderTheme, extendedColors)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backdropColor)
                .onSizeChanged { viewportWidthPx = it.width },
    ) {
        ReaderBodyContent(
            uiState = uiState,
            viewModel = viewModel,
            hudCallbacks = hudCallbacks,
            viewportWidthPx = viewportWidthPx,
            onNavigateBack = onNavigateBack,
        )
    }

    ReaderModals(uiState = uiState, viewModel = viewModel, onNavigateBack = onNavigateBack)
}

@Composable
private fun ObserveReaderLifecycle(
    viewModel: ReaderViewModel,
    keepScreenOn: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current

    DisposableEffect(hostView, keepScreenOn) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = previous }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.flushProgress()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushProgress()
        }
    }
}

@Composable
private fun ReaderBodyContent(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    hudCallbacks: ReaderHudCallbacks,
    viewportWidthPx: Int,
    onNavigateBack: () -> Unit,
) {
    val fatalError = uiState.fatalError
    when {
        fatalError != null ->
            ReaderFatalErrorScreen(error = fatalError, onBackToLibrary = onNavigateBack)

        uiState.isLoading -> ReaderLoadingContent()

        else -> {
            val viewportParams =
                ReaderViewportParams(
                    pageCount = uiState.pageCount,
                    pageDimensions = uiState.pageDimensions,
                    scrollMode = uiState.scrollMode,
                    renderTheme = uiState.renderTheme,
                    searchMatches = uiState.search.matches,
                    activeSearchMatch = uiState.search.activeMatch,
                    pendingScrollTarget = uiState.pendingScrollTarget,
                )
            val viewportCallbacks =
                remember(viewModel, viewportWidthPx) {
                    ReaderViewportCallbacks(
                        onScrollTargetConsumed = viewModel::consumePendingScrollTarget,
                        onToggleHud = viewModel::toggleHud,
                        onViewportChanged = { page, offset, zoom, range, crop ->
                            viewModel.onViewportScrolled(page, offset, zoom)
                            viewModel.syncVisibleTiles(
                                visibleRange = range,
                                viewportWidthPx = viewportWidthPx,
                                zoomScale = zoom,
                                primaryCrop = crop,
                            )
                        },
                    )
                }
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderViewport(
                    params = viewportParams,
                    bitmapController = viewModel.bitmapController,
                    callbacks = viewportCallbacks,
                )
                ReaderHudOverlay(uiState = uiState, callbacks = hudCallbacks)
            }
        }
    }
}

@Composable
private fun ReaderModals(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
) {
    PasswordPromptDialog(
        displayName = uiState.displayName,
        promptState = uiState.passwordPrompt,
        onDismissToLibrary = onNavigateBack,
        onSubmitPassword = viewModel::submitPassword,
    )
    BookmarksBottomSheet(
        isOpen = uiState.isBookmarksSheetOpen,
        bookmarks = uiState.bookmarks,
        currentPageIndex = uiState.currentPageIndex,
        isCurrentPageBookmarked = uiState.isCurrentPageBookmarked,
        onDismiss = viewModel::closeBookmarksSheet,
        onToggleCurrentPageBookmark = viewModel::toggleBookmarkOnCurrentPage,
        onSelectBookmark = { pageIndex ->
            viewModel.closeBookmarksSheet()
            viewModel.jumpToPage(pageIndex)
        },
        onDeleteBookmark = viewModel::deleteBookmark,
    )
}

@Composable
private fun ReaderLoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(ReaderScreenDimens.LoadingIndicatorSize),
            strokeWidth = ReaderScreenDimens.LoadingStroke,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(ReaderScreenDimens.LoadingGap))
        Text(
            text = stringResource(R.string.reader_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildReaderHudCallbacks(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
): ReaderHudCallbacks =
    ReaderHudCallbacks(
        onBackClick = {
            viewModel.flushProgress()
            onNavigateBack()
        },
        onOpenBookmarksSheet = viewModel::openBookmarksSheet,
        onOpenSettings = onOpenSettings,
        onSelectRenderTheme = viewModel::setRenderTheme,
        onToggleScrollMode = viewModel::toggleScrollMode,
        onJumpToPage = viewModel::jumpToPage,
        onToggleBookmark = viewModel::toggleBookmarkOnCurrentPage,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onPreviousMatch = viewModel::previousSearchMatch,
        onNextMatch = viewModel::nextSearchMatch,
    )
