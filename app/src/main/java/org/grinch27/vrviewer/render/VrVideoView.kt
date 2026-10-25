// Requirements/ambiguities: two fingers zoom and rotate the image; Activity orientation belongs to the system.
// Research/optimization: one-finger flat pan or panorama look; retain pointer IDs across multi-touch transitions.
// Risks/verification: suppress taps after multi-touch, rebase replaced pointers and serialize Surface publication.
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
import android.view.ViewConfiguration
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.Projection
import org.grinch27.vrviewer.core.ProjectionMath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class VrVideoView(
    context: Context,
    private val onOutput: (Surface?) -> Unit,
    private val onError: (String) -> Unit,
    private val onTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onProfile: (PlaybackProfile) -> Unit,
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
    private var multiTouch = false
    private var firstPointer = -1
    private var secondPointer = -1
    private var previousAngle: Float? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            changeProfile(currentProfile.copy(zoom = ProjectionMath.scaledZoom(currentProfile.zoom, detector.scaleFactor)))
            return true
        }
    }).apply { isQuickScaleEnabled = false }
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { performClick(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { onDoubleTap(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!multiTouch && !scale.isInProgress && e2.pointerCount == 1) {
                if (currentProfile.projection == Projection.FLAT) {
                    val unitsPerPixel = 2f / height.coerceAtLeast(1)
                    changeProfile(currentProfile.copy(
                        panX = currentProfile.panX - distanceX * unitsPerPixel,
                        panY = currentProfile.panY + distanceY * unitsPerPixel,
                    ))
                } else {
                    val tangents = ProjectionMath.viewTangents(currentProfile, width.toFloat().coerceAtLeast(1f) / height.coerceAtLeast(1))
                    val yaw = -distanceX * ProjectionMath.fovFromTangent(tangents.first) / width.coerceAtLeast(1)
                    val pitch = -distanceY * ProjectionMath.fovFromTangent(tangents.second) / height.coerceAtLeast(1)
                    val rotation = Math.toRadians(currentProfile.rotationDegrees.toDouble())
                    val cosine = cos(rotation).toFloat()
                    val sine = sin(rotation).toFloat()
                    renderer.move(
                        yaw * cosine + pitch * sine,
                        -yaw * sine + pitch * cosine,
                    )
                    requestRender()
                }
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
        contentDescription = "视频观看区域；单指拖动，双指缩放或旋转画面，双击播放或暂停"
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

    private fun changeProfile(value: PlaybackProfile) {
        currentProfile = value.normalized()
        renderer.profile = currentProfile
        onProfile(currentProfile)
        requestRender()
    }

    private fun pointerAngle(event: MotionEvent): Float? {
        val first = event.findPointerIndex(firstPointer)
        val second = event.findPointerIndex(secondPointer)
        if (first < 0 || second < 0) return null
        val dx = event.getX(second) - event.getX(first)
        val dy = event.getY(second) - event.getY(first)
        if (dx * dx + dy * dy < touchSlop * touchSlop) return null
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    private fun rebasePointers(event: MotionEvent, excludedIndex: Int = -1) {
        firstPointer = -1
        secondPointer = -1
        for (index in 0 until event.pointerCount) {
            if (index == excludedIndex) continue
            if (firstPointer < 0) firstPointer = event.getPointerId(index)
            else { secondPointer = event.getPointerId(index); break }
        }
        previousAngle = pointerAngle(event)
    }

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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiTouch = false
            previousAngle = null
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            if (!multiTouch) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                gestures.onTouchEvent(cancel)
                cancel.recycle()
            }
            multiTouch = true
            rebasePointers(event)
        }
        scale.onTouchEvent(event)
        if (multiTouch) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val angle = pointerAngle(event)
                    val previous = previousAngle
                    if (angle != null && previous != null) {
                        val delta = ProjectionMath.wrappedDegrees(angle - previous)
                        if (delta != 0f) changeProfile(currentProfile.copy(rotationDegrees = currentProfile.rotationDegrees + delta))
                    }
                    previousAngle = angle
                }
                MotionEvent.ACTION_POINTER_UP -> rebasePointers(event, event.actionIndex)
            }
        } else gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            multiTouch = false
            firstPointer = -1
            secondPointer = -1
            previousAngle = null
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); onTap(); return true }
}
