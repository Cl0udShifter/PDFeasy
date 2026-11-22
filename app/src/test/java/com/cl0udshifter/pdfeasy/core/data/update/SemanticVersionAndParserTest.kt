package com.cl0udshifter.pdfeasy.core.data.update

import com.cl0udshifter.pdfeasy.core.model.AppUpdateCheckResult
import com.cl0udshifter.pdfeasy.core.model.AppUpdateError
import com.cl0udshifter.pdfeasy.core.model.AppUpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SemanticVersionAndParserTest {
    @Test
    fun semanticVersionParsesAndComparesNumericAndPreReleaseVersions() {
        val v011 = SemanticVersion.parse("v0.1.1")!!
        val v012 = SemanticVersion.parse("0.1.2")!!
        val v012Alpha = SemanticVersion.parse("v0.1.2-alpha.1")!!
        val v012Beta = SemanticVersion.parse("v0.1.2-beta.1+build42")!!
        val v020 = SemanticVersion.parse("0.2")!!

        assertTrue(v012 > v011)
        assertTrue(v012 > v012Beta)
        assertTrue(v012Beta > v012Alpha)
        assertTrue(v012Alpha > v011)
        assertTrue(v020 > v012)
        assertNull(SemanticVersion.parse("release-latest"))
    }

    @Test
    fun parserIgnoresDraftsSelectsPreferredApkAndRespectsIncludePreReleases() {
        val releases = GitHubReleaseParser.parseReleasesJson(SAMPLE_RELEASES_JSON)
        assertNotNull(releases)

        val withPre = GitHubReleaseParser.evaluateLatestRelease(releases!!, "0.1.1", includePreReleases = true)
        assertTrue(withPre is AppUpdateCheckResult.Success)
        val available = (withPre as AppUpdateCheckResult.Success).status as AppUpdateStatus.UpdateAvailable
        assertEquals("v0.2.0-rc.1", available.release.tagName)
        assertEquals("0.2.0-rc.1", available.release.versionName)
        assertEquals(
            "f86a6477ae413339bcda87c2272d23e3eadec02c6368f84ba64697bf7240cd16",
            available.release.expectedSha256,
        )

        val stableOnly = GitHubReleaseParser.evaluateLatestRelease(releases, "0.1.1", includePreReleases = false)
        assertTrue(stableOnly is AppUpdateCheckResult.Success)
        val upToDate = (stableOnly as AppUpdateCheckResult.Success).status as AppUpdateStatus.UpToDate
        assertEquals("v0.1.1", upToDate.latestCheckedTag)
    }

    @Test
    fun parserReportsMissingApkAssetWhenNewerReleaseHasNoApk() {
        val json =
            """
            [
              {
                "tag_name": "v0.2.0",
                "name": "v0.2.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "source.zip",
                    "content_type": "application/zip",
                    "browser_download_url": "https://example.com/source.zip",
                    "size": 100
                  }
                ]
              }
            ]
            """.trimIndent()

        val releases = GitHubReleaseParser.parseReleasesJson(json)!!
        val result = GitHubReleaseParser.evaluateLatestRelease(releases, "0.1.1", includePreReleases = false)
        assertEquals(AppUpdateCheckResult.Failure(AppUpdateError.NoCompatibleApkAsset), result)
    }

    private companion object {
        val SAMPLE_RELEASES_JSON =
            """
            [
              {
                "tag_name": "v0.3.0-draft",
                "name": "Draft",
                "draft": true,
                "prerelease": false,
                "html_url": "https://github.com/Cl0udShifter/PDFeasy/releases/tag/v0.3.0-draft",
                "published_at": "2026-11-22T23:00:00Z",
                "assets": [
                  {
                    "name": "PDFeasy-v0.3.0.apk",
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://example.com/PDFeasy-v0.3.0.apk",
                    "size": 21000000
                  }
                ]
              },
              {
                "tag_name": "v0.2.0-rc.1",
                "name": "PDFeasy v0.2.0 RC1",
                "body": "Release notes",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/Cl0udShifter/PDFeasy/releases/tag/v0.2.0-rc.1",
                "published_at": "2026-11-22T22:30:00Z",
                "assets": [
                  {
                    "name": "checksums.txt",
                    "content_type": "text/plain",
                    "browser_download_url": "https://example.com/checksums.txt",
                    "size": 128
                  },
                  {
                    "name": "PDFeasy-v0.2.0-rc.1.apk",
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://example.com/PDFeasy-v0.2.0-rc.1.apk",
                    "size": 21321917,
                    "digest": "sha256:F86A6477AE413339BCDA87C2272D23E3EADEC02C6368F84BA64697BF7240CD16"
                  }
                ]
              },
              {
                "tag_name": "v0.1.1",
                "name": "PDFeasy v0.1.1",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/Cl0udShifter/PDFeasy/releases/tag/v0.1.1",
                "published_at": "2026-11-22T22:05:01Z",
                "assets": [
                  {
                    "name": "PDFeasy-v0.1.1.apk",
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://example.com/PDFeasy-v0.1.1.apk",
                    "size": 21321917
                  }
                ]
              }
            ]
            """.trimIndent()
    }
}
