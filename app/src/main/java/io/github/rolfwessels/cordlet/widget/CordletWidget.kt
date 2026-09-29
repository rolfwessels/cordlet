package io.github.rolfwessels.cordlet.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.rolfwessels.cordlet.MainActivity
import io.github.rolfwessels.cordlet.R

/** A launcher shortcut, not an editor or recorder. Both targets open the app. */
class CordletWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: android.content.Context, id: androidx.glance.GlanceId) {
        provideContent { CordletWidgetContent() }
    }
}

@Composable
private fun CordletWidgetContent() {
    val context = LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    Row(
        modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.cordlet_widget_panel)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(36.dp).background(ImageProvider(R.drawable.cordlet_widget_mint_tile)),
            contentAlignment = Alignment.Center,
        ) {
            Text("C", style = TextStyle(color = ColorProvider(Color(0xFF07110E)), fontSize = 16.sp))
        }
        Box(
            modifier = GlanceModifier.defaultWeight().height(48.dp).padding(start = 10.dp)
                .background(ImageProvider(R.drawable.cordlet_widget_text_well))
                .semantics { contentDescription = context.getString(R.string.widget_open_text) }
                .clickable(openApp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "Tap to type",
                modifier = GlanceModifier.padding(horizontal = 12.dp),
                style = TextStyle(color = ColorProvider(Color(0xFFADB4C0)), fontSize = 14.sp),
                maxLines = 1,
            )
        }
        Box(
            modifier = GlanceModifier.padding(start = 10.dp).size(48.dp)
                .background(ImageProvider(R.drawable.cordlet_widget_mint_tile))
                .semantics { contentDescription = context.getString(R.string.widget_open_voice) }
                .clickable(openApp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.cordlet_widget_microphone),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }
}
