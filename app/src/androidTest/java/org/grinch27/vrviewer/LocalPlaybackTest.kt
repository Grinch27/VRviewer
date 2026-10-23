// Verification: real MediaCodec -> SurfaceTexture -> OES shader -> PixelCopy, using generated color fixtures only.
// Unknowns: emulator results do not establish phone thermals, optical comfort, head tracking or 8K/HDR support.
package org.grinch27.vrviewer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.grinch27.vrviewer.core.*
import org.grinch27.vrviewer.playback.PlaybackDataSource
import org.grinch27.vrviewer.playback.PlaybackController
import org.grinch27.vrviewer.render.VrVideoView
import org.grinch27.vrviewer.storage.MovieEntry
import org.grinch27.vrviewer.storage.PlaybackStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LocalPlaybackTest {
    @Test fun unsupportedSchemesAndArbitraryFilePathsAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (uri in listOf("ftp://example.invalid/video.mp4", "file:///system/build.prop", "data:video/mp4;base64,AAAA")) {
            val source = PlaybackDataSource(context)
            try {
                source.open(DataSpec(Uri.parse(uri)))
                fail("Non-local or unselected file must be rejected")
            } catch (_: IOException) {
                // Expected at the I/O boundary before any connection is attempted.
            } finally { source.close() }
        }
    }

    @Test fun historyPreservesProfileAndProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PlaybackStore(context)
        val entry = MovieEntry("content://synthetic.test/video", "Generated fixture", 12_345, 100_000, PlaybackProfile(Projection.EQUIRECT_180, StereoLayout.TOP_BOTTOM, DisplayMode.DUAL, true))
        store.save(entry)
        val restored = PlaybackStore(context).recent().first { it.uri == entry.uri }
        assertEquals(entry.profile, restored.profile)
        assertEquals(entry.positionMs, restored.positionMs)
        assertEquals(entry.durationMs, restored.durationMs)
    }

    @Test fun sideBySideFramesKeepEyesSeparate() = verifyPackedPlayback("sbs.mp4", StereoLayout.LEFT_RIGHT)
    @Test fun topBottomFramesKeepEyesSeparate() = verifyPackedPlayback("tb.mp4", StereoLayout.TOP_BOTTOM)
    @Test fun httpUrlDecodesAndKeepsEyesSeparate() = verifyPackedPlayback("sbs.mp4", StereoLayout.LEFT_RIGHT, network = true)

    private fun verifyPackedPlayback(asset: String, layout: StereoLayout, network: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File(instrumentation.targetContext.cacheDir, "generated-$asset")
        instrumentation.context.assets.open(asset).use { input -> fixture.outputStream().use { input.copyTo(it) } }
        val server = if (network) FixtureServer(fixture.readBytes()) else null
        val sourceUri = server?.url ?: Uri.fromFile(fixture).toString()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var video: VrVideoView
        lateinit var controller: PlaybackController
        var videoReady = false
        var controllerReady = false
        var saved: MovieEntry? = null
        var base = PlaybackProfile(stereo = layout, display = DisplayMode.DUAL)
        val decodedWidth = if (layout == StereoLayout.TOP_BOTTOM) 160 else 320
        val decodedHeight = if (layout == StereoLayout.TOP_BOTTOM) 320 else 160
        try {
            scenario.onActivity { activity ->
                controller = PlaybackController(activity.applicationContext, MovieEntry(sourceUri, "Synthetic fixture")) { saved = it }
                controllerReady = true
                controller.updateProfile(base)
                video = VrVideoView(activity, controller::attachSurface, controller::reportError, {}, {}, {})
                videoReady = true
                video.update(base, decodedWidth, decodedHeight, 1f)
                activity.setContentView(video)
                video.onResume()
                controller.start()
            }
            assertEyeColors(scenario, video, dual = true, swapped = false)
            base = base.copy(display = DisplayMode.SINGLE)
            scenario.onActivity { controller.updateProfile(base); video.update(base, decodedWidth, decodedHeight, 1f) }
            assertEyeColors(scenario, video, dual = false, swapped = false)
            base = base.copy(swapEyes = true)
            scenario.onActivity { controller.updateProfile(base); video.update(base, decodedWidth, decodedHeight, 1f) }
            assertEyeColors(scenario, video, dual = false, swapped = true)
            base = base.copy(projection = Projection.EQUIRECT_360, display = DisplayMode.DUAL)
            scenario.onActivity { controller.updateProfile(base); video.update(base, decodedWidth, decodedHeight, 1f); controller.seek(1_000) }
            assertEyeColors(scenario, video, dual = true, swapped = true)
            scenario.onActivity {
                controller.tick()
                assertNull(controller.status.error)
                assertTrue("Decoder must report a seekable duration", controller.status.durationMs > 0)
                assertTrue("Seek must move playback near the requested second", controller.status.positionMs in 900L..4_000L)
                controller.stop()
                assertFalse(controller.status.playing)
                assertNotNull(saved)
                assertEquals(base, saved!!.profile)
                video.onPause()
            }
            scenario.moveToState(Lifecycle.State.CREATED)
        } finally {
            scenario.onActivity {
                if (controllerReady) controller.close()
                if (videoReady) { video.onPause(); video.close() }
            }
            scenario.close()
            server?.close()
            fixture.delete()
        }
    }

    private fun assertEyeColors(scenario: ActivityScenario<MainActivity>, video: VrVideoView, dual: Boolean, swapped: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var matched = false
        while (!matched && SystemClock.elapsedRealtime() < deadline) {
            var bitmap: Bitmap? = null
            var result = -1
            val latch = CountDownLatch(1)
            scenario.onActivity {
                if (video.width <= 0 || video.height <= 0) { latch.countDown(); return@onActivity }
                bitmap = Bitmap.createBitmap(video.width, video.height, Bitmap.Config.ARGB_8888)
                try {
                    PixelCopy.request(video, bitmap!!, { code -> result = code; latch.countDown() }, Handler(Looper.getMainLooper()))
                } catch (_: IllegalArgumentException) {
                    // A newly attached SurfaceView may not yet have a valid producer surface.
                    latch.countDown()
                }
            }
            assertTrue("PixelCopy callback timed out", latch.await(3, TimeUnit.SECONDS))
            val image = bitmap
            if (result == PixelCopy.SUCCESS && image != null) {
                fun isExpected(x: Int, blue: Boolean): Boolean {
                    val color = image.getPixel(x, image.height / 2)
                    val red = android.graphics.Color.red(color)
                    val blueChannel = android.graphics.Color.blue(color)
                    return if (blue) blueChannel > 120 && red < 90 else red > 120 && blueChannel < 90
                }
                matched = if (dual) isExpected(image.width / 4, swapped) && isExpected(image.width * 3 / 4, !swapped) else isExpected(image.width / 2, swapped)
            }
            image?.recycle()
            if (!matched) SystemClock.sleep(100)
        }
        assertTrue("Decoded ${if (dual) "two-eye" else "single-eye"} output must preserve source eye colors", matched)
    }
}
