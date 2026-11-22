package com.cl0udshifter.pdfeasy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun PdfEasyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) PdfEasyDarkColorScheme else PdfEasyLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalPdfEasyColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PdfEasyTypography,
            shapes = PdfEasyShapes,
            content = content,
        )
    }
}

object PdfEasyThemeTokens {
    val colors: PdfEasyExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPdfEasyColors.current
}
