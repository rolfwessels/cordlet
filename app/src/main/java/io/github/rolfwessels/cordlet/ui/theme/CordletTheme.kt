package io.github.rolfwessels.cordlet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import io.github.rolfwessels.cordlet.R

// Android resources are the shared source for Compose and widget artwork.
val CordletBackground @Composable get() = colorResource(R.color.cordlet_background)
val CordletPanel @Composable get() = colorResource(R.color.cordlet_panel)
val CordletMint @Composable get() = colorResource(R.color.cordlet_mint)
val CordletText @Composable get() = colorResource(R.color.cordlet_text)
val CordletMuted @Composable get() = colorResource(R.color.cordlet_muted)
val CordletBorder @Composable get() = colorResource(R.color.cordlet_border)
val CordletInset @Composable get() = colorResource(R.color.cordlet_inset)
val CordletInk @Composable get() = colorResource(R.color.cordlet_ink)
val CordletSecondary @Composable get() = colorResource(R.color.cordlet_secondary)
val CordletError @Composable get() = colorResource(R.color.cordlet_error)

@Composable
fun CordletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = CordletMint, onPrimary = CordletInk,
        secondary = CordletSecondary, error = CordletError,
        background = CordletBackground, onBackground = CordletText,
        surface = CordletPanel, onSurface = CordletText,
        onSurfaceVariant = CordletMuted, outline = CordletBorder,
    ), content = content)
}
