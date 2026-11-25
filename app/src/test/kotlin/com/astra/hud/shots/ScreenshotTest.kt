package com.astra.hud.shots

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.astra.hud.model.HudTab
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.PermissionStatus
import com.astra.hud.model.UserGender
import com.astra.hud.ui.AstraApp
import com.astra.hud.ui.screens.PreviewFixtures
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

  private fun shot(name: String, state: com.astra.hud.model.AstraUiState) {
    rule.setContent { AstraApp(state, PreviewFixtures.noOpActions) }
    rule.waitForIdle()
    val view = rule.activity.window.decorView
    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
    val out = File("build/screenshots", "$name.png")
    out.parentFile?.mkdirs()
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test fun home() = shot("home", PreviewFixtures.state(HudTab.HUD))
  @Test fun homeMale() = shot("home-male", PreviewFixtures.state(HudTab.HUD).let { it.copy(settings = it.settings.copy(userGender = UserGender.MALE, cycleTrackingEnabled = false)) })
  @Test fun homeFood() = shot("home-food", PreviewFixtures.state(HudTab.HUD).let { it.copy(settings = it.settings.copy(todayViewIndex = 1)) })
  @Test fun homeCycle() = shot("home-cycle", PreviewFixtures.state(HudTab.HUD).let { it.copy(settings = it.settings.copy(todayViewIndex = 2)) })
  @Test fun homeSleep() = shot("home-sleep", PreviewFixtures.state(HudTab.HUD).let { it.copy(settings = it.settings.copy(todayViewIndex = 3)) })
  @Test fun homePlan() = shot("home-plan", PreviewFixtures.state(HudTab.HUD).let { it.copy(settings = it.settings.copy(todayViewIndex = 4)) })
  @Test fun subsOverview() = shot("subs-overview", PreviewFixtures.state(HudTab.FINANCE))
  @Test fun subsRenewals() = shot("subs-renewals", PreviewFixtures.state(HudTab.FINANCE).let { it.copy(settings = it.settings.copy(subsViewIndex = 1)) })
  @Test fun subsBills() = shot("subs-bills", PreviewFixtures.state(HudTab.FINANCE).let { it.copy(settings = it.settings.copy(subsViewIndex = 2)) })
  @Test fun inbox() = shot("inbox", PreviewFixtures.state(HudTab.INTEL))
  @Test fun ask() = shot("ask", PreviewFixtures.state(HudTab.ASK).copy(isAskingAi = true))
  @Test fun settings() = shot("settings", PreviewFixtures.state().copy(showSettings = true))
  @Test @Config(qualifiers = "w900dp-h600dp-land-xhdpi") fun tablet() = shot("tablet", PreviewFixtures.state(HudTab.HUD))
  @Test fun onboarding() = shot("onboarding", com.astra.hud.model.AstraUiState())
  @Test fun sheet() = shot("sheet", PreviewFixtures.state(HudTab.INTEL).copy(openItemId = "b1"))
  @Test fun homeEmpty() = shot(
    "home-empty",
    com.astra.hud.model.AstraUiState(
      settings = AstraSettings(onboardingDone = true),
      permissions = PermissionStatus(false, false, false, false, false, false),
    ),
  )
}
