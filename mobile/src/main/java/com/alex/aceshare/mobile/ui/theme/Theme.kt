package com.alex.aceshare.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF0D1117),
    secondary = Color(0xFF8B949E),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFF85149),
    outline = Color(0xFF30363D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    secondary = Color(0xFF57606A),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFE8EEF4),
    onSurfaceVariant = Color(0xFF57606A),
    error = Color(0xFFCF222E),
    outline = Color(0xFFD0D7DE),
)

@Composable
fun AceShareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
