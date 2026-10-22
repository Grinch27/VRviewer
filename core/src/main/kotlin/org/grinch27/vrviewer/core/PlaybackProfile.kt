// Open decisions: metadata-based projection detection and calibrated headset profiles are deferred.
// Risks/verification: projection, source packing and output view MUST remain independent.
package org.grinch27.vrviewer.core

enum class Projection(val shaderId: Int) { FLAT(0), EQUIRECT_180(1), EQUIRECT_360(2) }
enum class StereoLayout { MONO, LEFT_RIGHT, TOP_BOTTOM }
enum class DisplayMode { SINGLE, DUAL }

data class PlaybackProfile(
    val projection: Projection = Projection.FLAT,
    val stereo: StereoLayout = StereoLayout.MONO,
    val display: DisplayMode = DisplayMode.SINGLE,
    val swapEyes: Boolean = false,
    val halfPacked: Boolean = false,
    val fieldOfView: Float = 80f,
    val headTracking: Boolean = false,
) {
    fun normalized() = copy(fieldOfView = fieldOfView.takeIf { it.isFinite() }?.coerceIn(35f, 110f) ?: 80f)
}

data class EyeRegion(val left: Float, val top: Float, val width: Float, val height: Float)

object ProjectionMath {
    /** Coordinates use the logical video image: origin at top left, before SurfaceTexture transform. */
    fun eyeRegion(layout: StereoLayout, eye: Int, swapped: Boolean = false): EyeRegion {
        require(eye in 0..1)
        val selected = if (swapped) 1 - eye else eye
        return when (layout) {
            StereoLayout.MONO -> EyeRegion(0f, 0f, 1f, 1f)
            StereoLayout.LEFT_RIGHT -> EyeRegion(selected * .5f, 0f, .5f, 1f)
            StereoLayout.TOP_BOTTOM -> EyeRegion(0f, selected * .5f, 1f, .5f)
        }
    }

    fun eyeAspect(width: Int, height: Int, pixelRatio: Float, profile: PlaybackProfile): Float {
        if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0f) return 16f / 9f
        val region = eyeRegion(profile.stereo, 0)
        val base = width.toFloat() / height * pixelRatio
        return if (profile.halfPacked && profile.stereo != StereoLayout.MONO) base else base * region.width / region.height
    }

    /** Independent CPU oracle for equirectangular shader coordinates; null means outside a VR180 hemisphere. */
    fun sphericalUv(x: Double, y: Double, z: Double, projection: Projection): Pair<Double, Double>? {
        require(projection != Projection.FLAT)
        val length = kotlin.math.sqrt(x * x + y * y + z * z)
        require(length.isFinite() && length > 0)
        val longitude = kotlin.math.atan2(x, -z)
        val latitude = kotlin.math.asin((y / length).coerceIn(-1.0, 1.0))
        if (projection == Projection.EQUIRECT_180 && kotlin.math.abs(longitude) > Math.PI / 2) return null
        val span = if (projection == Projection.EQUIRECT_180) Math.PI else Math.PI * 2
        return (longitude / span + .5) to (.5 - latitude / Math.PI)
    }

    fun resumePosition(position: Long, duration: Long): Long = when {
        position <= 0 -> 0
        duration > 0 && position >= (duration - 3_000).coerceAtLeast(0) -> 0
        duration > 0 -> position.coerceAtMost(duration)
        else -> position
    }
}
