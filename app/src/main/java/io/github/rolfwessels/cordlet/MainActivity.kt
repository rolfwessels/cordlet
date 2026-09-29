package io.github.rolfwessels.cordlet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.rolfwessels.cordlet.ui.CordletApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CordletApp(autoFocus = intent.getBooleanExtra(EXTRA_FOCUS_COMPOSER, false)) }
    }

    companion object {
        const val EXTRA_FOCUS_COMPOSER = "io.github.rolfwessels.cordlet.FOCUS_COMPOSER"
    }
}
