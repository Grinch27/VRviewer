// Open decisions: headset gaze selection, calibrated optics and spatial subtitles are future milestones.
// Risks/verification: recreation, background silence, Surface lifetime, mirrored transport and unknown-duration seeking.
package org.grinch27.vrviewer.ui

import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay
import org.grinch27.vrviewer.MainActivity
import org.grinch27.vrviewer.core.DisplayMode
import org.grinch27.vrviewer.playback.PlaybackController
import org.grinch27.vrviewer.playback.PlaybackStatus
import org.grinch27.vrviewer.render.VrVideoView
import org.grinch27.vrviewer.storage.MovieEntry

@UnstableApi
@Composable
fun PlaybackScreen(activity: MainActivity, entry: MovieEntry, checkpoint: (MovieEntry) -> Unit, onExit: () -> Unit) {
    val controller = remember(entry.uri) { PlaybackController(activity.applicationContext, entry, checkpoint) }
    val status = controller.status
    val profile = controller.profile
    var controls by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(false) }
    val video = remember(controller) {
        VrVideoView(activity, controller::attachSurface, controller::reportError, { controls = !controls }, controller::togglePlay) { fov -> controller.updateProfile(controller.profile.copy(fieldOfView = fov)) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect {
        video.update(profile, status.videoWidth, status.videoHeight, status.pixelRatio)
        video.keepScreenOn = status.playing || status.buffering
    }
    DisposableEffect(lifecycle, controller, video) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { video.onResume(); controller.start() }
                Lifecycle.Event.ON_RESUME -> video.updateTracking(true)
                Lifecycle.Event.ON_PAUSE -> video.stopTracking()
                Lifecycle.Event.ON_STOP -> { controller.stop(); video.stopTracking(); video.onPause() }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        activity.playerKeys = { key ->
            when (key) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER -> { controller.togglePlay(); true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { controller.seekBy(-10_000); true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { controller.seekBy(10_000); true }
                else -> false
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            activity.playerKeys = null
            controller.close()
            video.stopTracking()
            video.onPause()
            video.close()
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(profile.headTracking, profile.projection) {
        video.updateTracking(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(profile.display) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = if (profile.display == DisplayMode.DUAL) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity.requestedOrientation = previous }
    }
    DisposableEffect(activity) {
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { insets.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(controller, lifecycle) {
        while (true) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.tick()
            delay(250)
        }
    }
    LaunchedEffect(controls, status.playing, settings) {
        if (controls && status.playing && !settings) { delay(4_000); controls = false }
    }
    BackHandler { if (settings) settings = false else onExit() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { video }, modifier = Modifier.fillMaxSize())
        if (status.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))
        if (controls || status.error != null || !status.playing) {
            val dual = profile.display == DisplayMode.DUAL
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                repeat(if (dual) 2 else 1) {
                    PlayerChrome(
                        entry.name, status, dual, Modifier.weight(1f).fillMaxHeight(),
                        onExit, { settings = true }, controller::togglePlay, controller::seek,
                        { controller.seekBy(it) }, { video.recenter() }, controller::retry,
                    )
                }
            }
        }
        if (settings) ProfileSheet(profile, status, video.headTrackingAvailable, controller::updateProfile, controller::speed, controller::loop, video::recenter) { settings = false }
    }
}

@Composable
private fun PlayerChrome(
    name: String,
    status: PlaybackStatus,
    compact: Boolean,
    modifier: Modifier,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onPlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onRecenter: () -> Unit,
    onRetry: () -> Unit,
) {
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    Column(modifier.padding(if (compact) 6.dp else 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .65f)), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("返回") }
            Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, color = Color.White)
            TextButton(onClick = onSettings) { Text("设置") }
        }
        status.error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .95f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    Row { TextButton(onClick = onRetry) { Text("重试") }; TextButton(onClick = onExit) { Text("返回首页") } }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .75f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel(status.positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(timeLabel(status.durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = seekFraction ?: if (status.durationMs > 0) (status.positionMs.toDouble() / status.durationMs).toFloat().coerceIn(0f, 1f) else 0f,
                onValueChange = { seekFraction = it },
                onValueChangeFinished = { seekFraction?.let { onSeek((status.durationMs * it).toLong()) }; seekFraction = null },
                enabled = status.durationMs > 0 && status.error == null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSeekBy(-10_000) }) { Text("−10秒") }
                FilledTonalButton(onClick = onPlay, enabled = status.error == null) { Text(if (status.playing) "暂停" else if (status.ended) "重播" else "播放") }
                TextButton(onClick = { onSeekBy(10_000) }) { Text("+10秒") }
                if (!compact) TextButton(onClick = onRecenter) { Text("回正") }
            }
        }
    }
}
