package com.cl0udshifter.pdfeasy.core.domain

import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para verificacao, download e validacao de atualizacoes via Releases do GitHub.
 */
interface AppUpdateRepository {
    val currentVersionName: String
    val currentVersionCode: Long

    suspend fun checkForUpdate(includePreReleases: Boolean): AppUpdateCheckResult

    fun downloadUpdate(release: AppReleaseInfo): Flow<AppUpdateDownloadState>

    fun canRequestPackageInstalls(): Boolean

    suspend fun clearCachedUpdates()
}
