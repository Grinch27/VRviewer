// Open decisions: calibrated VR subtitles, HDR and software decoder extensions need separate validation.
// Risks/verification: all player calls run on main; stop releases decoder/audio focus and checkpoints progress.
package org.grinch27.vrviewer.playback

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.ProjectionMath
import org.grinch27.vrviewer.storage.MovieEntry

data class PlaybackStatus(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val pixelRatio: Float = 1f,
    val ended: Boolean = false,
    val error: String? = null,
    val speed: Float = 1f,
    val loop: Boolean = false,
)

@UnstableApi
class PlaybackController(
    private val context: Context,
    private val entry: MovieEntry,
    private val checkpoint: (MovieEntry) -> Unit,
) {
    var status: PlaybackStatus by mutableStateOf(PlaybackStatus(positionMs = ProjectionMath.resumePosition(entry.positionMs, entry.durationMs)))
        private set
    var profile: PlaybackProfile by mutableStateOf(entry.profile.normalized())
        private set
    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var resumePosition = status.positionMs
    private var autoplay = true
    private var lastCheckpoint = 0L
    private var closed = false

    fun start() {
        if (player != null || closed) return
        val sourceFactory = DataSource.Factory { PlaybackDataSource(context) }
        val created = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = created
        created.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        created.trackSelectionParameters = created.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        created.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = tick()
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                status = status.copy(videoWidth = videoSize.width, videoHeight = videoSize.height, pixelRatio = videoSize.pixelWidthHeightRatio)
            }
            override fun onTracksChanged(tracks: Tracks) {
                val hdr = tracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_VIDEO && (0 until group.length).any { index ->
                        val transfer = group.getTrackFormat(index).colorInfo?.colorTransfer
                        group.isTrackSelected(index) && (transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG)
                    }
                }
                if (hdr) {
                    created.pause()
                    reportError("此版本仅支持 SDR 渲染；HDR 视频需要经过验证的色调映射，请换用 SDR 片源。")
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "文件授权已失效，请返回并重新选择视频。"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "文件已移动、删除或存储设备已断开，请重新选择视频。"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "链接不可访问或授权已过期，请检查视频直链。"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接失败或超时，请检查链接和网络后重试。"
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "设备无法解码该视频格式或分辨率，请尝试 H.264 SDR 片源。"
                    else -> "播放失败（${error.errorCodeName}），可重试或重新选择文件。"
                }
                reportError(message)
            }
        })
        created.setVideoSurface(surface)
        created.setMediaItem(MediaItem.fromUri(Uri.parse(entry.uri)), resumePosition)
        created.repeatMode = if (status.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        created.setPlaybackSpeed(status.speed)
        created.prepare()
        created.playWhenReady = autoplay && status.error == null
    }

    fun attachSurface(value: Surface?) {
        if (closed) return
        surface = value
        player?.setVideoSurface(value)
    }

    fun updateProfile(value: PlaybackProfile) { profile = value.normalized() }

    fun tick() {
        val current = player ?: return
        status = status.copy(
            playing = current.isPlaying,
            buffering = current.playbackState == Player.STATE_BUFFERING,
            positionMs = current.currentPosition.coerceAtLeast(0),
            durationMs = current.duration.takeIf { it > 0 } ?: 0,
            ended = current.playbackState == Player.STATE_ENDED,
        )
        if (android.os.SystemClock.elapsedRealtime() - lastCheckpoint > 5_000) save()
    }

    fun togglePlay() {
        val current = player ?: return
        if (status.error != null) return
        if (current.isPlaying || current.playWhenReady) current.pause() else {
            if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
            current.play()
        }
    }

    fun seek(positionMs: Long) {
        if (status.durationMs > 0) player?.seekTo(positionMs.coerceIn(0, status.durationMs))
    }
    fun seekBy(deltaMs: Long) = seek(status.positionMs + deltaMs)
    fun speed(value: Float) {
        if (value !in .25f..2f) return
        status = status.copy(speed = value)
        player?.setPlaybackSpeed(value)
    }
    fun loop(enabled: Boolean) {
        status = status.copy(loop = enabled)
        player?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    fun retry() {
        stop()
        status = status.copy(error = null)
        autoplay = true
        start()
    }
    fun reportError(message: String) {
        player?.pause()
        status = status.copy(error = message, playing = false, buffering = false)
    }
    fun stop() {
        val current = player ?: return
        resumePosition = current.currentPosition.coerceAtLeast(0)
        save()
        current.clearVideoSurface()
        current.release()
        player = null
        autoplay = false
        status = status.copy(playing = false, buffering = false)
    }
    fun close() {
        if (closed) return
        stop()
        surface = null
        closed = true
    }
    private fun save() {
        val current = player
        checkpoint(entry.copy(
            positionMs = current?.currentPosition?.coerceAtLeast(0) ?: resumePosition,
            durationMs = current?.duration?.takeIf { it > 0 } ?: entry.durationMs,
            profile = profile,
        ))
        lastCheckpoint = android.os.SystemClock.elapsedRealtime()
    }
}
