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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.rolfwessels.cordlet.MainActivity
import io.github.rolfwessels.cordlet.R
import io.github.rolfwessels.cordlet.voice.RecorderActivity

/** Separate focused-text and local-recorder launch shortcuts. */
class CordletWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: android.content.Context, id: androidx.glance.GlanceId) {
        provideContent { CordletWidgetContent() }
    }
}

@Composable
private fun CordletWidgetContent() {
    val context = LocalContext.current
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_FOCUS_COMPOSER, true))
    val openVoice = actionStartActivity(Intent(context, RecorderActivity::class.java))
    // The launcher may allocate a taller cell than requested. Keep the artwork
    // at its content height rather than stretching the panel to fill that cell.
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Row(
        modifier = GlanceModifier.fillMaxWidth().height(68.dp)
            .background(ImageProvider(R.drawable.cordlet_widget_panel)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
            modifier = GlanceModifier.size(36.dp).background(ImageProvider(R.drawable.cordlet_widget_mint_tile)),
            contentAlignment = Alignment.Center,
        ) {
            Text("C", style = TextStyle(color = ColorProvider(Color(context.getColor(R.color.cordlet_ink))), fontSize = 16.sp))
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Box(
            modifier = GlanceModifier.defaultWeight().height(48.dp)
                .background(ImageProvider(R.drawable.cordlet_widget_text_well))
                .semantics { contentDescription = context.getString(R.string.widget_open_text) }
                .clickable(openApp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "Tap to type",
                modifier = GlanceModifier.padding(horizontal = 12.dp),
                style = TextStyle(color = ColorProvider(Color(context.getColor(R.color.cordlet_muted))), fontSize = 14.sp),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.width(10.dp))
        Box(
            modifier = GlanceModifier.size(48.dp)
                .background(ImageProvider(R.drawable.cordlet_widget_mint_tile))
                .semantics { contentDescription = context.getString(R.string.widget_open_voice) }
                .clickable(openVoice),
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
}
