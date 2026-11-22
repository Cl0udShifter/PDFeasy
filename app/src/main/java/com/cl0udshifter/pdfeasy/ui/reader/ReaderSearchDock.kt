package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons

private object SearchDockDimens {
    val DockCardPadHorizontal = 6.dp
    val DockCardPadVertical = 4.dp
    val FieldHeight = 40.dp
    val FieldPadHorizontal = 10.dp
    val ItemGap = 4.dp
    val InnerGap = 6.dp
    val MinTouchSize = 48.dp
    val IconSize = 18.dp
    val SmallIconSize = 14.dp
    val SpinnerSize = 14.dp
    val SpinnerStroke = 1.5.dp
}

@Composable
fun ReaderSearchDockRow(
    searchState: ReaderSearchState,
    onQueryChange: (String) -> Unit,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SearchDockDimens.DockCardPadHorizontal,
                    vertical = SearchDockDimens.DockCardPadVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchDockDimens.ItemGap),
    ) {
        ReaderSearchInputBox(
            searchState = searchState,
            onQueryChange = onQueryChange,
            onNextMatch = onNextMatch,
            modifier = Modifier.weight(1f),
        )
        ReaderSearchNavButtons(
            hasMatches = searchState.matches.isNotEmpty(),
            onPreviousMatch = onPreviousMatch,
            onNextMatch = onNextMatch,
            onClose = onClose,
        )
    }
}

@Composable
private fun ReaderSearchInputBox(
    searchState: ReaderSearchState,
    onQueryChange: (String) -> Unit,
    onNextMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.height(SearchDockDimens.FieldHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = SearchDockDimens.FieldPadHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SearchDockDimens.InnerGap),
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SearchDockDimens.SmallIconSize),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchState.query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.reader_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = searchState.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            SearchStatusReadout(searchState = searchState)
        }
    }
}

@Composable
private fun SearchStatusReadout(searchState: ReaderSearchState) {
    if (searchState.isSearching) {
        CircularProgressIndicator(
            modifier = Modifier.size(SearchDockDimens.SpinnerSize),
            strokeWidth = SearchDockDimens.SpinnerStroke,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    val countText =
        when {
            searchState.matches.isNotEmpty() && searchState.currentMatchIndex >= 0 ->
                stringResource(
                    R.string.reader_search_count_format,
                    searchState.currentMatchIndex + 1,
                    searchState.matches.size,
                )
            searchState.query.isNotBlank() && !searchState.isSearching ->
                stringResource(R.string.reader_search_empty)
            else -> ""
        }
    if (countText.isNotEmpty()) {
        Text(
            text = countText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReaderSearchNavButtons(
    hasMatches: Boolean,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
) {
    val navTint =
        if (hasMatches) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(
        onClick = onPreviousMatch,
        enabled = hasMatches,
        modifier = Modifier.size(SearchDockDimens.MinTouchSize),
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.ChevronUp),
            contentDescription = stringResource(R.string.reader_search_prev),
            tint = navTint,
            modifier = Modifier.size(SearchDockDimens.IconSize),
        )
    }
    IconButton(
        onClick = onNextMatch,
        enabled = hasMatches,
        modifier = Modifier.size(SearchDockDimens.MinTouchSize),
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.ChevronDown),
            contentDescription = stringResource(R.string.reader_search_next),
            tint = navTint,
            modifier = Modifier.size(SearchDockDimens.IconSize),
        )
    }
    IconButton(
        onClick = onClose,
        modifier = Modifier.size(SearchDockDimens.MinTouchSize),
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.Close),
            contentDescription = stringResource(R.string.reader_search_close),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SearchDockDimens.IconSize),
        )
    }
}
