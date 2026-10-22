// Open decisions: folder indexing, subtitle selection and calibrated headset setup are later milestones.
// Risk/verification: process restoration, provider failure, content-only external intents and cancellable metadata I/O.
package org.grinch27.vrviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grinch27.vrviewer.storage.MovieEntry
import org.grinch27.vrviewer.storage.PlaybackStore
import org.grinch27.vrviewer.ui.PlaybackScreen
import org.grinch27.vrviewer.ui.projectionLabel
import org.grinch27.vrviewer.ui.stereoLabel
import org.grinch27.vrviewer.ui.timeLabel

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
    var selectedUri by rememberSaveable { mutableStateOf(incomingUri) }
    var entry by remember { mutableStateOf<MovieEntry?>(null) }
    var recents by remember { mutableStateOf(emptyList<MovieEntry>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
        if (value == null) {
            loading = false
            recents = withContext(Dispatchers.IO) { store.recent() }
        } else {
            error = null
            loading = true
            try {
                entry = withContext(Dispatchers.IO) { store.resolve(activity, Uri.parse(value)) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "无法读取文件信息，请重新选择视频。"
                selectedUri = null
            } finally {
                loading = false
            }
        }
    }
    val movie = entry
    if (movie != null) {
        key(movie.uri) {
            PlaybackScreen(activity, movie, store::save) { selectedUri = null }
        }
    } else {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(
                modifier = Modifier.safeDrawingPadding(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("VRviewer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            Text("本地 · 离线 · 专注播放", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("0.1", style = MaterialTheme.typography.labelLarge)
                    }
                }
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("打开本地视频", style = MaterialTheme.typography.titleLarge)
                            Text("平面、180°、360°视频；单眼观看或双眼分屏。", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                                Text(if (loading) "读取文件信息…" else "选择视频")
                            }
                        }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                item {
                    Text("观看方式", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("在播放器设置中分别选择投影、片源排布和显示方式。360°支持拖动与双指缩放；姿态传感器可选。", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("双眼分屏尚无镜片校准。首版仅支持 SDR；文件解码能力取决于设备。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { Text("最近播放  ${recents.size}", style = MaterialTheme.typography.titleMedium) }
                if (recents.isEmpty()) item { Text("选择视频后，会在本机记住进度与观看模式。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(recents, key = { it.uri }) { recent ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedUri = recent.uri }.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("▶", color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(recent.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text("${projectionLabel(recent.profile.projection)} · ${stereoLabel(recent.profile.stereo)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Text("${timeLabel(recent.positionMs)} / ${timeLabel(recent.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("继续", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
