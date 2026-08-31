package com.sundwaeji.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sundwaeji.jarvis.ui.ares.JarvisHud
import com.sundwaeji.jarvis.ui.theme.LiveKitVoiceAssistantExampleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveKitVoiceAssistantExampleTheme(darkTheme = true, dynamicColor = false) { JarvisHud() }
        }
    }
}
