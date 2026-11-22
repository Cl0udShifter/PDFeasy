package com.cl0udshifter.pdfeasy.core.model

/**
 * Dimensoes originais da pagina em pontos PostScript (1/72 polegada).
 */
data class PageDimensions(
    val pageIndex: Int,
    val widthPoints: Int,
    val heightPoints: Int,
) {
    val aspectRatio: Float
        get() = if (heightPoints == 0) 1f else widthPoints.toFloat() / heightPoints.toFloat()
}

/**
 * Retangulo normalizado (0.0f..1.0f) relativo as dimensoes da pagina.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Requisicao de renderizacao de pagina inteira ou recorte para zoom alto.
 */
data class TileRequest(
    val pageIndex: Int,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val cropNormalized: NormalizedRect = NormalizedRect(0f, 0f, 1f, 1f),
)

/**
 * Ocorrencia de texto localizada durante uma busca no documento.
 */
data class TextSearchMatch(
    val pageIndex: Int,
    val matchIndexInPage: Int,
    val snippet: String,
    val boundsNormalized: List<NormalizedRect>,
)
