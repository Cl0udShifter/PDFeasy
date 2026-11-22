package com.cl0udshifter.pdfeasy.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens

@Composable
fun rememberAppThemeOptions(): List<SegmentedOption<AppThemeMode>> =
    listOf(
        SegmentedOption(AppThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
        SegmentedOption(AppThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
        SegmentedOption(AppThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
    )

@Composable
fun rememberRenderThemeOptions(): List<SegmentedOption<PageRenderTheme>> {
    val extendedColors = PdfEasyThemeTokens.colors
    return listOf(
        SegmentedOption(
            PageRenderTheme.ORIGINAL,
            stringResource(R.string.reader_theme_original),
            extendedColors.themeSwatchOriginal,
        ),
        SegmentedOption(
            PageRenderTheme.DARK,
            stringResource(R.string.reader_theme_dark),
            extendedColors.themeSwatchDark,
        ),
        SegmentedOption(
            PageRenderTheme.SEPIA,
            stringResource(R.string.reader_theme_sepia),
            extendedColors.themeSwatchSepia,
        ),
    )
}

@Composable
fun rememberScrollModeOptions(): List<SegmentedOption<ScrollMode>> =
    listOf(
        SegmentedOption(ScrollMode.CONTINUOUS_VERTICAL, stringResource(R.string.settings_scroll_vertical)),
        SegmentedOption(ScrollMode.CONTINUOUS_HORIZONTAL, stringResource(R.string.settings_scroll_horizontal)),
    )
