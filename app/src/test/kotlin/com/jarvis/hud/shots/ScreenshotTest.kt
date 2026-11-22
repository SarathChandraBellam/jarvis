package com.jarvis.hud.shots

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.PermissionStatus
import com.jarvis.hud.ui.JarvisApp
import com.jarvis.hud.ui.screens.PreviewFixtures
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1400dp-xhdpi")
class ScreenshotTest {
  @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

  private fun shot(name: String, state: com.jarvis.hud.model.JarvisUiState) {
    rule.setContent { JarvisApp(state, PreviewFixtures.noOpActions) }
    rule.waitForIdle()
    val view = rule.activity.window.decorView
    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
    val out = File("build/screenshots", "$name.png")
    out.parentFile.mkdirs()
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test fun home() = shot("home", PreviewFixtures.state(HudTab.HUD))
  @Test fun money() = shot("money", PreviewFixtures.state(HudTab.FINANCE))
  @Test fun inbox() = shot("inbox", PreviewFixtures.state(HudTab.INTEL))
  @Test fun ask() = shot("ask", PreviewFixtures.state(HudTab.ASK).copy(isAskingAi = true))
  @Test fun settings() = shot("settings", PreviewFixtures.state().copy(showSettings = true))
  @Test @Config(qualifiers = "w900dp-h600dp-land-xhdpi") fun tablet() = shot("tablet", PreviewFixtures.state(HudTab.HUD))
  @Test fun onboarding() = shot("onboarding", com.jarvis.hud.model.JarvisUiState())
  @Test fun sheet() = shot("sheet", PreviewFixtures.state(HudTab.INTEL).copy(openItemId = "b1"))
  @Test fun homeEmpty() = shot("home-empty", com.jarvis.hud.model.JarvisUiState(settings = com.jarvis.hud.model.JarvisSettings(onboardingDone = true),
    permissions = PermissionStatus(false, false, false, false, false)))
}
