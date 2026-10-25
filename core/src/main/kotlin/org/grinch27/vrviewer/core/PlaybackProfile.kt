// Requirements: one viewport, free positive zoom, screen-plane rotation and independent FOV axes.
// Open decisions/research: gesture feel and extreme magnification need physical-device feedback.
// Risks/verification: perspective FOV stays below 180 degrees; invalid persisted values must remain renderable.
package org.grinch27.vrviewer.core

enum class Projection(val shaderId: Int) { FLAT(0), EQUIRECT_180(1), EQUIRECT_360(2) }
enum class StereoLayout { MONO, LEFT_RIGHT, TOP_BOTTOM }

data class PlaybackProfile(
    val projection: Projection = Projection.FLAT,
    val stereo: StereoLayout = StereoLayout.MONO,
    val rightEye: Boolean = false,
    val halfPacked: Boolean = false,
    val fieldOfView: Float = 80f,
    val headTracking: Boolean = false,
    val zoom: Float = 1f,
    // Missing legacy values are initialized once from the first viewport, then stored independently.
    val horizontalFieldOfView: Float? = null,
    val rotationDegrees: Float = 0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun normalized() = copy(
        fieldOfView = fieldOfView.takeIf { it.isFinite() }?.coerceIn(MIN_FOV, MAX_FOV) ?: 80f,
        horizontalFieldOfView = horizontalFieldOfView?.takeIf { it.isFinite() }?.coerceIn(MIN_FOV, MAX_FOV),
        zoom = zoom.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(java.lang.Float.MIN_NORMAL) ?: 1f,
        rotationDegrees = ProjectionMath.wrappedDegrees(rotationDegrees),
        panX = panX.takeIf { it.isFinite() } ?: 0f,
        panY = panY.takeIf { it.isFinite() } ?: 0f,
    )

    companion object {
        const val MIN_FOV = .1f
        const val MAX_FOV = 179.9f
    }
}

data class EyeRegion(val left: Float, val top: Float, val width: Float, val height: Float)

object ProjectionMath {
    fun wrappedDegrees(degrees: Float): Float =
        if (degrees.isFinite()) ((degrees % 360f + 540f) % 360f) - 180f else 0f

    /** No product zoom cap: retain only the finite, positive range representable by the renderer. */
    fun scaledZoom(zoom: Float, factor: Float): Float =
        if (factor.isFinite() && factor > 0f) {
            (zoom.toDouble() * factor).coerceIn(java.lang.Float.MIN_NORMAL.toDouble(), Float.MAX_VALUE.toDouble()).toFloat()
        } else zoom

    fun horizontalFov(profile: PlaybackProfile, viewportAspect: Float): Float =
        profile.horizontalFieldOfView ?: Math.toDegrees(2 * kotlin.math.atan(
            kotlin.math.tan(Math.toRadians(profile.fieldOfView.toDouble() / 2)) * viewportAspect,
        )).toFloat()

    /** Clamp ray slopes only at numerical extremes so normalize() cannot overflow on GLES 2.0. */
    fun viewTangents(profile: PlaybackProfile, viewportAspect: Float): Pair<Float, Float> {
        val vertical = kotlin.math.tan(Math.toRadians(profile.fieldOfView.toDouble() / 2))
        val horizontal = profile.horizontalFieldOfView?.let { kotlin.math.tan(Math.toRadians(it.toDouble() / 2)) }
            ?: (vertical * viewportAspect)
        return (horizontal / profile.zoom).coerceIn(1e-6, 1e6).toFloat() to
            (vertical / profile.zoom).coerceIn(1e-6, 1e6).toFloat()
    }

    fun fovFromTangent(tangent: Float): Float = Math.toDegrees(2 * kotlin.math.atan(tangent.toDouble())).toFloat()

    /** Coordinates use the logical video image: origin at top left, before SurfaceTexture transform. */
    fun eyeRegion(layout: StereoLayout, eye: Int): EyeRegion {
        require(eye in 0..1)
        return when (layout) {
            StereoLayout.MONO -> EyeRegion(0f, 0f, 1f, 1f)
            StereoLayout.LEFT_RIGHT -> EyeRegion(eye * .5f, 0f, .5f, 1f)
            StereoLayout.TOP_BOTTOM -> EyeRegion(0f, eye * .5f, 1f, .5f)
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
