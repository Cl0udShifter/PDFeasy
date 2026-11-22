package com.cl0udshifter.pdfeasy.core.data.repository

import com.cl0udshifter.pdfeasy.core.data.local.BookmarkDao
import com.cl0udshifter.pdfeasy.core.data.local.toDomain
import com.cl0udshifter.pdfeasy.core.domain.BookmarkRepository
import com.cl0udshifter.pdfeasy.core.model.Bookmark
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookmarkRepositoryImpl(
    private val bookmarkDao: BookmarkDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {
    override fun observeBookmarks(documentId: String): Flow<List<Bookmark>> =
        bookmarkDao
            .observeForDocument(documentId)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override fun observeIsBookmarked(
        documentId: String,
        pageIndex: Int,
    ): Flow<Boolean> =
        bookmarkDao
            .observeExists(documentId, pageIndex)
            .flowOn(ioDispatcher)

    override suspend fun toggleBookmark(
        documentId: String,
        pageIndex: Int,
        label: String?,
    ): Boolean =
        withContext(ioDispatcher) {
            bookmarkDao.toggle(
                documentId = documentId,
                pageIndex = pageIndex,
                label = label?.trim()?.ifEmpty { null },
                createdAtEpochMillis = nowMillis(),
            )
        }

    override suspend fun deleteBookmark(bookmarkId: Long) {
        withContext(ioDispatcher) {
            bookmarkDao.deleteById(bookmarkId)
        }
    }
}
