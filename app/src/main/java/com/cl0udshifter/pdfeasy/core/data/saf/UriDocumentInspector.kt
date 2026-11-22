package com.cl0udshifter.pdfeasy.core.data.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

data class InspectedUriDocument(
    val displayName: String,
    val sizeBytes: Long,
)

interface UriDocumentInspector {
    suspend fun inspectAndPersist(uriString: String): CoreResult<InspectedUriDocument>

    suspend fun checkAvailability(uriString: String): DocumentAvailability

    suspend fun releasePermission(uriString: String)
}

class AndroidUriDocumentInspector(
    context: Context,
) : UriDocumentInspector {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun inspectAndPersist(uriString: String): CoreResult<InspectedUriDocument> {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> inspectContentUri(uri)
            ContentResolver.SCHEME_FILE -> inspectFileUri(uri)
            else -> CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
        }
    }

    override suspend fun checkAvailability(uriString: String): DocumentAvailability {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> checkContentAvailability(uri)
            ContentResolver.SCHEME_FILE -> checkFileAvailability(uri)
            else -> DocumentAvailability.MISSING_OR_MOVED
        }
    }

    override suspend fun releasePermission(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        val isPersisted =
            contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }
        if (isPersisted) {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun inspectContentUri(uri: Uri): CoreResult<InspectedUriDocument> {
        tryTakePersistableReadPermission(uri)
        return try {
            val descriptorSize =
                contentResolver.openAssetFileDescriptor(uri, MODE_READ)?.use { afd ->
                    afd.length.takeIf { it >= 0L }
                } ?: return CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)

            val metadata = queryDisplayNameAndSize(uri)
            val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: DEFAULT_DOCUMENT_NAME
            CoreResult.Success(
                InspectedUriDocument(
                    displayName = metadata.first ?: fallbackName,
                    sizeBytes = metadata.second ?: descriptorSize,
                ),
            )
        } catch (_: SecurityException) {
            CoreResult.Failure(PdfCoreError.PermissionDenied)
        } catch (_: FileNotFoundException) {
            CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
        } catch (_: IllegalArgumentException) {
            CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
        } catch (error: IOException) {
            CoreResult.Failure(PdfCoreError.IoFailure(error.message ?: "I/O failure while reading URI"))
        }
    }

    private fun inspectFileUri(uri: Uri): CoreResult<InspectedUriDocument> {
        val file = uri.path?.let(::File)
        return when {
            file == null || !file.exists() || !file.isFile ->
                CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
            !file.canRead() ->
                CoreResult.Failure(PdfCoreError.PermissionDenied)
            else ->
                CoreResult.Success(
                    InspectedUriDocument(
                        displayName = file.name.ifBlank { DEFAULT_DOCUMENT_NAME },
                        sizeBytes = file.length(),
                    ),
                )
        }
    }

    private fun checkContentAvailability(uri: Uri): DocumentAvailability =
        try {
            val readable = contentResolver.openAssetFileDescriptor(uri, MODE_READ)?.use { true } ?: false
            if (readable) {
                DocumentAvailability.AVAILABLE
            } else {
                DocumentAvailability.MISSING_OR_MOVED
            }
        } catch (_: SecurityException) {
            DocumentAvailability.PERMISSION_REVOKED
        } catch (_: FileNotFoundException) {
            DocumentAvailability.MISSING_OR_MOVED
        } catch (_: IllegalArgumentException) {
            DocumentAvailability.MISSING_OR_MOVED
        } catch (_: IllegalStateException) {
            DocumentAvailability.MISSING_OR_MOVED
        } catch (_: IOException) {
            DocumentAvailability.MISSING_OR_MOVED
        }

    private fun checkFileAvailability(uri: Uri): DocumentAvailability {
        val file = uri.path?.let(::File)
        return when {
            file == null || !file.exists() || !file.isFile -> DocumentAvailability.MISSING_OR_MOVED
            !file.canRead() -> DocumentAvailability.PERMISSION_REVOKED
            else -> DocumentAvailability.AVAILABLE
        }
    }

    private fun tryTakePersistableReadPermission(uri: Uri) {
        // Provedores de ACTION_VIEW temporarios lancam SecurityException aqui; seguimos mantendo
        // o registro para progresso e marcadores e reavaliamos em refreshAvailability().
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun queryDisplayNameAndSize(uri: Uri): Pair<String?, Long?> {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        var displayName: String? = null
        var sizeBytes: Long? = null
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)?.ifBlank { null }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                }
            }
        }
        return displayName to sizeBytes
    }

    private companion object {
        const val MODE_READ = "r"
        const val DEFAULT_DOCUMENT_NAME = "document.pdf"
    }
}
