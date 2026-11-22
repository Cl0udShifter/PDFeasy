package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de persistencia e observacao do progresso de leitura.
 */
interface ReadingProgressRepository {
    fun observeProgress(documentId: String): Flow<ReadingProgress?>

    suspend fun getProgress(documentId: String): ReadingProgress?

    suspend fun saveProgress(progress: ReadingProgress)
}
