package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.PdfDocumentItem
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Item da biblioteca combinado com seu ultimo progresso de leitura.
 */
data class LibraryItemWithProgress(
    val document: PdfDocumentItem,
    val progress: ReadingProgress?,
)

/**
 * Contrato de gerenciamento da biblioteca local de PDFs.
 */
interface LibraryRepository {
    fun observeLibrary(searchQuery: String = ""): Flow<List<LibraryItemWithProgress>>

    suspend fun registerOrUpdateDocument(uriString: String): CoreResult<PdfDocumentItem>

    suspend fun updateDocumentMetadata(
        documentId: String,
        pageCount: Int,
        isPasswordProtected: Boolean,
        lastOpenedAtEpochMillis: Long,
    )

    suspend fun refreshAvailability()

    suspend fun removeFromLibrary(documentId: String)
}
