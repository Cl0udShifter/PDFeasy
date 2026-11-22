package com.cl0udshifter.pdfeasy.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState

    data object Checking : UpdateCheckUiState

    data class UpToDate(
        val status: AppUpdateStatus.UpToDate,
    ) : UpdateCheckUiState

    data class Available(
        val status: AppUpdateStatus.UpdateAvailable,
    ) : UpdateCheckUiState

    data class Error(
        val error: AppUpdateError,
    ) : UpdateCheckUiState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val currentVersionName: String = "",
    val currentVersionCode: Long = 0L,
    val updateCheckState: UpdateCheckUiState = UpdateCheckUiState.Idle,
    val downloadState: AppUpdateDownloadState = AppUpdateDownloadState.Idle,
    val canInstallPackages: Boolean = true,
    val releaseNotesExpanded: Boolean = false,
    @StringRes val statusMessageRes: Int? = null,
) {
    val hasUpdateAvailable: Boolean
        get() = updateCheckState is UpdateCheckUiState.Available
}

sealed interface SettingsEvent {
    data class LaunchPackageInstaller(
        val contentUriString: String,
    ) : SettingsEvent
}

@Immutable
data class SettingsCallbacks(
    val onBackClick: () -> Unit,
    val onAppThemeSelected: (AppThemeMode) -> Unit,
    val onDefaultRenderThemeSelected: (PageRenderTheme) -> Unit,
    val onDefaultScrollModeSelected: (ScrollMode) -> Unit,
    val onDefaultZoomChanged: (Float) -> Unit,
    val onKeepScreenOnChanged: (Boolean) -> Unit,
    val onIncludePreReleasesChanged: (Boolean) -> Unit,
    val onCheckUpdatesOnStartupChanged: (Boolean) -> Unit,
    val onCheckUpdatesClick: () -> Unit,
    val onToggleReleaseNotes: () -> Unit,
    val onDownloadUpdateClick: (AppReleaseInfo) -> Unit,
    val onInstallReadyClick: () -> Unit,
    val onRequestInstallPermissionClick: () -> Unit,
    val onClearUpdateCacheClick: () -> Unit,
)
