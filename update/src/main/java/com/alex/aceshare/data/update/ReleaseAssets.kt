package com.alex.aceshare.data.update

fun selectApkAsset(assets: List<GitHubAsset>, prefix: String): GitHubAsset? =
    assets.firstOrNull { asset ->
        asset.name.startsWith(prefix, ignoreCase = true) &&
            asset.name.endsWith(".apk", ignoreCase = true)
    }

fun isNewerRelease(tagName: String, localVersionName: String): Boolean {
    val remote = parseVersionCode(tagName.trim().removePrefix("v")) ?: return false
    val local = parseVersionCode(localVersionName) ?: return false
    return remote > local
}
