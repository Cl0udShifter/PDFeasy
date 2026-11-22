package com.cl0udshifter.pdfeasy.ui.reader

import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import kotlin.math.abs

data class PageRectPx(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

data class PageLayoutSpec(
    val pageCount: Int,
    val pageDimensions: Map<Int, PageDimensions>,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val pageSpacingPx: Float,
    val zoomScale: Float,
    val scrollMode: ScrollMode,
)

class PageGeometryArrays(
    val startsMainPx: FloatArray,
    val endsMainPx: FloatArray,
    val widthsPx: FloatArray,
    val heightsPx: FloatArray,
    val totalMainExtentPx: Float,
    val maxCrossExtentPx: Float,
)

/**
 * Geometria pre-calculada de todas as paginas para busca O(log N) durante o frame loop a 120FPS.
 */
class PageLayoutSnapshot(
    private val spec: PageLayoutSpec,
    private val arrays: PageGeometryArrays,
) {
    val pageCount: Int get() = spec.pageCount
    val viewportWidthPx: Float get() = spec.viewportWidthPx
    val viewportHeightPx: Float get() = spec.viewportHeightPx
    val pageSpacingPx: Float get() = spec.pageSpacingPx
    val zoomScale: Float get() = spec.zoomScale
    val scrollMode: ScrollMode get() = spec.scrollMode
    val pageStartsMainPx: FloatArray get() = arrays.startsMainPx
    val pageEndsMainPx: FloatArray get() = arrays.endsMainPx
    val pageWidthsPx: FloatArray get() = arrays.widthsPx
    val pageHeightsPx: FloatArray get() = arrays.heightsPx
    val totalMainExtentPx: Float get() = arrays.totalMainExtentPx
    val maxCrossExtentPx: Float get() = arrays.maxCrossExtentPx

    val maxScrollMainPx: Float
        get() =
            if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
                (totalMainExtentPx - viewportHeightPx).coerceAtLeast(0f)
            } else {
                (totalMainExtentPx - viewportWidthPx).coerceAtLeast(0f)
            }

    val maxScrollCrossPx: Float
        get() =
            if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
                (maxCrossExtentPx - viewportWidthPx).coerceAtLeast(0f)
            } else {
                (maxCrossExtentPx - viewportHeightPx).coerceAtLeast(0f)
            }

    fun visiblePageIndices(scrollMainPx: Float): IntRange {
        if (pageCount <= 0) return IntRange.EMPTY
        val viewportMain =
            if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) viewportHeightPx else viewportWidthPx
        val first = findFirstPageEndingAfter(scrollMainPx)
        val last = findLastPageStartingBefore(scrollMainPx + viewportMain)
        return if (first in 0 until pageCount && last in first until pageCount) {
            first..last
        } else {
            IntRange.EMPTY
        }
    }

    fun currentPageIndex(scrollMainPx: Float): Int {
        if (pageCount <= 0) return 0
        val viewportMain =
            if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) viewportHeightPx else viewportWidthPx
        val centerMain = scrollMainPx + viewportMain * HALF
        var index = findLastPageStartingBefore(centerMain).coerceIn(0, pageCount - 1)
        if (index + 1 < pageCount) {
            val currentDist = abs(centerMain - (pageStartsMainPx[index] + pageEndsMainPx[index]) * HALF)
            val nextDist = abs(centerMain - (pageStartsMainPx[index + 1] + pageEndsMainPx[index + 1]) * HALF)
            if (nextDist < currentDist) {
                index += 1
            }
        }
        return index
    }

    fun normalizedOffsetOnPage(
        pageIndex: Int,
        scrollMainPx: Float,
    ): Float {
        if (pageIndex !in 0 until pageCount) return 0f
        val start = pageStartsMainPx[pageIndex]
        val extent = (pageEndsMainPx[pageIndex] - start).coerceAtLeast(1f)
        return ((scrollMainPx - start) / extent).coerceIn(0f, 1f)
    }

    fun scrollMainForPage(
        pageIndex: Int,
        normalizedOffset: Float = 0f,
    ): Float {
        if (pageCount <= 0) return 0f
        val safePage = pageIndex.coerceIn(0, pageCount - 1)
        val start = pageStartsMainPx[safePage]
        val extent = pageEndsMainPx[safePage] - start
        return (start + extent * normalizedOffset.coerceIn(0f, 1f)).coerceIn(0f, maxScrollMainPx)
    }

    fun pageScreenRect(
        pageIndex: Int,
        scrollMainPx: Float,
        scrollCrossPx: Float,
    ): PageRectPx {
        val width = pageWidthsPx[pageIndex]
        val height = pageHeightsPx[pageIndex]
        val mainStart = pageStartsMainPx[pageIndex]
        return if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
            val left = if (width <= viewportWidthPx) (viewportWidthPx - width) * HALF else -scrollCrossPx
            PageRectPx(pageIndex, left = left, top = mainStart - scrollMainPx, width = width, height = height)
        } else {
            val top = if (height <= viewportHeightPx) (viewportHeightPx - height) * HALF else -scrollCrossPx
            PageRectPx(pageIndex, left = mainStart - scrollMainPx, top = top, width = width, height = height)
        }
    }

    fun scrollForMatch(match: TextSearchMatch): Pair<Float, Float> {
        val rect = match.boundsNormalized.firstOrNull()
        val safePage = match.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return when {
            pageCount <= 0 -> 0f to 0f
            rect == null -> scrollMainForPage(safePage, 0f) to 0f
            else -> computeCenteredMatchScroll(this, safePage, rect)
        }
    }

    fun computeVisibleCropNormalized(
        pageIndex: Int,
        scrollMainPx: Float,
        scrollCrossPx: Float,
        overscanFraction: Float = DEFAULT_OVERSCAN_FRACTION,
    ): NormalizedRect? {
        val rect =
            if (pageIndex in 0 until pageCount) {
                pageScreenRect(pageIndex, scrollMainPx, scrollCrossPx)
            } else {
                null
            } ?: return null

        val visibleLeft = maxOf(0f, rect.left)
        val visibleTop = maxOf(0f, rect.top)
        val visibleRight = minOf(viewportWidthPx, rect.right)
        val visibleBottom = minOf(viewportHeightPx, rect.bottom)

        val hasVisibleArea =
            rect.width > 0f &&
                rect.height > 0f &&
                visibleRight > visibleLeft &&
                visibleBottom > visibleTop

        return if (hasVisibleArea) {
            buildPaddedNormalizedRect(
                VisiblePageBounds(rect, visibleLeft, visibleTop, visibleRight, visibleBottom, overscanFraction),
            )
        } else {
            null
        }
    }

    private fun findFirstPageEndingAfter(threshold: Float): Int {
        var low = 0
        var high = pageCount - 1
        var result = pageCount
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageEndsMainPx[mid] > threshold) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return result
    }

    private fun findLastPageStartingBefore(threshold: Float): Int {
        var low = 0
        var high = pageCount - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pageStartsMainPx[mid] <= threshold) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    companion object {
        const val HALF = 0.5f
        private const val DEFAULT_OVERSCAN_FRACTION = 0.15f
        const val MIN_CROP_SPAN = 0.01f
        private const val A4_WIDTH_PT = 595f
        private const val A4_HEIGHT_PT = 842f
        private const val DEFAULT_ASPECT_RATIO = A4_WIDTH_PT / A4_HEIGHT_PT

        fun create(spec: PageLayoutSpec): PageLayoutSnapshot {
            val starts = FloatArray(spec.pageCount)
            val ends = FloatArray(spec.pageCount)
            val widths = FloatArray(spec.pageCount)
            val heights = FloatArray(spec.pageCount)

            var cursor = 0f
            var maxCross = 0f

            for (index in 0 until spec.pageCount) {
                val aspectRatio = spec.pageDimensions[index]?.aspectRatio ?: DEFAULT_ASPECT_RATIO
                val safeRatio = if (aspectRatio > 0f) aspectRatio else DEFAULT_ASPECT_RATIO
                val pageWidth: Float
                val pageHeight: Float

                if (spec.scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
                    pageWidth = spec.viewportWidthPx * spec.zoomScale
                    pageHeight = pageWidth / safeRatio
                    maxCross = maxOf(maxCross, pageWidth)
                } else {
                    val baseHeight = minOf(spec.viewportHeightPx, spec.viewportWidthPx / safeRatio)
                    val baseWidth = baseHeight * safeRatio
                    pageWidth = baseWidth * spec.zoomScale
                    pageHeight = baseHeight * spec.zoomScale
                    maxCross = maxOf(maxCross, pageHeight)
                }

                widths[index] = pageWidth
                heights[index] = pageHeight
                starts[index] = cursor
                val extent = if (spec.scrollMode == ScrollMode.CONTINUOUS_VERTICAL) pageHeight else pageWidth
                cursor += extent
                ends[index] = cursor
                if (index < spec.pageCount - 1) {
                    cursor += spec.pageSpacingPx
                }
            }

            return PageLayoutSnapshot(
                spec = spec,
                arrays = PageGeometryArrays(starts, ends, widths, heights, cursor, maxCross),
            )
        }
    }
}

