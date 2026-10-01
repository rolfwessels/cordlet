package io.github.rolfwessels.cordlet.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import io.github.rolfwessels.cordlet.MainActivity
import io.github.rolfwessels.cordlet.ingress.PrivateConfigStore

/** A distinct voice destination. Single-task + handled rotation keep one file owner. */
class RecorderActivity : ComponentActivity() {
    private lateinit var session: VoiceSession
    private var visible = false
    private var pendingStart = false
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startWhenVisible() else session.permissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = VoiceSession.get(applicationContext)
        val config = PrivateConfigStore(applicationContext).load()
        setContent { RecorderScreen(session, botName = config?.botName ?: "Hermes", botIconBase64 = config?.botIconBase64, onStart = ::requestRecording, onBack = {
            session.close()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }) }
        // A kept note always wins over automatic recording, including after process restart.
        if (savedInstanceState == null && canAutoStart(session.phase)) requestRecording()
    }

    private fun requestRecording() {
        if (!canAutoStart(session.phase)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWhenVisible()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startWhenVisible() {
        if (visible) session.start() else pendingStart = true
    }

    override fun onResume() {
        super.onResume()
        visible = true
        if (pendingStart) { pendingStart = false; session.start() }
    }

    override fun onPause() {
        visible = false
        session.background()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Do not reset, resume, request permission, or overwrite an existing session.
    }

    override fun onStop() {
        visible = false
        // A paused MPEG-4 recorder is not finalized. onDestroy is not guaranteed
        // after background process death: stop/release while onStop still runs.
        if (::session.isInitialized) session.close()
        super.onStop()
    }

    override fun onDestroy() {
        if (::session.isInitialized) session.close()
        super.onDestroy()
    }
}
