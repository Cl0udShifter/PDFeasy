package com.cl0udshifter.pdfeasy.core.data.update

import com.cl0udshifter.pdfeasy.core.model.AppReleaseInfo
import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class ParsedGitHubAsset(
    val name: String,
    val contentType: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256Digest: String?,
)

internal data class ParsedGitHubRelease(
    val tagName: String,
    val version: SemanticVersion,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAtIso: String,
    val isDraft: Boolean,
    val isPreRelease: Boolean,
    val apkAsset: ParsedGitHubAsset?,
)

internal object GitHubReleaseParser {
    private const val APK_EXTENSION = ".apk"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val SHA256_PREFIX = "sha256:"
    private const val SHA256_HEX_LENGTH = 64

    fun parseReleasesJson(rawJson: String): List<ParsedGitHubRelease>? =
        try {
            val array = JSONArray(rawJson)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    parseSingleRelease(obj)?.let(::add)
                }
            }
        } catch (_: JSONException) {
            null
        }

    fun evaluateLatestRelease(
        releases: List<ParsedGitHubRelease>,
        currentVersionName: String,
        includePreReleases: Boolean,
    ): AppUpdateCheckResult {
        val currentVersion =
            SemanticVersion.parse(currentVersionName)
                ?: return AppUpdateCheckResult.Failure(
                    AppUpdateError.InvalidResponse("Invalid installed version: $currentVersionName"),
                )

        val candidates =
            releases.filter { release ->
                !release.isDraft && (includePreReleases || !release.isPreRelease)
            }
        val latest = candidates.maxByOrNull { it.version }

        return when {
            latest == null || latest.version <= currentVersion ->
                AppUpdateCheckResult.Success(
                    AppUpdateStatus.UpToDate(
                        currentVersionName = currentVersionName,
                        latestCheckedTag = latest?.tagName,
                    ),
                )

            latest.apkAsset == null ->
                AppUpdateCheckResult.Failure(AppUpdateError.NoCompatibleApkAsset)

            else ->
                AppUpdateCheckResult.Success(
                    AppUpdateStatus.UpdateAvailable(
                        currentVersionName = currentVersionName,
                        release = latest.toDomain(latest.apkAsset),
                    ),
                )
        }
    }

    private fun parseSingleRelease(obj: JSONObject): ParsedGitHubRelease? {
        val tagName = obj.optString("tag_name").trim()
        val version = SemanticVersion.parse(tagName) ?: return null
        val assets = parseAssets(obj.optJSONArray("assets"))
        val preferredApk = selectPreferredApkAsset(assets)

        return ParsedGitHubRelease(
            tagName = tagName,
            version = version,
            releaseName = obj.optString("name").ifBlank { tagName },
            releaseNotes = obj.optString("body"),
            htmlUrl = obj.optString("html_url"),
            publishedAtIso = obj.optString("published_at"),
            isDraft = obj.optBoolean("draft", false),
            isPreRelease = obj.optBoolean("prerelease", false),
            apkAsset = preferredApk,
        )
    }

    private fun parseAssets(array: JSONArray?): List<ParsedGitHubAsset> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val name = obj.optString("name")
                val url = obj.optString("browser_download_url")
                if (name.isNotBlank() && url.isNotBlank()) {
                    add(
                        ParsedGitHubAsset(
                            name = name,
                            contentType = obj.optString("content_type"),
                            downloadUrl = url,
                            sizeBytes = obj.optLong("size", 0L).coerceAtLeast(0L),
                            sha256Digest = parseSha256Digest(obj.optString("digest")),
                        ),
                    )
                }
            }
        }
    }

    private fun selectPreferredApkAsset(assets: List<ParsedGitHubAsset>): ParsedGitHubAsset? {
        val apkAssets =
            assets.filter { asset ->
                asset.name.endsWith(APK_EXTENSION, ignoreCase = true) ||
                    asset.contentType.equals(APK_MIME_TYPE, ignoreCase = true)
            }
        return apkAssets.firstOrNull { it.name.startsWith("PDFeasy", ignoreCase = true) }
            ?: apkAssets.firstOrNull()
    }

    private fun parseSha256Digest(rawDigest: String?): String? =
        rawDigest
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.startsWith(SHA256_PREFIX) }
            ?.removePrefix(SHA256_PREFIX)
            ?.takeIf { hex -> hex.length == SHA256_HEX_LENGTH && hex.all { it in '0'..'9' || it in 'a'..'f' } }

    private fun ParsedGitHubRelease.toDomain(asset: ParsedGitHubAsset): AppReleaseInfo =
        AppReleaseInfo(
            tagName = tagName,
            versionName = tagName.removePrefix("v").removePrefix("V"),
            releaseName = releaseName,
            releaseNotes = releaseNotes,
            htmlUrl = htmlUrl,
            publishedAtIso = publishedAtIso,
            isPreRelease = isPreRelease,
            apkAssetName = asset.name,
            apkDownloadUrl = asset.downloadUrl,
            apkSizeBytes = asset.sizeBytes,
            expectedSha256 = asset.sha256Digest,
        )
}