private data class VisiblePageBounds(
    val rect: PageRectPx,
    val visibleLeft: Float,
    val visibleTop: Float,
    val visibleRight: Float,
    val visibleBottom: Float,
    val overscanFraction: Float,
)

private fun computeCenteredMatchScroll(
    snapshot: PageLayoutSnapshot,
    pageIndex: Int,
    rect: NormalizedRect,
): Pair<Float, Float> {
    val centerXNorm = (rect.left + rect.right) * PageLayoutSnapshot.HALF
    val centerYNorm = (rect.top + rect.bottom) * PageLayoutSnapshot.HALF
    val pageWidth = snapshot.pageWidthsPx[pageIndex]
    val pageHeight = snapshot.pageHeightsPx[pageIndex]
    val pageStart = snapshot.pageStartsMainPx[pageIndex]

    return if (snapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
        val targetMain = (pageStart + centerYNorm * pageHeight - snapshot.viewportHeightPx * PageLayoutSnapshot.HALF)
            .coerceIn(0f, snapshot.maxScrollMainPx)
        val targetCross = (centerXNorm * pageWidth - snapshot.viewportWidthPx * PageLayoutSnapshot.HALF)
            .coerceIn(0f, snapshot.maxScrollCrossPx)
        targetMain to targetCross
    } else {
        val targetMain = (pageStart + centerXNorm * pageWidth - snapshot.viewportWidthPx * PageLayoutSnapshot.HALF)
            .coerceIn(0f, snapshot.maxScrollMainPx)
        val targetCross = (centerYNorm * pageHeight - snapshot.viewportHeightPx * PageLayoutSnapshot.HALF)
            .coerceIn(0f, snapshot.maxScrollCrossPx)
        targetMain to targetCross
    }
}

