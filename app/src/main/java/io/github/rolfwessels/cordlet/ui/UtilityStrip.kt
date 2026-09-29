package io.github.rolfwessels.cordlet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rolfwessels.cordlet.ui.theme.CordletMint
import io.github.rolfwessels.cordlet.ui.theme.CordletPanel
import io.github.rolfwessels.cordlet.ui.theme.CordletText

private val wellColor = Color(0xFF0F131A)
private val borderColor = Color(0xFF2A3140)
private val inkColor = Color(0xFF07110E)
private val pressedColor = Color(0xFFFF5F74)

@Composable
fun UtilityStrip(
    state: ComposerState,
    onMessageChange: (String) -> Unit,
    onMicrophoneClick: () -> Unit,
    onSendClick: () -> Unit,
    sending: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(CordletPanel, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(CordletMint, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("C", color = inkColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        BasicTextField(
            value = state.message,
            onValueChange = onMessageChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = CordletText, fontSize = 14.sp),
            cursorBrush = SolidColor(CordletMint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { if (!sending && state.message.isNotBlank()) onSendClick() }),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .border(1.dp, Color(0xFF252C38), RoundedCornerShape(10.dp))
                .background(wellColor, RoundedCornerShape(10.dp))
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics { contentDescription = "Discord message" },
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.message.isEmpty()) {
                        Text("Send something quickly…", color = Color(0xFF636B79), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    innerTextField()
                }
            },
        )
        val hasText = state.message.isNotBlank()
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (!hasText && state.microphonePressed) pressedColor else CordletMint, RoundedCornerShape(10.dp))
                .semantics {
                    contentDescription = if (hasText) "Send Discord message" else "Microphone button, visual feedback only"
                    if (!hasText) stateDescription = if (state.microphonePressed) "Pressed" else "Not pressed"
                }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (hasText) "Send message" else "Press microphone button",
                    onClick = { if (hasText) { if (!sending) onSendClick() } else onMicrophoneClick() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasText) Text(if (sending) "…" else "Send", color = inkColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else MicrophoneGlyph(color = if (state.microphonePressed) Color.White else inkColor)
        }
    }
}

@Composable
private fun MicrophoneGlyph(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.9.dp.toPx()
        val unit = size.width / 24f
        drawRoundRect(
            color = color,
            topLeft = Offset(9 * unit, 3 * unit),
            size = Size(6 * unit, 11 * unit),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3 * unit),
            style = Stroke(stroke),
        )
        drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(5.5f * unit, 5 * unit), size = Size(13 * unit, 13 * unit), style = Stroke(stroke))
        drawLine(color, Offset(12 * unit, 18 * unit), Offset(12 * unit, 21 * unit), stroke)
        drawLine(color, Offset(9 * unit, 21 * unit), Offset(15 * unit, 21 * unit), stroke)
    }
}
