package com.alex.aceshare.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.alex.aceshare.mobile.ui.navigation.AceShareNavHost
import com.alex.aceshare.mobile.ui.theme.AceShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AceShareTheme {
                Surface(Modifier.fillMaxSize()) {
                    AceShareNavHost()
                }
            }
        }
    }
}
