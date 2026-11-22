package com.cl0udshifter.pdfeasy.ui.reader

import com.cl0udshifter.pdfeasy.core.model.ScrollMode

data class ZoomTransformInput(
    val oldZoom: Float,
    val newZoom: Float,
    val oldScrollMainPx: Float,
    val oldScrollCrossPx: Float,
    val centroidMainPx: Float,
    val centroidCrossPx: Float,
    val panMainPx: Float = 0f,
    val panCrossPx: Float = 0f,
)

data class ZoomTransformOutput(
    val zoomScale: Float,
    val scrollMainPx: Float,
    val scrollCrossPx: Float,
)

object ReaderZoomMath {
    private const val MIN_SAFE_ZOOM = 0.01f

    fun scaledMaxScrollMain(
        baseSnapshot: PageLayoutSnapshot,
        zoomScale: Float,
    ): Float {
        if (baseSnapshot.pageCount <= 0) return 0f
        val scale = zoomScale / baseSnapshot.zoomScale.coerceAtLeast(MIN_SAFE_ZOOM)
        val scaledPagesExtent = pagesExtentAtScale(baseSnapshot, scale)
        val leading = leadingOffsetAtScale(baseSnapshot, scaledPagesExtent)
        val totalMainAtZoom = leading + scaledPagesExtent + baseSnapshot.mainPaddingEndPx
        return (totalMainAtZoom - baseSnapshot.viewportMainPx).coerceAtLeast(0f)
    }

    fun scaledMaxScrollCross(
        baseSnapshot: PageLayoutSnapshot,
        zoomScale: Float,
    ): Float {
        if (baseSnapshot.pageCount <= 0) return 0f
        val scale = zoomScale / baseSnapshot.zoomScale.coerceAtLeast(MIN_SAFE_ZOOM)
        val crossAtZoom = baseSnapshot.maxCrossExtentPx * scale
        val viewportCross =
            if (baseSnapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
                baseSnapshot.viewportWidthPx
            } else {
                baseSnapshot.viewportHeightPx
            }
        return (crossAtZoom - viewportCross).coerceAtLeast(0f)
    }

    fun transformScrollForZoom(
        baseSnapshot: PageLayoutSnapshot,
        input: ZoomTransformInput,
    ): ZoomTransformOutput {
        if (baseSnapshot.pageCount <= 0 || input.oldZoom <= 0f || input.newZoom <= 0f) {
            return ZoomTransformOutput(input.newZoom, 0f, 0f)
        }
        val baseZoom = baseSnapshot.zoomScale.coerceAtLeast(MIN_SAFE_ZOOM)
        val zOld = input.oldZoom / baseZoom
        val zNew = input.newZoom / baseZoom
        val gap = baseSnapshot.pageSpacingPx
        val leadingOld = leadingOffsetAtScale(baseSnapshot, pagesExtentAtScale(baseSnapshot, zOld))
        val leadingNew = leadingOffsetAtScale(baseSnapshot, pagesExtentAtScale(baseSnapshot, zNew))
        val baseAnchor = baseSnapshot.pageStartsMainPx[0]

        val docMainOld = input.oldScrollMainPx + input.centroidMainPx
        val pageIndex = findPageIndexAtScaledOffset(baseSnapshot, docMainOld, zOld, leadingOld)
        val relStart = (baseSnapshot.pageStartsMainPx[pageIndex] - baseAnchor) - pageIndex * gap
        val relEnd = (baseSnapshot.pageEndsMainPx[pageIndex] - baseAnchor) - pageIndex * gap
        val startOld = leadingOld + relStart * zOld + pageIndex * gap
        val endOld = leadingOld + relEnd * zOld + pageIndex * gap
        val startNew = leadingNew + relStart * zNew + pageIndex * gap
        val endNew = leadingNew + relEnd * zNew + pageIndex * gap

        val anchoredDocMainNew =
            when {
                docMainOld <= endOld -> startNew + ((docMainOld - startOld) / zOld) * zNew
                pageIndex < baseSnapshot.pageCount - 1 -> endNew + (docMainOld - endOld).coerceIn(0f, gap)
                else -> endNew + ((docMainOld - endOld) / zOld) * zNew
            }

        val maxMainNew = scaledMaxScrollMain(baseSnapshot, input.newZoom)
        val nextMain = (anchoredDocMainNew - input.centroidMainPx - input.panMainPx).coerceIn(0f, maxMainNew)
        val nextCross =
            computeAnchoredCrossScroll(
                baseSnapshot = baseSnapshot,
                pageIndex = pageIndex,
                zOld = zOld,
                input = input,
            )
        return ZoomTransformOutput(
            zoomScale = input.newZoom,
            scrollMainPx = nextMain,
            scrollCrossPx = nextCross,
        )
    }

