package com.cl0udshifter.pdfeasy.core.data.repository

import com.cl0udshifter.pdfeasy.core.data.local.LibraryDao
import com.cl0udshifter.pdfeasy.core.data.local.toDomain
import com.cl0udshifter.pdfeasy.core.data.saf.UriDocumentInspector
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.domain.LibraryRepository
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.PdfDocumentItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LibraryRepositoryImpl(
    private val libraryDao: LibraryDao,
    private val uriInspector: UriDocumentInspector,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : LibraryRepository {
    override fun observeLibrary(searchQuery: String): Flow<List<LibraryItemWithProgress>> {
        val trimmed = searchQuery.trim()
        val hasQuery = trimmed.isNotEmpty()
        val likePattern = if (hasQuery) "%${escapeSqlLike(trimmed)}%" else ""
        return libraryDao
            .observeLibrary(hasQuery = hasQuery, likePattern = likePattern)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun registerOrUpdateDocument(uriString: String): CoreResult<PdfDocumentItem> =
        withContext(ioDispatcher) {
            when (val inspected = uriInspector.inspectAndPersist(uriString)) {
                is CoreResult.Failure -> inspected
                is CoreResult.Success -> {
                    val entity =
                        libraryDao.upsertPreservingMetadata(
                            id = DocumentIdGenerator.fromUri(uriString),
                            uriString = uriString,
                            displayName = inspected.data.displayName,
                            sizeBytes = inspected.data.sizeBytes,
                            addedAtEpochMillis = nowMillis(),
                        )
                    CoreResult.Success(entity.toDomain())
                }
            }
        }

    override suspend fun updateDocumentMetadata(
        documentId: String,
        pageCount: Int,
        isPasswordProtected: Boolean,
        lastOpenedAtEpochMillis: Long,
    ) {
        withContext(ioDispatcher) {
            libraryDao.updateMetadata(
                documentId = documentId,
                pageCount = pageCount,
                isPasswordProtected = isPasswordProtected,
                lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
            )
        }
    }

    override suspend fun refreshAvailability() {
        withContext(ioDispatcher) {
            val documents = libraryDao.getAll()
            documents.forEach { document ->
                val latestAvailability = uriInspector.checkAvailability(document.uriString)
                if (latestAvailability != document.availability) {
                    libraryDao.updateAvailability(document.id, latestAvailability)
                }
            }
        }
    }

    override suspend fun removeFromLibrary(documentId: String) {
        withContext(ioDispatcher) {
            val existing = libraryDao.getById(documentId)
            libraryDao.deleteById(documentId)
            if (existing != null) {
                uriInspector.releasePermission(existing.uriString)
            }
        }
    }

    private fun escapeSqlLike(input: String): String =
        input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
