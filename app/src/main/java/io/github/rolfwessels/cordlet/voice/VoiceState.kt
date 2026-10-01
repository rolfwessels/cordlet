package io.github.rolfwessels.cordlet.voice

import kotlin.math.sqrt

enum class EntryMode { TEXT, TEXT_FOCUSED, VOICE }
fun entryMode(voice: Boolean, focusText: Boolean): EntryMode = when {
    voice -> EntryMode.VOICE
    focusText -> EntryMode.TEXT_FOCUSED
    else -> EntryMode.TEXT
}

enum class VoicePhase { READY, RECORDING, PAUSED, SAVED, ERROR }
enum class VoiceEvent { START, PAUSE, RESUME, SAVE, DISCARD }
fun transition(phase: VoicePhase, event: VoiceEvent): VoicePhase = when (event) {
    VoiceEvent.START -> if (phase == VoicePhase.READY) VoicePhase.RECORDING else phase
    VoiceEvent.PAUSE -> if (phase == VoicePhase.RECORDING) VoicePhase.PAUSED else phase
    VoiceEvent.RESUME -> if (phase == VoicePhase.PAUSED) VoicePhase.RECORDING else phase
    VoiceEvent.SAVE -> VoicePhase.SAVED
    VoiceEvent.DISCARD -> VoicePhase.READY
}
fun canAutoStart(phase: VoicePhase) = phase == VoicePhase.READY
const val MAX_RECORDING_MS = 300000L
fun limitReached(elapsed: Long) = elapsed >= MAX_RECORDING_MS
fun amplitudeLevel(amplitude: Int) = sqrt(amplitude.coerceIn(0, 32767) / 32767f)

/** Monotonic time; paused wall time never contributes to the recording. */
data class VoiceClock(val accumulated: Long = 0, val startedAt: Long? = null) {
    fun start(now: Long) = if (startedAt == null) copy(startedAt = now) else this
    fun pause(now: Long) = copy(accumulated = elapsed(now), startedAt = null)
    fun elapsed(now: Long) = accumulated + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
}
