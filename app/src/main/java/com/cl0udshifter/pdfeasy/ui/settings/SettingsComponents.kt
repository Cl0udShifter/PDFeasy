package com.cl0udshifter.pdfeasy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons

private object SettingsDimens {
    val MinTouchSize = 48.dp
    val TopBarPadVertical = 8.dp
    val TopBarGap = 4.dp
    val BackIconSize = 20.dp
    val CardPadding = 16.dp
    val RowVerticalPad = 14.dp
    val RowGap = 10.dp
    val SmallGap = 6.dp
    val PillOuterPad = 4.dp
    val PillOptionHeight = 36.dp
    val SwatchSize = 12.dp
    val ThumbWidth = 4.dp
    val ThumbHeight = 20.dp
    val TrackHeight = 6.dp
    val ThumbGap = 4.dp
    val TrackRadius = 3.dp
    val ZoomBadgePadH = 10.dp
    val ZoomBadgePadV = 4.dp
    const val DEFAULT_ZOOM_STEPS = 19
    const val SWATCH_BORDER_ALPHA = 0.2f
}

data class SegmentedOption<T>(
    val value: T,
    val label: String,
    val swatchColor: Color? = null,
)

@Composable
fun SettingsTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = SettingsDimens.TopBarPadVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(SettingsDimens.MinTouchSize),
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.ArrowBack),
                contentDescription = stringResource(R.string.settings_back),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(SettingsDimens.BackIconSize),
            )
        }
        Spacer(modifier = Modifier.width(SettingsDimens.TopBarGap))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(SettingsDimens.SmallGap / 2))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SettingsDimens.CardPadding),
            content = content,
        )
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun <T> SettingsSegmentedBlock(
    title: String,
    subtitle: String? = null,
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SettingsDimens.RowVerticalPad),
        verticalArrangement = Arrangement.spacedBy(SettingsDimens.RowGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsDimens.SmallGap / 2)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SettingsDimens.PillOuterPad),
                horizontalArrangement = Arrangement.spacedBy(SettingsDimens.PillOuterPad),
            ) {
                options.forEach { option ->
                    SegmentedChoiceChip(
                        option = option,
                        selected = option.value == selectedValue,
                        onClick = { onSelect(option.value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentedChoiceChip(
    option: SegmentedOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = modifier.height(SettingsDimens.PillOptionHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = SettingsDimens.SmallGap),
        ) {
            if (option.swatchColor != null) {
                Box(
                    modifier =
                        Modifier
                            .size(SettingsDimens.SwatchSize)
                            .clip(CircleShape)
                            .background(option.swatchColor)
                            .border(1.dp, Color.Black.copy(alpha = SettingsDimens.SWATCH_BORDER_ALPHA), CircleShape),
                )
                Spacer(modifier = Modifier.width(SettingsDimens.SmallGap))
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SettingsZoomRow(
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
) {
    val clamped = zoomScale.coerceIn(AppSettings.MIN_DEFAULT_ZOOM, AppSettings.MAX_DEFAULT_ZOOM)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SettingsDimens.RowVerticalPad),
        verticalArrangement = Arrangement.spacedBy(SettingsDimens.SmallGap),
    ) {
        ZoomHeaderRow(zoomScale = clamped)
        SettingsZoomSlider(zoomScale = clamped, onZoomChange = onZoomChange)
    }
}

@Composable
private fun ZoomHeaderRow(zoomScale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_default_zoom_label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(R.string.settings_default_zoom_format, zoomScale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier.padding(
                        horizontal = SettingsDimens.ZoomBadgePadH,
                        vertical = SettingsDimens.ZoomBadgePadV,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsZoomSlider(
    zoomScale: Float,
    onZoomChange: (Float) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    Slider(
        value = zoomScale,
        onValueChange = onZoomChange,
        valueRange = AppSettings.MIN_DEFAULT_ZOOM..AppSettings.MAX_DEFAULT_ZOOM,
        steps = SettingsDimens.DEFAULT_ZOOM_STEPS,
        colors = sliderColors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = sliderColors,
                enabled = true,
                thumbSize = DpSize(SettingsDimens.ThumbWidth, SettingsDimens.ThumbHeight),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                colors = sliderColors,
                enabled = true,
                sliderState = sliderState,
                modifier = Modifier.height(SettingsDimens.TrackHeight),
                drawStopIndicator = null,
                thumbTrackGapSize = SettingsDimens.ThumbGap,
                trackInsideCornerSize = SettingsDimens.TrackRadius,
            )
        },
        modifier = Modifier.fillMaxWidth().testTag("settings_default_zoom_slider"),
    )
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = SettingsDimens.RowVerticalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsDimens.RowGap),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SettingsDimens.SmallGap / 2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier,
        )
    }
}
