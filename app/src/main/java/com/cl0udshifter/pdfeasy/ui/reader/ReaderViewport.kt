package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens
import com.cl0udshifter.pdfeasy.ui.theme.ReaderRenderColorFilters
import kotlinx.coroutines.flow.distinctUntilChanged

private object ViewportConstants {
    val PageGapDp = 10.dp
    val MatchStrokeWidthDp = 1.5.dp
    const val HEX_PLACEHOLDER_DARK = 0xFF181817
    const val HEX_PLACEHOLDER_SEPIA = 0xFFF4EAD5
}

data class ReaderViewportParams(
    val pageCount: Int,
    val pageDimensions: Map<Int, PageDimensions>,
    val scrollMode: ScrollMode,
    val renderTheme: PageRenderTheme,
    val searchMatches: List<TextSearchMatch>,
    val activeSearchMatch: TextSearchMatch?,
    val pendingScrollTarget: ScrollRestoreTarget?,
)

@Immutable
data class ReaderViewportCallbacks(
    val onScrollTargetConsumed: (Long) -> Unit,
    val onToggleHud: () -> Unit,
    val onViewportChanged: (
        currentPage: Int,
        offsetNormalized: Float,
        zoomScale: Float,
        visibleRange: IntRange,
        primaryCrop: Pair<Int, NormalizedRect>?,
    ) -> Unit,
)

@Composable
fun ReaderViewport(
    params: ReaderViewportParams,
    bitmapController: ReaderBitmapController,
    callbacks: ReaderViewportCallbacks,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val motionState = remember { ViewportMotionState() }
    val baseSpec = rememberViewportBaseSpec(params, motionState)
    val baseSnapshot = remember(baseSpec) { PageLayoutSnapshot.create(baseSpec) }
    val layoutSnapshot =
        remember(baseSpec, motionState.zoomScale) {
            PageLayoutSnapshot.create(baseSpec.copy(zoomScale = motionState.zoomScale))
        }
    val latestBaseSnapshot by rememberUpdatedState(baseSnapshot)
    val latestSnapshot by rememberUpdatedState(layoutSnapshot)
    val latestBaseSpec by rememberUpdatedState(baseSpec)
    val latestCallbacks by rememberUpdatedState(callbacks)

    HandleScrollRestoration(
        pendingScrollTarget = params.pendingScrollTarget,
        baseSpec = baseSpec,
        motionState = motionState,
        onScrollTargetConsumed = latestCallbacks.onScrollTargetConsumed,
    )
    ObserveViewportFrameUpdates(
        motionState = motionState,
        baseSpecProvider = { latestBaseSpec },
        snapshotProvider = { latestSnapshot },
        onViewportChanged = latestCallbacks.onViewportChanged,
    )

    val drawContext = buildPageDrawContext(params = params, zoomScale = motionState.zoomScale)
    val gestureContext =
        ViewportGestureContext(
            coroutineScope = coroutineScope,
            motionState = motionState,
            baseSnapshotProvider = { latestBaseSnapshot },
            onSingleTap = { latestCallbacks.onToggleHud() },
        )

    ReaderCanvasSurface(
        bitmapController = bitmapController,
        drawContext = drawContext,
        gestureContext = gestureContext,
        zoomedSnapshotProvider = { latestSnapshot },
        modifier = modifier,
    )
}

@Composable
private fun rememberViewportBaseSpec(
    params: ReaderViewportParams,
    motionState: ViewportMotionState,
): PageLayoutSpec {
    val density = LocalDensity.current
    val pageSpacingPx = with(density) { ViewportConstants.PageGapDp.toPx() }
    return remember(
        params.pageCount,
        params.pageDimensions,
        motionState.viewportWidthPx,
        motionState.viewportHeightPx,
        params.scrollMode,
        pageSpacingPx,
    ) {
        PageLayoutSpec(
            pageCount = params.pageCount,
            pageDimensions = params.pageDimensions,
            viewportWidthPx = motionState.viewportWidthPx,
            viewportHeightPx = motionState.viewportHeightPx,
            pageSpacingPx = pageSpacingPx,
            zoomScale = ReaderViewModel.MIN_ZOOM,
            scrollMode = params.scrollMode,
        )
    }
}

@Composable
private fun buildPageDrawContext(
    params: ReaderViewportParams,
    zoomScale: Float,
): PageDrawContext {
    val extendedColors = PdfEasyThemeTokens.colors
    val matchStrokePx = with(LocalDensity.current) { ViewportConstants.MatchStrokeWidthDp.toPx() }
    val matchesByPage = remember(params.searchMatches) { params.searchMatches.groupBy { it.pageIndex } }
    return PageDrawContext(
        backdropColor = ReaderRenderColorFilters.backdropColorFor(params.renderTheme, extendedColors),
        colorFilter = ReaderRenderColorFilters.colorFilterFor(params.renderTheme),
        placeholderColor = placeholderPaperColor(params.renderTheme),
        highlightConfig = SearchHighlightConfig(matchesByPage, params.activeSearchMatch, extendedColors, matchStrokePx),
        zoomScale = zoomScale,
    )
}

