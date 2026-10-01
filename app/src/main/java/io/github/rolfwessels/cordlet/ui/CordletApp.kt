package io.github.rolfwessels.cordlet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rolfwessels.cordlet.ingress.IngressMessageClient
import io.github.rolfwessels.cordlet.ingress.PrivateConfigStore
import io.github.rolfwessels.cordlet.ingress.RequestIdentity
import io.github.rolfwessels.cordlet.ingress.SendOutcome
import io.github.rolfwessels.cordlet.ui.theme.CordletBackground
import io.github.rolfwessels.cordlet.ui.theme.CordletMuted
import io.github.rolfwessels.cordlet.ui.theme.CordletTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CordletApp(autoFocus: Boolean = false, focusLaunchId: Int = 0, onVoice: () -> Unit = {}) {
    var composer by rememberSaveable(stateSaver = listSaver(
        save = { listOf(it.message, it.microphonePressed.toString()) },
        restore = { ComposerState(it[0], it[1].toBoolean()) },
    )) { mutableStateOf(ComposerState()) }
    var request by rememberSaveable(stateSaver = listSaver(
        save = { listOf(it.text, it.requestId) },
        restore = { RequestIdentity(it[0], it[1]) },
    )) { mutableStateOf(RequestIdentity()) }
    var sending by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val context = LocalContext.current.applicationContext
    val store = remember { PrivateConfigStore(context) }
    var config by remember { mutableStateOf(store.load()) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                config = withContext(Dispatchers.IO) { store.import(uri) }
                status = "Private config imported"
            } catch (_: Exception) {
                status = "Config import failed; existing config kept"
            } finally { importing = false }
        }
    }
    LaunchedEffect(autoFocus, focusLaunchId) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val client = remember { IngressMessageClient() }
    CordletTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(CordletBackground).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BotIdentity(config?.botName ?: "Hermes", config?.botIconBase64)
                UtilityStrip(
                    state = composer,
                    onMessageChange = {
                        composer = composer.withMessage(it)
                        request = request.edited(it)
                        status = ""
                    },
                    onMicrophoneClick = onVoice,
                    focusRequester = focusRequester,
                    sending = sending,
                    onSendClick = {
                        if (!sending && !importing && composer.message.isNotBlank()) {
                            request = request.edited(composer.message)
                            val submitted = request
                            sending = true
                            status = "Sending…"
                            scope.launch {
                                try {
                                    val result = client.send(config, submitted)
                                    status = when (result) {
                                        SendOutcome.Accepted -> {
                                            if (request.requestId == submitted.requestId) {
                                                composer = composer.withMessage("")
                                                request = submitted.accepted().edited("")
                                            }
                                            "Accepted by Hermes; reply in Discord"
                                        }
                                        is SendOutcome.Failed -> result.message
                                    }
                                } finally { sending = false }
                            }
                        }
                    },
                )
                TextButton(enabled = !sending && !importing, onClick = {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text(if (config == null) "Import private config" else "Replace private config") }
                if (status.isNotEmpty()) Text(status, color = CordletMuted)
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
