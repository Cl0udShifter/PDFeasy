package com.cl0udshifter.pdfeasy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private const val HEX_LIGHT_BACKGROUND = 0xFFFAF8F4
private const val HEX_LIGHT_INK = 0xFF1F201D
private const val HEX_LIGHT_SURFACE_VARIANT = 0xFFF2EDE3
private const val HEX_LIGHT_ON_SURFACE_VARIANT = 0xFF636361
private const val HEX_LIGHT_SURFACE_LOWEST = 0xFFFFFEFC
private const val HEX_LIGHT_SURFACE_CONTAINER = 0xFFEDE6D9
private const val HEX_LIGHT_SURFACE_HIGH = 0xFFE8E1D3
private const val HEX_LIGHT_SURFACE_HIGHEST = 0xFFDFD7C8
private const val HEX_LIGHT_PRIMARY = 0xFF1B6E66
private const val HEX_LIGHT_PRIMARY_CONTAINER = 0xFFD7ECE8
private const val HEX_LIGHT_ON_PRIMARY_CONTAINER = 0xFF0E3D38
private const val HEX_LIGHT_OUTLINE = 0xFFCFC7B8
private const val HEX_LIGHT_OUTLINE_VARIANT = 0xFFE2DACA
private const val HEX_LIGHT_ERROR = 0xFF9E2A2B
private const val HEX_LIGHT_ERROR_CONTAINER = 0xFFFDECEA
private const val HEX_LIGHT_ON_ERROR_CONTAINER = 0xFF7A1D14

private const val HEX_DARK_BACKGROUND = 0xFF161513
private const val HEX_DARK_INK = 0xFFEBE6DD
private const val HEX_DARK_SURFACE_VARIANT = 0xFF201E1B
private const val HEX_DARK_ON_SURFACE_VARIANT = 0xFFA8A298
private const val HEX_DARK_SURFACE_LOWEST = 0xFF1C1A17
private const val HEX_DARK_SURFACE_CONTAINER = 0xFF262320
private const val HEX_DARK_SURFACE_HIGH = 0xFF2E2A26
private const val HEX_DARK_SURFACE_HIGHEST = 0xFF38332E
private const val HEX_DARK_PRIMARY = 0xFF5DB7AE
private const val HEX_DARK_ON_PRIMARY = 0xFF0C2926
private const val HEX_DARK_PRIMARY_CONTAINER = 0xFF1B4742
private const val HEX_DARK_ON_PRIMARY_CONTAINER = 0xFFBCEAE4
private const val HEX_DARK_OUTLINE = 0xFF4A443C
private const val HEX_DARK_OUTLINE_VARIANT = 0xFF332F29
private const val HEX_DARK_ERROR = 0xFFF28B82
private const val HEX_DARK_ON_ERROR = 0xFF3D0A09
private const val HEX_DARK_ERROR_CONTAINER = 0xFF4B1C1B
private const val HEX_DARK_ON_ERROR_CONTAINER = 0xFFFDECEA

private const val HEX_LIGHT_MOVED_BG = 0xFFFBF0D9
private const val HEX_LIGHT_MOVED_INK = 0xFF8A4B08
private const val HEX_LIGHT_SEARCH_OTHER = 0x61FACC15
private const val HEX_LIGHT_SEARCH_CURRENT = 0x3D1B6E66
private const val HEX_LIGHT_BACKDROP_ORIGINAL = 0xFFEAE5DB
private const val HEX_LIGHT_BACKDROP_DARK = 0xFF111110
private const val HEX_LIGHT_BACKDROP_SEPIA = 0xFFE2D6BF

private const val HEX_DARK_MOVED_BG = 0xFF3A2B12
private const val HEX_DARK_MOVED_INK = 0xFFF3C474
private const val HEX_DARK_REVOKED_INK = 0xFFF5A39E
private const val HEX_DARK_SEARCH_OTHER = 0x59EAB308
private const val HEX_DARK_SEARCH_CURRENT = 0x4D5DB7AE
private const val HEX_DARK_BACKDROP_ORIGINAL = 0xFF201E1B
private const val HEX_DARK_BACKDROP_DARK = 0xFF0B0B0A
private const val HEX_DARK_BACKDROP_SEPIA = 0xFF29231A

