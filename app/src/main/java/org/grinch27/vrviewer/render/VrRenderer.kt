// Requirements: one full-size viewport; source eye selection does not create a split-screen display.
// Open decisions/research: mesh metadata, fisheye, cubemaps and HDR need separate validation.
// Optimization/verification: one OES texture; test EGL recreation, source crop, aspect and shader precision.
package org.grinch27.vrviewer.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import org.grinch27.vrviewer.R
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.ProjectionMath
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan

internal class VrRenderer(
    private val context: Context,
    private val requestFrame: () -> Unit,
    private val publishTexture: (SurfaceTexture) -> Unit,
    private val onError: (String) -> Unit,
) : GLSurfaceView.Renderer {
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }
    private val pendingFrame = AtomicBoolean(false)
    private val poseLock = Any()
    private val head = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val headSnapshot = FloatArray(16)
    private val touch = FloatArray(16)
    private val camera = FloatArray(16)
    private val textureTransform = FloatArray(16)
    private var yaw = 0f
    private var pitch = 0f
    private var width = 1
    private var height = 1
    private var texture: SurfaceTexture? = null
    private var program = 0
    private var textureId = 0
    private var positionLocation = 0
    private var hasFrame = false
    private var failed = false
    private val locations = mutableMapOf<String, Int>()
    @Volatile var profile = PlaybackProfile()
    @Volatile var videoAspect = 16f / 9
    @Volatile var videoWidth = 4096
    @Volatile var videoHeight = 2048

    fun move(dx: Float, dy: Float) = synchronized(poseLock) {
        yaw = (yaw + dx) % 360f
        pitch = (pitch + dy).coerceIn(-89f, 89f)
    }
    fun setHead(matrix: FloatArray) = synchronized(poseLock) { matrix.copyInto(head) }
    fun recenter() = synchronized(poseLock) {
        yaw = 0f
        pitch = 0f
        Matrix.setIdentityM(head, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        failed = false
        hasFrame = false
        pendingFrame.set(false)
        try {
            val vertex = shader(GLES20.GL_VERTEX_SHADER, R.raw.video_vertex)
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, R.raw.video_fragment)
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(program) }
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
            locations.clear()
            for (name in listOf("uTexture", "uTextureTransform", "uCameraRotation", "uEyeRect", "uTexel", "uViewportAspect", "uVideoAspect", "uTanHalfFov", "uProjection")) {
                locations[name] = GLES20.glGetUniformLocation(program, name)
            }
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            texture = SurfaceTexture(textureId).also { surfaceTexture ->
                surfaceTexture.setOnFrameAvailableListener {
                    pendingFrame.set(true)
                    requestFrame()
                }
                publishTexture(surfaceTexture)
            }
        } catch (_: RuntimeException) {
            failed = true
            onError("设备无法初始化 OpenGL 视频渲染，请退出后重试。")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (failed) return
        try {
            if (pendingFrame.getAndSet(false)) {
                texture?.updateTexImage()
                texture?.getTransformMatrix(textureTransform)
                hasFrame = true
            }
            if (!hasFrame) return
            val currentProfile = profile
            synchronized(poseLock) {
                head.copyInto(headSnapshot)
                Matrix.setRotateM(touch, 0, yaw, 0f, 1f, 0f)
                Matrix.rotateM(touch, 0, pitch, 1f, 0f, 0f)
            }
            Matrix.multiplyMM(camera, 0, headSnapshot, 0, touch, 0)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(location("uTexture"), 0)
            GLES20.glUniformMatrix4fv(location("uTextureTransform"), 1, false, textureTransform, 0)
            GLES20.glUniformMatrix4fv(location("uCameraRotation"), 1, false, camera, 0)
            GLES20.glUniform2f(location("uTexel"), 1f / videoWidth.coerceAtLeast(2), 1f / videoHeight.coerceAtLeast(2))
            GLES20.glUniform1f(location("uVideoAspect"), videoAspect)
            GLES20.glUniform1f(location("uTanHalfFov"), tan(Math.toRadians(currentProfile.fieldOfView.toDouble() / 2)).toFloat())
            GLES20.glUniform1i(location("uProjection"), currentProfile.projection.shaderId)
            quad.position(0)
            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, quad)
            val region = ProjectionMath.eyeRegion(currentProfile.stereo, if (currentProfile.rightEye) 1 else 0)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUniform1f(location("uViewportAspect"), width.toFloat() / height)
            GLES20.glUniform4f(location("uEyeRect"), region.left, region.top, region.width, region.height)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionLocation)
        } catch (_: RuntimeException) {
            failed = true
            onError("视频渲染已中断，请重试或重新打开文件。")
        }
    }

    private fun location(name: String): Int = locations.getValue(name)
    private fun shader(type: Int, resource: Int): Int {
        val code = context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }
}
