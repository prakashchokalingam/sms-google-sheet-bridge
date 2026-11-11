package io.github.prakashchokalingam.smsgooglesheetbridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.prakashchokalingam.smsgooglesheetbridge.ui.App
import io.github.prakashchokalingam.smsgooglesheetbridge.ui.theme.SmsBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsBridgeTheme {
                App()
            }
        }
    }
}
