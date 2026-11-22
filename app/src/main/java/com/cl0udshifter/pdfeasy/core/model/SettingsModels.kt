package com.cl0udshifter.pdfeasy.core.model

/**
 * Preferencia de tema da interface do aplicativo.
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Configuracoes globais do aplicativo e valores padrao para novos documentos abertos.
 */
data class AppSettings(
    val appTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultRenderTheme: PageRenderTheme = PageRenderTheme.ORIGINAL,
    val defaultScrollMode: ScrollMode = ScrollMode.CONTINUOUS_VERTICAL,
    val defaultZoomScale: Float = DEFAULT_ZOOM_SCALE,
    val keepScreenOn: Boolean = false,
    val includePreReleases: Boolean = true,
    val checkUpdatesOnStartup: Boolean = false,
    val lastUpdateCheckEpochMillis: Long? = null,
) {
    companion object {
        const val MIN_DEFAULT_ZOOM = 1.0f
        const val MAX_DEFAULT_ZOOM = 3.0f
        const val DEFAULT_ZOOM_SCALE = 1.0f
    }
}
