package com.alex.aceshare.data.update

interface AppUpdater {
    suspend fun checkForUpdate(): UpdateCheckResult

    suspend fun downloadAndInstall(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit,
    ): Result<Unit>

    fun currentVersionName(): String

    companion object {
        const val PERMISSION_REQUIRED = "install_permission_required"
    }
}

object NoOpAppUpdater : AppUpdater {
    override suspend fun checkForUpdate(): UpdateCheckResult = UpdateCheckResult.UpToDate

    override suspend fun downloadAndInstall(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    override fun currentVersionName(): String = ""
}

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val apkUrl: String,
    val apkFileName: String,
)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: AppUpdateInfo) : UpdateCheckResult()
    data object Failed : UpdateCheckResult()
}
