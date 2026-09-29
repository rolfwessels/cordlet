package io.github.rolfwessels.cordlet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rolfwessels.cordlet.ui.theme.CordletBackground
import io.github.rolfwessels.cordlet.ui.theme.CordletTheme

@Composable
fun CordletApp() {
    var composer by remember { mutableStateOf(ComposerState()) }
    CordletTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(CordletBackground).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            UtilityStrip(
                state = composer,
                onMessageChange = { composer = composer.withMessage(it) },
                onMicrophoneClick = { composer = composer.toggleMicrophonePressed() },
            )
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
            UtilityStrip(ComposerState(), onMessageChange = {}, onMicrophoneClick = {})
        }
    }
}
