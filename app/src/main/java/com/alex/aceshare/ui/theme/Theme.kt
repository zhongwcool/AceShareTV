package com.alex.aceshare.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AceShareTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = TvPrimary,
        onPrimary = TvOnPrimary,
        secondary = TvTextSecondary,
        background = TvBackground,
        onBackground = TvTextPrimary,
        surface = TvSurface,
        onSurface = TvTextPrimary,
        error = TvError,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
