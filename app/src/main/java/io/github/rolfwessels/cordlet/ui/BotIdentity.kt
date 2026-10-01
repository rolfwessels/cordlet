package io.github.rolfwessels.cordlet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rolfwessels.cordlet.ingress.decodeBotIcon
import io.github.rolfwessels.cordlet.ui.theme.*

/** Offline branding only; decoding is bounded and memoized, with no network path. */
@Composable
fun BotIdentity(botName: String = "Hermes", botIconBase64: String? = null, prefix: String = "", modifier: Modifier = Modifier) {
    val icon = remember(botIconBase64) {
        try { botIconBase64?.let { decodeBotIcon(it).asImageBitmap() } }
        catch (_: Exception) { null }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(CordletPanel), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(botName.take(1).uppercase(), color = CordletSecondary, fontSize = 13.sp)
        }
        Text("$prefix$botName", color = CordletMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
