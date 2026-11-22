package com.cl0udshifter.pdfeasy.core.pdf

import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import kotlin.math.roundToInt

internal data class TileRenderRegion(
    val startX: Int,
    val startY: Int,
    val drawSizeX: Int,
    val drawSizeY: Int,
)

internal object TileGeometry {
    fun computeRenderRegion(request: TileRequest): TileRenderRegion? {
        val crop = request.cropNormalized
        val validDimensions = request.targetWidthPx > 0 && request.targetHeightPx > 0
        val validHorizontal = crop.left in 0f..1f && crop.right in 0f..1f && crop.right > crop.left
        val validVertical = crop.top in 0f..1f && crop.bottom in 0f..1f && crop.bottom > crop.top
        if (!validDimensions || !validHorizontal || !validVertical) {
            return null
        }

        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        val fullWidthPx = (request.targetWidthPx / cropWidth).roundToInt().coerceAtLeast(1)
        val fullHeightPx = (request.targetHeightPx / cropHeight).roundToInt().coerceAtLeast(1)

        return TileRenderRegion(
            startX = -(crop.left * fullWidthPx).roundToInt(),
            startY = -(crop.top * fullHeightPx).roundToInt(),
            drawSizeX = fullWidthPx,
            drawSizeY = fullHeightPx,
        )
    }

    fun normalizeDeviceRect(
        leftPx: Int,
        topPx: Int,
        rightPx: Int,
        bottomPx: Int,
        referenceSizePx: Int,
    ): NormalizedRect? {
        if (referenceSizePx <= 0) return null
        val scale = referenceSizePx.toFloat()
        val normLeft = (minOf(leftPx, rightPx) / scale).coerceIn(0f, 1f)
        val normTop = (minOf(topPx, bottomPx) / scale).coerceIn(0f, 1f)
        val normRight = (maxOf(leftPx, rightPx) / scale).coerceIn(0f, 1f)
        val normBottom = (maxOf(topPx, bottomPx) / scale).coerceIn(0f, 1f)
        return NormalizedRect(
            left = normLeft,
            top = normTop,
            right = normRight,
            bottom = normBottom,
        )
    }
}

internal object SearchSnippetExtractor {
    private const val CONTEXT_BEFORE_CHARS = 24
    private const val CONTEXT_AFTER_CHARS = 40
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun computeWindow(
        totalChars: Int,
        matchStart: Int,
        matchLength: Int,
    ): IntRange {
        if (totalChars <= 0 || matchLength <= 0) return IntRange.EMPTY
        val safeStart = matchStart.coerceIn(0, totalChars)
        val safeEnd = (matchStart + matchLength).coerceIn(safeStart, totalChars)
        val windowStart = (safeStart - CONTEXT_BEFORE_CHARS).coerceAtLeast(0)
        val windowEnd = (safeEnd + CONTEXT_AFTER_CHARS).coerceAtMost(totalChars)
        return windowStart until windowEnd
    }

    fun formatSnippet(
        rawSlice: String,
        truncatedStart: Boolean,
        truncatedEnd: Boolean,
    ): String {
        val collapsed = rawSlice.replace(WHITESPACE_REGEX, " ").trim()
        val prefix = if (truncatedStart && collapsed.isNotEmpty()) "..." else ""
        val suffix = if (truncatedEnd && collapsed.isNotEmpty()) "..." else ""
        return "$prefix$collapsed$suffix"
    }
}
