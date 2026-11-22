package com.cl0udshifter.pdfeasy.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.model.DocumentAvailability
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyExtendedColors
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens

private object CardDimens {
    val IconSmall = 16.dp
    val IconBadge = 12.dp
    val ChipSpacing = 8.dp
    val ThumbHeight = 108.dp
    val ThumbPadHorizontal = 12.dp
    val ThumbPadTop = 10.dp
    val BodyPadHorizontal = 12.dp
    val BodyPadVertical = 10.dp
    val ProgressHeight = 3.dp
    val MiniSheetTopRadius = 6.dp
    val MiniSheetBorderWidth = 1.dp
    val MiniSheetLineRadius = 2.dp
    val BadgeOffset = 8.dp
    val BadgePadHorizontal = 8.dp
    val BadgePadVertical = 4.dp
    val MorePillSize = 28.dp
    const val CARD_TITLE_LINES = 2
    const val HEADER_LINE_WIDTH_FRACTION = 0.72f
    const val LINE_WIDTH_LONG = 0.92f
    const val LINE_WIDTH_MID = 0.84f
    const val LINE_WIDTH_SHORT = 0.64f
    const val MINI_LINE_HEIGHT_DP = 3f
    const val MINI_TITLE_HEIGHT_DP = 5f
    const val MINI_TOP_PAD_DP = 11f
    const val MINI_SIDE_PAD_DP = 11f
    const val MINI_GAP_DP = 6f
    const val MINI_PARAGRAPH_GAP_DP = 10f
    const val BODY_LINE_COUNT = 4
    const val BADGE_ALPHA_BACKGROUND = 0.86f
}

private enum class CardBadgeKind {
    REVOKED,
    MOVED,
    PASSWORD,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryDocumentCard(
    item: LibraryItemWithProgress,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doc = item.document
    val progress = item.progress
    val completionFraction = progress?.completionFraction ?: 0f
    val hasProgress = doc.lastOpenedAtEpochMillis != null || progress != null

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onOptionsClick,
                    onLongClickLabel = stringResource(R.string.library_more_actions),
                ),
    ) {
        Column {
            CardThumbnailPreview(
                badgeKind = resolveBadgeKind(doc.availability, doc.isPasswordProtected),
                onOptionsClick = onOptionsClick,
            )
            CardFooterMetadata(
                displayName = doc.displayName,
                pageCount = doc.pageCount,
                sizeBytes = doc.sizeBytes,
                hasProgress = hasProgress,
                completionFraction = completionFraction,
            )
            CardProgressBar(fraction = completionFraction)
        }
    }
}

@Composable
private fun CardFooterMetadata(
    displayName: String,
    pageCount: Int,
    sizeBytes: Long,
    hasProgress: Boolean,
    completionFraction: Float,
) {
    val pageText =
        if (pageCount > 0) {
            stringResource(R.string.library_pages_format, pageCount)
        } else {
            stringResource(R.string.library_pages_unknown)
        }
    val trailingMeta =
        if (hasProgress) {
            LibraryFormatters.formatCompletionPercent(completionFraction)
        } else {
            LibraryFormatters.formatFileSize(sizeBytes)
        }

    Column(
        modifier =
            Modifier.padding(
                horizontal = CardDimens.BodyPadHorizontal,
                vertical = CardDimens.BodyPadVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(CardDimens.ChipSpacing),
    ) {
        Text(
            text = LibraryFormatters.cleanDisplayName(displayName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = CardDimens.CARD_TITLE_LINES,
            maxLines = CardDimens.CARD_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pageText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = trailingMeta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardThumbnailPreview(
    badgeKind: CardBadgeKind?,
    onOptionsClick: () -> Unit,
) {
    val extendedColors = PdfEasyThemeTokens.colors
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val optionsDesc = stringResource(R.string.library_more_actions)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CardDimens.ThumbHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(
                    start = CardDimens.ThumbPadHorizontal,
                    end = CardDimens.ThumbPadHorizontal,
                    top = CardDimens.ThumbPadTop,
                ),
    ) {
        MiniSchematicPageCanvas()

        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(CardDimens.BadgeOffset)
                    .size(CardDimens.MorePillSize)
                    .clip(CircleShape)
                    .background(sheetBg.copy(alpha = CardDimens.BADGE_ALPHA_BACKGROUND))
                    .clickable(role = Role.Button, onClick = onOptionsClick)
                    .semantics { contentDescription = optionsDesc },
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.MoreVertical),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CardDimens.IconSmall),
            )
        }

        if (badgeKind != null) {
            StatusBadge(
                badgeKind = badgeKind,
                extendedColors = extendedColors,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(CardDimens.BadgeOffset),
            )
        }
    }
}

