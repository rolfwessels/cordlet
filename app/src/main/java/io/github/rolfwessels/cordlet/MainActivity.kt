package io.github.rolfwessels.cordlet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.rolfwessels.cordlet.ui.CordletApp
import io.github.rolfwessels.cordlet.voice.RecorderActivity

class MainActivity : ComponentActivity() {
    private var focus by mutableStateOf(false)
    private var launchId by mutableStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        setContent { CordletApp(autoFocus = focus, focusLaunchId = launchId, onVoice = {
            startActivity(Intent(this, RecorderActivity::class.java))
        }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    private fun route(intent: Intent) {
        // Exported entry handles text only. Voice requires a widget/composer action
        // directly targeting the non-exported recorder.
        focus = intent.getBooleanExtra(EXTRA_FOCUS_COMPOSER, false)
        if (focus) launchId++
    }

    companion object {
        const val EXTRA_FOCUS_COMPOSER = "io.github.rolfwessels.cordlet.FOCUS_COMPOSER"
    }
}
