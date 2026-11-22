package com.cl0udshifter.pdfeasy.ui.reader

import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageLayoutGeometryTest {
    @Test
    fun `vertical layout calculates mixed aspect ratios visible range and normalized offsets`() {
        val dimensions =
            mapOf(
                0 to PageDimensions(pageIndex = 0, widthPoints = 500, heightPoints = 1000), // height 2000 at width 1000
                1 to PageDimensions(pageIndex = 1, widthPoints = 1000, heightPoints = 500), // height 500 at width 1000
                2 to PageDimensions(pageIndex = 2, widthPoints = 500, heightPoints = 1000), // height 2000 at width 1000
            )
        val snapshot =
            PageLayoutSnapshot.create(
                PageLayoutSpec(
                    pageCount = 3,
                    pageDimensions = dimensions,
                    viewportWidthPx = 1000f,
                    viewportHeightPx = 1000f,
                    pageSpacingPx = 20f,
                    zoomScale = 1f,
                    scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                ),
            )

        assertEquals(0f, snapshot.pageStartsMainPx[0], 0.01f)
        assertEquals(2000f, snapshot.pageEndsMainPx[0], 0.01f)
        assertEquals(2020f, snapshot.pageStartsMainPx[1], 0.01f)
        assertEquals(2520f, snapshot.pageEndsMainPx[1], 0.01f)
        assertEquals(2540f, snapshot.pageStartsMainPx[2], 0.01f)

        assertEquals(0..0, snapshot.visiblePageIndices(scrollMainPx = 0f))
        assertEquals(0..2, snapshot.visiblePageIndices(scrollMainPx = 1800f))
        assertEquals(1, snapshot.currentPageIndex(scrollMainPx = 1770f)) // center 2270 inside page 1

        val scrollToMidPage2 = snapshot.scrollMainForPage(pageIndex = 2, normalizedOffset = 0.5f)
        assertEquals(3540f, scrollToMidPage2, 0.01f)
        assertEquals(0.5f, snapshot.normalizedOffsetOnPage(2, scrollToMidPage2), 0.001f)
    }

    @Test
    fun `horizontal layout computes crop and search match centering`() {
        val dimensions =
            mapOf(
                0 to PageDimensions(pageIndex = 0, widthPoints = 500, heightPoints = 1000),
                1 to PageDimensions(pageIndex = 1, widthPoints = 500, heightPoints = 1000),
            )
        val snapshot =
            PageLayoutSnapshot.create(
                PageLayoutSpec(
                    pageCount = 2,
                    pageDimensions = dimensions,
                    viewportWidthPx = 1000f,
                    viewportHeightPx = 1000f,
                    pageSpacingPx = 20f,
                    zoomScale = 2f,
                    scrollMode = ScrollMode.CONTINUOUS_HORIZONTAL,
                ),
            )

        val match =
            TextSearchMatch(
                pageIndex = 1,
                matchIndexInPage = 0,
                snippet = "renderizacao",
                boundsNormalized = listOf(NormalizedRect(0.4f, 0.45f, 0.6f, 0.55f)),
            )
        val (targetMain, targetCross) = snapshot.scrollForMatch(match)
        assertTrue(targetMain > 0f)
        assertTrue(targetCross >= 0f)

        val crop =
            snapshot.computeVisibleCropNormalized(
                pageIndex = 1,
                scrollMainPx = targetMain,
                scrollCrossPx = targetCross,
            )
        assertNotNull(crop)
        assertTrue(crop!!.left in 0f..0.6f)
        assertTrue(crop.right in 0.4f..1f)
        assertTrue(crop.top in 0f..0.6f)
        assertTrue(crop.bottom in 0.4f..1f)
    }

    @Test
    fun `multi step pinch zoom in and out keeps focal document point pinned on current page`() {
        val dimensions = (0 until 30).associateWith { PageDimensions(it, 600, 900) }
        val baseSpec =
            PageLayoutSpec(
                pageCount = 30,
                pageDimensions = dimensions,
                viewportWidthPx = 1000f,
                viewportHeightPx = 1500f,
                pageSpacingPx = 24f,
                zoomScale = 1f,
                scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
            )
        val baseSnapshot = PageLayoutSnapshot.create(baseSpec)
        val targetPage = 14
        val centroidMain = 750f
        val startScrollMain = baseSnapshot.pageStartsMainPx[targetPage] + 300f
        val initialFractionOnPage =
            ((startScrollMain + centroidMain) - baseSnapshot.pageStartsMainPx[targetPage]) /
                baseSnapshot.pageHeightsPx[targetPage]

        val afterZoomIn =
            stepThroughZooms(
                baseSpec = baseSpec,
                targetPage = targetPage,
                centroidMain = centroidMain,
                initialState = ZoomTransformOutput(1f, startScrollMain, 0f),
                zoomTargets = listOf(1.15f, 1.32f, 1.55f, 1.85f, 2.2f, 2.6f, 2.8f),
            )
        val zoomedSnap = PageLayoutSnapshot.create(baseSpec.copy(zoomScale = afterZoomIn.zoomScale))
        val zoomedFraction =
            ((afterZoomIn.scrollMainPx + centroidMain) - zoomedSnap.pageStartsMainPx[targetPage]) /
                zoomedSnap.pageHeightsPx[targetPage]
        assertEquals(initialFractionOnPage, zoomedFraction, 0.002f)

        val afterZoomOut =
            stepThroughZooms(
                baseSpec = baseSpec,
                targetPage = targetPage,
                centroidMain = centroidMain,
                initialState = afterZoomIn,
                zoomTargets = listOf(2.4f, 2.0f, 1.6f, 1.25f, 1.0f),
            )
        assertEquals(startScrollMain, afterZoomOut.scrollMainPx, 1f)
    }

    private fun stepThroughZooms(
        baseSpec: PageLayoutSpec,
        targetPage: Int,
        centroidMain: Float,
        initialState: ZoomTransformOutput,
        zoomTargets: List<Float>,
    ): ZoomTransformOutput {
        val baseSnapshot = PageLayoutSnapshot.create(baseSpec)
        var current = initialState
        for (nextZoom in zoomTargets) {
            current =
                ReaderZoomMath.transformScrollForZoom(
                    baseSnapshot = baseSnapshot,
                    input =
                        ZoomTransformInput(
                            oldZoom = current.zoomScale,
                            newZoom = nextZoom,
                            oldScrollMainPx = current.scrollMainPx,
                            oldScrollCrossPx = current.scrollCrossPx,
                            centroidMainPx = centroidMain,
                            centroidCrossPx = 500f,
                        ),
                )
            val snapAtZoom = PageLayoutSnapshot.create(baseSpec.copy(zoomScale = current.zoomScale))
            assertEquals(targetPage, snapAtZoom.currentPageIndex(current.scrollMainPx))
        }
        return current
    }

    @Test
    fun `horizontal zoom from centered landscape page anchors cross axis without edge jump`() {
        val dimensions = mapOf(0 to PageDimensions(pageIndex = 0, widthPoints = 1000, heightPoints = 500))
        val baseSpec =
            PageLayoutSpec(
                pageCount = 1,
                pageDimensions = dimensions,
                viewportWidthPx = 1000f,
                viewportHeightPx = 1000f,
                pageSpacingPx = 20f,
                zoomScale = 1f,
                scrollMode = ScrollMode.CONTINUOUS_HORIZONTAL,
            )
        val baseSnapshot = PageLayoutSnapshot.create(baseSpec)
        val out =
            ReaderZoomMath.transformScrollForZoom(
                baseSnapshot = baseSnapshot,
                input =
                    ZoomTransformInput(
                        oldZoom = 1f,
                        newZoom = 3f,
                        oldScrollMainPx = 0f,
                        oldScrollCrossPx = 0f,
                        centroidMainPx = 500f,
                        centroidCrossPx = 500f,
                    ),
            )
        // Height scales from 500 to 1500 inside 1000px viewport -> centered cross scroll is 250px
        assertEquals(250f, out.scrollCrossPx, 0.5f)
        assertEquals(1000f, out.scrollMainPx, 0.5f)
    }

    @Test
    fun `vertical multi page layout clears top hud and restores pages below padding`() {
        val dimensions =
            (0 until 5).associateWith { PageDimensions(pageIndex = it, widthPoints = 595, heightPoints = 842) }
        val spec =
            PageLayoutSpec(
                pageCount = 5,
                pageDimensions = dimensions,
                viewportWidthPx = 1080f,
                viewportHeightPx = 2400f,
                pageSpacingPx = 20f,
                zoomScale = 1f,
                scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                mainPaddingStartPx = 220f,
                mainPaddingEndPx = 300f,
            )
        val snapshot = PageLayoutSnapshot.create(spec)

        // Page 0 starts below top safe + HUD clearance, not under the status bar at y = 0
        assertEquals(220f, snapshot.pageScreenRect(pageIndex = 0, scrollMainPx = 0f, scrollCrossPx = 0f).top, 0.1f)
        assertEquals(0f, snapshot.scrollMainForPage(pageIndex = 0, normalizedOffset = 0f), 0.1f)
        assertEquals(0f, snapshot.normalizedOffsetOnPage(pageIndex = 0, scrollMainPx = 0f), 0.001f)

        // Restoring page 2 at top places page 2 cleanly at mainPaddingStartPx below the HUD
        val page2TopScroll = snapshot.scrollMainForPage(pageIndex = 2, normalizedOffset = 0f)
        val page2Rect = snapshot.pageScreenRect(pageIndex = 2, scrollMainPx = page2TopScroll, scrollCrossPx = 0f)
        assertEquals(220f, page2Rect.top, 0.1f)
        assertEquals(2, snapshot.currentPageIndex(page2TopScroll))
    }

    @Test
    fun `single short page centers vertically and zooms smoothly without focal jump`() {
        val dimensions = mapOf(0 to PageDimensions(pageIndex = 0, widthPoints = 595, heightPoints = 842))
        val baseSpec =
            PageLayoutSpec(
                pageCount = 1,
                pageDimensions = dimensions,
                viewportWidthPx = 1080f,
                viewportHeightPx = 2400f,
                pageSpacingPx = 20f,
                zoomScale = 1f,
                scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                mainPaddingStartPx = 220f,
                mainPaddingEndPx = 300f,
            )
        val baseSnapshot = PageLayoutSnapshot.create(baseSpec)
        val rect = baseSnapshot.pageScreenRect(pageIndex = 0, scrollMainPx = 0f, scrollCrossPx = 0f)
        val usableMain = 2400f - 220f - 300f
        val expectedTop = 220f + (usableMain - rect.height) * 0.5f

        assertEquals(0f, baseSnapshot.maxScrollMainPx, 0.01f)
        assertEquals(expectedTop, rect.top, 0.5f)
        assertTrue(rect.bottom <= 2400f - 300f)

        // Pinch centered at screen y = 600 (page top region) while crossing from centered -> scrollable
        val centroidY = 600f
        val focalFractionBefore = (centroidY - rect.top) / rect.height
        val zoomed =
            ReaderZoomMath.transformScrollForZoom(
                baseSnapshot = baseSnapshot,
                input =
                    ZoomTransformInput(
                        oldZoom = 1f,
                        newZoom = 2f,
                        oldScrollMainPx = 0f,
                        oldScrollCrossPx = 0f,
                        centroidMainPx = centroidY,
                        centroidCrossPx = 540f,
                    ),
            )
        val zoomedSnap = PageLayoutSnapshot.create(baseSpec.copy(zoomScale = zoomed.zoomScale))
        val zoomedRect =
            zoomedSnap.pageScreenRect(pageIndex = 0, scrollMainPx = zoomed.scrollMainPx, scrollCrossPx = 0f)
        val focalFractionAfter = (centroidY - zoomedRect.top) / zoomedRect.height

        assertEquals(zoomedSnap.maxScrollMainPx, ReaderZoomMath.scaledMaxScrollMain(baseSnapshot, 2f), 0.5f)
        assertEquals(focalFractionBefore, focalFractionAfter, 0.01f)
    }
}
