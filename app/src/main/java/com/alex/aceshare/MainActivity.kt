package com.alex.aceshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.NonInteractiveSurfaceDefaults
import androidx.tv.material3.Surface
import com.alex.aceshare.ui.navigation.AceShareNavHost
import com.alex.aceshare.ui.theme.AceShareTheme
import com.alex.aceshare.ui.theme.TvBackground

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AceShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = NonInteractiveSurfaceDefaults.colors(
                        containerColor = TvBackground,
                    ),
                ) {
                    AceShareNavHost(onExit = { finish() })
                }
            }
        }
    }
}
