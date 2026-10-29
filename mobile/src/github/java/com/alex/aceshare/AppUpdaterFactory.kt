package com.alex.aceshare

import android.content.Context
import com.alex.aceshare.data.update.AppUpdateChecker
import com.alex.aceshare.data.update.AppUpdater

fun createAppUpdater(context: Context): AppUpdater = AppUpdateChecker(
    context = context,
    releaseUrl = "https://api.github.com/repos/zhongwcool/AceShareMobile/releases/latest",
    assetPrefix = "AceShare-Mobile-",
)
