package com.cl0udshifter.pdfeasy.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cl0udshifter.pdfeasy.core.data.preferences.DataStoreReaderPreferences
import com.cl0udshifter.pdfeasy.core.data.preferences.ReaderDefaults
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SettingsRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `settings repository persists all fields clamps zoom and syncs with reader preferences`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings_test.preferences_pb").toFile() },
                )
            val settingsRepo = SettingsRepositoryImpl(dataStore, dispatcher)
            val readerPrefs = DataStoreReaderPreferences(dataStore)

            assertEquals(AppSettings(), settingsRepo.getSettings())
            settingsRepo.setAppTheme(AppThemeMode.DARK)
            settingsRepo.setDefaultRenderTheme(PageRenderTheme.SEPIA)
            settingsRepo.setDefaultScrollMode(ScrollMode.CONTINUOUS_HORIZONTAL)
            settingsRepo.setDefaultZoomScale(9.0f)
            settingsRepo.setKeepScreenOn(true)
            settingsRepo.setIncludePreReleases(false)
            settingsRepo.setCheckUpdatesOnStartup(true)
            settingsRepo.updateLastUpdateCheckTimestamp(12_345L)

            val saved = settingsRepo.getSettings()
            assertEquals(AppThemeMode.DARK, saved.appTheme)
            assertEquals(PageRenderTheme.SEPIA, saved.defaultRenderTheme)
            assertEquals(ScrollMode.CONTINUOUS_HORIZONTAL, saved.defaultScrollMode)
            assertEquals(AppSettings.MAX_DEFAULT_ZOOM, saved.defaultZoomScale)
            assertTrue(saved.keepScreenOn && !saved.includePreReleases && saved.checkUpdatesOnStartup)
            assertEquals(12_345L, saved.lastUpdateCheckEpochMillis)
            assertEquals(
                ReaderDefaults(PageRenderTheme.SEPIA, ScrollMode.CONTINUOUS_HORIZONTAL),
                readerPrefs.getDefaults(),
            )

            readerPrefs.updateDefaults(PageRenderTheme.DARK, ScrollMode.CONTINUOUS_VERTICAL)
            val synced = settingsRepo.observeSettings().first()
            assertEquals(PageRenderTheme.DARK, synced.defaultRenderTheme)
            assertEquals(ScrollMode.CONTINUOUS_VERTICAL, synced.defaultScrollMode)
        }
}
