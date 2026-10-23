// Requirements/ambiguities: the home screen contains exactly two buttons; URL means a direct HTTP(S) media link.
// Research/optimization: keep playback controls in the player; no library scanning or network preflight on launch.
// Risks/verification: restore the selected source, validate URL schemes, and keep signed URLs out of permanent history.
package org.grinch27.vrviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grinch27.vrviewer.core.MediaUrl
import org.grinch27.vrviewer.playback.PlaybackSession
import org.grinch27.vrviewer.storage.MovieEntry
import org.grinch27.vrviewer.storage.PlaybackStore
import org.grinch27.vrviewer.ui.PlaybackScreen

@UnstableApi
class MainActivity : ComponentActivity() {
    var playerKeys: ((Int) -> Boolean)? = null
    private var incomingUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingUri = if (savedInstanceState == null) acceptedUri(intent) else null
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFA8C7FA),
                secondary = Color(0xFF95D5CE),
                background = Color(0xFF080C14),
                surface = Color(0xFF111A29),
            )) {
                PlayerApp(this, incomingUri)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri = acceptedUri(intent)
    }
    fun consumeIncomingUri() { incomingUri = null }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (event.repeatCount == 0 && playerKeys?.invoke(keyCode) == true) true else super.onKeyDown(keyCode, event)

    private fun acceptedUri(intent: Intent): String? =
        intent.data?.takeIf { intent.action == Intent.ACTION_VIEW && it.scheme == "content" }?.toString()
}

private class LocalVideoPicker : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input).apply {
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
}

@UnstableApi
@Composable
private fun PlayerApp(activity: MainActivity, incomingUri: String?) {
    val store = remember { PlaybackStore(activity.applicationContext) }
    val session = remember { ViewModelProvider(activity)[PlaybackSession::class.java] }
    var selectedUri by rememberSaveable { mutableStateOf(incomingUri) }
    var entry by remember { mutableStateOf<MovieEntry?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showUrl by rememberSaveable { mutableStateOf(false) }
    var urlText by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(LocalVideoPicker()) { uri ->
        uri?.let {
            if (!store.retainReadGrant(activity, it)) {
                Toast.makeText(activity, "此提供方只授予临时访问，下次可能需要重新选择视频。", Toast.LENGTH_LONG).show()
            }
            selectedUri = it.toString()
        }
    }
    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            selectedUri = incomingUri
            activity.consumeIncomingUri()
        }
    }
    LaunchedEffect(selectedUri) {
        entry = null
        val value = selectedUri
        session.select(value)
        if (value == null) {
            loading = false
        } else {
            loading = true
            try {
                entry = session.current ?: withContext(Dispatchers.IO) { store.resolve(activity, value.toUri()) }
                entry?.let(session::checkpoint)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(activity, "无法打开视频，请重新选择文件或检查链接。", Toast.LENGTH_LONG).show()
                selectedUri = null
            } finally {
                loading = false
            }
        }
    }
    val movie = entry
    if (movie != null) {
        key(movie.uri) {
            PlaybackScreen(activity, movie, { checkpoint -> session.checkpoint(checkpoint); store.save(checkpoint) }) {
                session.select(null)
                selectedUri = null
            }
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("打开本地文件")
                    }
                    Button(onClick = { showUrl = true }, enabled = !loading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("打开 URL 链接")
                    }
                }
            }
        }
    }
    if (showUrl) {
        val normalized = MediaUrl.normalize(urlText)
        AlertDialog(
            onDismissRequest = { showUrl = false; urlText = "" },
            title = { Text("打开 URL 链接") },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("视频直链") },
                    placeholder = { Text("https://…/video.mp4") },
                    supportingText = { Text("HTTP / HTTPS 视频直链，或 m3u8、mpd；不解析视频网页。") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = urlText.isNotBlank() && normalized == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { if (normalized != null) { selectedUri = normalized; showUrl = false; urlText = "" } }, enabled = normalized != null) { Text("打开") }
            },
            dismissButton = { TextButton(onClick = { showUrl = false; urlText = "" }) { Text("取消") } },
        )
    }
}
