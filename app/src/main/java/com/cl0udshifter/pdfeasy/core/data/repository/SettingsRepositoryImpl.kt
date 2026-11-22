package com.cl0udshifter.pdfeasy.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cl0udshifter.pdfeasy.core.domain.SettingsRepository
import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher,
) : SettingsRepository {
    private val settingsFlow: Flow<AppSettings> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map(SettingsPreferencesCodec::decode)
            .flowOn(ioDispatcher)

    override fun observeSettings(): Flow<AppSettings> = settingsFlow

    override suspend fun getSettings(): AppSettings =
        withContext(ioDispatcher) {
            settingsFlow.first()
        }

    override suspend fun setAppTheme(theme: AppThemeMode) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_APP_THEME] = theme.name
        }
    }

    override suspend fun setDefaultRenderTheme(theme: PageRenderTheme) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_RENDER_THEME] = theme.name
        }
    }

    override suspend fun setDefaultScrollMode(scrollMode: ScrollMode) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_SCROLL_MODE] = scrollMode.name
        }
    }

    override suspend fun setDefaultZoomScale(zoomScale: Float) {
        val clamped = zoomScale.coerceIn(AppSettings.MIN_DEFAULT_ZOOM, AppSettings.MAX_DEFAULT_ZOOM)
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_DEFAULT_ZOOM_SCALE] = clamped
        }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    override suspend fun setIncludePreReleases(enabled: Boolean) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_INCLUDE_PRE_RELEASES] = enabled
        }
    }

    override suspend fun setCheckUpdatesOnStartup(enabled: Boolean) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_CHECK_UPDATES_ON_STARTUP] = enabled
        }
    }

    override suspend fun updateLastUpdateCheckTimestamp(epochMillis: Long) {
        SettingsPreferencesCodec.edit(dataStore, ioDispatcher) { preferences ->
            preferences[SettingsPreferencesCodec.KEY_LAST_UPDATE_CHECK] = epochMillis
        }
    }
}

private object SettingsPreferencesCodec {
    val KEY_APP_THEME = stringPreferencesKey("app_theme_mode")
    val KEY_RENDER_THEME = stringPreferencesKey("default_render_theme")
    val KEY_SCROLL_MODE = stringPreferencesKey("default_scroll_mode")
    val KEY_DEFAULT_ZOOM_SCALE = floatPreferencesKey("default_zoom_scale")
    val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val KEY_INCLUDE_PRE_RELEASES = booleanPreferencesKey("include_pre_releases")
    val KEY_CHECK_UPDATES_ON_STARTUP = booleanPreferencesKey("check_updates_on_startup")
    val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_epoch_ms")

    suspend fun edit(
        dataStore: DataStore<Preferences>,
        ioDispatcher: CoroutineDispatcher,
        block: (MutablePreferences) -> Unit,
    ) {
        withContext(ioDispatcher) {
            dataStore.edit(block)
        }
    }

    fun decode(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        val rawZoom = preferences[KEY_DEFAULT_ZOOM_SCALE] ?: defaults.defaultZoomScale
        return AppSettings(
            appTheme = parseEnum(preferences[KEY_APP_THEME], defaults.appTheme),
            defaultRenderTheme = parseEnum(preferences[KEY_RENDER_THEME], defaults.defaultRenderTheme),
            defaultScrollMode = parseEnum(preferences[KEY_SCROLL_MODE], defaults.defaultScrollMode),
            defaultZoomScale = rawZoom.coerceIn(AppSettings.MIN_DEFAULT_ZOOM, AppSettings.MAX_DEFAULT_ZOOM),
            keepScreenOn = preferences[KEY_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            includePreReleases = preferences[KEY_INCLUDE_PRE_RELEASES] ?: defaults.includePreReleases,
            checkUpdatesOnStartup = preferences[KEY_CHECK_UPDATES_ON_STARTUP] ?: defaults.checkUpdatesOnStartup,
            lastUpdateCheckEpochMillis = preferences[KEY_LAST_UPDATE_CHECK],
        )
    }

    private inline fun <reified T : Enum<T>> parseEnum(
        raw: String?,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
