package com.cl0udshifter.pdfeasy.core.pdf

import android.graphics.Bitmap
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import kotlinx.coroutines.flow.Flow

/**
 * Ponto de entrada para abertura de sessoes de leitura de documentos PDF.
 */
interface PdfEngine {
    suspend fun openSession(
        uriString: String,
        password: String? = null,
    ): CoreResult<PdfSession>
}

/**
 * Sessao ativa de um documento PDF para renderizacao por tiles e busca textual.
 */
interface PdfSession : AutoCloseable {
    val documentUri: String
    val pageCount: Int

    suspend fun getPageDimensions(pageIndex: Int): CoreResult<PageDimensions>

    suspend fun renderTile(request: TileRequest): CoreResult<Bitmap>

    fun recycleBitmap(bitmap: Bitmap)

    fun search(query: String): Flow<TextSearchMatch>

    override fun close()
}
