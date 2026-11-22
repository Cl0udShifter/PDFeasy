package com.cl0udshifter.pdfeasy.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private const val RADIUS_EXTRA_SMALL_DP = 6
private const val RADIUS_SMALL_DP = 12
private const val RADIUS_MEDIUM_DP = 18
private const val RADIUS_LARGE_DP = 20

val PdfEasyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(RADIUS_EXTRA_SMALL_DP.dp),
        small = RoundedCornerShape(RADIUS_SMALL_DP.dp),
        medium = RoundedCornerShape(RADIUS_MEDIUM_DP.dp),
        large = RoundedCornerShape(RADIUS_LARGE_DP.dp),
        extraLarge = CircleShape,
    )
