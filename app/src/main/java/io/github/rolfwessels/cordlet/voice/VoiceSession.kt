package io.github.rolfwessels.cordlet.voice

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Main-thread, single-owner recorder. One private note, bounded to five active minutes. */
class VoiceSession(context: Context) {
    private val directory = File(context.filesDir, "voice").apply { mkdirs() }
    private val file = File(directory, "latest.m4a")
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var clock = VoiceClock()
    var phase by mutableStateOf(VoicePhase.READY); private set
    var elapsed by mutableStateOf(0L); private set
    var level by mutableStateOf(0f); private set
    var playing by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set

    init { recover() }
    private fun now() = SystemClock.elapsedRealtime()

    private fun duration(): Long {
        if (!file.exists() || file.length() == 0L) return 0
        val metadata = try { MediaMetadataRetriever() } catch (_: Exception) { return 0 }
        return try {
            metadata.setDataSource(file.absolutePath)
            metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (_: Exception) { 0 } finally { runCatching { metadata.release() } }
    }

    private fun recover() {
        val savedDuration = duration()
        if (savedDuration > 0) {
            phase = transition(phase, VoiceEvent.SAVE)
            elapsed = savedDuration
            message = "Saved locally · recording kept"
        } else if (file.exists()) {
            phase = VoicePhase.ERROR
            elapsed = 0
            message = "Interrupted recording is unreadable; file kept locally. Discard explicitly before recording again."
        }
    }

    @Suppress("DEPRECATION") // API 26 supported; newer Context constructor requires API 31.
    fun start() {
        if (!canAutoStart(phase)) return
        stopPlayback()
        if (file.exists()) { recover(); return }
        try {
            val created = MediaRecorder()
            recorder = created
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(64000)
            created.setAudioSamplingRate(44100)
            created.setOutputFile(file.absolutePath)
            // Also enforce in polling against active time (not paused wall time).
            created.setMaxDuration(MAX_RECORDING_MS.toInt())
            created.setMaxFileSize(4L * 1024 * 1024)
            created.setOnInfoListener { _, what, _ ->
                if (recorder === created && (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)) finish()
            }
            created.setOnErrorListener { _, _, _ ->
                if (recorder === created) failRecorder("Recording interrupted; recoverable audio kept")
            }
            created.prepare()
            created.start()
            clock = VoiceClock().start(now())
            phase = transition(phase, VoiceEvent.START)
            elapsed = 0
            message = "Pause to review · up to 5 minutes"
        } catch (_: Exception) { failRecorder("Could not start microphone. Check permission and try again.") }
    }

    fun tick() {
        if (phase != VoicePhase.RECORDING) { level = 0f; return }
        elapsed = clock.elapsed(now())
        try { level = amplitudeLevel(recorder?.maxAmplitude ?: 0) }
        catch (_: Exception) { failRecorder("Microphone interrupted; recoverable audio kept"); return }
        if (limitReached(elapsed)) finish()
    }

    fun pause() {
        if (phase != VoicePhase.RECORDING) return
        try {
            recorder?.pause()
            clock = clock.pause(now())
            elapsed = clock.accumulated
            phase = transition(phase, VoiceEvent.PAUSE)
            level = 0f
            message = "Paused · finish to listen back"
        } catch (_: Exception) { failRecorder("Could not pause; recoverable audio kept") }
    }

    fun resume() {
        if (phase != VoicePhase.PAUSED) return
        try {
            recorder?.resume()
            clock = clock.start(now())
            phase = transition(phase, VoiceEvent.RESUME)
            message = "Recording · up to 5 minutes"
        } catch (_: Exception) { failRecorder("Could not resume; recoverable audio kept") }
    }

    fun finish() {
        if (recorder == null) return
        clock = clock.pause(now())
        elapsed = clock.accumulated
        val current = recorder
        recorder = null
        runCatching { current?.stop() }
        runCatching { current?.release() }
        phase = VoicePhase.READY
        level = 0f
        recover()
        if (phase == VoicePhase.READY) message = "No recording was created. Try again."
    }

    private fun failRecorder(reason: String) {
        finish()
        message = when (phase) {
            VoicePhase.SAVED -> "$reason · saved locally"
            VoicePhase.ERROR -> "$reason · file kept locally. Discard explicitly before recording again."
            else -> reason
        }
    }

    fun play() {
        if (phase != VoicePhase.SAVED) return
        if (playing) { stopPlayback(); return }
        try {
            val created = MediaPlayer()
            player = created
            created.setDataSource(file.absolutePath)
            created.setOnCompletionListener { if (player === created) stopPlayback() }
            created.setOnErrorListener { _, _, _ ->
                if (player === created) { stopPlayback(); message = "Playback failed; recording kept locally" }
                true
            }
            created.prepare()
            created.start()
            playing = true
        } catch (_: Exception) { stopPlayback(); message = "Playback failed; recording kept locally" }
    }

    private fun stopPlayback() {
        val current = player
        player = null
        runCatching { current?.release() }
        playing = false
    }

    fun permissionDenied() { message = "Microphone permission denied. Tap Start recording to retry; enable Microphone in app settings if blocked." }

    fun discard() {
        stopPlayback()
        val current = recorder
        recorder = null
        runCatching { current?.stop() }
        runCatching { current?.release() }
        if (file.exists() && !file.delete()) {
            phase = VoicePhase.READY
            recover()
            message = "Could not delete recording. Try Discard again."
            return
        }
        phase = transition(phase, VoiceEvent.DISCARD)
        clock = VoiceClock()
        elapsed = 0
        level = 0f
        message = "Recording discarded"
    }

    fun background() { pause(); stopPlayback() }
    fun close() { stopPlayback(); finish() }
}
