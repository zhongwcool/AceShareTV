package com.alex.aceshare.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.alex.aceshare.data.AceShareApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class AppUpdateChecker(
    private val context: Context,
    private val releaseUrl: String,
    private val assetPrefix: String,
    private val client: OkHttpClient = downloadClient(),
    private val json: Json = AceShareApi.defaultJson(),
) : AppUpdater {
    private val downloader = ApkDownloader(client)
    private val installer = ApkInstaller(context)

    override suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(releaseUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            val release = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching UpdateCheckResult.Failed
                }
                val body = response.body?.string()
                    ?: return@runCatching UpdateCheckResult.Failed
                json.decodeFromString(GitHubRelease.serializer(), body)
            }
            val versionName = release.tagName.trim().removePrefix("v")
            val remoteCode = parseVersionCode(versionName)
                ?: return@runCatching UpdateCheckResult.Failed
            if (!isNewerRelease(release.tagName, currentVersionName())) {
                return@runCatching UpdateCheckResult.UpToDate
            }
            val asset = selectApkAsset(release.assets, assetPrefix)
                ?: return@runCatching UpdateCheckResult.Failed
            UpdateCheckResult.Available(
                AppUpdateInfo(
                    versionName = versionName,
                    versionCode = remoteCode,
                    releaseNotes = release.body.orEmpty().trim(),
                    apkUrl = asset.browserDownloadUrl,
                    apkFileName = asset.name,
                ),
            )
        }.getOrElse { UpdateCheckResult.Failed }
    }

    override suspend fun downloadAndInstall(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!installer.canInstallPackages()) {
                withContext(Dispatchers.Main) {
                    installer.openInstallPermissionSettings()
                }
                error(AppUpdater.PERMISSION_REQUIRED)
            }
            val dest = File(updatesDir(), info.apkFileName)
            downloader.download(info.apkUrl, dest, onProgress)
            withContext(Dispatchers.Main) {
                installer.install(dest)
            }
        }
    }

    override fun currentVersionName(): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName.orEmpty().ifBlank { "1.0" }
        }.getOrDefault("1.0")
    }

    private fun updatesDir(): File = File(context.cacheDir, "updates")

    companion object {
        fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
