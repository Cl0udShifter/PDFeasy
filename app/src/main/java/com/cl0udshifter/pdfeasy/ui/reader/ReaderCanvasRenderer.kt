package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyExtendedColors

class SearchHighlightConfig(
    val matchesByPage: Map<Int, List<TextSearchMatch>>,
    val activeMatch: TextSearchMatch?,
    val extendedColors: PdfEasyExtendedColors,
    val matchStrokePx: Float,
)

class PageDrawContext(
    val backdropColor: Color,
    val colorFilter: ColorFilter?,
    val placeholderColor: Color,
    val highlightConfig: SearchHighlightConfig,
    val zoomScale: Float,
)

fun DrawScope.drawPageItem(
    rect: PageRectPx,
    baseEntry: RenderedBitmapEntry?,
    detailTile: RenderedDetailTile?,
    drawContext: PageDrawContext,
) {
    drawRect(
        color = drawContext.placeholderColor,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
    )
    if (baseEntry != null && isBitmapDrawable(baseEntry)) {
        drawScaledBitmap(rect, baseEntry, drawContext.colorFilter)
    }
    if (detailTile != null && isBitmapDrawable(detailTile.entry)) {
        val crop = detailTile.cropNormalized
        val tileRect =
            PageRectPx(
                pageIndex = rect.pageIndex,
                left = rect.left + crop.left * rect.width,
                top = rect.top + crop.top * rect.height,
                width = (crop.right - crop.left) * rect.width,
                height = (crop.bottom - crop.top) * rect.height,
            )
        drawScaledBitmap(tileRect, detailTile.entry, drawContext.colorFilter)
    }
    drawSearchHighlights(
        rect = rect,
        highlightConfig = drawContext.highlightConfig,
    )
}

private fun DrawScope.drawScaledBitmap(
    rect: PageRectPx,
    entry: RenderedBitmapEntry,
    colorFilter: ColorFilter?,
) {
    withTransform({
        translate(left = rect.left, top = rect.top)
        scale(scaleX = rect.width / entry.widthPx, scaleY = rect.height / entry.heightPx, pivot = Offset.Zero)
    }) {
        drawImage(image = entry.imageBitmap, colorFilter = colorFilter)
    }
}

private fun DrawScope.drawSearchHighlights(
    rect: PageRectPx,
    highlightConfig: SearchHighlightConfig,
) {
    val pageMatches = highlightConfig.matchesByPage[rect.pageIndex].orEmpty()
    for (match in pageMatches) {
        val isCurrent = match === highlightConfig.activeMatch
        val fillColor =
            if (isCurrent) {
                highlightConfig.extendedColors.searchMatchCurrentFill
            } else {
                highlightConfig.extendedColors.searchMatchOther
            }
        for (normRect in match.boundsNormalized) {
            val boxLeft = rect.left + normRect.left * rect.width
            val boxTop = rect.top + normRect.top * rect.height
            val boxWidth = ((normRect.right - normRect.left) * rect.width).coerceAtLeast(1f)
            val boxHeight = ((normRect.bottom - normRect.top) * rect.height).coerceAtLeast(1f)
            drawRect(color = fillColor, topLeft = Offset(boxLeft, boxTop), size = Size(boxWidth, boxHeight))
            if (isCurrent) {
                drawRect(
                    color = highlightConfig.extendedColors.searchMatchCurrentStroke,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = highlightConfig.matchStrokePx),
                )
            }
        }
    }
}

private fun isBitmapDrawable(entry: RenderedBitmapEntry): Boolean =
    !entry.bitmap.isRecycled && entry.widthPx > 0 && entry.heightPx > 0
