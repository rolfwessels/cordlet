package io.github.rolfwessels.cordlet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CordletBackground = Color(0xFF0B0E14)
val CordletPanel = Color(0xFF181D27)
val CordletMint = Color(0xFF32D3A4)
val CordletText = Color(0xFFF6F8FB)

private val cordletColors = darkColorScheme(
    primary = CordletMint,
    background = CordletBackground,
    surface = CordletPanel,
    onSurface = CordletText,
)

@Composable
fun CordletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = cordletColors, content = content)
}