@Composable
private fun ReaderCanvasSurface(
    bitmapController: ReaderBitmapController,
    drawContext: PageDrawContext,
    gestureContext: ViewportGestureContext,
    zoomedSnapshotProvider: () -> PageLayoutSnapshot,
    modifier: Modifier = Modifier,
) {
    val baseSnapshot = gestureContext.baseSnapshotProvider()
    val motionState = gestureContext.motionState
    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .testTag("reader_viewport")
                .onSizeChanged {
                    motionState.viewportWidthPx = it.width.toFloat()
                    motionState.viewportHeightPx = it.height.toFloat()
                }
                .pointerInput(baseSnapshot.pageCount, baseSnapshot.scrollMode) {
                    detectReaderGestures(gestureContext)
                },
    ) {
        drawRect(color = drawContext.backdropColor)
        val currentSnapshot = zoomedSnapshotProvider()
        if (currentSnapshot.pageCount <= 0) return@Canvas
        val scrollMain = motionState.scrollMainPx
        val scrollCross = motionState.scrollCrossPx
        val visibleRange = currentSnapshot.visiblePageIndices(scrollMain)

        for (pageIndex in visibleRange) {
            val rect = currentSnapshot.pageScreenRect(pageIndex, scrollMain, scrollCross)
            val detailTile =
                if (drawContext.zoomScale > ReaderBitmapController.DETAIL_TILE_ZOOM_THRESHOLD) {
                    bitmapController.detailTiles[pageIndex]
                } else {
                    null
                }
            drawPageItem(
                rect = rect,
                baseEntry = bitmapController.basePages[pageIndex],
                detailTile = detailTile,
                drawContext = drawContext,
            )
        }
    }
}

@Composable
private fun HandleScrollRestoration(
    pendingScrollTarget: ScrollRestoreTarget?,
    baseSpec: PageLayoutSpec,
    motionState: ViewportMotionState,
    onScrollTargetConsumed: (Long) -> Unit,
) {
    LaunchedEffect(pendingScrollTarget, baseSpec.viewportWidthPx, baseSpec.viewportHeightPx, baseSpec.pageCount) {
        val target = pendingScrollTarget ?: return@LaunchedEffect
        val viewportReady =
            baseSpec.viewportWidthPx > 0f &&
                baseSpec.viewportHeightPx > 0f &&
                baseSpec.pageCount > 0
        if (!viewportReady) return@LaunchedEffect

        val clampedZoom = target.zoomScale.coerceIn(ReaderViewModel.MIN_ZOOM, ReaderViewModel.MAX_ZOOM)
        motionState.cancelAnimations()
        motionState.zoomScale = clampedZoom
        val snapshot = PageLayoutSnapshot.create(baseSpec.copy(zoomScale = clampedZoom))
        val (targetMain, targetCross) =
            if (target.targetMatch != null) {
                snapshot.scrollForMatch(target.targetMatch)
            } else {
                snapshot.scrollMainForPage(target.pageIndex, target.offsetNormalized) to 0f
            }
        motionState.scrollMainPx = targetMain
        motionState.scrollCrossPx = targetCross
        onScrollTargetConsumed(target.token)
    }
}

@Composable
private fun ObserveViewportFrameUpdates(
    motionState: ViewportMotionState,
    baseSpecProvider: () -> PageLayoutSpec,
    snapshotProvider: () -> PageLayoutSnapshot,
    onViewportChanged: (
        currentPage: Int,
        offsetNormalized: Float,
        zoomScale: Float,
        visibleRange: IntRange,
        primaryCrop: Pair<Int, NormalizedRect>?,
    ) -> Unit,
) {
    LaunchedEffect(Unit) {
        snapshotFlow {
            val baseSpec = baseSpecProvider()
            ViewportSnapshotKey(
                pageCount = baseSpec.pageCount,
                scrollMode = baseSpec.scrollMode,
                viewportW = baseSpec.viewportWidthPx,
                main = motionState.scrollMainPx,
                cross = motionState.scrollCrossPx,
                zoom = motionState.zoomScale,
            )
        }.distinctUntilChanged().collect { key ->
            val rawSnapshot = snapshotProvider()
            val snapshot =
                if (rawSnapshot.zoomScale == key.zoom) {
                    rawSnapshot
                } else {
                    PageLayoutSnapshot.create(baseSpecProvider().copy(zoomScale = key.zoom))
                }
            if (snapshot.pageCount <= 0 || snapshot.viewportWidthPx <= 0f) return@collect
            val range = snapshot.visiblePageIndices(key.main)
            val currentPage = snapshot.currentPageIndex(key.main)
            val offsetNorm = snapshot.normalizedOffsetOnPage(currentPage, key.main)
            val crop =
                if (key.zoom > ReaderBitmapController.DETAIL_TILE_ZOOM_THRESHOLD) {
                    snapshot.computeVisibleCropNormalized(currentPage, key.main, key.cross)?.let {
                        currentPage to it
                    }
                } else {
                    null
                }
            onViewportChanged(currentPage, offsetNorm, key.zoom, range, crop)
        }
    }
}

private data class ViewportSnapshotKey(
    val pageCount: Int,
    val scrollMode: ScrollMode,
    val viewportW: Float,
    val main: Float,
    val cross: Float,
    val zoom: Float,
)

private fun placeholderPaperColor(renderTheme: PageRenderTheme): Color =
    when (renderTheme) {
        PageRenderTheme.ORIGINAL -> Color.White
        PageRenderTheme.DARK -> Color(ViewportConstants.HEX_PLACEHOLDER_DARK)
        PageRenderTheme.SEPIA -> Color(ViewportConstants.HEX_PLACEHOLDER_SEPIA)
    }
