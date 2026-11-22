package com.cl0udshifter.pdfeasy.core.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cl0udshifter.pdfeasy.core.data.repository.SettingsRepositoryImpl
import com.cl0udshifter.pdfeasy.core.domain.SettingsRepository
import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateDownloadState
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun checksReleasesViaHttpAndValidatesSha256() =
        runBlocking {
            val apkBytes = "fake-apk-payload-bytes-for-testing".toByteArray()
            val expectedSha256 = sha256Hex(apkBytes)
            withUpdateServer(apkBytes, expectedSha256) { port ->
                val settingsRepo = createSettingsRepo("update_test.preferences_pb")
                val cacheStorage = TempApkCacheStorage(tempFolder.newFolder("updates"))
                val updateRepo =
                    createUpdateRepo(
                        url = "http://127.0.0.1:$port/releases",
                        cacheStorage = cacheStorage,
                        settingsRepo = settingsRepo,
                        nowMillis = { 99_999L },
                    )

                val checkResult = updateRepo.checkForUpdate(includePreReleases = false)
                val available =
                    (checkResult as AppUpdateCheckResult.Success).status as AppUpdateStatus.UpdateAvailable
                assertEquals(99_999L, settingsRepo.getSettings().lastUpdateCheckEpochMillis)

                val ready =
                    updateRepo.downloadUpdate(available.release).toList().last()
                        as AppUpdateDownloadState.ReadyToInstall
                assertTrue(ready.sha256Verified)
                assertTrue(ready.contentUriString.endsWith("PDFeasy-v0.1.2.apk"))

                val corrupted = available.release.copy(expectedSha256 = "0".repeat(SHA256_HEX_LEN))
                val failed = updateRepo.downloadUpdate(corrupted).toList().last() as AppUpdateDownloadState.Failed
                val mismatch = failed.error as AppUpdateError.ChecksumMismatch
                assertEquals(expectedSha256, mismatch.actualSha256)
                assertEquals(0, cacheStorage.updatesDir.listFiles()?.size ?: 0)
            }
        }

    @Test
    fun mapsRateLimitHeadersAndMissingDigestFlag() =
        runBlocking {
            val apkBytes = "unsigned-digest-payload".toByteArray()
            withUpdateServer(apkBytes, sha256 = null) { port ->
                val settingsRepo = createSettingsRepo("ratelimit_test.preferences_pb")
                val cacheStorage = TempApkCacheStorage(tempFolder.newFolder("updates2"))

                val rateLimitedRepo =
                    createUpdateRepo(
                        url = "http://127.0.0.1:$port/rate-limited",
                        cacheStorage = cacheStorage,
                        settingsRepo = settingsRepo,
                    )
                assertEquals(
                    AppUpdateCheckResult.Failure(AppUpdateError.RateLimited(RETRY_AFTER_SEC)),
                    rateLimitedRepo.checkForUpdate(includePreReleases = true),
                )

                val noDigestRepo =
                    createUpdateRepo(
                        url = "http://127.0.0.1:$port/releases",
                        cacheStorage = cacheStorage,
                        settingsRepo = settingsRepo,
                    )
                val available =
                    ((noDigestRepo.checkForUpdate(false) as AppUpdateCheckResult.Success).status
                        as AppUpdateStatus.UpdateAvailable).release
                val ready =
                    noDigestRepo.downloadUpdate(available).toList().last()
                        as AppUpdateDownloadState.ReadyToInstall
                assertFalse(ready.sha256Verified)
            }
        }

    private suspend fun withUpdateServer(
        apkBytes: ByteArray,
        sha256: String?,
        block: suspend (port: Int) -> Unit,
    ) {
        val server = ServerSocket(0, SERVER_BACKLOG, InetAddress.getByName("127.0.0.1"))
        val port = server.localPort
        val worker =
            thread(isDaemon = true, name = "test-http-server") {
                serveConnections(server, port, apkBytes, sha256)
            }
        try {
            block(port)
        } finally {
            server.close()
            worker.join(SERVER_JOIN_TIMEOUT_MS)
        }
    }

    private fun serveConnections(
        server: ServerSocket,
        port: Int,
        apkBytes: ByteArray,
        sha256: String?,
    ) {
        while (!server.isClosed) {
            try {
                handleClientSocket(server.accept(), port, apkBytes, sha256)
            } catch (_: SocketException) {
                break
            }
        }
    }

    private fun handleClientSocket(
        socket: Socket,
        port: Int,
        apkBytes: ByteArray,
        sha256: String?,
    ) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            while (!reader.readLine().isNullOrEmpty()) {
                // Drain request headers
            }
            val path = requestLine.split(' ').getOrNull(1).orEmpty()
            val response = buildHttpResponse(path, port, apkBytes, sha256)
            client.getOutputStream().use { out ->
                out.write(response)
                out.flush()
            }
        }
    }

    private fun buildHttpResponse(
        path: String,
        port: Int,
        apkBytes: ByteArray,
        sha256: String?,
    ): ByteArray =
        when (path) {
            "/releases" -> httpOkResponse(buildReleasesPayload(port, apkBytes.size, sha256))
            "/download/PDFeasy-v0.1.2.apk" -> httpOkResponse(apkBytes)
            "/rate-limited" -> httpRateLimitedResponse()
            else -> "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
        }

    private fun httpOkResponse(body: ByteArray): ByteArray {
        val headers =
            "HTTP/1.1 $HTTP_OK OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                .toByteArray(Charsets.US_ASCII)
        return headers + body
    }

    private fun httpRateLimitedResponse(): ByteArray =
        (
            "HTTP/1.1 $HTTP_FORBIDDEN Forbidden\r\n" +
                "X-RateLimit-Remaining: 0\r\n" +
                "Retry-After: $RETRY_AFTER_SEC\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII)

    private fun buildReleasesPayload(
        port: Int,
        apkSize: Int,
        sha256: String?,
    ): ByteArray {
        val digestProperty = if (sha256 != null) """, "digest": "sha256:$sha256" """ else ""
        return """
            [
              {
                "tag_name": "v0.1.2",
                "name": "PDFeasy v0.1.2",
                "body": "Bug fixes",
                "draft": false,
                "prerelease": false,
                "html_url": "http://127.0.0.1:$port/html/v0.1.2",
                "published_at": "2026-11-22T23:00:00Z",
                "assets": [
                  {
                    "name": "PDFeasy-v0.1.2.apk",
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "http://127.0.0.1:$port/download/PDFeasy-v0.1.2.apk",
                    "size": $apkSize $digestProperty
                  }
                ]
              }
            ]
            """.trimIndent().toByteArray(Charsets.UTF_8)
    }

    private fun createSettingsRepo(fileName: String): SettingsRepository =
        SettingsRepositoryImpl(
            dataStore =
                PreferenceDataStoreFactory.create(
                    produceFile = { tempFolder.newFile(fileName) },
                ),
            ioDispatcher = Dispatchers.IO,
        )

    private fun createUpdateRepo(
        url: String,
        cacheStorage: TempApkCacheStorage,
        settingsRepo: SettingsRepository,
        nowMillis: () -> Long = { 0L },
    ): AppUpdateRepositoryImpl =
        AppUpdateRepositoryImpl(
            installedVersion = InstalledAppVersion(versionName = "0.1.1", versionCode = 2L),
            remoteDataSource = HttpGitHubReleaseRemoteDataSource(releasesUrl = url, allowCleartextForTests = true),
            apkStorage = cacheStorage,
            settingsRepository = settingsRepo,
            ioDispatcher = Dispatchers.IO,
            nowMillis = nowMillis,
        )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private class TempApkCacheStorage(
        val updatesDir: File,
    ) : ApkCacheStorage {
        override fun preparePartFile(fileName: String): File {
            updatesDir.mkdirs()
            return File(updatesDir, "$fileName.part")
        }

        override fun finalizeApkFile(
            partFile: File,
            finalFileName: String,
        ): File {
            val target = File(updatesDir, finalFileName)
            partFile.renameTo(target)
            return target
        }

        override fun contentUriForFile(file: File): String = file.toURI().toString()

        override fun clearAll() {
            updatesDir.listFiles()?.forEach { it.delete() }
        }

        override fun canRequestPackageInstalls(): Boolean = true
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_FORBIDDEN = 403
        const val RETRY_AFTER_SEC = 60L
        const val SHA256_HEX_LEN = 64
        const val SERVER_BACKLOG = 8
        const val SERVER_JOIN_TIMEOUT_MS = 1000L
    }
}