private const val HEX_SWATCH_ORIGINAL = 0xFFFFFFFF
private const val HEX_SWATCH_DARK = 0xFF18181B
private const val HEX_SWATCH_SEPIA = 0xFFEFE2C8

private val LightBackground = Color(HEX_LIGHT_BACKGROUND)
private val LightInk = Color(HEX_LIGHT_INK)
private val LightSurfaceVariant = Color(HEX_LIGHT_SURFACE_VARIANT)
private val LightOnSurfaceVariant = Color(HEX_LIGHT_ON_SURFACE_VARIANT)
private val LightSurfaceLowest = Color(HEX_LIGHT_SURFACE_LOWEST)
private val LightSurfaceContainer = Color(HEX_LIGHT_SURFACE_CONTAINER)
private val LightSurfaceHigh = Color(HEX_LIGHT_SURFACE_HIGH)
private val LightSurfaceHighest = Color(HEX_LIGHT_SURFACE_HIGHEST)
private val LightPrimary = Color(HEX_LIGHT_PRIMARY)
private val LightPrimaryContainer = Color(HEX_LIGHT_PRIMARY_CONTAINER)
private val LightOnPrimaryContainer = Color(HEX_LIGHT_ON_PRIMARY_CONTAINER)
private val LightOutline = Color(HEX_LIGHT_OUTLINE)
private val LightOutlineVariant = Color(HEX_LIGHT_OUTLINE_VARIANT)
private val LightError = Color(HEX_LIGHT_ERROR)
private val LightErrorContainer = Color(HEX_LIGHT_ERROR_CONTAINER)
private val LightOnErrorContainer = Color(HEX_LIGHT_ON_ERROR_CONTAINER)

private val DarkBackground = Color(HEX_DARK_BACKGROUND)
private val DarkInk = Color(HEX_DARK_INK)
private val DarkSurfaceVariant = Color(HEX_DARK_SURFACE_VARIANT)
private val DarkOnSurfaceVariant = Color(HEX_DARK_ON_SURFACE_VARIANT)
private val DarkSurfaceLowest = Color(HEX_DARK_SURFACE_LOWEST)
private val DarkSurfaceContainer = Color(HEX_DARK_SURFACE_CONTAINER)
private val DarkSurfaceHigh = Color(HEX_DARK_SURFACE_HIGH)
private val DarkSurfaceHighest = Color(HEX_DARK_SURFACE_HIGHEST)
private val DarkPrimary = Color(HEX_DARK_PRIMARY)
private val DarkOnPrimary = Color(HEX_DARK_ON_PRIMARY)
private val DarkPrimaryContainer = Color(HEX_DARK_PRIMARY_CONTAINER)
private val DarkOnPrimaryContainer = Color(HEX_DARK_ON_PRIMARY_CONTAINER)
private val DarkOutline = Color(HEX_DARK_OUTLINE)
private val DarkOutlineVariant = Color(HEX_DARK_OUTLINE_VARIANT)
private val DarkError = Color(HEX_DARK_ERROR)
private val DarkOnError = Color(HEX_DARK_ON_ERROR)
private val DarkErrorContainer = Color(HEX_DARK_ERROR_CONTAINER)
private val DarkOnErrorContainer = Color(HEX_DARK_ON_ERROR_CONTAINER)

val PdfEasyLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightBackground,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnPrimaryContainer,
        secondary = LightInk,
        onSecondary = LightBackground,
        secondaryContainer = LightSurfaceHigh,
        onSecondaryContainer = LightInk,
        tertiary = LightPrimary,
        onTertiary = LightBackground,
        background = LightBackground,
        onBackground = LightInk,
        surface = LightBackground,
        onSurface = LightInk,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceContainerLowest = LightSurfaceLowest,
        surfaceContainerLow = LightSurfaceVariant,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceHigh,
        surfaceContainerHighest = LightSurfaceHighest,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = LightError,
        onError = Color.White,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
    )

val PdfEasyDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        secondary = DarkInk,
        onSecondary = DarkBackground,
        secondaryContainer = DarkSurfaceHigh,
        onSecondaryContainer = DarkInk,
        tertiary = DarkPrimary,
        onTertiary = DarkOnPrimary,
        background = DarkBackground,
        onBackground = DarkInk,
        surface = DarkBackground,
        onSurface = DarkInk,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        surfaceContainerLowest = DarkSurfaceLowest,
        surfaceContainerLow = DarkSurfaceVariant,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceHigh,
        surfaceContainerHighest = DarkSurfaceHighest,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = DarkError,
        onError = DarkOnError,
        errorContainer = DarkErrorContainer,
        onErrorContainer = DarkOnErrorContainer,
    )

@Immutable
data class PdfEasyExtendedColors(
    val passwordBadgeBg: Color,
    val passwordBadgeInk: Color,
    val movedBadgeBg: Color,
    val movedBadgeInk: Color,
    val revokedBadgeBg: Color,
    val revokedBadgeInk: Color,
    val searchMatchOther: Color,
    val searchMatchCurrentFill: Color,
    val searchMatchCurrentStroke: Color,
    val readerBackdropOriginal: Color,
    val readerBackdropDark: Color,
    val readerBackdropSepia: Color,
    val themeSwatchOriginal: Color,
    val themeSwatchDark: Color,
    val themeSwatchSepia: Color,
)

val LightExtendedColors =
    PdfEasyExtendedColors(
        passwordBadgeBg = LightSurfaceHigh,
        passwordBadgeInk = LightInk,
        movedBadgeBg = Color(HEX_LIGHT_MOVED_BG),
        movedBadgeInk = Color(HEX_LIGHT_MOVED_INK),
        revokedBadgeBg = LightErrorContainer,
        revokedBadgeInk = LightError,
        searchMatchOther = Color(HEX_LIGHT_SEARCH_OTHER),
        searchMatchCurrentFill = Color(HEX_LIGHT_SEARCH_CURRENT),
        searchMatchCurrentStroke = LightPrimary,
        readerBackdropOriginal = Color(HEX_LIGHT_BACKDROP_ORIGINAL),
        readerBackdropDark = Color(HEX_LIGHT_BACKDROP_DARK),
        readerBackdropSepia = Color(HEX_LIGHT_BACKDROP_SEPIA),
        themeSwatchOriginal = Color(HEX_SWATCH_ORIGINAL),
        themeSwatchDark = Color(HEX_SWATCH_DARK),
        themeSwatchSepia = Color(HEX_SWATCH_SEPIA),
    )

val DarkExtendedColors =
    PdfEasyExtendedColors(
        passwordBadgeBg = DarkSurfaceHigh,
        passwordBadgeInk = DarkInk,
        movedBadgeBg = Color(HEX_DARK_MOVED_BG),
        movedBadgeInk = Color(HEX_DARK_MOVED_INK),
        revokedBadgeBg = DarkErrorContainer,
        revokedBadgeInk = Color(HEX_DARK_REVOKED_INK),
        searchMatchOther = Color(HEX_DARK_SEARCH_OTHER),
        searchMatchCurrentFill = Color(HEX_DARK_SEARCH_CURRENT),
        searchMatchCurrentStroke = DarkPrimary,
        readerBackdropOriginal = Color(HEX_DARK_BACKDROP_ORIGINAL),
        readerBackdropDark = Color(HEX_DARK_BACKDROP_DARK),
        readerBackdropSepia = Color(HEX_DARK_BACKDROP_SEPIA),
        themeSwatchOriginal = Color(HEX_SWATCH_ORIGINAL),
        themeSwatchDark = Color(HEX_SWATCH_DARK),
        themeSwatchSepia = Color(HEX_SWATCH_SEPIA),
    )

val LocalPdfEasyColors = staticCompositionLocalOf { LightExtendedColors }
