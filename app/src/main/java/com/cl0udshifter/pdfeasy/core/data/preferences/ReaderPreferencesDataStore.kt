package com.cl0udshifter.pdfeasy.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

internal val Context.readerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences",
)

/**
 * Preferencias globais padrao aplicadas ao abrir documentos sem historico proprio.
 */
data class ReaderDefaults(
    val renderTheme: PageRenderTheme = PageRenderTheme.ORIGINAL,
    val scrollMode: ScrollMode = ScrollMode.CONTINUOUS_VERTICAL,
)

interface ReaderPreferencesStore {
    val defaults: Flow<ReaderDefaults>

    suspend fun getDefaults(): ReaderDefaults

    suspend fun updateDefaults(
        renderTheme: PageRenderTheme,
        scrollMode: ScrollMode,
    )
}

class DataStoreReaderPreferences(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesStore {
    constructor(context: Context) : this(context.applicationContext.readerPreferencesDataStore)

    override val defaults: Flow<ReaderDefaults> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }.map { preferences ->
                ReaderDefaults(
                    renderTheme = parseTheme(preferences[KEY_RENDER_THEME]),
                    scrollMode = parseScrollMode(preferences[KEY_SCROLL_MODE]),
                )
            }

    override suspend fun getDefaults(): ReaderDefaults = defaults.first()

    override suspend fun updateDefaults(
        renderTheme: PageRenderTheme,
        scrollMode: ScrollMode,
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_RENDER_THEME] = renderTheme.name
            preferences[KEY_SCROLL_MODE] = scrollMode.name
        }
    }

    private fun parseTheme(raw: String?): PageRenderTheme =
        PageRenderTheme.entries.firstOrNull { it.name == raw } ?: PageRenderTheme.ORIGINAL

    private fun parseScrollMode(raw: String?): ScrollMode =
        ScrollMode.entries.firstOrNull { it.name == raw } ?: ScrollMode.CONTINUOUS_VERTICAL

    private companion object {
        val KEY_RENDER_THEME = stringPreferencesKey("default_render_theme")
        val KEY_SCROLL_MODE = stringPreferencesKey("default_scroll_mode")
    }
}
