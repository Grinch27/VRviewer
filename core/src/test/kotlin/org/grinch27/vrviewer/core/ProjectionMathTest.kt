// Verification: independent projection landmarks, eye isolation, aspect restoration and corrupt persisted values.
// Device-only unknowns: SurfaceTexture orientation, optics, sensors and hardware decode are outside JVM coverage.
package org.grinch27.vrviewer.core

import org.junit.Assert.*
import org.junit.Test

class ProjectionMathTest {
    @Test fun stereoCropsDoNotOverlap() {
        assertEquals(EyeRegion(0f, 0f, .5f, 1f), ProjectionMath.eyeRegion(StereoLayout.LEFT_RIGHT, 0))
        assertEquals(EyeRegion(.5f, 0f, .5f, 1f), ProjectionMath.eyeRegion(StereoLayout.LEFT_RIGHT, 1))
        assertEquals(EyeRegion(0f, 0f, 1f, .5f), ProjectionMath.eyeRegion(StereoLayout.TOP_BOTTOM, 0))
        assertEquals(EyeRegion(0f, .5f, 1f, .5f), ProjectionMath.eyeRegion(StereoLayout.TOP_BOTTOM, 1))
    }
    @Test fun monoUsesWholeFrameForEitherSourceEye() {
        assertEquals(ProjectionMath.eyeRegion(StereoLayout.MONO, 0), ProjectionMath.eyeRegion(StereoLayout.MONO, 1))
    }
    @Test fun fullAndHalfPackingRestoreFlatVideoAspect() {
        val sbs = PlaybackProfile(stereo = StereoLayout.LEFT_RIGHT)
        assertEquals(16f / 9, ProjectionMath.eyeAspect(3840, 1080, 1f, sbs), .0001f)
        assertEquals(16f / 9, ProjectionMath.eyeAspect(1920, 1080, 1f, sbs.copy(halfPacked = true)), .0001f)
        val tb = PlaybackProfile(stereo = StereoLayout.TOP_BOTTOM)
        assertEquals(16f / 9, ProjectionMath.eyeAspect(1920, 2160, 1f, tb), .0001f)
        assertEquals(16f / 9, ProjectionMath.eyeAspect(1920, 1080, 1f, tb.copy(halfPacked = true)), .0001f)
    }
    @Test fun pixelAspectAndUnknownSizeAreHandled() {
        assertEquals(2f, ProjectionMath.eyeAspect(100, 100, 2f, PlaybackProfile()), .0001f)
        assertEquals(16f / 9, ProjectionMath.eyeAspect(0, 0, Float.NaN, PlaybackProfile()), .0001f)
    }
    @Test fun sphericalCardinalDirectionsMatchVideoLandmarks() {
        val projection = Projection.EQUIRECT_360
        val front = ProjectionMath.sphericalUv(0.0, 0.0, -1.0, projection)!!
        val right = ProjectionMath.sphericalUv(1.0, 0.0, 0.0, projection)!!
        val top = ProjectionMath.sphericalUv(0.0, 1.0, 0.0, projection)!!
        assertEquals(.5, front.first, .0001)
        assertEquals(.5, front.second, .0001)
        assertEquals(.75, right.first, .0001)
        assertEquals(0.0, top.second, .0001)
    }
    @Test fun vr180ShowsOnlyFrontHemisphere() {
        assertNull(ProjectionMath.sphericalUv(0.0, 0.0, 1.0, Projection.EQUIRECT_180))
        assertEquals(.5, ProjectionMath.sphericalUv(0.0, 0.0, -1.0, Projection.EQUIRECT_180)!!.first, .0001)
        assertEquals(1.0, ProjectionMath.sphericalUv(1.0, 0.0, 0.0, Projection.EQUIRECT_180)!!.first, .0001)
    }
    @Test fun fovIsFiniteAndBounded() {
        assertEquals(80f, PlaybackProfile(fieldOfView = Float.NaN).normalized().fieldOfView, 0f)
        assertEquals(35f, PlaybackProfile(fieldOfView = -1f).normalized().fieldOfView, 0f)
        assertEquals(110f, PlaybackProfile(fieldOfView = 400f).normalized().fieldOfView, 0f)
    }
    @Test fun finishedOrNegativePositionsDoNotResumeAtTheEnd() {
        assertEquals(0L, ProjectionMath.resumePosition(-1, 100_000))
        assertEquals(0L, ProjectionMath.resumePosition(99_000, 100_000))
        assertEquals(42_000L, ProjectionMath.resumePosition(42_000, 100_000))
        assertEquals(42_000L, ProjectionMath.resumePosition(42_000, -1))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidEyeIsRejected() {
        ProjectionMath.eyeRegion(StereoLayout.LEFT_RIGHT, 2)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidRayIsRejected() {
        ProjectionMath.sphericalUv(0.0, 0.0, 0.0, Projection.EQUIRECT_360)
    }
}
