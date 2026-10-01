package io.github.rolfwessels.cordlet.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.rolfwessels.cordlet.voice.RecorderActivity

/** A direct widget activity PendingIntent avoids Android 12+ receiver trampolines.
 * This private, UI-less entry generates a new trusted nonce on every tap. */
class WidgetVoiceLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) startActivity(RecorderActivity.captureIntent(this))
        finish()
    }
}