@Composable
private fun MiniSchematicPageCanvas() {
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerLowest
    val sheetBorder = MaterialTheme.colorScheme.outlineVariant
    val titleLineColor = MaterialTheme.colorScheme.onSurface
    val bodyLineColor = MaterialTheme.colorScheme.outlineVariant
    val sheetShape =
        RoundedCornerShape(
            topStart = CardDimens.MiniSheetTopRadius,
            topEnd = CardDimens.MiniSheetTopRadius,
        )

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(sheetShape)
                .background(sheetBg)
                .border(CardDimens.MiniSheetBorderWidth, sheetBorder, sheetShape),
    ) {
        val sidePad = CardDimens.MINI_SIDE_PAD_DP.dp.toPx()
        val usableWidth = (size.width - sidePad * 2f).coerceAtLeast(0f)
        var y = CardDimens.MINI_TOP_PAD_DP.dp.toPx()
        val titleHeight = CardDimens.MINI_TITLE_HEIGHT_DP.dp.toPx()
        val lineHeight = CardDimens.MINI_LINE_HEIGHT_DP.dp.toPx()
        val lineGap = CardDimens.MINI_GAP_DP.dp.toPx()
        val corner = CornerRadius(CardDimens.MiniSheetLineRadius.toPx())

        drawRoundRect(
            color = titleLineColor,
            topLeft = Offset(sidePad, y),
            size = Size(usableWidth * CardDimens.HEADER_LINE_WIDTH_FRACTION, titleHeight),
            cornerRadius = corner,
        )
        y += titleHeight + CardDimens.MINI_PARAGRAPH_GAP_DP.dp.toPx()

        val widths =
            listOf(
                CardDimens.LINE_WIDTH_LONG,
                CardDimens.LINE_WIDTH_MID,
                CardDimens.LINE_WIDTH_SHORT,
                CardDimens.LINE_WIDTH_LONG,
            )
        repeat(CardDimens.BODY_LINE_COUNT) { index ->
            drawRoundRect(
                color = bodyLineColor,
                topLeft = Offset(sidePad, y),
                size = Size(usableWidth * widths[index % widths.size], lineHeight),
                cornerRadius = corner,
            )
            y += lineHeight + lineGap
        }
    }
}

@Composable
private fun StatusBadge(
    badgeKind: CardBadgeKind,
    extendedColors: PdfEasyExtendedColors,
    modifier: Modifier = Modifier,
) {
    val bgColor: Color
    val inkColor: Color
    @DrawableRes val iconRes: Int
    val textRes: Int

    when (badgeKind) {
        CardBadgeKind.REVOKED -> {
            bgColor = extendedColors.revokedBadgeBg
            inkColor = extendedColors.revokedBadgeInk
            iconRes = PdfEasyIcons.Warning
            textRes = R.string.library_badge_revoked
        }

        CardBadgeKind.MOVED -> {
            bgColor = extendedColors.movedBadgeBg
            inkColor = extendedColors.movedBadgeInk
            iconRes = PdfEasyIcons.Warning
            textRes = R.string.library_badge_moved
        }

        CardBadgeKind.PASSWORD -> {
            bgColor = extendedColors.passwordBadgeBg
            inkColor = extendedColors.passwordBadgeInk
            iconRes = PdfEasyIcons.Lock
            textRes = R.string.library_badge_password
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = bgColor,
        contentColor = inkColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = CardDimens.BadgePadHorizontal,
                    vertical = CardDimens.BadgePadVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CardDimens.BadgePadVertical),
        ) {
            Icon(
                painter = PdfEasyIcons.painter(iconRes),
                contentDescription = null,
                modifier = Modifier.size(CardDimens.IconBadge),
            )
            Text(text = stringResource(textRes), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun CardProgressBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CardDimens.ProgressHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (clamped > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(clamped)
                        .height(CardDimens.ProgressHeight)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun resolveBadgeKind(
    availability: DocumentAvailability,
    isPasswordProtected: Boolean,
): CardBadgeKind? =
    when {
        availability == DocumentAvailability.PERMISSION_REVOKED -> CardBadgeKind.REVOKED
        availability == DocumentAvailability.MISSING_OR_MOVED -> CardBadgeKind.MOVED
        isPasswordProtected -> CardBadgeKind.PASSWORD
        else -> null
    }
