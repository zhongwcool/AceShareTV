package com.alex.aceshare.legal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.alex.aceshare.core.R

fun Context.openServerDownloadPage() {
    val url = getString(R.string.server_download_url)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, url, Toast.LENGTH_LONG).show()
    }
}
