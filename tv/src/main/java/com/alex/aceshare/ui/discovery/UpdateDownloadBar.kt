package com.alex.aceshare.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alex.aceshare.R
import com.alex.aceshare.ui.theme.TvBorder
import com.alex.aceshare.ui.theme.TvPrimary

@Composable
internal fun UpdateDownloadBar(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val value = progress.coerceIn(0, 100)
    val fraction = value / 100f
    val description = stringResource(R.string.update_downloading, value)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                contentDescription = description
            }
            .background(TvBorder),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(TvPrimary),
        )
    }
}
