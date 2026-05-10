package com.kash.imgpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kash.imgpro.navigation.AppNavGraph
import com.kash.imgpro.ui.theme.DeepCharcoal
import com.kash.imgpro.ui.theme.ImgProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen BEFORE super.onCreate() for a flicker-free launch.
        // The SplashScreen API reads Theme.ImgPro.Splash attributes and renders
        // the cyan camera icon on the Deep Charcoal background until setContent.
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ImgProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepCharcoal,
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
