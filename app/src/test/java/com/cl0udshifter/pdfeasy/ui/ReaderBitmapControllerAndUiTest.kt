package com.cl0udshifter.pdfeasy.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.model.Bookmark
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.PdfDocumentItem
import com.cl0udshifter.pdfeasy.core.model.ReadingProgress
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import com.cl0udshifter.pdfeasy.core.pdf.PdfSession
import com.cl0udshifter.pdfeasy.ui.library.LibraryScreenCallbacks
import com.cl0udshifter.pdfeasy.ui.library.LibraryScreenContent
import com.cl0udshifter.pdfeasy.ui.library.LibraryUiState
import com.cl0udshifter.pdfeasy.ui.reader.BookmarksBottomSheet
import com.cl0udshifter.pdfeasy.ui.reader.PasswordPromptDialog
import com.cl0udshifter.pdfeasy.ui.reader.PasswordPromptState
import com.cl0udshifter.pdfeasy.ui.reader.ReaderBitmapController
import com.cl0udshifter.pdfeasy.ui.reader.ReaderHudCallbacks
import com.cl0udshifter.pdfeasy.ui.reader.ReaderHudOverlay
import com.cl0udshifter.pdfeasy.ui.reader.ReaderSearchState
import com.cl0udshifter.pdfeasy.ui.reader.ReaderUiState
import com.cl0udshifter.pdfeasy.ui.reader.ReaderViewport
import com.cl0udshifter.pdfeasy.ui.reader.ReaderViewportCallbacks
import com.cl0udshifter.pdfeasy.ui.reader.ReaderViewportParams
import com.cl0udshifter.pdfeasy.ui.reader.ScrollRestoreTarget
import com.cl0udshifter.pdfeasy.ui.reader.TileSyncRequest
import com.cl0udshifter.pdfeasy.ui.theme.LightExtendedColors
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyTheme
import com.cl0udshifter.pdfeasy.ui.theme.ReaderRenderColorFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderBitmapControllerAndUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryEmptyAndPopulatedStatesRenderBadgesAndProgress() {
        var openClicks = 0
        val emptyCallbacks = emptyLibraryCallbacks(onOpenPdf = { openClicks += 1 })

        composeRule.setContent {
            PdfEasyTheme(darkTheme = false) {
                LibraryScreenContent(
                    uiState = LibraryUiState(totalDocumentCount = 0),
                    callbacks = emptyCallbacks,
                )
            }
        }

        composeRule.onNodeWithText("Sua estante está vazia").assertIsDisplayed()
        composeRule.onNodeWithText("Escolher PDF").performClick()
        assertEquals(1, openClicks)
    }

    @Test
    fun libraryPopulatedGridDisplaysCleanTitlesProgressAndStatusBadges() {
        val sampleItems =
            listOf(
                itemWith(
                    id = "1",
                    name = "Arquitetura Limpa.pdf",
                    locked = true,
                    currentPage = 83,
                ),
                itemWith(
                    id = "2",
                    name = "Manual Movido.pdf",
                    availability = DocumentAvailability.MISSING_OR_MOVED,
                ),
            )
        composeRule.setContent {
            PdfEasyTheme(darkTheme = false) {
                LibraryScreenContent(
                    uiState = LibraryUiState(items = sampleItems, totalDocumentCount = 2, unavailableCount = 1),
                    callbacks = emptyLibraryCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Arquitetura Limpa").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
        composeRule.onNodeWithText("Senha").assertIsDisplayed()
        composeRule.onNodeWithText("Manual Movido").assertIsDisplayed()
        composeRule.onNodeWithText("Movido").assertIsDisplayed()
    }

    @Test
    fun readerHudThemeSwitcherSearchAndPasswordDialogRespondCorrectly() {
        var selectedTheme: PageRenderTheme? = null
        var nextClicked = 0
        var submittedPassword: String? = null

        val state =
            ReaderUiState(
                documentId = "doc-1",
                uriString = "content://docs/1.pdf",
                displayName = "Notas.pdf",
                isLoading = false,
                pageCount = 120,
                currentPageIndex = 9,
                search =
                    ReaderSearchState(
                        isOpen = false,
                        query = "tile",
                        matches = listOf(TextSearchMatch(9, 0, "tile", emptyList())),
                        currentMatchIndex = 0,
                    ),
            )
        val callbacks =
            noopHudCallbacks(onNextMatch = { nextClicked += 1 }).copy(
                onSelectRenderTheme = { selectedTheme = it },
            )

        composeRule.setContent {
            PdfEasyTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderHudOverlay(uiState = state, callbacks = callbacks)
                    PasswordPromptDialog(
                        displayName = "Protegido.pdf",
                        promptState = PasswordPromptState(isVisible = true, isInvalidAttempt = true),
                        onDismissToLibrary = {},
                        onSubmitPassword = { submittedPassword = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("10 / 120").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_scrubber_thumb", useUnmergedTree = true).assertHeightIsEqualTo(20.dp)
        composeRule.onNodeWithText("Sépia").performClick()
        assertEquals(PageRenderTheme.SEPIA, selectedTheme)
        composeRule.onNodeWithText("Senha incorreta. Tente novamente.").assertIsDisplayed()
        assertNull(submittedPassword)
        assertEquals(0, nextClicked)
    }

    @Test
    fun readerSearchDockAndBookmarksSheetTriggerCallbacks() {
        var nextClicked = 0
        var selectedPage = -1

        val searchState =
            ReaderUiState(
                documentId = "doc-1",
                uriString = "content://docs/1.pdf",
                displayName = "Notas.pdf",
                isLoading = false,
                pageCount = 50,
                search =
                    ReaderSearchState(
                        isOpen = true,
                        query = "pdf",
                        matches =
                            listOf(
                                TextSearchMatch(2, 0, "pdf", emptyList()),
                                TextSearchMatch(4, 0, "pdf", emptyList()),
                            ),
                        currentMatchIndex = 0,
                    ),
            )

        composeRule.setContent {
            PdfEasyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderHudOverlay(
                        uiState = searchState,
                        callbacks = noopHudCallbacks(onNextMatch = { nextClicked += 1 }),
                    )
                    BookmarksBottomSheet(
                        isOpen = true,
                        bookmarks =
                            listOf(
                                Bookmark(
                                    id = 1L,
                                    documentId = "doc-1",
                                    pageIndex = 6,
                                    label = "Resumo",
                                    createdAtEpochMillis = 1L,
                                ),
                            ),
                        currentPageIndex = 2,
                        isCurrentPageBookmarked = false,
                        onDismiss = {},
                        onToggleCurrentPageBookmark = {},
                        onSelectBookmark = { selectedPage = it },
                        onDeleteBookmark = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("1/2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Próxima ocorrência").performClick()
        assertEquals(1, nextClicked)
        composeRule.onNodeWithText("Resumo").performClick()
        assertEquals(6, selectedPage)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun bitmapControllerRendersDetailTilesAndRecyclesOffscreenBitmaps() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val session = FakeBitmapSession(pageCount = 20)
            val controller = ReaderBitmapController(scope = scope, recycleDelayMs = 0L)

            val dims = (0 until 20).associateWith { PageDimensions(it, 600, 800) }
            controller.syncVisiblePages(
                TileSyncRequest(
                    session = session,
                    visibleRange = 0..1,
                    pageDimensions = dims,
                    viewportWidthPx = 800,
                    zoomScale = 2.0f,
                    primaryCrop = 0 to NormalizedRect(0.1f, 0.1f, 0.6f, 0.6f),
                ),
            )
            advanceUntilIdle()

            assertTrue(controller.basePages.containsKey(0))
            assertTrue(controller.basePages.containsKey(2))
            assertTrue(controller.detailTiles.containsKey(0))

            controller.syncVisiblePages(
                TileSyncRequest(
                    session = session,
                    visibleRange = 10..11,
                    pageDimensions = dims,
                    viewportWidthPx = 800,
                    zoomScale = 1.0f,
                    primaryCrop = null,
                ),
            )
            advanceUntilIdle()

            assertTrue(session.recycledBitmaps.isNotEmpty())
            assertTrue(controller.detailTiles.isEmpty())

            controller.releaseSessionBitmaps(session)
            assertTrue(controller.basePages.isEmpty())
            scope.cancel()
        }

    @Test
    fun gpuColorFiltersAndMainActivityLaunchWorkCleanly() {
        assertNull(ReaderRenderColorFilters.colorFilterFor(PageRenderTheme.ORIGINAL))
        assertNotNull(ReaderRenderColorFilters.colorFilterFor(PageRenderTheme.DARK))
        assertNotNull(ReaderRenderColorFilters.colorFilterFor(PageRenderTheme.SEPIA))
        assertEquals(
            LightExtendedColors.readerBackdropSepia,
            ReaderRenderColorFilters.backdropColorFor(PageRenderTheme.SEPIA, LightExtendedColors),
        )

        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://docs/missing.pdf"), "application/pdf")
            }
        val controller = Robolectric.buildActivity(MainActivity::class.java, viewIntent).setup()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun readerViewportPinchZoomPreservesCurrentPageWithoutJumping() {
        val observedPages = mutableListOf<Int>()
        var latestZoom = 1f
        val dimensions = (0 until 20).associateWith { PageDimensions(it, 600, 900) }
        val controllerScope = CoroutineScope(SupervisorJob())
        val controller = ReaderBitmapController(scope = controllerScope, recycleDelayMs = 0L)

        try {
            composeRule.setContent {
                var pendingTarget by remember {
                    mutableStateOf<ScrollRestoreTarget?>(
                        ScrollRestoreTarget(pageIndex = 10, offsetNormalized = 0.2f, zoomScale = 1f, token = 1L),
                    )
                }
                Box(modifier = Modifier.size(400.dp, 600.dp)) {
                    ReaderViewport(
                        params =
                            ReaderViewportParams(
                                pageCount = 20,
                                pageDimensions = dimensions,
                                scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                                renderTheme = PageRenderTheme.ORIGINAL,
                                searchMatches = emptyList(),
                                activeSearchMatch = null,
                                pendingScrollTarget = pendingTarget,
                            ),
                        bitmapController = controller,
                        callbacks =
                            ReaderViewportCallbacks(
                                onScrollTargetConsumed = { pendingTarget = null },
                                onToggleHud = {},
                                onViewportChanged = { page, _, zoom, _, _ ->
                                    observedPages.add(page)
                                    latestZoom = zoom
                                },
                            ),
                    )
                }
            }

            composeRule.waitForIdle()
            assertEquals(10, observedPages.lastOrNull())
            observedPages.clear()

            composeRule.onNodeWithTag("reader_viewport").performTouchInput {
                pinch(
                    start0 = Offset(centerX - 30f, centerY),
                    end0 = Offset(centerX - 150f, centerY),
                    start1 = Offset(centerX + 30f, centerY),
                    end1 = Offset(centerX + 150f, centerY),
                )
            }
            composeRule.waitForIdle()

            assertTrue(latestZoom > 1.5f)
            assertTrue(observedPages.isNotEmpty())
            assertTrue(observedPages.all { it in 9..11 })
        } finally {
            controllerScope.cancel()
        }
    }

    private fun emptyLibraryCallbacks(onOpenPdf: () -> Unit = {}): LibraryScreenCallbacks =
        LibraryScreenCallbacks(
            onOpenPdfClick = onOpenPdf,
            onSearchQueryChange = {},
            onClearSearch = {},
            onFilterSelected = {},
            onDocumentClick = {},
            onDocumentOptionsClick = {},
        )

    private fun noopHudCallbacks(onNextMatch: () -> Unit = {}): ReaderHudCallbacks =
        ReaderHudCallbacks(
            onBackClick = {},
            onOpenBookmarksSheet = {},
            onSelectRenderTheme = {},
            onToggleScrollMode = {},
            onJumpToPage = {},
            onToggleBookmark = {},
            onOpenSearch = {},
            onCloseSearch = {},
            onSearchQueryChange = {},
            onPreviousMatch = {},
            onNextMatch = onNextMatch,
        )

    private fun itemWith(
        id: String,
        name: String,
        locked: Boolean = false,
        availability: DocumentAvailability = DocumentAvailability.AVAILABLE,
        currentPage: Int? = null,
    ): LibraryItemWithProgress =
        LibraryItemWithProgress(
            document =
                PdfDocumentItem(
                    id = id,
                    uriString = "content://docs/$id",
                    displayName = name,
                    sizeBytes = 1_500_000L,
                    pageCount = 200,
                    isPasswordProtected = locked,
                    availability = availability,
                    addedAtEpochMillis = 100L,
                    lastOpenedAtEpochMillis = currentPage?.let { 200L },
                ),
            progress =
                currentPage?.let {
                    ReadingProgress(
                        documentId = id,
                        currentPageIndex = it,
                        totalPages = 200,
                        scrollOffsetNormalized = 0f,
                        zoomScale = 1f,
                        scrollMode = ScrollMode.CONTINUOUS_VERTICAL,
                        renderTheme = PageRenderTheme.ORIGINAL,
                        updatedAtEpochMillis = 200L,
                    )
                },
        )

    private class FakeBitmapSession(
        override val pageCount: Int,
    ) : PdfSession {
        override val documentUri: String = "content://docs/1.pdf"
        val recycledBitmaps = mutableListOf<Bitmap>()

        override suspend fun getPageDimensions(pageIndex: Int): CoreResult<PageDimensions> =
            CoreResult.Success(PageDimensions(pageIndex, 600, 800))

        override suspend fun renderTile(request: TileRequest): CoreResult<Bitmap> =
            CoreResult.Success(
                Bitmap.createBitmap(request.targetWidthPx, request.targetHeightPx, Bitmap.Config.ARGB_8888),
            )

        override fun recycleBitmap(bitmap: Bitmap) {
            recycledBitmaps.add(bitmap)
        }

        override fun search(query: String): Flow<TextSearchMatch> = emptyFlow()

        override fun close() = Unit
    }
}
