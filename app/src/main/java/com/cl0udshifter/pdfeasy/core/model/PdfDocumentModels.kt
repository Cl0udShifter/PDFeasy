package com.cl0udshifter.pdfeasy.core.model

/**
 * Representa um documento PDF catalogado na biblioteca local.
 */
data class PdfDocumentItem(
    val id: String,
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val isPasswordProtected: Boolean,
    val availability: DocumentAvailability,
    val addedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long?,
)

/**
 * Estado atual de acessibilidade do arquivo no armazenamento do dispositivo.
 */
enum class DocumentAvailability {
    AVAILABLE,
    MISSING_OR_MOVED,
    PERMISSION_REVOKED,
}

/**
 * Posicao de leitura e configuracao de viewport salvas por documento.
 */
data class ReadingProgress(
    val documentId: String,
    val currentPageIndex: Int,
    val totalPages: Int,
    val scrollOffsetNormalized: Float,
    val zoomScale: Float,
    val scrollMode: ScrollMode,
    val renderTheme: PageRenderTheme,
    val updatedAtEpochMillis: Long,
) {
    val completionFraction: Float
        get() =
            if (totalPages <= 0) {
                0f
            } else {
                ((currentPageIndex + 1).toFloat() / totalPages).coerceIn(0f, 1f)
            }
}

/**
 * Direcao de rolagem das paginas no leitor.
 */
enum class ScrollMode {
    CONTINUOUS_VERTICAL,
    CONTINUOUS_HORIZONTAL,
}

/**
 * Tema de renderizacao aplicado na GPU via ColorMatrix.
 */
enum class PageRenderTheme {
    ORIGINAL,
    DARK,
    SEPIA,
}

/**
 * Marcador de pagina vinculado a um documento.
 */
data class Bookmark(
    val id: Long,
    val documentId: String,
    val pageIndex: Int,
    val label: String?,
    val createdAtEpochMillis: Long,
)
