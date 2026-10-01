package io.github.rolfwessels.cordlet.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.rolfwessels.cordlet.MainActivity
import io.github.rolfwessels.cordlet.ingress.PrivateConfigStore
import java.util.UUID

/** Private trusted quick-capture entry. Recreation restores only its own note. */
class RecorderActivity : ComponentActivity() {
    companion object {
        private const val CAPTURE = "io.github.rolfwessels.cordlet.CAPTURE"
        fun captureIntent(context: Context) = Intent(context, RecorderActivity::class.java)
            .setAction(CAPTURE).putExtra("capture_nonce", UUID.randomUUID().toString())
    }
    private var sessionState by mutableStateOf<VoiceSession?>(null)
    private val session get() = checkNotNull(sessionState)
    private var foregroundVisible by mutableStateOf(false)
    private var pendingStart = false
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startWhenVisible() else { pendingStart = false; session.permissionDenied() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val restored = savedInstanceState?.getString("session_id")
        sessionState = if (restored != null) VoiceSession.restore(applicationContext, restored)
            else VoiceSession.fresh(applicationContext)
        val config = PrivateConfigStore(applicationContext).load()
        setContent {
            val session = session
            LaunchedEffect(session, session.phase, session.upload, foregroundVisible) {
                if (keepCaptureAwake(foregroundVisible, session.phase, session.upload.uploading))
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                // This effect belongs to the displayed note; detached uploads cannot close a newer one.
                if (shouldFinishCapture(foregroundVisible, session.upload.accepted)) finish()
            }
            RecorderScreen(session, botName = config?.botName ?: "Hermes", botIconBase64 = config?.botIconBase64, onStart = ::requestRecording, onBack = {
                session.close()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            })
        }
        if (restored == null && intent.action == CAPTURE) requestRecording()
    }

    private fun openFreshCapture() {
        pendingStart = false
        session.close() // Finalize old audio; never discard it or cancel its upload.
        sessionState = VoiceSession.fresh(applicationContext)
        requestRecording()
    }

    private fun requestRecording() {
        if (!canAutoStart(session.phase)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startWhenVisible()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startWhenVisible() {
        if (foregroundVisible) session.start() else pendingStart = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("session_id", session.sessionId)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        foregroundVisible = true
        if (pendingStart) { pendingStart = false; session.start() }
    }

    override fun onPause() {
        foregroundVisible = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        session.background()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == CAPTURE) openFreshCapture()
    }

    override fun onStop() {
        foregroundVisible = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        session.close() // Finalize before possible process death; never record in background.
        super.onStop()
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
