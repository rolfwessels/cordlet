package io.github.rolfwessels.cordlet.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rolfwessels.cordlet.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun RecorderScreen(session: VoiceSession, botName: String = "Hermes", botIconBase64: String? = null, onStart: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(session) { while (true) { session.tick(); delay(100) } }
    CordletTheme {
        val mint = CordletMint
        val blue = CordletSecondary
        Column(Modifier.fillMaxSize().background(CordletBackground).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton(onClick = onBack, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("‹", fontSize = 28.sp, modifier = Modifier.semantics { contentDescription = "Back to Cordlet" })
                }
                Text("Cordlet", color = CordletText, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(32.dp))
            io.github.rolfwessels.cordlet.ui.BotIdentity(botName, botIconBase64, prefix = "Voice note to ")
            Spacer(Modifier.height(10.dp))
            Text("Say what’s on your mind.", color = CordletText, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp)
            Spacer(Modifier.height(48.dp))
            Column(Modifier.fillMaxWidth().border(1.dp, CordletBorder, RoundedCornerShape(24.dp))
                .background(CordletPanel, RoundedCornerShape(24.dp)).padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(when (session.phase) {
                        VoicePhase.RECORDING -> "● Recording"
                        VoicePhase.PAUSED -> "Paused"
                        VoicePhase.SAVED -> if (session.upload.uploading) "Uploading…" else if (session.upload.accepted) "Accepted" else "Saved locally"
                        VoicePhase.ERROR -> "Recording unreadable · file kept"
                        VoicePhase.READY -> "Ready to record"
                    }, color = if (session.phase == VoicePhase.RECORDING) CordletMint else CordletText, fontSize = 14.sp)
                    val seconds = session.elapsed / 1000
                    Text("%02d:%02d".format(seconds / 60, seconds % 60), color = CordletMuted, fontSize = 14.sp)
                }
                // One scrolling waveform, driven only by real microphone samples.
                // Silence has a small visible baseline; paused history stays frozen.
                Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Microphone audio history" }) {
                    val samples = session.history.samples
                    val step = size.width / samples.size
                    val width = step * 0.55f
                    val baseline = 4.dp.toPx()
                    val peak = size.height - 20.dp.toPx()
                    samples.forEachIndexed { index, sample ->
                        val height = baseline + (peak - baseline) * sample
                        val color = lerp(mint, blue, index.toFloat() / (samples.size - 1))
                        drawRoundRect(color, Offset(step * index + (step - width) / 2, (size.height - height) / 2),
                            Size(width, height), CornerRadius(width / 2))
                    }
                }
                Text(session.message.ifEmpty { "Audio stays on this device" }, color = CordletMuted, fontSize = 13.sp)
                if (session.phase == VoicePhase.PAUSED) {
                    TextButton(onClick = session::finish, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Finish & review", color = CordletSecondary) }
                }
                if (session.phase == VoicePhase.SAVED) {
                    TextButton(onClick = session::play, enabled = !session.upload.controlsLocked, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (session.playing) "Stop playback" else "Play recording", color = CordletSecondary)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    when (session.phase) {
                        VoicePhase.READY -> onStart()
                        VoicePhase.RECORDING -> session.pause()
                        VoicePhase.PAUSED -> session.resume()
                        VoicePhase.SAVED -> Unit
                        VoicePhase.ERROR -> Unit
                    }
                }, enabled = session.phase != VoicePhase.SAVED && session.phase != VoicePhase.ERROR, modifier = Modifier.weight(1f).height(58.dp), contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(when (session.phase) {
                        VoicePhase.READY -> "Start"
                        VoicePhase.RECORDING -> "Pause"
                        VoicePhase.PAUSED -> "Resume"
                        VoicePhase.SAVED -> "Kept"
                        VoicePhase.ERROR -> "Kept"
                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = session::send, enabled = session.upload.canSend(session.phase), modifier = Modifier.weight(1.4f).height(58.dp), contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(16.dp)) { Text(if (session.upload.uploading) "Uploading…" else if (session.upload.accepted) "Accepted" else "Send ↗", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Pause or finish to send · reply in Discord", color = CordletMuted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            TextButton(onClick = session::discard, enabled = session.phase != VoicePhase.READY && !session.upload.controlsLocked, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Discard recording", color = CordletMuted)
            }
        }
    }
}
