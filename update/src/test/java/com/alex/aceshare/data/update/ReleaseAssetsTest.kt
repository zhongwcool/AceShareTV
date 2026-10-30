package com.alex.aceshare.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAssetsTest {
    @Test
    fun selectApkAsset_matchesPrefixAndSuffixIgnoringCase() {
        val assets = listOf(
            asset("notes.txt"),
            asset("Mobile-AceShare-v1.2.0.apk"),
            asset("Mobile-AceShare-v1.2.0.aab"),
            asset("aceshare-tv-v1.2.0.APK"),
            asset("AceShare-TV-v1.2.0.aab"),
        )
        assertEquals("aceshare-tv-v1.2.0.APK", selectApkAsset(assets, "AceShare-TV-")?.name)
        assertEquals("Mobile-AceShare-v1.2.0.apk", selectApkAsset(assets, "Mobile-AceShare-")?.name)
    }

    @Test
    fun selectApkAsset_returnsNullWhenNothingMatches() {
        val assets = listOf(asset("AceShare-TV-v1.2.0.aab"), asset("readme.md"))
        assertNull(selectApkAsset(assets, "AceShare-Mobile-"))
    }

    @Test
    fun isNewerRelease_acceptsTagWithOrWithoutV() {
        assertTrue(isNewerRelease("v1.2.0", "1.1.0"))
        assertTrue(isNewerRelease("1.2.0", "v1.1.0"))
    }

    @Test
    fun isNewerRelease_isFalseWhenLocalIsEqualOrNewer() {
        assertFalse(isNewerRelease("v1.2.0", "1.2.0"))
        assertFalse(isNewerRelease("v1.2.0", "1.3.0"))
    }

    @Test
    fun isNewerRelease_isFalseWhenVersionCannotBeParsed() {
        assertFalse(isNewerRelease("latest", "1.0.0"))
        assertFalse(isNewerRelease("v1.2.0", "beta"))
    }

    private fun asset(name: String) = GitHubAsset(name = name, browserDownloadUrl = "https://example/$name")
}
