package com.cl0udshifter.pdfeasy.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyExtendedColors
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens

private object LibraryStateDimens {
    val IconEmpty = 38.dp
    val IconSmall = 16.dp
    val IconMedium = 18.dp
    val MinTouchTarget = 48.dp
    val GapSmall = 8.dp
    val GapMedium = 12.dp
    val PaddingHorizontal = 20.dp
    val SheetBottomPadding = 28.dp
    val NoticePadding = 14.dp
    val EmptySubtitleMaxWidth = 260.dp
    const val TITLE_MAX_LINES = 2
}

@Composable
fun LibraryEmptyState(
    onChoosePdfClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(LibraryStateDimens.PaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.DocumentEmpty),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(LibraryStateDimens.IconEmpty),
        )
        Spacer(modifier = Modifier.height(LibraryStateDimens.GapMedium))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(LibraryStateDimens.GapSmall))
        Text(
            text = stringResource(R.string.library_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = LibraryStateDimens.EmptySubtitleMaxWidth),
        )
        Spacer(modifier = Modifier.height(LibraryStateDimens.PaddingHorizontal))
        Button(
            onClick = onChoosePdfClick,
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier = Modifier.height(LibraryStateDimens.MinTouchTarget),
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Add),
                contentDescription = null,
                modifier = Modifier.size(LibraryStateDimens.IconSmall),
            )
            Spacer(modifier = Modifier.size(LibraryStateDimens.GapSmall))
            Text(
                text = stringResource(R.string.library_choose_pdf),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun LibraryFilteredEmptyState(
    searchQuery: String,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(LibraryStateDimens.PaddingHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text =
                if (searchQuery.isNotBlank()) {
                    stringResource(R.string.library_search_empty_format, searchQuery)
                } else {
                    stringResource(R.string.library_filter_empty)
                },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(LibraryStateDimens.GapMedium))
        TextButton(onClick = onResetFilters) {
            Text(
                text = stringResource(R.string.library_clear_search),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentActionsSheet(
    item: LibraryItemWithProgress,
    onDismiss: () -> Unit,
    onOpenClick: () -> Unit,
    onRelinkClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val doc = item.document
    val extendedColors = PdfEasyThemeTokens.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = LibraryStateDimens.PaddingHorizontal,
                        vertical = LibraryStateDimens.GapSmall,
                    )
                    .padding(bottom = LibraryStateDimens.SheetBottomPadding),
            verticalArrangement = Arrangement.spacedBy(LibraryStateDimens.GapMedium),
        ) {
            Text(
                text = LibraryFormatters.cleanDisplayName(doc.displayName),
                style = MaterialTheme.typography.titleLarge,
                maxLines = LibraryStateDimens.TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            AvailabilityNoticeCard(availability = doc.availability, extendedColors = extendedColors)
            DocumentActionButtons(
                isAvailable = doc.availability == DocumentAvailability.AVAILABLE,
                onOpenClick = onOpenClick,
                onRelinkClick = onRelinkClick,
                onRemoveClick = onRemoveClick,
            )
        }
    }
}

@Composable
private fun DocumentActionButtons(
    isAvailable: Boolean,
    onOpenClick: () -> Unit,
    onRelinkClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    if (isAvailable) {
        Button(
            onClick = onOpenClick,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().height(LibraryStateDimens.MinTouchTarget),
        ) {
            Text(text = stringResource(R.string.doc_action_open), style = MaterialTheme.typography.labelLarge)
        }
    }
    OutlinedButton(
        onClick = onRelinkClick,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(LibraryStateDimens.MinTouchTarget),
    ) {
        Text(text = stringResource(R.string.doc_action_relink), style = MaterialTheme.typography.labelLarge)
    }
    TextButton(
        onClick = onRemoveClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().height(LibraryStateDimens.MinTouchTarget),
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.DeleteOutline),
            contentDescription = null,
            modifier = Modifier.size(LibraryStateDimens.IconMedium),
        )
        Spacer(modifier = Modifier.size(LibraryStateDimens.GapSmall))
        Text(text = stringResource(R.string.doc_action_remove), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AvailabilityNoticeCard(
    availability: DocumentAvailability,
    extendedColors: PdfEasyExtendedColors,
) {
    if (availability == DocumentAvailability.AVAILABLE) return
    val isMoved = availability == DocumentAvailability.MISSING_OR_MOVED
    val bgColor = if (isMoved) extendedColors.movedBadgeBg else extendedColors.revokedBadgeBg
    val inkColor = if (isMoved) extendedColors.movedBadgeInk else extendedColors.revokedBadgeInk
    val descriptionRes = if (isMoved) R.string.doc_status_moved_desc else R.string.doc_status_revoked_desc

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        contentColor = inkColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(LibraryStateDimens.NoticePadding),
        )
    }
}
