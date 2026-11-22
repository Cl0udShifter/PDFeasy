package com.cl0udshifter.pdfeasy.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons

private object LibraryHeaderDimens {
    val HeaderTopPadding = 12.dp
    val HeaderBottomPadding = 10.dp
    val PrimaryButtonHeight = 40.dp
    val IconSmall = 16.dp
    val IconMedium = 18.dp
    val IconLarge = 20.dp
    val UpdateBadgeSize = 7.dp
    val UpdateBadgeOffset = 10.dp
    val MinTouchTarget = 48.dp
    val SearchHeight = 44.dp
    val SearchIconPadding = 14.dp
    val ChipHeight = 32.dp
    val ChipSpacing = 8.dp
}

@Composable
fun LibraryHeader(
    onOpenPdfClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasUpdateAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.padding(
                top = LibraryHeaderDimens.HeaderTopPadding,
                bottom = LibraryHeaderDimens.HeaderBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LibrarySettingsButton(
                hasUpdateAvailable = hasUpdateAvailable,
                onClick = onSettingsClick,
            )
            Spacer(modifier = Modifier.size(LibraryHeaderDimens.ChipSpacing))
            Button(
                onClick = onOpenPdfClick,
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(horizontal = LibraryHeaderDimens.SearchIconPadding),
                modifier = Modifier.height(LibraryHeaderDimens.PrimaryButtonHeight),
            ) {
                Icon(
                    painter = PdfEasyIcons.painter(PdfEasyIcons.Add),
                    contentDescription = null,
                    modifier = Modifier.size(LibraryHeaderDimens.IconSmall),
                )
                Spacer(modifier = Modifier.size(LibraryHeaderDimens.ChipSpacing))
                Text(text = stringResource(R.string.library_open_pdf), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LibrarySettingsButton(
    hasUpdateAvailable: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(LibraryHeaderDimens.MinTouchTarget),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(LibraryHeaderDimens.MinTouchTarget)) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Settings),
                contentDescription = stringResource(R.string.settings_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LibraryHeaderDimens.IconLarge),
            )
        }
        if (hasUpdateAvailable) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(
                            x = -LibraryHeaderDimens.UpdateBadgeOffset,
                            y = LibraryHeaderDimens.UpdateBadgeOffset,
                        )
                        .size(LibraryHeaderDimens.UpdateBadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(LibraryHeaderDimens.SearchHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = LibraryHeaderDimens.SearchIconPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LibraryHeaderDimens.IconMedium),
            )
            Spacer(modifier = Modifier.size(LibraryHeaderDimens.ChipSpacing))
            SearchTextFieldBox(query = query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick, modifier = Modifier.size(LibraryHeaderDimens.MinTouchTarget)) {
                    Icon(
                        painter = PdfEasyIcons.painter(PdfEasyIcons.Close),
                        contentDescription = stringResource(R.string.library_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(LibraryHeaderDimens.IconSmall),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTextFieldBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.library_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun LibraryFilterRow(
    totalCount: Int,
    unavailableCount: Int,
    activeFilter: LibraryFilter,
    onFilterSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(LibraryHeaderDimens.ChipSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorialFilterChip(
            label = stringResource(R.string.library_filter_all_format, totalCount),
            selected = activeFilter == LibraryFilter.ALL,
            onClick = { onFilterSelected(LibraryFilter.ALL) },
        )
        EditorialFilterChip(
            label = stringResource(R.string.library_filter_reading),
            selected = activeFilter == LibraryFilter.READING,
            onClick = { onFilterSelected(LibraryFilter.READING) },
        )
        if (unavailableCount > 0) {
            EditorialFilterChip(
                label = stringResource(R.string.library_filter_unavailable_format, unavailableCount),
                selected = activeFilter == LibraryFilter.UNAVAILABLE,
                onClick = { onFilterSelected(LibraryFilter.UNAVAILABLE) },
            )
        }
    }
}

@Composable
private fun EditorialFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.height(LibraryHeaderDimens.ChipHeight),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = LibraryHeaderDimens.SearchIconPadding),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
