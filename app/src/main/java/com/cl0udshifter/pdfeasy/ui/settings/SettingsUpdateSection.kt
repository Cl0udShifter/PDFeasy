package com.cl0udshifter.pdfeasy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons

private object UpdateDimens {
    val RowPadVertical = 14.dp
    val BlockGap = 10.dp
    val SmallGap = 6.dp
    val ButtonHeight = 48.dp
    val CardPadding = 14.dp
    val SmallSpinner = 16.dp
    val SmallIcon = 16.dp
    val SpinnerStroke = 2.dp
}

@Composable
fun AboutAndUpdatesCard(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    SettingsCardContainer {
        VersionAndTimestampBlock(
            versionName = uiState.currentVersionName,
            versionCode = uiState.currentVersionCode,
            lastCheckEpochMillis = uiState.settings.lastUpdateCheckEpochMillis,
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = stringResource(R.string.settings_include_prereleases_label),
            description = stringResource(R.string.settings_include_prereleases_desc),
            checked = uiState.settings.includePreReleases,
            onCheckedChange = callbacks.onIncludePreReleasesChanged,
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = stringResource(R.string.settings_check_startup_label),
            description = stringResource(R.string.settings_check_startup_desc),
            checked = uiState.settings.checkUpdatesOnStartup,
            onCheckedChange = callbacks.onCheckUpdatesOnStartupChanged,
        )
        SettingsDivider()
        UpdateActionArea(uiState = uiState, callbacks = callbacks)
    }
}

@Composable
private fun VersionAndTimestampBlock(
    versionName: String,
    versionCode: Long,
    lastCheckEpochMillis: Long?,
) {
    val formattedCheck = UpdateFormatters.formatCheckTimestamp(lastCheckEpochMillis)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = UpdateDimens.RowPadVertical),
        verticalArrangement = Arrangement.spacedBy(UpdateDimens.SmallGap),
    ) {
        MetadataKeyValueRow(
            label = stringResource(R.string.settings_installed_version_label),
            value = stringResource(R.string.settings_installed_version_format, versionName, versionCode),
        )
        MetadataKeyValueRow(
            label = stringResource(R.string.settings_last_check_label),
            value = formattedCheck ?: stringResource(R.string.settings_last_check_never),
        )
    }
}

@Composable
private fun MetadataKeyValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UpdateActionArea(
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    val isChecking = uiState.updateCheckState is UpdateCheckUiState.Checking
    val isDownloading = uiState.downloadState is AppUpdateDownloadState.Downloading

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = UpdateDimens.RowPadVertical),
        verticalArrangement = Arrangement.spacedBy(UpdateDimens.BlockGap),
    ) {
        OutlinedButton(
            onClick = callbacks.onCheckUpdatesClick,
            enabled = !isChecking && !isDownloading,
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().height(UpdateDimens.ButtonHeight),
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(UpdateDimens.SmallSpinner),
                    strokeWidth = UpdateDimens.SpinnerStroke,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(UpdateDimens.SmallGap))
                Text(
                    text = stringResource(R.string.settings_checking_updates),
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_check_updates_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        UpdateCheckOutcomePanel(
            checkState = uiState.updateCheckState,
            uiState = uiState,
            callbacks = callbacks,
        )

        TextButton(
            onClick = callbacks.onClearUpdateCacheClick,
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth().height(UpdateDimens.ButtonHeight),
        ) {
            Text(
                text = stringResource(R.string.settings_update_clear_cache),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateCheckOutcomePanel(
    checkState: UpdateCheckUiState,
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    when (checkState) {
        UpdateCheckUiState.Idle,
        UpdateCheckUiState.Checking -> Unit

        is UpdateCheckUiState.UpToDate ->
            UpToDateNoticeBanner(tag = checkState.status.latestCheckedTag ?: checkState.status.currentVersionName)

        is UpdateCheckUiState.Error ->
            UpdateErrorNoticeBanner(error = checkState.error)

        is UpdateCheckUiState.Available ->
            UpdateAvailableCard(
                release = checkState.status.release,
                uiState = uiState,
                callbacks = callbacks,
            )
    }
}

@Composable
private fun UpToDateNoticeBanner(tag: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UpdateDimens.SmallGap),
            modifier = Modifier.padding(UpdateDimens.CardPadding),
        ) {
            Icon(
                painter = PdfEasyIcons.painter(PdfEasyIcons.Check),
                contentDescription = null,
                modifier = Modifier.size(UpdateDimens.SmallIcon),
            )
            Text(
                text = stringResource(R.string.settings_up_to_date_format, tag),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun UpdateErrorNoticeBanner(error: AppUpdateError) {
    val msg = UpdateFormatters.describeError(error)
    val text =
        if (msg.formatArg != null) {
            stringResource(msg.messageRes, msg.formatArg)
        } else {
            stringResource(msg.messageRes)
        }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(UpdateDimens.CardPadding),
        )
    }
}
