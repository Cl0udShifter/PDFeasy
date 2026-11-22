package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.AppSettings
import com.cl0udshifter.pdfeasy.core.model.AppThemeMode
import com.cl0udshifter.pdfeasy.core.model.PageRenderTheme
import com.cl0udshifter.pdfeasy.core.model.ScrollMode
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de persistencia das configuracoes globais do aplicativo e padroes de leitura.
 */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>

    suspend fun getSettings(): AppSettings

    suspend fun setAppTheme(theme: AppThemeMode)

    suspend fun setDefaultRenderTheme(theme: PageRenderTheme)

    suspend fun setDefaultScrollMode(scrollMode: ScrollMode)

    suspend fun setDefaultZoomScale(zoomScale: Float)

    suspend fun setKeepScreenOn(enabled: Boolean)

    suspend fun setIncludePreReleases(enabled: Boolean)

    suspend fun setCheckUpdatesOnStartup(enabled: Boolean)

    suspend fun updateLastUpdateCheckTimestamp(epochMillis: Long)
}
