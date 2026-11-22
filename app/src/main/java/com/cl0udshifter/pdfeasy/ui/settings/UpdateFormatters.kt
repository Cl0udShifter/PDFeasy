package com.cl0udshifter.pdfeasy.ui.settings

import androidx.annotation.StringRes
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.ui.library.LibraryFormatters
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class UpdateErrorMessage(
    @StringRes val messageRes: Int,
    val formatArg: Any? = null,
)

object UpdateFormatters {
    fun formatZoomMultiplier(
        zoomScale: Float,
        locale: Locale = Locale.getDefault(),
    ): String = String.format(locale, "%.1fx", zoomScale)

    fun formatCheckTimestamp(
        epochMillis: Long?,
        locale: Locale = Locale.getDefault(),
    ): String? {
        if (epochMillis == null || epochMillis <= 0L) return null
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        return formatter.format(Date(epochMillis))
    }

    fun formatDownloadBytes(
        downloadedBytes: Long,
        totalBytes: Long,
    ): String {
        val downloadedText = LibraryFormatters.formatFileSize(downloadedBytes.coerceAtLeast(0L))
        return if (totalBytes > 0L) {
            val totalText = LibraryFormatters.formatFileSize(totalBytes)
            "$downloadedText / $totalText"
        } else {
            downloadedText
        }
    }

    fun describeError(error: AppUpdateError): UpdateErrorMessage =
        when (error) {
            AppUpdateError.NetworkUnavailable ->
                UpdateErrorMessage(R.string.settings_update_error_network)

            is AppUpdateError.RateLimited ->
                if (error.retryAfterSeconds != null && error.retryAfterSeconds > 0L) {
                    UpdateErrorMessage(
                        R.string.settings_update_error_rate_limited_seconds,
                        error.retryAfterSeconds,
                    )
                } else {
                    UpdateErrorMessage(R.string.settings_update_error_rate_limited)
                }

            is AppUpdateError.HttpFailure ->
                UpdateErrorMessage(R.string.settings_update_error_http, error.statusCode)

            AppUpdateError.NoCompatibleApkAsset ->
                UpdateErrorMessage(R.string.settings_update_error_no_apk)

            is AppUpdateError.ChecksumMismatch ->
                UpdateErrorMessage(R.string.settings_update_error_checksum)

            is AppUpdateError.InvalidResponse ->
                UpdateErrorMessage(R.string.settings_update_error_invalid_response)

            is AppUpdateError.StorageFailure ->
                UpdateErrorMessage(R.string.settings_update_error_storage)
        }
}
