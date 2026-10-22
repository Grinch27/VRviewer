// Open decisions: calibrated viewer parameters and stereo controls are tracked in docs/DESIGN.md.
// Risks/verification: Surface publication is serialized on main; pause GL before final SurfaceTexture release.
package org.grinch27.vrviewer.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.Projection
import org.grinch27.vrviewer.core.ProjectionMath

@SuppressLint("ViewConstructor")
class VrVideoView(
    context: Context,
    private val onOutput: (Surface?) -> Unit,
    private val onError: (String) -> Unit,
    private val onTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onFov: (Float) -> Unit,
) : GLSurfaceView(context) {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var outputSurface: Surface? = null
    private var outputTexture: SurfaceTexture? = null
    private var currentProfile = PlaybackProfile()
    private val renderer = VrRenderer(context, ::requestRender, ::publish) { message -> main.post { if (!closed) onError(message) } }
    private val tracker = HeadTracker(context, { display?.rotation ?: Surface.ROTATION_0 }) { matrix ->
        renderer.setHead(matrix)
        requestRender()
    }
    val headTrackingAvailable get() = tracker.available
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (currentProfile.projection != Projection.FLAT) onFov((currentProfile.fieldOfView / detector.scaleFactor).coerceIn(35f, 110f))
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { onDoubleTap(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scale.isInProgress && currentProfile.projection != Projection.FLAT) {
                val degreesPerPixel = currentProfile.fieldOfView / height.coerceAtLeast(1)
                renderer.move(-distanceX * degreesPerPixel, -distanceY * degreesPerPixel)
                requestRender()
            }
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "视频观看区域；拖动改变视角，双指调整视野，双击播放或暂停"
    }

    fun update(profile: PlaybackProfile, width: Int, height: Int, pixelRatio: Float) {
        currentProfile = profile
        renderer.profile = profile
        renderer.videoAspect = ProjectionMath.eyeAspect(width, height, pixelRatio, profile)
        renderer.videoWidth = width.coerceAtLeast(2)
        renderer.videoHeight = height.coerceAtLeast(2)
        requestRender()
    }
    fun updateTracking(resumed: Boolean) {
        if (resumed && currentProfile.headTracking && currentProfile.projection != Projection.FLAT) {
            if (!tracker.start()) {
                renderer.recenter()
                onError("无法启用姿态传感器，请关闭陀螺仪并使用拖动控制。")
            }
        } else {
            tracker.stop()
            renderer.setHead(FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) })
        }
        requestRender()
    }
    fun recenter() {
        renderer.recenter()
        tracker.recenter()
        requestRender()
    }
    fun stopTracking() = tracker.stop()

    /** Main thread only, after onPause() has stopped the GL thread. */
    fun close() {
        if (closed) return
        closed = true
        tracker.stop()
        onOutput(null)
        outputSurface?.release()
        outputTexture?.release()
        outputSurface = null
        outputTexture = null
    }

    private fun publish(texture: SurfaceTexture) {
        main.post {
            if (closed) {
                texture.release()
            } else {
                val previousSurface = outputSurface
                val previousTexture = outputTexture
                outputTexture = texture
                outputSurface = Surface(texture)
                onOutput(outputSurface)
                previousSurface?.release()
                previousTexture?.release()
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); onTap(); return true }
}
