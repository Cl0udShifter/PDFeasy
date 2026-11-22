package com.cl0udshifter.pdfeasy.core.data.update

import android.content.Context
import androidx.core.content.FileProvider
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.security.MessageDigest

internal sealed interface GitHubReleasesFetchResult {
    data class Success(
        val rawJson: String,
    ) : GitHubReleasesFetchResult

    data class Failure(
        val error: AppUpdateError,
    ) : GitHubReleasesFetchResult
}

internal sealed interface ApkDownloadResult {
    data class Success(
        val actualSha256: String,
    ) : ApkDownloadResult

    data class Failure(
        val error: AppUpdateError,
    ) : ApkDownloadResult
}

internal interface GitHubReleaseRemoteDataSource {
    suspend fun fetchReleases(): GitHubReleasesFetchResult

    suspend fun downloadApk(
        url: String,
        destinationFile: File,
        expectedTotalBytes: Long,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): ApkDownloadResult
}

internal interface ApkCacheStorage {
    fun preparePartFile(fileName: String): File

    fun finalizeApkFile(
        partFile: File,
        finalFileName: String,
    ): File

    fun contentUriForFile(file: File): String

    fun clearAll()

    fun canRequestPackageInstalls(): Boolean
}

internal class HttpGitHubReleaseRemoteDataSource(
    private val releasesUrl: String = DEFAULT_RELEASES_URL,
    private val userAgent: String = "PDFeasy-Android",
    private val allowCleartextForTests: Boolean = false,
) : GitHubReleaseRemoteDataSource {
    override suspend fun fetchReleases(): GitHubReleasesFetchResult =
        try {
            val connection =
                openConnection(releasesUrl).apply {
                    setRequestProperty("Accept", GITHUB_JSON_ACCEPT)
                    setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
                }
            try {
                readReleasesResponse(connection)
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            GitHubReleasesFetchResult.Failure(AppUpdateError.NetworkUnavailable)
        }

    private fun readReleasesResponse(connection: HttpURLConnection): GitHubReleasesFetchResult {
        val statusCode = connection.responseCode
        return if (statusCode in HTTP_OK_START until HTTP_OK_END) {
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            GitHubReleasesFetchResult.Success(body)
        } else {
            GitHubReleasesFetchResult.Failure(mapHttpError(connection, statusCode))
        }
    }

    override suspend fun downloadApk(
        url: String,
        destinationFile: File,
        expectedTotalBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): ApkDownloadResult {
        val scheme =
            try {
                URI(url).scheme?.lowercase()
            } catch (_: URISyntaxException) {
                null
            }
        val allowedScheme = scheme == SCHEME_HTTPS || (allowCleartextForTests && scheme == SCHEME_HTTP)
        if (!allowedScheme) {
            return ApkDownloadResult.Failure(
                AppUpdateError.InvalidResponse("Unsupported download URL scheme"),
            )
        }

        return try {
            val connection = openConnection(url)
            try {
                val statusCode = connection.responseCode
                if (statusCode !in HTTP_OK_START until HTTP_OK_END) {
                    ApkDownloadResult.Failure(mapHttpError(connection, statusCode))
                } else {
                    val totalBytes =
                        connection.contentLengthLong.takeIf { it > 0L } ?: expectedTotalBytes
                    val sha256 =
                        streamToFileAndHash(
                            connection = connection,
                            destinationFile = destinationFile,
                            totalBytes = totalBytes,
                            onProgress = onProgress,
                        )
                    ApkDownloadResult.Success(sha256)
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            ApkDownloadResult.Failure(AppUpdateError.NetworkUnavailable)
        }
    }

    private suspend fun streamToFileAndHash(
        connection: HttpURLConnection,
        destinationFile: File,
        totalBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val downloaded =
            connection.inputStream.use { input ->
                destinationFile.outputStream().use { output ->
                    copyWithDigest(input, output, digest, totalBytes, onProgress)
                }
            }
        onProgress(downloaded, totalBytes.coerceAtLeast(downloaded))
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private suspend fun copyWithDigest(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        digest: MessageDigest,
        totalBytes: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): Long {
        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
        var downloaded = 0L
        var lastReportedBytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) {
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                downloaded += read
                if (downloaded - lastReportedBytes >= PROGRESS_STEP_BYTES) {
                    lastReportedBytes = downloaded
                    onProgress(downloaded, totalBytes)
                }
            }
        }
        output.flush()
        return downloaded
    }

    private fun openConnection(targetUrl: String): HttpURLConnection =
        (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }

    private fun mapHttpError(
        connection: HttpURLConnection,
        statusCode: Int,
    ): AppUpdateError {
        val remaining = connection.getHeaderField("X-RateLimit-Remaining")
        val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
        val isRateLimit =
            statusCode == HTTP_TOO_MANY_REQUESTS ||
                (statusCode == HTTP_FORBIDDEN && remaining == "0")
        return if (isRateLimit) {
            AppUpdateError.RateLimited(retryAfterSeconds = retryAfter)
        } else {
            val message = connection.responseMessage ?: "HTTP $statusCode"
            AppUpdateError.HttpFailure(statusCode = statusCode, message = message)
        }
    }

    companion object {
        const val DEFAULT_RELEASES_URL =
            "https://api.github.com/repos/Cl0udShifter/PDFeasy/releases?per_page=10"
        private const val GITHUB_JSON_ACCEPT = "application/vnd.github+json"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_BUFFER_BYTES = 16 * 1024
        private const val PROGRESS_STEP_BYTES = 64 * 1024L
        private const val HTTP_OK_START = 200
        private const val HTTP_OK_END = 300
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val SCHEME_HTTPS = "https"
        private const val SCHEME_HTTP = "http"
    }
}

internal class AndroidApkCacheStorage(
    context: Context,
    private val uriResolver: ((Context, File) -> String)? = null,
) : ApkCacheStorage {
    private val appContext: Context = context.applicationContext
    private val updatesDir: File
        get() = File(appContext.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }

    override fun preparePartFile(fileName: String): File {
        val safeName = sanitizeFileName(fileName)
        return File(updatesDir, "$safeName$PART_SUFFIX").apply {
            if (exists()) delete()
        }
    }

    override fun finalizeApkFile(
        partFile: File,
        finalFileName: String,
    ): File {
        val target = File(updatesDir, sanitizeFileName(finalFileName))
        if (target.exists() && !target.delete()) {
            throw IOException("Unable to overwrite existing APK file: ${target.name}")
        }
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        return target
    }

    override fun contentUriForFile(file: File): String =
        uriResolver?.invoke(appContext, file)
            ?: FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            ).toString()

    override fun clearAll() {
        val dir = File(appContext.cacheDir, UPDATES_SUBDIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { file -> file.delete() }
        }
    }

    override fun canRequestPackageInstalls(): Boolean =
        appContext.packageManager.canRequestPackageInstalls()

    private fun sanitizeFileName(rawName: String): String {
        val base = rawName.substringAfterLast('/').substringAfterLast('\\')
        return base.replace(UNSAFE_FILE_CHARS_REGEX, "_").ifBlank { DEFAULT_APK_NAME }
    }

    private companion object {
        const val UPDATES_SUBDIR = "updates"
        const val PART_SUFFIX = ".part"
        const val DEFAULT_APK_NAME = "PDFeasy-update.apk"
        val UNSAFE_FILE_CHARS_REGEX = Regex("[^A-Za-z0-9._-]")
    }
}
