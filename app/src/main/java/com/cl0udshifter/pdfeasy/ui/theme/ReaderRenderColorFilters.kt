package com.cl0udshifter.pdfeasy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme

/**
 * Filtros ColorMatrix pre-alocados para aplicacao instantanea na GPU sem re-renderizar bitmaps.
 */
object ReaderRenderColorFilters {
    private const val DARK_SCALE = -0.86f
    private const val DARK_OFFSET = 236f

    private const val SEPIA_RR = 0.95f
    private const val SEPIA_RG = 0.08f
    private const val SEPIA_RB = 0.02f
    private const val SEPIA_GR = 0.04f
    private const val SEPIA_GG = 0.88f
    private const val SEPIA_GB = 0.03f
    private const val SEPIA_BR = 0.02f
    private const val SEPIA_BG = 0.06f
    private const val SEPIA_BB = 0.72f

    private val darkColorFilter: ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    DARK_SCALE, 0f, 0f, 0f, DARK_OFFSET,
                    0f, DARK_SCALE, 0f, 0f, DARK_OFFSET,
                    0f, 0f, DARK_SCALE, 0f, DARK_OFFSET,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

    private val sepiaColorFilter: ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    SEPIA_RR, SEPIA_RG, SEPIA_RB, 0f, 0f,
                    SEPIA_GR, SEPIA_GG, SEPIA_GB, 0f, 0f,
                    SEPIA_BR, SEPIA_BG, SEPIA_BB, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

    fun colorFilterFor(theme: PageRenderTheme): ColorFilter? =
        when (theme) {
            PageRenderTheme.ORIGINAL -> null
            PageRenderTheme.DARK -> darkColorFilter
            PageRenderTheme.SEPIA -> sepiaColorFilter
        }

    fun backdropColorFor(
        theme: PageRenderTheme,
        extendedColors: PdfEasyExtendedColors,
    ): Color =
        when (theme) {
            PageRenderTheme.ORIGINAL -> extendedColors.readerBackdropOriginal
            PageRenderTheme.DARK -> extendedColors.readerBackdropDark
            PageRenderTheme.SEPIA -> extendedColors.readerBackdropSepia
        }
}
