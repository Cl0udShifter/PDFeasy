package com.cl0udshifter.pdfeasy.core.model

/**
 * Metadados de uma release elegivel publicada no GitHub (Cl0udShifter/PDFeasy).
 */
data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAtIso: String,
    val isPreRelease: Boolean,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val expectedSha256: String?,
)

/**
 * Resultado da comparacao entre a versao instalada e a release mais recente encontrada.
 */
sealed interface AppUpdateStatus {
    data class UpToDate(
        val currentVersionName: String,
        val latestCheckedTag: String?,
    ) : AppUpdateStatus

    data class UpdateAvailable(
        val currentVersionName: String,
        val release: AppReleaseInfo,
    ) : AppUpdateStatus
}

/**
 * Erros tipados do fluxo de verificacao e download de atualizacoes via GitHub Releases.
 */
sealed interface AppUpdateError {
    data object NetworkUnavailable : AppUpdateError

    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : AppUpdateError

    data class HttpFailure(
        val statusCode: Int,
        val message: String,
    ) : AppUpdateError

    data object NoCompatibleApkAsset : AppUpdateError

    data class ChecksumMismatch(
        val expectedSha256: String,
        val actualSha256: String,
    ) : AppUpdateError

    data class InvalidResponse(
        val reason: String,
    ) : AppUpdateError

    data class StorageFailure(
        val message: String,
    ) : AppUpdateError
}

/**
 * Resultado explicito da consulta de atualizacao.
 */
sealed interface AppUpdateCheckResult {
    data class Success(
        val status: AppUpdateStatus,
    ) : AppUpdateCheckResult

    data class Failure(
        val error: AppUpdateError,
    ) : AppUpdateCheckResult
}

/**
 * Estados emitidos durante o download e validacao de integridade do APK de atualizacao.
 */
sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : AppUpdateDownloadState {
        val progressFraction: Float?
            get() = if (totalBytes <= 0L) null else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val contentUriString: String,
        val sha256Verified: Boolean,
    ) : AppUpdateDownloadState

    data class Failed(
        val error: AppUpdateError,
    ) : AppUpdateDownloadState
}
