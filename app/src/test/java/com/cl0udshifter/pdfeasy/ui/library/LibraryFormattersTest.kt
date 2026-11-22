package com.cl0udshifter.pdfeasy.ui.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryFormattersTest {
    @Test
    fun `cleanDisplayName removes pdf suffix case insensitively while keeping fallback`() {
        assertEquals("Arquitetura Limpa", LibraryFormatters.cleanDisplayName("Arquitetura Limpa.pdf"))
        assertEquals("Manual", LibraryFormatters.cleanDisplayName("  Manual.PDF  "))
        assertEquals(".pdf", LibraryFormatters.cleanDisplayName(".pdf"))
        assertEquals("Relatorio Final", LibraryFormatters.cleanDisplayName("Relatorio Final"))
    }

    @Test
    fun `formatFileSize formats bytes kilobytes megabytes and gigabytes`() {
        assertEquals("512 B", LibraryFormatters.formatFileSize(512L))
        assertEquals("840 KB", LibraryFormatters.formatFileSize(840L * 1024L))
        assertEquals("6,4 MB", LibraryFormatters.formatFileSize((6.4 * 1024.0 * 1024.0).toLong()))
        assertEquals("1,5 GB", LibraryFormatters.formatFileSize((1.5 * 1024.0 * 1024.0 * 1024.0).toLong()))
    }

    @Test
    fun `formatCompletionPercent clamps bounds cleanly`() {
        assertEquals("0%", LibraryFormatters.formatCompletionPercent(-0.2f))
        assertEquals("42%", LibraryFormatters.formatCompletionPercent(0.42f))
        assertEquals("100%", LibraryFormatters.formatCompletionPercent(1.4f))
    }
}
