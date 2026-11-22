package com.cl0udshifter.pdfeasy.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cl0udshifter.pdfeasy.core.domain.LibraryItemWithProgress
import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import com.cl0udshifter.pdfeasy.ui.library.LibraryScreenCallbacks
import com.cl0udshifter.pdfeasy.ui.library.LibraryScreenContent
import com.cl0udshifter.pdfeasy.ui.library.LibraryUiState
import com.cl0udshifter.pdfeasy.ui.reader.ReaderHudCallbacks
import com.cl0udshifter.pdfeasy.ui.reader.ReaderHudOverlay
import com.cl0udshifter.pdfeasy.ui.reader.ReaderUiState
import com.cl0udshifter.pdfeasy.ui.theme.PdfEasyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenRendersPreferencesAndUpdateCardCallbacks() {
        var selectedAppTheme: AppThemeMode? = null
        var selectedScrollMode: ScrollMode? = null
        var keepScreenOnResult: Boolean? = null
        var checkClicks = 0
        var downloadedTag: String? = null
        val release = sampleRelease()
        val uiState =
            SettingsUiState(
                settings = AppSettings(appTheme = AppThemeMode.SYSTEM, defaultZoomScale = 1.5f),
                currentVersionName = "0.1.1",
                currentVersionCode = 2L,
                updateCheckState = UpdateCheckUiState.Available(AppUpdateStatus.UpdateAvailable("0.1.1", release)),
                releaseNotesExpanded = true,
            )

        composeRule.setContent {
            PdfEasyTheme(darkTheme = false) {
                SettingsScreenContent(
                    uiState = uiState,
                    callbacks =
                        noopSettingsCallbacks().copy(
                            onAppThemeSelected = { selectedAppTheme = it },
                            onDefaultScrollModeSelected = { selectedScrollMode = it },
                            onKeepScreenOnChanged = { keepScreenOnResult = it },
                            onCheckUpdatesClick = { checkClicks += 1 },
                            onDownloadUpdateClick = { downloadedTag = it.tagName },
                        ),
                )
            }
        }

        composeRule.onNodeWithText("Configurações").assertIsDisplayed()
        composeRule.onAllNodesWithText("Escuro")[0].performClick()
        assertEquals(AppThemeMode.DARK, selectedAppTheme)
        composeRule.onNodeWithText("Horizontal").performScrollTo().performClick()
        assertEquals(ScrollMode.CONTINUOUS_HORIZONTAL, selectedScrollMode)
        composeRule.onNodeWithText(UpdateFormatters.formatZoomMultiplier(1.5f))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings_keep_screen_on_switch").performScrollTo().performClick()
        assertEquals(true, keepScreenOnResult)
        composeRule.onNodeWithText("v0.1.1 (build 2)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Verificar atualizações").performScrollTo().performClick()
        assertEquals(1, checkClicks)
        composeRule.onNodeWithText("PDFeasy v0.2.0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Baixar e instalar").performScrollTo().performClick()
        assertEquals("v0.2.0", downloadedTag)
    }

    @Test
    fun readyToInstallPermissionPromptAndEntryPointsRespondCorrectly() {
        var permissionRequested = 0
        var librarySettingsClicks = 0
        var readerSettingsClicks = 0
        var activePane by mutableIntStateOf(0)
        val release = sampleRelease()

        composeRule.setContent {
            PdfEasyTheme(darkTheme = false) {
                ReadyToInstallScreenFixture(
                    activePane = activePane,
                    release = release,
                    onRequestPermission = { permissionRequested += 1 },
                    onLibrarySettings = { librarySettingsClicks += 1 },
                    onReaderSettings = { readerSettingsClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Permitir instalação").performScrollTo().performClick()
        assertEquals(1, permissionRequested)

        activePane = 1
        composeRule.onNode(hasContentDescription("Abrir configurações")).performClick()
        assertEquals(1, librarySettingsClicks)

        activePane = 2
        composeRule.onNode(hasContentDescription("Abrir configurações")).performClick()
        assertEquals(1, readerSettingsClicks)
    }

    @androidx.compose.runtime.Composable
    private fun ReadyToInstallScreenFixture(
        activePane: Int,
        release: AppReleaseInfo,
        onRequestPermission: () -> Unit,
        onLibrarySettings: () -> Unit,
        onReaderSettings: () -> Unit,
    ) {
        when (activePane) {
            0 ->
                SettingsScreenContent(
                    uiState =
                        SettingsUiState(
                            currentVersionName = "0.1.1",
                            currentVersionCode = 2L,
                            updateCheckState =
                                UpdateCheckUiState.Available(AppUpdateStatus.UpdateAvailable("0.1.1", release)),
                            downloadState =
                                AppUpdateDownloadState.ReadyToInstall(
                                    release = release,
                                    contentUriString = "content://apk/0.2.0",
                                    sha256Verified = true,
                                ),
                            canInstallPackages = false,
                        ),
                    callbacks = noopSettingsCallbacks().copy(onRequestInstallPermissionClick = onRequestPermission),
                )

            1 ->
                LibraryScreenContent(
                    uiState = LibraryUiState(),
                    callbacks = noopLibraryCallbacks(onSettingsClick = onLibrarySettings),
                    hasUpdateAvailable = true,
                )

            else ->
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderHudOverlay(
                        uiState =
                            ReaderUiState(
                                documentId = "doc-1",
                                uriString = "content://docs/1.pdf",
                                displayName = "Manual.pdf",
                                isLoading = false,
                                pageCount = 10,
                            ),
                        callbacks = noopReaderCallbacks(onOpenSettings = onReaderSettings),
                    )
                }
        }
    }

    private fun noopLibraryCallbacks(onSettingsClick: () -> Unit): LibraryScreenCallbacks =
        LibraryScreenCallbacks(
            onOpenPdfClick = {},
            onSettingsClick = onSettingsClick,
            onSearchQueryChange = {},
            onClearSearch = {},
            onFilterSelected = {},
            onDocumentClick = { _: LibraryItemWithProgress -> },
            onDocumentOptionsClick = {},
        )

    private fun noopReaderCallbacks(onOpenSettings: () -> Unit): ReaderHudCallbacks =
        ReaderHudCallbacks(
            onBackClick = {},
            onOpenBookmarksSheet = {},
            onOpenSettings = onOpenSettings,
            onSelectRenderTheme = { _: PageRenderTheme -> },
            onToggleScrollMode = {},
            onJumpToPage = {},
            onToggleBookmark = {},
            onOpenSearch = {},
            onCloseSearch = {},
            onSearchQueryChange = {},
            onPreviousMatch = {},
            onNextMatch = {},
        )

    private fun sampleRelease(): AppReleaseInfo =
        AppReleaseInfo(
            tagName = "v0.2.0",
            versionName = "0.2.0",
            releaseName = "PDFeasy v0.2.0",
            releaseNotes = "Correcao no zoom e nova barra editorial.",
            htmlUrl = "https://github.com/Cl0udShifter/PDFeasy/releases/tag/v0.2.0",
            publishedAtIso = "2026-11-22T20:00:00Z",
            isPreRelease = true,
            apkAssetName = "PDFeasy-v0.2.0.apk",
            apkDownloadUrl = "https://example.com/app.apk",
            apkSizeBytes = 21_000_000L,
            expectedSha256 = "deadbeef",
        )

    private fun noopSettingsCallbacks(): SettingsCallbacks =
        SettingsCallbacks(
            onBackClick = {},
            onAppThemeSelected = {},
            onDefaultRenderThemeSelected = {},
            onDefaultScrollModeSelected = {},
            onDefaultZoomChanged = {},
            onKeepScreenOnChanged = {},
            onIncludePreReleasesChanged = {},
            onCheckUpdatesOnStartupChanged = {},
            onCheckUpdatesClick = {},
            onToggleReleaseNotes = {},
            onDownloadUpdateClick = {},
            onInstallReadyClick = {},
            onRequestInstallPermissionClick = {},
            onClearUpdateCacheClick = {},
        )
}
