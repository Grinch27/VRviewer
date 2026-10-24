// Requirements: exactly two home actions; no display mode or player-owned orientation changes.
// Research/unknowns: physical-device auto-rotation and input methods still need device acceptance.
// Risks/verification: use generated loopback media; exercise real settings and repeated Activity recreation.
package org.grinch27.vrviewer

import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeHasExactlyTwoActionsAndUrlInputRejectsOtherSchemes() {
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithText("打开本地文件").assertIsDisplayed()
        compose.onNodeWithText("打开 URL 链接").performClick()
        compose.onNodeWithText("视频直链").assertIsDisplayed()
        compose.onNodeWithText("打开", substring = false).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("javascript:alert(1)")
        compose.onNodeWithText("打开", substring = false).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("https://example.invalid/video.mp4")
        compose.onNodeWithText("打开", substring = false).assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test fun settingsAndRecreationKeepSystemOrientationWithoutDisplayOptions() {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("sbs.mp4").use { it.readBytes() }
        val previousOrientation = compose.runOnUiThread {
            val previous = compose.activity.requestedOrientation
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            previous
        }
        try {
            FixtureServer(bytes).use { server ->
                compose.onNodeWithText("打开 URL 链接").performClick()
                compose.onNode(hasSetTextAction()).performTextInput(server.url)
                compose.onNodeWithText("打开", substring = false).performClick()
                pausePlayer()
                for (projection in listOf("180° 等距柱状", "360° 等距柱状")) {
                    compose.onNodeWithText("设置").performClick()
                    assertNoDisplayOptions()
                    compose.onNodeWithText(projection).performScrollTo().performClick()
                    compose.onNodeWithText("左右 SBS").performScrollTo().performClick()
                    assertOrientationUnchanged()
                    compose.onNodeWithText("完成").performScrollTo().performClick()
                    compose.waitForIdle()

                    val previousActivity = compose.activity
                    compose.activityRule.scenario.recreate()
                    assertNotSame(previousActivity, compose.activity)
                    pausePlayer()
                    compose.onNodeWithText("设置").performClick()
                    assertNoDisplayOptions()
                    compose.onNodeWithText(projection).assertIsSelected()
                    compose.onNodeWithText("左右 SBS").assertIsSelected()
                    compose.onNodeWithText("完成").performScrollTo().performClick()
                    compose.waitForIdle()
                    assertOrientationUnchanged()
                }
                compose.onNodeWithText("返回", substring = false).performClick()
                compose.onAllNodes(hasClickAction()).assertCountEquals(2)
                assertOrientationUnchanged()
            }
        } finally {
            compose.runOnUiThread { compose.activity.requestedOrientation = previousOrientation }
        }
    }

    private fun pausePlayer() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.runOnUiThread { compose.activity.playerKeys != null }
        }
        compose.runOnUiThread { compose.activity.playerKeys?.invoke(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        compose.onNodeWithText("设置").assertIsDisplayed()
        assertOrientationUnchanged()
    }

    private fun assertNoDisplayOptions() {
        // Scroll state can survive recreation; make the header visible before checking the panel.
        compose.onNodeWithText("观看设置").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("显示方式").assertDoesNotExist()
        compose.onNodeWithText("单眼窗口").assertDoesNotExist()
        compose.onNodeWithText("双眼分屏").assertDoesNotExist()
    }

    private fun assertOrientationUnchanged() {
        compose.runOnUiThread {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, compose.activity.requestedOrientation)
        }
    }
}
