package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons
import com.cl0udshifter.pdfeasy.ui.library.LibraryFormatters
import kotlin.math.roundToInt

private object HudDimens {
    val TopMarginHorizontal = 14.dp
    val TopMarginVertical = 8.dp
    val PillPadHorizontal = 14.dp
    val PillPadVertical = 8.dp
    val PillMinHeight = 38.dp
    val TopGap = 10.dp
    val DockMaxWidth = 440.dp
    val DockPadding = 12.dp
    val DockInnerGap = 8.dp
    val DockElevation = 10.dp
    val TopElevation = 6.dp
    val ThemePillPad = 4.dp
    val SwatchGap = 6.dp
    val DockCardPadHorizontal = 6.dp
    val DockCardPadVertical = 4.dp
    val MinTouchSize = 48.dp
    val IconSize = 18.dp
    val SmallIconSize = 14.dp
    val BubblePadHorizontal = 12.dp
    val BubblePadVertical = 6.dp
    val ScrubberThumbWidth = 4.dp
    val ScrubberThumbHeight = 20.dp
    val ScrubberTrackHeight = 6.dp
    val ScrubberThumbGap = 4.dp
    val ScrubberTrackRadius = 3.dp
    const val HUD_ANIM_DURATION_MS = 180
    const val PILL_SURFACE_ALPHA = 0.96f
}

@Immutable
data class ReaderHudCallbacks(
    val onBackClick: () -> Unit,
    val onOpenBookmarksSheet: () -> Unit,
    val onSelectRenderTheme: (PageRenderTheme) -> Unit,
    val onToggleScrollMode: () -> Unit,
    val onJumpToPage: (Int) -> Unit,
    val onToggleBookmark: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onPreviousMatch: () -> Unit,
    val onNextMatch: () -> Unit,
)

@Composable
fun BoxScope.ReaderHudOverlay(
    uiState: ReaderUiState,
    callbacks: ReaderHudCallbacks,
) {
    val animSpec = tween<Float>(durationMillis = HudDimens.HUD_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
    val slideSpec = tween<IntOffset>(durationMillis = HudDimens.HUD_ANIM_DURATION_MS, easing = FastOutSlowInEasing)

    AnimatedVisibility(
        visible = uiState.hudVisible,
        enter = fadeIn(animSpec) + slideInVertically(slideSpec) { -it / 2 },
        exit = fadeOut(animSpec) + slideOutVertically(slideSpec) { -it / 2 },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        ReaderTopBar(
            displayName = uiState.displayName,
            currentPageIndex = uiState.currentPageIndex,
            pageCount = uiState.pageCount,
            hasBookmarks = uiState.bookmarks.isNotEmpty(),
            onOpenBookmarksSheet = callbacks.onOpenBookmarksSheet,
        )
    }

    AnimatedVisibility(
        visible = uiState.hudVisible,
        enter = fadeIn(animSpec) + slideInVertically(slideSpec) { it / 2 },
        exit = fadeOut(animSpec) + slideOutVertically(slideSpec) { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        ReaderBottomDock(uiState = uiState, callbacks = callbacks)
    }
}

@Composable
private fun ReaderTopBar(
    displayName: String,
    currentPageIndex: Int,
    pageCount: Int,
    hasBookmarks: Boolean,
    onOpenBookmarksSheet: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = HudDimens.TopMarginHorizontal, vertical = HudDimens.TopMarginVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderTopTitlePill(displayName = displayName, modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.width(HudDimens.TopGap))
        ReaderTopPagePill(
            currentPageIndex = currentPageIndex,
            pageCount = pageCount,
            hasBookmarks = hasBookmarks,
            onOpenBookmarksSheet = onOpenBookmarksSheet,
        )
    }
}

@Composable
private fun ReaderTopTitlePill(
    displayName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = HudDimens.PILL_SURFACE_ALPHA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = HudDimens.TopElevation,
        modifier = modifier,
    ) {
        Text(
            text = LibraryFormatters.cleanDisplayName(displayName),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(
                    horizontal = HudDimens.PillPadHorizontal,
                    vertical = HudDimens.PillPadVertical,
                ),
        )
    }
}

