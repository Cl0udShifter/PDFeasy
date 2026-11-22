package com.cl0udshifter.pdfeasy.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreModelsTest {
    @Test
    fun `reading progress completion fraction handles empty and valid page counts`() {
        val emptyProgress =
            ReadingProgress(
                documentId = "doc-1",
                currentPageIndex = 0,
                totalPages = 0,
                scrollOffsetNormalized = 0f,
                zoomScale = 1f,
                scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                renderTheme = PageRenderTheme.ORIGINAL,
                updatedAtEpochMillis = 100L,
            )
        assertEquals(0f, emptyProgress.completionFraction)

        val midProgress = emptyProgress.copy(currentPageIndex = 4, totalPages = 10)
        assertEquals(0.5f, midProgress.completionFraction, 0.0001f)

        val endProgress = emptyProgress.copy(currentPageIndex = 12, totalPages = 10)
        assertEquals(1f, endProgress.completionFraction, 0.0001f)
    }

    @Test
    fun `page dimensions aspect ratio handles portrait landscape and zero height`() {
        assertEquals(1f, PageDimensions(0, 612, 0).aspectRatio)
        assertEquals(612f / 792f, PageDimensions(0, 612, 792).aspectRatio, 0.0001f)
        assertEquals(792f / 612f, PageDimensions(1, 792, 612).aspectRatio, 0.0001f)
    }

    @Test
    fun `tile request defaults to full normalized page bounds`() {
        val request = TileRequest(pageIndex = 2, targetWidthPx = 1080, targetHeightPx = 1920)
        assertEquals(NormalizedRect(0f, 0f, 1f, 1f), request.cropNormalized)
    }

    @Test
    fun `core result encapsulates domain errors without exceptions`() {
        val failure: CoreResult<String> = CoreResult.Failure(PdfCoreError.PasswordRequired)
        val success: CoreResult<String> = CoreResult.Success("ok")

        assertTrue(failure is CoreResult.Failure)
        assertEquals(PdfCoreError.PasswordRequired, (failure as CoreResult.Failure).error)
        assertEquals("ok", (success as CoreResult.Success).data)
    }
}
