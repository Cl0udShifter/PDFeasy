package com.cl0udshifter.pdfeasy.ui.settings

import com.cl0udshifter.pdfeasy.R
import com.cl0udshifter.pdfeasy.core.domain.AppUpdateRepository
import com.cl0udshifter.pdfeasy.core.domain.SettingsRepository
import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preference updates persist clamped settings without auto check when disabled`() =
        runTest(testDispatcher) {
            val settingsRepo = FakeSettingsRepo(AppSettings(checkUpdatesOnStartup = false))
            val updateRepo = FakeUpdateRepo()
            val viewModel = SettingsViewModel(settingsRepo, updateRepo)
            advanceUntilIdle()

            assertEquals(0, updateRepo.checkCallCount)

            viewModel.setAppTheme(AppThemeMode.DARK)
            viewModel.setDefaultRenderTheme(PageRenderTheme.SEPIA)
            viewModel.setDefaultScrollMode(ScrollMode.CONTINUOUS_HORIZONTAL)
            viewModel.setDefaultZoomScale(2.14f)
            viewModel.setKeepScreenOn(true)
            viewModel.setIncludePreReleases(false)
            viewModel.setCheckUpdatesOnStartup(true)
            advanceUntilIdle()

            val saved = viewModel.uiState.value.settings
            assertEquals(AppThemeMode.DARK, saved.appTheme)
            assertEquals(PageRenderTheme.SEPIA, saved.defaultRenderTheme)
            assertEquals(ScrollMode.CONTINUOUS_HORIZONTAL, saved.defaultScrollMode)
            assertEquals(2.1f, saved.defaultZoomScale, 0.001f)
            assertTrue(saved.keepScreenOn)
            assertFalse(saved.includePreReleases)
            assertTrue(saved.checkUpdatesOnStartup)
            assertEquals(0, updateRepo.checkCallCount)
        }

    @Test
    fun `startup check runs once when enabled and keeps idle if startup check fails silently`() =
        runTest(testDispatcher) {
            val settingsRepo = FakeSettingsRepo(AppSettings(checkUpdatesOnStartup = true, includePreReleases = true))
            val updateRepo =
                FakeUpdateRepo().apply {
                    nextCheckResult = AppUpdateCheckResult.Failure(AppUpdateError.NetworkUnavailable)
                }
            val viewModel = SettingsViewModel(settingsRepo, updateRepo)
            advanceUntilIdle()

            assertEquals(1, updateRepo.checkCallCount)
            assertEquals(UpdateCheckUiState.Idle, viewModel.uiState.value.updateCheckState)

            viewModel.checkForUpdates()
            advanceUntilIdle()

            assertEquals(2, updateRepo.checkCallCount)
            assertTrue(viewModel.uiState.value.updateCheckState is UpdateCheckUiState.Error)
        }

    @Test
    fun `update check download permission refresh and cache clear follow expected states`() =
        runTest(testDispatcher) {
            val settingsRepo = FakeSettingsRepo(AppSettings(includePreReleases = false))
            val release = sampleRelease()
            val updateRepo =
                FakeUpdateRepo().apply {
                    canInstall = false
                    nextCheckResult =
                        AppUpdateCheckResult.Success(
                            AppUpdateStatus.UpdateAvailable(currentVersionName = "0.1.1", release = release),
                        )
                    downloadEmissions =
                        listOf(
                            AppUpdateDownloadState.Downloading(512L, 1024L),
                            AppUpdateDownloadState.ReadyToInstall(
                                release = release,
                                contentUriString = "content://apk/0.2.0",
                                sha256Verified = true,
                            ),
                        )
                }
            val viewModel = SettingsViewModel(settingsRepo, updateRepo)
            val emittedEvents = mutableListOf<SettingsEvent>()
            val collectorJob = launch { viewModel.events.collect { emittedEvents.add(it) } }

            viewModel.checkForUpdates()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.hasUpdateAvailable)
            assertFalse(viewModel.uiState.value.canInstallPackages)

            viewModel.downloadUpdate(release)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.downloadState is AppUpdateDownloadState.ReadyToInstall)
            assertTrue(emittedEvents.isEmpty())

            updateRepo.canInstall = true
            viewModel.refreshInstallPermission()
            viewModel.installReadyApk()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.canInstallPackages)
            assertEquals(1, emittedEvents.size)

            viewModel.clearUpdateCache()
            advanceUntilIdle()

            assertTrue(updateRepo.cacheCleared)
            assertEquals(AppUpdateDownloadState.Idle, viewModel.uiState.value.downloadState)
            assertEquals(R.string.settings_update_cache_cleared, viewModel.uiState.value.statusMessageRes)
            collectorJob.cancel()
        }

    @Test
    fun `error formatter maps all typed AppUpdateError variants`() {
        assertEquals(
            R.string.settings_update_error_network,
            UpdateFormatters.describeError(AppUpdateError.NetworkUnavailable).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_rate_limited_seconds,
            UpdateFormatters.describeError(AppUpdateError.RateLimited(30L)).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_rate_limited,
            UpdateFormatters.describeError(AppUpdateError.RateLimited(null)).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_http,
            UpdateFormatters.describeError(AppUpdateError.HttpFailure(500, "err")).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_no_apk,
            UpdateFormatters.describeError(AppUpdateError.NoCompatibleApkAsset).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_checksum,
            UpdateFormatters.describeError(AppUpdateError.ChecksumMismatch("a", "b")).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_invalid_response,
            UpdateFormatters.describeError(AppUpdateError.InvalidResponse("bad")).messageRes,
        )
        assertEquals(
            R.string.settings_update_error_storage,
            UpdateFormatters.describeError(AppUpdateError.StorageFailure("disk")).messageRes,
        )
    }

    private fun sampleRelease(): AppReleaseInfo =
        AppReleaseInfo(
            tagName = "v0.2.0",
            versionName = "0.2.0",
            releaseName = "PDFeasy v0.2.0",
            releaseNotes = "Melhorias gerais",
            htmlUrl = "https://github.com/Cl0udShifter/PDFeasy/releases/tag/v0.2.0",
            publishedAtIso = "2026-11-23T00:00:00Z",
            isPreRelease = true,
            apkAssetName = "PDFeasy-v0.2.0.apk",
            apkDownloadUrl = "https://github.com/Cl0udShifter/PDFeasy/releases/download/v0.2.0/PDFeasy-v0.2.0.apk",
            apkSizeBytes = 20_000_000L,
            expectedSha256 = "abc123",
        )

    private class FakeSettingsRepo(
        initial: AppSettings = AppSettings(),
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)

        override fun observeSettings(): Flow<AppSettings> = state

        override suspend fun getSettings(): AppSettings = state.value

        override suspend fun setAppTheme(theme: AppThemeMode) {
            state.value = state.value.copy(appTheme = theme)
        }

        override suspend fun setDefaultRenderTheme(theme: PageRenderTheme) {
            state.value = state.value.copy(defaultRenderTheme = theme)
        }

        override suspend fun setDefaultScrollMode(scrollMode: ScrollMode) {
            state.value = state.value.copy(defaultScrollMode = scrollMode)
        }

        override suspend fun setDefaultZoomScale(zoomScale: Float) {
            state.value = state.value.copy(defaultZoomScale = zoomScale)
        }

        override suspend fun setKeepScreenOn(enabled: Boolean) {
            state.value = state.value.copy(keepScreenOn = enabled)
        }

        override suspend fun setIncludePreReleases(enabled: Boolean) {
            state.value = state.value.copy(includePreReleases = enabled)
        }

        override suspend fun setCheckUpdatesOnStartup(enabled: Boolean) {
            state.value = state.value.copy(checkUpdatesOnStartup = enabled)
        }

        override suspend fun updateLastUpdateCheckTimestamp(epochMillis: Long) {
            state.value = state.value.copy(lastUpdateCheckEpochMillis = epochMillis)
        }
    }

    private class FakeUpdateRepo : AppUpdateRepository {
        override val currentVersionName: String = "0.1.1"
        override val currentVersionCode: Long = 2L
        var canInstall: Boolean = true
        var checkCallCount: Int = 0
        var cacheCleared: Boolean = false
        var nextCheckResult: AppUpdateCheckResult =
            AppUpdateCheckResult.Success(AppUpdateStatus.UpToDate("0.1.1", "v0.1.1"))
        var downloadEmissions: List<AppUpdateDownloadState> = emptyList()

        override suspend fun checkForUpdate(includePreReleases: Boolean): AppUpdateCheckResult {
            checkCallCount += 1
            return nextCheckResult
        }

        override fun downloadUpdate(release: AppReleaseInfo): Flow<AppUpdateDownloadState> =
            flow { downloadEmissions.forEach { emit(it) } }

        override fun canRequestPackageInstalls(): Boolean = canInstall

        override suspend fun clearCachedUpdates() {
            cacheCleared = true
        }
    }
}