@Composable
private fun ReaderTopPagePill(
    currentPageIndex: Int,
    pageCount: Int,
    hasBookmarks: Boolean,
    onOpenBookmarksSheet: () -> Unit,
) {
    val safePageDisplay = if (pageCount > 0) currentPageIndex + 1 else 0
    val bookmarksDesc = stringResource(R.string.reader_bookmarks_open)

    Surface(
        onClick = onOpenBookmarksSheet,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = HudDimens.PILL_SURFACE_ALPHA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = HudDimens.TopElevation,
        modifier = Modifier.height(HudDimens.PillMinHeight).semantics { contentDescription = bookmarksDesc },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HudDimens.SwatchGap),
            modifier = Modifier.padding(horizontal = HudDimens.PillPadHorizontal),
        ) {
            if (hasBookmarks) {
                Icon(
                    painter = PdfEasyIcons.painter(PdfEasyIcons.BookmarksList),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(HudDimens.SmallIconSize),
                )
            }
            Text(
                text = stringResource(R.string.reader_page_counter_format, safePageDisplay, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ReaderBottomDock(
    uiState: ReaderUiState,
    callbacks: ReaderHudCallbacks,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPageIndex by remember(uiState.currentPageIndex) { mutableIntStateOf(uiState.currentPageIndex) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = HudDimens.DockMaxWidth)
                .navigationBarsPadding()
                .padding(horizontal = HudDimens.DockPadding, vertical = HudDimens.DockPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HudDimens.DockInnerGap),
    ) {
        if (isScrubbing && uiState.pageCount > 0) {
            ScrubberPageBubble(pageNumber = scrubPageIndex + 1, pageCount = uiState.pageCount)
        }
        if (!uiState.search.isOpen) {
            ReaderThemeAndScrollPill(
                renderTheme = uiState.renderTheme,
                scrollMode = uiState.scrollMode,
                onSelectRenderTheme = callbacks.onSelectRenderTheme,
                onToggleScrollMode = callbacks.onToggleScrollMode,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = HudDimens.DockElevation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.search.isOpen) {
                ReaderSearchDockRow(
                    searchState = uiState.search,
                    onQueryChange = callbacks.onSearchQueryChange,
                    onPreviousMatch = callbacks.onPreviousMatch,
                    onNextMatch = callbacks.onNextMatch,
                    onClose = callbacks.onCloseSearch,
                )
            } else {
                ReaderMainDockRow(
                    pageCount = uiState.pageCount,
                    sliderPage = if (isScrubbing) scrubPageIndex else uiState.currentPageIndex,
                    isCurrentPageBookmarked = uiState.isCurrentPageBookmarked,
                    callbacks = callbacks,
                    onScrubEvent = { nextPage, finished ->
                        if (finished) {
                            isScrubbing = false
                            callbacks.onJumpToPage(scrubPageIndex)
                        } else {
                            isScrubbing = true
                            scrubPageIndex = nextPage
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ScrubberPageBubble(
    pageNumber: Int,
    pageCount: Int,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shadowElevation = HudDimens.TopElevation,
    ) {
        Text(
            text = stringResource(R.string.reader_scrubber_bubble_format, pageNumber, pageCount),
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.padding(
                    horizontal = HudDimens.BubblePadHorizontal,
                    vertical = HudDimens.BubblePadVertical,
                ),
        )
    }
}

@Composable
private fun ReaderMainDockRow(
    pageCount: Int,
    sliderPage: Int,
    isCurrentPageBookmarked: Boolean,
    callbacks: ReaderHudCallbacks,
    onScrubEvent: (pageIndex: Int, finished: Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = HudDimens.DockCardPadHorizontal, vertical = HudDimens.DockCardPadVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = callbacks.onBackClick, modifier = Modifier.size(HudDimens.MinTouchSize)) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.ArrowBack),
                contentDescription = stringResource(R.string.reader_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(HudDimens.IconSize),
            )
        }
        ReaderScrubberSlider(
            pageCount = pageCount,
            sliderPage = sliderPage,
            onScrubEvent = onScrubEvent,
            modifier = Modifier.weight(1f).padding(horizontal = HudDimens.ThemePillPad),
        )
        IconButton(onClick = callbacks.onOpenSearch, modifier = Modifier.size(HudDimens.MinTouchSize)) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Search),
                contentDescription = stringResource(R.string.reader_search_action),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(HudDimens.IconSize),
            )
        }
        ReaderBookmarkDockButton(
            isBookmarked = isCurrentPageBookmarked,
            onToggleBookmark = callbacks.onToggleBookmark,
            onOpenBookmarksSheet = callbacks.onOpenBookmarksSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScrubberSlider(
    pageCount: Int,
    sliderPage: Int,
    onScrubEvent: (pageIndex: Int, finished: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxIndex = (pageCount - 1).coerceAtLeast(0)
    val enabled = pageCount > 1
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    Slider(
        value = sliderPage.coerceIn(0, maxIndex).toFloat(),
        onValueChange = { raw -> onScrubEvent(raw.roundToInt().coerceIn(0, maxIndex), false) },
        onValueChangeFinished = { onScrubEvent(sliderPage, true) },
        valueRange = 0f..maxIndex.toFloat().coerceAtLeast(1f),
        enabled = enabled,
        colors = sliderColors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = sliderColors,
                enabled = enabled,
                thumbSize = DpSize(HudDimens.ScrubberThumbWidth, HudDimens.ScrubberThumbHeight),
                modifier = Modifier.testTag("reader_scrubber_thumb"),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                colors = sliderColors,
                enabled = enabled,
                sliderState = sliderState,
                modifier = Modifier.height(HudDimens.ScrubberTrackHeight),
                drawStopIndicator = null,
                thumbTrackGapSize = HudDimens.ScrubberThumbGap,
                trackInsideCornerSize = HudDimens.ScrubberTrackRadius,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderBookmarkDockButton(
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenBookmarksSheet: () -> Unit,
) {
    val bg = if (isBookmarked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val tint =
        if (isBookmarked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val icon = if (isBookmarked) PdfEasyIcons.BookmarkFilled else PdfEasyIcons.BookmarkOutline
    val desc = stringResource(R.string.reader_bookmark_toggle)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(HudDimens.MinTouchSize)
                .clip(MaterialTheme.shapes.small)
                .background(bg)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onToggleBookmark,
                    onLongClick = onOpenBookmarksSheet,
                    onLongClickLabel = stringResource(R.string.reader_bookmarks_open),
                )
                .semantics { contentDescription = desc },
    ) {
        Icon(
            painter = PdfEasyIcons.painter(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(HudDimens.IconSize),
        )
    }
}