    private fun computeAnchoredCrossScroll(
        baseSnapshot: PageLayoutSnapshot,
        pageIndex: Int,
        zOld: Float,
        input: ZoomTransformInput,
    ): Float {
        val isVertical = baseSnapshot.scrollMode == ScrollMode.CONTINUOUS_VERTICAL
        val viewportCross = if (isVertical) baseSnapshot.viewportWidthPx else baseSnapshot.viewportHeightPx
        val pageCrossBase = if (isVertical) baseSnapshot.pageWidthsPx[pageIndex] else baseSnapshot.pageHeightsPx[pageIndex]
        val pageCrossOld = pageCrossBase * zOld
        val crossOriginOld =
            if (pageCrossOld <= viewportCross) {
                (viewportCross - pageCrossOld) * PageLayoutSnapshot.HALF
            } else {
                -input.oldScrollCrossPx
            }
        val ratio = input.newZoom / input.oldZoom
        val scaledCrossOffset = (input.centroidCrossPx - crossOriginOld) * ratio
        val desiredOriginNew = input.centroidCrossPx - scaledCrossOffset + input.panCrossPx
        val maxCrossNew = scaledMaxScrollCross(baseSnapshot, input.newZoom)
        return (-desiredOriginNew).coerceIn(0f, maxCrossNew)
    }

    private fun findPageIndexAtScaledOffset(
        baseSnapshot: PageLayoutSnapshot,
        docMainPx: Float,
        scaleFactor: Float,
        leadingOffsetPx: Float,
    ): Int {
        val gap = baseSnapshot.pageSpacingPx
        val baseAnchor = baseSnapshot.pageStartsMainPx[0]
        var low = 0
        var high = baseSnapshot.pageCount - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val relStart = (baseSnapshot.pageStartsMainPx[mid] - baseAnchor) - mid * gap
            val startAtScale = leadingOffsetPx + relStart * scaleFactor + mid * gap
            if (startAtScale <= docMainPx) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result.coerceIn(0, baseSnapshot.pageCount - 1)
    }

    private fun pagesExtentAtScale(
        baseSnapshot: PageLayoutSnapshot,
        scaleFactor: Float,
    ): Float {
        if (baseSnapshot.pageCount <= 0) return 0f
        val baseContentExtent =
            baseSnapshot.pageEndsMainPx[baseSnapshot.pageCount - 1] - baseSnapshot.pageStartsMainPx[0]
        val totalGaps = (baseSnapshot.pageCount - 1).coerceAtLeast(0) * baseSnapshot.pageSpacingPx
        return (baseContentExtent - totalGaps) * scaleFactor + totalGaps
    }

    private fun leadingOffsetAtScale(
        baseSnapshot: PageLayoutSnapshot,
        scaledPagesExtent: Float,
    ): Float =
        if (baseSnapshot.pageCount > 0 && scaledPagesExtent < baseSnapshot.usableMainPx) {
            baseSnapshot.mainPaddingStartPx +
                (baseSnapshot.usableMainPx - scaledPagesExtent) * PageLayoutSnapshot.HALF
        } else {
            baseSnapshot.mainPaddingStartPx
        }
}
