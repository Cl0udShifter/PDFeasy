package com.cl0udshifter.pdfeasy.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens

private object ThemePillDimens {
    val Elevation = 6.dp
    val Padding = 4.dp
    val OptionHeight = 32.dp
    val OptionPadHorizontal = 10.dp
    val SwatchSize = 12.dp
    val SwatchGap = 6.dp
    val DividerWidth = 1.dp
    val DividerHeight = 18.dp
    val IconSize = 18.dp
    const val SWATCH_BORDER_ALPHA = 0.2f
}

@Composable
fun ReaderThemeAndScrollPill(
    renderTheme: PageRenderTheme,
    scrollMode: ScrollMode,
    onSelectRenderTheme: (PageRenderTheme) -> Unit,
    onToggleScrollMode: () -> Unit,
) {
    val extendedColors = PdfEasyThemeTokens.colors
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = ThemePillDimens.Elevation,
    ) {
        Row(
            modifier = Modifier.padding(ThemePillDimens.Padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThemePillDimens.Padding),
        ) {
            ThemeChipOption(
                label = stringResource(R.string.reader_theme_original),
                swatchColor = extendedColors.themeSwatchOriginal,
                selected = renderTheme == PageRenderTheme.ORIGINAL,
                onClick = { onSelectRenderTheme(PageRenderTheme.ORIGINAL) },
            )
            ThemeChipOption(
                label = stringResource(R.string.reader_theme_dark),
                swatchColor = extendedColors.themeSwatchDark,
                selected = renderTheme == PageRenderTheme.DARK,
                onClick = { onSelectRenderTheme(PageRenderTheme.DARK) },
            )
            ThemeChipOption(
                label = stringResource(R.string.reader_theme_sepia),
                swatchColor = extendedColors.themeSwatchSepia,
                selected = renderTheme == PageRenderTheme.SEPIA,
                onClick = { onSelectRenderTheme(PageRenderTheme.SEPIA) },
            )
            Box(
                modifier =
                    Modifier
                        .width(ThemePillDimens.DividerWidth)
                        .height(ThemePillDimens.DividerHeight)
                        .background(MaterialTheme.colorScheme.outlineVariant),
            )
            ScrollModeToggleButton(scrollMode = scrollMode, onToggleScrollMode = onToggleScrollMode)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollModeToggleButton(
    scrollMode: ScrollMode,
    onToggleScrollMode: () -> Unit,
) {
    val scrollDesc =
        if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
            stringResource(R.string.reader_scroll_vertical)
        } else {
            stringResource(R.string.reader_scroll_horizontal)
        }
    val scrollIcon =
        if (scrollMode == ScrollMode.CONTINUOUS_VERTICAL) {
            PdfEasyIcons.ScrollVertical
        } else {
            PdfEasyIcons.ScrollHorizontal
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(ThemePillDimens.OptionHeight)
                .clip(CircleShape)
                .combinedClickable(role = Role.Button, onClick = onToggleScrollMode)
                .semantics { contentDescription = scrollDesc },
    ) {
        Icon(
            painter = PdfEasyIcons.painter(scrollIcon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(ThemePillDimens.IconSize),
        )
    }
}

@Composable
private fun ThemeChipOption(
    label: String,
    swatchColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = Modifier.height(ThemePillDimens.OptionHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ThemePillDimens.SwatchGap),
            modifier = Modifier.padding(horizontal = ThemePillDimens.OptionPadHorizontal),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(ThemePillDimens.SwatchSize)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(1.dp, Color.Black.copy(alpha = ThemePillDimens.SWATCH_BORDER_ALPHA), CircleShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