private fun buildPaddedNormalizedRect(bounds: VisiblePageBounds): NormalizedRect? {
    val rect = bounds.rect
    val normLeft = ((bounds.visibleLeft - rect.left) / rect.width).coerceIn(0f, 1f)
    val normTop = ((bounds.visibleTop - rect.top) / rect.height).coerceIn(0f, 1f)
    val normRight = ((bounds.visibleRight - rect.left) / rect.width).coerceIn(0f, 1f)
    val normBottom = ((bounds.visibleBottom - rect.top) / rect.height).coerceIn(0f, 1f)
    val padW = (normRight - normLeft) * bounds.overscanFraction
    val padH = (normBottom - normTop) * bounds.overscanFraction
    val finalLeft = (normLeft - padW).coerceIn(0f, 1f)
    val finalTop = (normTop - padH).coerceIn(0f, 1f)
    val finalRight = (normRight + padW).coerceIn(finalLeft + PageLayoutSnapshot.MIN_CROP_SPAN, 1f)
    val finalBottom = (normBottom + padH).coerceIn(finalTop + PageLayoutSnapshot.MIN_CROP_SPAN, 1f)
    return if (finalRight > finalLeft && finalBottom > finalTop) {
        NormalizedRect(finalLeft, finalTop, finalRight, finalBottom)
    } else {
        null
    }
}
