package com.cl0udshifter.pdfeasy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.model.Bookmark
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.PdfDocumentItem
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import com.cl0udshifter.pdfeasy.core.model.ScrollMode

@Entity(
    tableName = "pdf_documents",
    indices = [
        Index(value = ["uri_string"], unique = true),
        Index(value = ["last_opened_at", "added_at"]),
    ],
)
data class PdfDocumentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "uri_string")
    val uriString: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "page_count")
    val pageCount: Int,
    @ColumnInfo(name = "is_password_protected")
    val isPasswordProtected: Boolean,
    @ColumnInfo(name = "availability")
    val availability: DocumentAvailability,
    @ColumnInfo(name = "added_at")
    val addedAtEpochMillis: Long,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAtEpochMillis: Long?,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "current_page_index")
    val currentPageIndex: Int,
    @ColumnInfo(name = "total_pages")
    val totalPages: Int,
    @ColumnInfo(name = "scroll_offset_normalized")
    val scrollOffsetNormalized: Float,
    @ColumnInfo(name = "zoom_scale")
    val zoomScale: Float,
    @ColumnInfo(name = "scroll_mode")
    val scrollMode: ScrollMode,
    @ColumnInfo(name = "render_theme")
    val renderTheme: PageRenderTheme,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["document_id", "page_index"], unique = true),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "document_id")
    val documentId: String,
    @ColumnInfo(name = "page_index")
    val pageIndex: Int,
    @ColumnInfo(name = "label")
    val label: String?,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long,
)

data class DocumentWithProgressRow(
    @Embedded
    val document: PdfDocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "document_id",
    )
    val progress: ReadingProgressEntity?,
)

class PdfEasyTypeConverters {
    @TypeConverter
    fun fromAvailability(value: DocumentAvailability): String = value.name

    @TypeConverter
    fun toAvailability(raw: String): DocumentAvailability = DocumentAvailability.valueOf(raw)

    @TypeConverter
    fun fromScrollMode(value: ScrollMode): String = value.name

    @TypeConverter
    fun toScrollMode(raw: String): ScrollMode = ScrollMode.valueOf(raw)

    @TypeConverter
    fun fromRenderTheme(value: PageRenderTheme): String = value.name

    @TypeConverter
    fun toRenderTheme(raw: String): PageRenderTheme = PageRenderTheme.valueOf(raw)
}

fun PdfDocumentEntity.toDomain(): PdfDocumentItem =
    PdfDocumentItem(
        id = id,
        uriString = uriString,
        displayName = displayName,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        isPasswordProtected = isPasswordProtected,
        availability = availability,
        addedAtEpochMillis = addedAtEpochMillis,
        lastOpenedAtEpochMillis = lastOpenedAtEpochMillis,
    )

fun ReadingProgressEntity.toDomain(): ReadingProgress =
    ReadingProgress(
        documentId = documentId,
        currentPageIndex = currentPageIndex,
        totalPages = totalPages,
        scrollOffsetNormalized = scrollOffsetNormalized,
        zoomScale = zoomScale,
        scrollMode = scrollMode,
        renderTheme = renderTheme,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

fun ReadingProgress.toEntity(): ReadingProgressEntity =
    ReadingProgressEntity(
        documentId = documentId,
        currentPageIndex = currentPageIndex,
        totalPages = totalPages,
        scrollOffsetNormalized = scrollOffsetNormalized,
        zoomScale = zoomScale,
        scrollMode = scrollMode,
        renderTheme = renderTheme,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        label = label,
        createdAtEpochMillis = createdAtEpochMillis,
    )

fun DocumentWithProgressRow.toDomain(): LibraryItemWithProgress =
    LibraryItemWithProgress(
        document = document.toDomain(),
        progress = progress?.toDomain(),
    )
