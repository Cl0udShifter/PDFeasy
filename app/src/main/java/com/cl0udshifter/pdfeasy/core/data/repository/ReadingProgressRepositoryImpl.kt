package com.cl0udshifter.pdfeasy.core.data.repository

import com.cl0udshifter.pdfeasy.core.data.local.ReadingProgressDao
import com.cl0udshifter.pdfeasy.core.data.local.toDomain
import com.cl0udshifter.pdfeasy.core.data.local.toEntity
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderPreferencesStore
import com.cl0udshifter.pdfeasy.core.domain.ReadingProgressRepository
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ReadingProgressRepositoryImpl(
    private val readingProgressDao: ReadingProgressDao,
    private val preferencesStore: ReaderPreferencesStore,
    private val ioDispatcher: CoroutineDispatcher,
) : ReadingProgressRepository {
    override fun observeProgress(documentId: String): Flow<ReadingProgress?> =
        readingProgressDao
            .observeByDocumentId(documentId)
            .map { entity -> entity?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun getProgress(documentId: String): ReadingProgress? =
        withContext(ioDispatcher) {
            readingProgressDao.getByDocumentId(documentId)?.toDomain()
        }

    override suspend fun saveProgress(progress: ReadingProgress) {
        withContext(ioDispatcher) {
            readingProgressDao.upsert(progress.toEntity())
            preferencesStore.updateDefaults(
                renderTheme = progress.renderTheme,
                scrollMode = progress.scrollMode,
            )
        }
    }
}
