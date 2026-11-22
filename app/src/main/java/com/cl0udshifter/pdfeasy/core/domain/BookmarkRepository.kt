package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.Bookmark
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de gerenciamento de marcadores de pagina por documento.
 */
interface BookmarkRepository {
    fun observeBookmarks(documentId: String): Flow<List<Bookmark>>

    fun observeIsBookmarked(
        documentId: String,
        pageIndex: Int,
    ): Flow<Boolean>

    suspend fun toggleBookmark(
        documentId: String,
        pageIndex: Int,
        label: String? = null,
    ): Boolean

    suspend fun deleteBookmark(bookmarkId: Long)
}
