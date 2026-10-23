// Verification: exactly two home actions; URL dialog validates before opening, without external networking.
// Unknowns/research: physical-device input methods and accessibility traversal remain device acceptance items.
package org.grinch27.vrviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.util.UnstableApi
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
}
