package com.cl0udshifter.pdfeasy.ui.library

import java.util.Locale
import kotlin.math.roundToInt

object LibraryFormatters {
    private const val BYTES_PER_KB = 1024.0
    private const val BYTES_PER_MB = BYTES_PER_KB * BYTES_PER_KB
    private const val BYTES_PER_GB = BYTES_PER_MB * BYTES_PER_KB
    private const val PERCENT_SCALE = 100f
    private const val PDF_EXTENSION = ".pdf"
    private val LOCALE_PT_BR = Locale("pt", "BR")

    fun cleanDisplayName(rawName: String): String {
        val trimmed = rawName.trim()
        val withoutExtension =
            if (trimmed.lowercase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
                trimmed.dropLast(PDF_EXTENSION.length).trim()
            } else {
                trimmed
            }
        return withoutExtension.ifEmpty { trimmed }
    }

    fun formatFileSize(sizeBytes: Long): String {
        val safeBytes = sizeBytes.coerceAtLeast(0L).toDouble()
        return when {
            safeBytes < BYTES_PER_KB -> String.format(LOCALE_PT_BR, "%.0f B", safeBytes)
            safeBytes < BYTES_PER_MB -> String.format(LOCALE_PT_BR, "%.0f KB", safeBytes / BYTES_PER_KB)
            safeBytes < BYTES_PER_GB -> String.format(LOCALE_PT_BR, "%.1f MB", safeBytes / BYTES_PER_MB)
            else -> String.format(LOCALE_PT_BR, "%.1f GB", safeBytes / BYTES_PER_GB)
        }
    }

    fun formatCompletionPercent(fraction: Float): String {
        val clamped = fraction.coerceIn(0f, 1f)
        val pct = (clamped * PERCENT_SCALE).roundToInt()
        return "$pct%"
    }
}
