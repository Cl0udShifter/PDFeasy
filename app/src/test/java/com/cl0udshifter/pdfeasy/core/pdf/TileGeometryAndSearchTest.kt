package com.cl0udshifter.pdfeasy.core.pdf

import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileGeometryAndSearchTest {
    @Test
    fun `full page tile request maps to full bitmap dimensions at origin`() {
        val request = TileRequest(pageIndex = 0, targetWidthPx = 1080, targetHeightPx = 1528)
        val region = TileGeometry.computeRenderRegion(request)

        assertEquals(TileRenderRegion(startX = 0, startY = 0, drawSizeX = 1080, drawSizeY = 1528), region)
    }

    @Test
    fun `cropped tile request scales full page size and offsets negatively into viewport`() {
        val request =
            TileRequest(
                pageIndex = 3,
                targetWidthPx = 500,
                targetHeightPx = 500,
                cropNormalized = NormalizedRect(left = 0.5f, top = 0.25f, right = 1.0f, bottom = 0.75f),
            )
        val region = TileGeometry.computeRenderRegion(request)

        assertEquals(TileRenderRegion(startX = -500, startY = -250, drawSizeX = 1000, drawSizeY = 1000), region)
    }

    @Test
    fun `invalid tile dimensions or crop bounds return null`() {
        assertNull(TileGeometry.computeRenderRegion(TileRequest(0, 0, 100)))
        assertNull(TileGeometry.computeRenderRegion(TileRequest(0, 100, -1)))
        assertNull(
            TileGeometry.computeRenderRegion(
                TileRequest(0, 100, 100, NormalizedRect(0.6f, 0f, 0.4f, 1f)),
            ),
        )
        assertNull(
            TileGeometry.computeRenderRegion(
                TileRequest(0, 100, 100, NormalizedRect(-0.1f, 0f, 0.5f, 1f)),
            ),
        )
    }

    @Test
    fun `normalize device rect orders inverted coordinates and clamps to unit square`() {
        val normalized = TileGeometry.normalizeDeviceRect(800, 1200, 200, -50, 1000)
        assertNotNull(normalized)
        assertEquals(NormalizedRect(0.2f, 0f, 0.8f, 1f), normalized)
        assertNull(TileGeometry.normalizeDeviceRect(0, 0, 10, 10, 0))
    }

    @Test
    fun `search snippet extractor computes clamped window and formats whitespace with ellipsis`() {
        val window = SearchSnippetExtractor.computeWindow(totalChars = 120, matchStart = 50, matchLength = 7)
        assertEquals(26 until 97, window)

        val emptyWindow = SearchSnippetExtractor.computeWindow(totalChars = 0, matchStart = 0, matchLength = 3)
        assertTrue(emptyWindow.isEmpty())

        val snippet =
            SearchSnippetExtractor.formatSnippet(
                rawSlice = "  line one\n\tline   two  ",
                truncatedStart = true,
                truncatedEnd = true,
            )
        assertEquals("...line one line two...", snippet)
    }
}
