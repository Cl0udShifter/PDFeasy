package com.cl0udshifter.pdfeasy.core.data.update

import com.cl0udshifter.pdfeasy.core.domain.AppUpdateRepository
import com.cl0udshifter.pdfeasy.core.domain.SettingsRepository
import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

internal data class InstalledAppVersion(
    val versionName: String,
    val versionCode: Long,
)

internal class AppUpdateRepositoryImpl(
    installedVersion: InstalledAppVersion,
    private val remoteDataSource: GitHubReleaseRemoteDataSource,
    private val apkStorage: ApkCacheStorage,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AppUpdateRepository {
    override val currentVersionName: String = installedVersion.versionName
    override val currentVersionCode: Long = installedVersion.versionCode
    override suspend fun checkForUpdate(includePreReleases: Boolean): AppUpdateCheckResult =
        withContext(ioDispatcher) {
            when (val fetched = remoteDataSource.fetchReleases()) {
                is GitHubReleasesFetchResult.Failure ->
                    AppUpdateCheckResult.Failure(fetched.error)

                is GitHubReleasesFetchResult.Success ->
                    processFetchedReleases(fetched.rawJson, includePreReleases)
            }
        }

    override fun downloadUpdate(release: AppReleaseInfo): Flow<AppUpdateDownloadState> =
        flow {
            val partFile = preparePartFileSafely(release.apkAssetName)
            if (partFile == null) {
                emit(AppUpdateDownloadState.Failed(AppUpdateError.StorageFailure("Unable to prepare cache file")))
                return@flow
            }

            try {
                emit(AppUpdateDownloadState.Downloading(0L, release.apkSizeBytes))
                val downloadResult =
                    remoteDataSource.downloadApk(
                        url = release.apkDownloadUrl,
                        destinationFile = partFile,
                        expectedTotalBytes = release.apkSizeBytes,
                    ) { downloaded, total ->
                        emit(AppUpdateDownloadState.Downloading(downloaded, total))
                    }
                handleDownloadOutcome(release, partFile, downloadResult)
            } catch (cancelled: CancellationException) {
                partFile.delete()
                throw cancelled
            }
        }.flowOn(ioDispatcher)

    override fun canRequestPackageInstalls(): Boolean = apkStorage.canRequestPackageInstalls()

    override suspend fun clearCachedUpdates() {
        withContext(ioDispatcher) {
            apkStorage.clearAll()
        }
    }

    private suspend fun processFetchedReleases(
        rawJson: String,
        includePreReleases: Boolean,
    ): AppUpdateCheckResult {
        val parsed =
            GitHubReleaseParser.parseReleasesJson(rawJson)
                ?: return AppUpdateCheckResult.Failure(
                    AppUpdateError.InvalidResponse("Malformed GitHub releases JSON payload"),
                )

        val evaluated =
            GitHubReleaseParser.evaluateLatestRelease(
                releases = parsed,
                currentVersionName = currentVersionName,
                includePreReleases = includePreReleases,
            )

        if (evaluated is AppUpdateCheckResult.Success) {
            settingsRepository.updateLastUpdateCheckTimestamp(nowMillis())
            if (evaluated.status is AppUpdateStatus.UpToDate) {
                apkStorage.clearAll()
            }
        }
        return evaluated
    }

    private fun preparePartFileSafely(assetName: String): File? =
        try {
            apkStorage.clearAll()
            apkStorage.preparePartFile(assetName)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }

    private suspend fun FlowCollector<AppUpdateDownloadState>.handleDownloadOutcome(
        release: AppReleaseInfo,
        partFile: File,
        downloadResult: ApkDownloadResult,
    ) {
        when (downloadResult) {
            is ApkDownloadResult.Failure -> {
                partFile.delete()
                emit(AppUpdateDownloadState.Failed(downloadResult.error))
            }

            is ApkDownloadResult.Success -> {
                val expected = release.expectedSha256
                val mismatch =
                    expected != null && !expected.equals(downloadResult.actualSha256, ignoreCase = true)
                if (mismatch) {
                    partFile.delete()
                    emit(
                        AppUpdateDownloadState.Failed(
                            AppUpdateError.ChecksumMismatch(
                                expectedSha256 = expected.orEmpty(),
                                actualSha256 = downloadResult.actualSha256,
                            ),
                        ),
                    )
                } else {
                    finalizeAndEmitReady(release, partFile, sha256Verified = expected != null)
                }
            }
        }
    }

    private suspend fun FlowCollector<AppUpdateDownloadState>.finalizeAndEmitReady(
        release: AppReleaseInfo,
        partFile: File,
        sha256Verified: Boolean,
    ) {
        try {
            val apkFile = apkStorage.finalizeApkFile(partFile, release.apkAssetName)
            val contentUri = apkStorage.contentUriForFile(apkFile)
            emit(
                AppUpdateDownloadState.ReadyToInstall(
                    release = release,
                    contentUriString = contentUri,
                    sha256Verified = sha256Verified,
                ),
            )
        } catch (error: IOException) {
            partFile.delete()
            emit(
                AppUpdateDownloadState.Failed(
                    AppUpdateError.StorageFailure(error.message ?: "Failed to finalize APK file"),
                ),
            )
        } catch (error: IllegalArgumentException) {
            partFile.delete()
            emit(
                AppUpdateDownloadState.Failed(
                    AppUpdateError.StorageFailure(error.message ?: "Failed to resolve FileProvider URI"),
                ),
            )
        }
    }
}
