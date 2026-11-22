package com.cl0udshifter.pdfeasy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.ui.icons.PdfEasyIcons
import com.cl0udshifter.pdfeasy.ui.library.LibraryFormatters
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyThemeTokens

private object ReleaseCardDimens {
    val BlockGap = 10.dp
    val SmallGap = 6.dp
    val ButtonHeight = 48.dp
    val CardPadding = 14.dp
    val ProgressHeight = 4.dp
    val SmallIcon = 16.dp
    val BadgePadH = 8.dp
    val BadgePadV = 3.dp
}

@Composable
fun UpdateAvailableCard(
    release: AppReleaseInfo,
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ReleaseCardDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(ReleaseCardDimens.BlockGap),
        ) {
            UpdateAvailableHeader(isPreRelease = release.isPreRelease)
            Text(
                text = release.releaseName.ifBlank { release.tagName },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = buildReleaseMetaLine(release.apkSizeBytes, release.expectedSha256 != null),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReleaseNotesExpander(
                notes = release.releaseNotes,
                expanded = uiState.releaseNotesExpanded,
                onToggle = callbacks.onToggleReleaseNotes,
            )
            DownloadAndInstallActionArea(
                release = release,
                downloadState = uiState.downloadState,
                canInstallPackages = uiState.canInstallPackages,
                callbacks = callbacks,
            )
        }
    }
}

@Composable
private fun UpdateAvailableHeader(isPreRelease: Boolean) {
    val extendedColors = PdfEasyThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_update_available_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (isPreRelease) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = extendedColors.movedBadgeBg,
                contentColor = extendedColors.movedBadgeInk,
            ) {
                Text(
                    text = stringResource(R.string.settings_update_prerelease_badge),
                    style = MaterialTheme.typography.labelSmall,
                    modifier =
                        Modifier.padding(
                            horizontal = ReleaseCardDimens.BadgePadH,
                            vertical = ReleaseCardDimens.BadgePadV,
                        ),
                )
            }
        }
    }
}

@Composable
private fun buildReleaseMetaLine(
    apkSizeBytes: Long,
    hasSha256: Boolean,
): String {
    val sizeLabel = LibraryFormatters.formatFileSize(apkSizeBytes)
    return if (hasSha256) {
        "$sizeLabel | ${stringResource(R.string.settings_update_sha256_available)}"
    } else {
        sizeLabel
    }
}

@Composable
private fun ReleaseNotesExpander(
    notes: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (notes.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(ReleaseCardDimens.SmallGap)) {
        TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
            Text(
                text =
                    stringResource(
                        if (expanded) R.string.settings_update_notes_hide else R.string.settings_update_notes_show,
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (expanded) {
            Text(
                text = notes.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadAndInstallActionArea(
    release: AppReleaseInfo,
    downloadState: AppUpdateDownloadState,
    canInstallPackages: Boolean,
    callbacks: SettingsCallbacks,
) {
    when (downloadState) {
        AppUpdateDownloadState.Idle -> {
            DownloadApkButton(
                label = stringResource(R.string.settings_update_download_install),
                onClick = { callbacks.onDownloadUpdateClick(release) },
            )
        }

        is AppUpdateDownloadState.Downloading -> DownloadProgressPanel(state = downloadState)

        is AppUpdateDownloadState.ReadyToInstall -> {
            ReadyToInstallPanel(
                state = downloadState,
                canInstallPackages = canInstallPackages,
                onInstallClick = callbacks.onInstallReadyClick,
                onRequestPermissionClick = callbacks.onRequestInstallPermissionClick,
            )
        }

        is AppUpdateDownloadState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(ReleaseCardDimens.SmallGap)) {
                UpdateErrorNoticeBanner(error = downloadState.error)
                DownloadApkButton(
                    label = stringResource(R.string.settings_update_retry),
                    onClick = { callbacks.onDownloadUpdateClick(release) },
                )
            }
        }
    }
}

@Composable
private fun DownloadApkButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = Modifier.fillMaxWidth().height(ReleaseCardDimens.ButtonHeight),
    ) {
        Icon(
            painter = PdfEasyIcons.painter(PdfEasyIcons.Download),
            contentDescription = null,
            modifier = Modifier.size(ReleaseCardDimens.SmallIcon),
        )
        Spacer(modifier = Modifier.width(ReleaseCardDimens.SmallGap))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DownloadProgressPanel(state: AppUpdateDownloadState.Downloading) {
    val fraction = state.progressFraction
    Column(verticalArrangement = Arrangement.spacedBy(ReleaseCardDimens.SmallGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        if (fraction != null) {
                            R.string.settings_update_downloading
                        } else {
                            R.string.settings_update_downloading_indeterminate
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = UpdateFormatters.formatDownloadBytes(state.downloadedBytes, state.totalBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(ReleaseCardDimens.ProgressHeight),
            )
        } else {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(ReleaseCardDimens.ProgressHeight),
            )
        }
    }
}

@Composable
private fun ReadyToInstallPanel(
    state: AppUpdateDownloadState.ReadyToInstall,
    canInstallPackages: Boolean,
    onInstallClick: () -> Unit,
    onRequestPermissionClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReleaseCardDimens.SmallGap)) {
        Text(
            text = stringResource(R.string.settings_update_ready_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                stringResource(
                    if (state.sha256Verified) {
                        R.string.settings_update_sha256_verified
                    } else {
                        R.string.settings_update_sha256_unverified
                    },
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canInstallPackages) {
            Button(
                onClick = onInstallClick,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().height(ReleaseCardDimens.ButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.settings_update_install_now),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.settings_update_permission_needed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = onRequestPermissionClick,
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().height(ReleaseCardDimens.ButtonHeight),
            ) {
                Text(
                    text = stringResource(R.string.settings_update_grant_permission),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
