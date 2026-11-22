package com.cl0udshifter.pdfeasy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cl0udshifter.pdfeasy.R

val SourceSerif4Family =
    FontFamily(
        Font(R.font.sourceserif4_regular, FontWeight.Normal),
        Font(R.font.sourceserif4_semibold, FontWeight.SemiBold),
    )

val InterFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
    )

private const val TABULAR_NUMBERS = "tnum"

private const val HEADLINE_SIZE_SP = 28
private const val HEADLINE_LINE_SP = 32
private const val HEADLINE_TRACKING_SP = -0.56f

private const val TITLE_LARGE_SIZE_SP = 22
private const val TITLE_LARGE_LINE_SP = 28
private const val TITLE_LARGE_TRACKING_SP = -0.33f

private const val TITLE_MEDIUM_SIZE_SP = 15
private const val TITLE_MEDIUM_LINE_SP = 20
private const val TITLE_MEDIUM_TRACKING_SP = -0.15f

private const val TITLE_SMALL_SIZE_SP = 14
private const val TITLE_SMALL_LINE_SP = 18

private const val BODY_LARGE_SIZE_SP = 15
private const val BODY_LARGE_LINE_SP = 22

private const val BODY_MEDIUM_SIZE_SP = 13
private const val BODY_MEDIUM_LINE_SP = 19

private const val BODY_SMALL_SIZE_SP = 12
private const val BODY_SMALL_LINE_SP = 16

private const val LABEL_LARGE_SIZE_SP = 13
private const val LABEL_LARGE_LINE_SP = 18

private const val LABEL_MEDIUM_SIZE_SP = 12
private const val LABEL_MEDIUM_LINE_SP = 16

private const val LABEL_SMALL_SIZE_SP = 11
private const val LABEL_SMALL_LINE_SP = 14

val PdfEasyTypography =
    Typography(
        headlineMedium =
            TextStyle(
                fontFamily = SourceSerif4Family,
                fontWeight = FontWeight.SemiBold,
                fontSize = HEADLINE_SIZE_SP.sp,
                lineHeight = HEADLINE_LINE_SP.sp,
                letterSpacing = HEADLINE_TRACKING_SP.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = SourceSerif4Family,
                fontWeight = FontWeight.SemiBold,
                fontSize = TITLE_LARGE_SIZE_SP.sp,
                lineHeight = TITLE_LARGE_LINE_SP.sp,
                letterSpacing = TITLE_LARGE_TRACKING_SP.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = SourceSerif4Family,
                fontWeight = FontWeight.SemiBold,
                fontSize = TITLE_MEDIUM_SIZE_SP.sp,
                lineHeight = TITLE_MEDIUM_LINE_SP.sp,
                letterSpacing = TITLE_MEDIUM_TRACKING_SP.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = SourceSerif4Family,
                fontWeight = FontWeight.SemiBold,
                fontSize = TITLE_SMALL_SIZE_SP.sp,
                lineHeight = TITLE_SMALL_LINE_SP.sp,
                letterSpacing = 0.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Normal,
                fontSize = BODY_LARGE_SIZE_SP.sp,
                lineHeight = BODY_LARGE_LINE_SP.sp,
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Normal,
                fontSize = BODY_MEDIUM_SIZE_SP.sp,
                lineHeight = BODY_MEDIUM_LINE_SP.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Normal,
                fontSize = BODY_SMALL_SIZE_SP.sp,
                lineHeight = BODY_SMALL_LINE_SP.sp,
                letterSpacing = 0.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                fontSize = LABEL_LARGE_SIZE_SP.sp,
                lineHeight = LABEL_LARGE_LINE_SP.sp,
                letterSpacing = 0.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                fontSize = LABEL_MEDIUM_SIZE_SP.sp,
                lineHeight = LABEL_MEDIUM_LINE_SP.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = TABULAR_NUMBERS,
            ),
        labelSmall =
            TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                fontSize = LABEL_SMALL_SIZE_SP.sp,
                lineHeight = LABEL_SMALL_LINE_SP.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = TABULAR_NUMBERS,
            ),
    )
