package io.github.rolfwessels.cordlet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rolfwessels.cordlet.BuildConfig
import io.github.rolfwessels.cordlet.discord.DiscordDestination
import io.github.rolfwessels.cordlet.discord.DiscordMessageClient
import io.github.rolfwessels.cordlet.discord.SendOutcome
import io.github.rolfwessels.cordlet.ui.theme.CordletBackground
import io.github.rolfwessels.cordlet.ui.theme.CordletTheme
import kotlinx.coroutines.launch

@Composable
fun CordletApp() {
    var composer by remember { mutableStateOf(ComposerState()) }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val client = remember { DiscordMessageClient() }
    // This is a single compiled-in test destination; widget-instance routing comes later.
    val destination = remember { DiscordDestination(BuildConfig.DISCORD_BOT_TOKEN, BuildConfig.DISCORD_CHANNEL_ID) }

    CordletTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(CordletBackground).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UtilityStrip(
                    state = composer,
                    onMessageChange = { composer = composer.withMessage(it); status = "" },
                    onMicrophoneClick = { composer = composer.toggleMicrophonePressed() },
                    sending = sending,
                    onSendClick = {
                        if (!sending && composer.message.isNotBlank()) {
                            val submitted = composer.message
                            sending = true
                            status = "Sending…"
                            scope.launch {
                                val result = client.send(destination, submitted)
                                sending = false
                                status = when (result) {
                                    SendOutcome.Sent -> {
                                        if (composer.message == submitted) composer = composer.withMessage("")
                                        "Sent to Discord"
                                    }
                                    is SendOutcome.Failed -> result.message
                                }
                            }
                        }
                    },
                )
                if (status.isNotEmpty()) Text(status, color = Color(0xFFADB4C0))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0E14, widthDp = 390, heightDp = 220)
@Composable
private fun EmptyUtilityStripPreview() {
    CordletTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(CordletBackground).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            UtilityStrip(ComposerState(), onMessageChange = {}, onMicrophoneClick = {}, onSendClick = {})
        }
    }
}
