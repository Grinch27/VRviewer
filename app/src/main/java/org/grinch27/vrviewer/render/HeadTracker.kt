// Open decisions: predicted pose and lens-calibrated tracking require the optional Cardboard backend.
// Risk/verification: sensor absence, display rotation, recenter and lifecycle registration; no high-rate permission.
package org.grinch27.vrviewer.render

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Surface

internal class HeadTracker(
    context: Context,
    private val displayRotation: () -> Int,
    private val onPose: (FloatArray) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val available get() = sensor != null
    private val raw = FloatArray(16)
    private val adjusted = FloatArray(16)
    private val cameraToWorld = FloatArray(16)
    private val referenceInverse = FloatArray(16)
    private val relative = FloatArray(16)
    private var referenceReady = false
    private var lastRotation = -1
    private var registered = false

    fun start(): Boolean {
        if (registered) return true
        recenter()
        registered = sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } ?: false
        return registered
    }
    fun stop() {
        manager.unregisterListener(this)
        registered = false
        recenter()
    }
    fun recenter() { referenceReady = false }

    override fun onSensorChanged(event: SensorEvent) {
        if (!registered) return
        SensorManager.getRotationMatrixFromVector(raw, event.values)
        val rotation = displayRotation()
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(raw, axes.first, axes.second, adjusted)
        // SensorManager returns row-major world-from-device. Our ray shader needs column-major camera-to-world.
        Matrix.transposeM(cameraToWorld, 0, adjusted, 0)
        if (rotation != lastRotation) referenceReady = false
        lastRotation = rotation
        if (!referenceReady) {
            // Rotation matrices are orthonormal: transpose is the inverse, with no allocation.
            Matrix.transposeM(referenceInverse, 0, cameraToWorld, 0)
            referenceReady = true
        }
        Matrix.multiplyMM(relative, 0, referenceInverse, 0, cameraToWorld, 0)
        onPose(relative)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
