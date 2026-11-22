package com.jarvis.hud.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jarvis.hud.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Boots the real app (real Application, container, storage and data sources) and walks the first run. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivitySmokeTest {
  @get:Rule val rule = createAndroidComposeRule<MainActivity>()

  @Test fun coldStartThroughOnboardingIntoEveryTab() {
    rule.onNodeWithText("Meet Jarvis").assertIsDisplayed()
    rule.onNodeWithTag("onboardingName").performTextInput("Tony")
    rule.onNodeWithTag("onboardingStart").performScrollTo().performClick()
    // The real repository writes on Dispatchers.IO, which waitForIdle doesn't track.
    rule.waitUntil(10_000) { rule.onAllNodesWithTag("screen-home").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
    for (tab in listOf("FINANCE", "INTEL", "ASK", "HUD")) {
      rule.onNodeWithTag("tab-$tab").performClick()
      rule.waitForIdle()
    }
    rule.onNodeWithTag("openSettings").performClick()
    rule.onNodeWithTag("settings").assertIsDisplayed()
    rule.onNodeWithText("Tony").assertIsDisplayed()
  }
}
