package com.cl0udshifter.pdfeasy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.domain.AppUpdateRepository
import com.cl0udshifter.pdfeasy.core.domain.SettingsRepository
import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {
    private val uiStateMutable =
        MutableStateFlow(
            SettingsUiState(
                currentVersionName = appUpdateRepository.currentVersionName,
                currentVersionCode = appUpdateRepository.currentVersionCode,
                canInstallPackages = appUpdateRepository.canRequestPackageInstalls(),
            ),
        )
    val uiState: StateFlow<SettingsUiState> = uiStateMutable.asStateFlow()

    private val eventsMutable = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = eventsMutable.asSharedFlow()

    private var startupCheckAttempted = false
    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { loaded ->
                uiStateMutable.update { it.copy(settings = loaded) }
                if (!startupCheckAttempted) {
                    startupCheckAttempted = true
                    if (loaded.checkUpdatesOnStartup) {
                        checkForUpdates(silentOnFailure = true)
                    }
                }
            }
        }
    }

    fun setAppTheme(theme: AppThemeMode) {
        viewModelScope.launch { settingsRepository.setAppTheme(theme) }
    }

    fun setDefaultRenderTheme(theme: PageRenderTheme) {
        viewModelScope.launch { settingsRepository.setDefaultRenderTheme(theme) }
    }

    fun setDefaultScrollMode(scrollMode: ScrollMode) {
        viewModelScope.launch { settingsRepository.setDefaultScrollMode(scrollMode) }
    }

    fun setDefaultZoomScale(zoomScale: Float) {
        val quantized = ((zoomScale * ZOOM_STEP_SCALE).roundToInt() / ZOOM_STEP_SCALE)
            .coerceIn(AppSettings.MIN_DEFAULT_ZOOM, AppSettings.MAX_DEFAULT_ZOOM)
        viewModelScope.launch { settingsRepository.setDefaultZoomScale(quantized) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
    }

    fun setIncludePreReleases(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludePreReleases(enabled) }
    }

    fun setCheckUpdatesOnStartup(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCheckUpdatesOnStartup(enabled) }
    }

    fun checkForUpdates(silentOnFailure: Boolean = false) {
        if (uiStateMutable.value.updateCheckState is UpdateCheckUiState.Checking) return
        checkJob?.cancel()
        checkJob =
            viewModelScope.launch {
                if (!silentOnFailure) {
                    uiStateMutable.update { it.copy(updateCheckState = UpdateCheckUiState.Checking) }
                }
                val includePre = uiStateMutable.value.settings.includePreReleases
                when (val result = appUpdateRepository.checkForUpdate(includePreReleases = includePre)) {
                    is AppUpdateCheckResult.Success -> {
                        val nextCheckState =
                            when (val status = result.status) {
                                is AppUpdateStatus.UpToDate -> UpdateCheckUiState.UpToDate(status)
                                is AppUpdateStatus.UpdateAvailable -> UpdateCheckUiState.Available(status)
                            }
                        uiStateMutable.update {
                            it.copy(
                                updateCheckState = nextCheckState,
                                canInstallPackages = appUpdateRepository.canRequestPackageInstalls(),
                            )
                        }
                    }

                    is AppUpdateCheckResult.Failure -> {
                        if (!silentOnFailure) {
                            uiStateMutable.update { it.copy(updateCheckState = UpdateCheckUiState.Error(result.error)) }
                        }
                    }
                }
            }
    }

    fun downloadUpdate(release: AppReleaseInfo) {
        downloadJob?.cancel()
        downloadJob =
            viewModelScope.launch {
                uiStateMutable.update {
                    it.copy(
                        downloadState = AppUpdateDownloadState.Downloading(0L, release.apkSizeBytes),
                        canInstallPackages = appUpdateRepository.canRequestPackageInstalls(),
                    )
                }
                appUpdateRepository.downloadUpdate(release).collect { state ->
                    val canInstall = appUpdateRepository.canRequestPackageInstalls()
                    uiStateMutable.update {
                        it.copy(downloadState = state, canInstallPackages = canInstall)
                    }
                    if (state is AppUpdateDownloadState.ReadyToInstall && canInstall) {
                        eventsMutable.tryEmit(SettingsEvent.LaunchPackageInstaller(state.contentUriString))
                    }
                }
            }
    }

    fun installReadyApk() {
        val ready = uiStateMutable.value.downloadState as? AppUpdateDownloadState.ReadyToInstall ?: return
        val canInstall = appUpdateRepository.canRequestPackageInstalls()
        uiStateMutable.update { it.copy(canInstallPackages = canInstall) }
        if (canInstall) {
            eventsMutable.tryEmit(SettingsEvent.LaunchPackageInstaller(ready.contentUriString))
        }
    }

    fun refreshInstallPermission() {
        uiStateMutable.update {
            it.copy(canInstallPackages = appUpdateRepository.canRequestPackageInstalls())
        }
    }

    fun clearUpdateCache() {
        downloadJob?.cancel()
        downloadJob = null
        viewModelScope.launch {
            appUpdateRepository.clearCachedUpdates()
            uiStateMutable.update {
                it.copy(
                    downloadState = AppUpdateDownloadState.Idle,
                    statusMessageRes = R.string.settings_update_cache_cleared,
                )
            }
        }
    }

    fun toggleReleaseNotes() {
        uiStateMutable.update { it.copy(releaseNotesExpanded = !it.releaseNotesExpanded) }
    }

    fun reportInstallerUnavailable() {
        uiStateMutable.update {
            it.copy(statusMessageRes = R.string.settings_update_installer_unavailable)
        }
    }

    fun dismissStatusMessage() {
        uiStateMutable.update { it.copy(statusMessageRes = null) }
    }

    companion object {
        private const val ZOOM_STEP_SCALE = 10f

        fun provideFactory(
            settingsRepository: SettingsRepository,
            appUpdateRepository: AppUpdateRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(
                        settingsRepository = settingsRepository,
                        appUpdateRepository = appUpdateRepository,
                    ) as T
            }
    }
}
