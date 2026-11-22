package com.cl0udshifter.pdfeasy.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Transaction
    @Query(
        """
        SELECT * FROM pdf_documents
        WHERE (:hasQuery = 0 OR display_name LIKE :likePattern ESCAPE '\')
        ORDER BY COALESCE(last_opened_at, added_at) DESC, added_at DESC
        """,
    )
    fun observeLibrary(
        hasQuery: Boolean,
        likePattern: String,
    ): Flow<List<DocumentWithProgressRow>>

    @Query("SELECT * FROM pdf_documents WHERE id = :documentId LIMIT 1")
    suspend fun getById(documentId: String): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents")
    suspend fun getAll(): List<PdfDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PdfDocumentEntity)

    @Update
    suspend fun update(entity: PdfDocumentEntity)

    @Query(
        """
        UPDATE pdf_documents
        SET page_count = :pageCount,
            is_password_protected = :isPasswordProtected,
            last_opened_at = :lastOpenedAtEpochMillis
        WHERE id = :documentId
        """,
    )
    suspend fun updateMetadata(
        documentId: String,
        pageCount: Int,
        isPasswordProtected: Boolean,
        lastOpenedAtEpochMillis: Long,
    )

    @Query("UPDATE pdf_documents SET availability = :availability WHERE id = :documentId")
    suspend fun updateAvailability(
        documentId: String,
        availability: DocumentAvailability,
    )

    @Query("DELETE FROM pdf_documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String)

    /**
     * Insere novo documento ou atualiza nome/tamanho/disponibilidade sem disparar CASCADE DELETE
     * do SQLite REPLACE sobre reading_progress e bookmarks existentes.
     */
    @Transaction
    suspend fun upsertPreservingMetadata(
        id: String,
        uriString: String,
        displayName: String,
        sizeBytes: Long,
        addedAtEpochMillis: Long,
    ): PdfDocumentEntity {
        val existing = getById(id)
        return if (existing == null) {
            val created =
                PdfDocumentEntity(
                    id = id,
                    uriString = uriString,
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                    pageCount = 0,
                    isPasswordProtected = false,
                    availability = DocumentAvailability.AVAILABLE,
                    addedAtEpochMillis = addedAtEpochMillis,
                    lastOpenedAtEpochMillis = null,
                )
            insert(created)
            created
        } else {
            val updated =
                existing.copy(
                    displayName = displayName,
                    sizeBytes = sizeBytes,
                    availability = DocumentAvailability.AVAILABLE,
                )
            update(updated)
            updated
        }
    }
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE document_id = :documentId LIMIT 1")
    fun observeByDocumentId(documentId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE document_id = :documentId LIMIT 1")
    suspend fun getByDocumentId(documentId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsert(entity: ReadingProgressEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE document_id = :documentId ORDER BY page_index ASC")
    fun observeForDocument(documentId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE document_id = :documentId AND page_index = :pageIndex)")
    fun observeExists(
        documentId: String,
        pageIndex: Int,
    ): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE document_id = :documentId AND page_index = :pageIndex LIMIT 1")
    suspend fun getByDocumentAndPage(
        documentId: String,
        pageIndex: Int,
    ): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: Long)

    @Transaction
    suspend fun toggle(
        documentId: String,
        pageIndex: Int,
        label: String?,
        createdAtEpochMillis: Long,
    ): Boolean {
        val existing = getByDocumentAndPage(documentId, pageIndex)
        return if (existing != null) {
            deleteById(existing.id)
            false
        } else {
            insert(
                BookmarkEntity(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    label = label,
                    createdAtEpochMillis = createdAtEpochMillis,
                ),
            )
            true
        }
    }
}
