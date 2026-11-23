package com.jarvis.hud.e2e

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.Priority
import com.jarvis.hud.ui.FakeContainer
import com.jarvis.hud.ui.FakeSettingsRepo
import com.jarvis.hud.ui.FakeStore
import com.jarvis.hud.ui.JarvisApp
import com.jarvis.hud.ui.JarvisViewModel
import com.jarvis.hud.ui.emptyContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end UI flows: the real [JarvisApp] + real [JarvisViewModel], driven by taps and typing,
 * with only the phone data / AI / storage behind fakes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val onboarded = JarvisSettings(onboardingDone = true, userCallsign = "Tony")

  private fun launch(c: FakeContainer): JarvisViewModel {
    lateinit var vm: JarvisViewModel
    rule.setContent {
      vm = remember { JarvisViewModel(c) }
      val state by vm.uiState.collectAsStateWithLifecycle()
      JarvisApp(state, vm)
    }
    rule.waitForIdle()
    return vm
  }

  private fun item(id: String, title: String, cat: IntelCategory = IntelCategory.BILL, p: Priority = Priority.CRITICAL) =
    OrganizedItem(id, null, cat, title, "Summary of $title", amount = 499.0, dueDate = "25 Nov",
      priority = p, timestamp = System.currentTimeMillis() - 60_000, source = DataSource.SMS)

  private fun containerWith(vararg items: OrganizedItem, settings: JarvisSettings = onboarded) =
    FakeContainer(
      phoneData = emptyContainer().phoneData,
      notifications = emptyContainer().notifications,
      settingsRepo = FakeSettingsRepo(settings),
      store = FakeStore().apply { this.items.value = items.toList() },
    )

  // ------------------------------------------------------------------ onboarding

  @Test fun firstLaunchShowsWelcomeAndSavesName() {
    val c = containerWith(settings = JarvisSettings())
    launch(c)
    rule.onNodeWithText("Meet Jarvis").assertIsDisplayed()
    rule.onNodeWithTag("onboardingName").performTextInput("Pepper")
    rule.onNodeWithTag("onboardingStart").performScrollTo().performClick()
    rule.waitForIdle()
    assertTrue(c.settingsRepo.settings.value.onboardingDone)
    assertEquals("Pepper", c.settingsRepo.settings.value.userCallsign)
    rule.onNodeWithText("Pepper", substring = true).assertIsDisplayed()
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
  }

  @Test fun skipOnboardingKeepsDefaultName() {
    val c = containerWith(settings = JarvisSettings())
    c.permissions.status = com.jarvis.hud.model.PermissionStatus(false, false, false, false, false)
    launch(c)
    rule.onNodeWithText("Skip for now").performScrollTo().performClick()
    rule.waitForIdle()
    assertTrue(c.settingsRepo.settings.value.onboardingDone)
    assertEquals("Sir", c.settingsRepo.settings.value.userCallsign)
  }

  // ------------------------------------------------------------------ navigation

  @Test fun bottomTabsNavigateEverywhere() {
    launch(containerWith(item("b1", "Electricity bill")))
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
    rule.onNodeWithTag("tab-FINANCE").performClick()
    rule.onNodeWithTag("screen-money").assertIsDisplayed()
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithTag("inboxSearch").assertIsDisplayed()
    rule.onNodeWithTag("tab-ASK").performClick()
    rule.onNodeWithText("How can I help, Tony?").assertIsDisplayed()
    rule.onNodeWithTag("tab-HUD").performClick()
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
  }

  @Test @Config(qualifiers = "w900dp-h600dp-land-xhdpi")
  fun tabletUsesSideRail() {
    launch(containerWith(item("b1", "Electricity bill")))
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithTag("inboxSearch").assertIsDisplayed()
  }

  // ------------------------------------------------------------------ settings (the reported bug)

  @Test fun settingsAutoSaveWithoutASaveButton() {
    val c = containerWith()
    launch(c)
    rule.onNodeWithTag("openSettings").performClick()
    rule.onNodeWithText("All changes saved").assertIsDisplayed()
    rule.onNodeWithTag("callsignField").performTextClearance()
    rule.onNodeWithTag("callsignField").performTextInput("Boss")
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
    assertEquals("Boss", c.settingsRepo.settings.value.userCallsign)
    rule.onNodeWithText("All changes saved").assertIsDisplayed()
  }

  @Test fun closingSettingsImmediatelyStillSaves() {
    val c = containerWith()
    c.settingsRepo.settings.value = onboarded
    launch(c)
    rule.onNodeWithTag("openSettings").performClick()
    rule.onNodeWithTag("apiKeyField").performTextInput("sk-or-v1-abc")
    // Leave right away, before the autosave delay elapses.
    rule.onNodeWithContentDescription("Back").performClick()
    rule.waitForIdle()
    assertEquals("sk-or-v1-abc", c.settingsRepo.settings.value.apiKey)
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
  }

  @Test fun togglesAndBudgetSave() {
    val c = containerWith()
    launch(c)
    rule.onNodeWithTag("openSettings").performClick()
    rule.onNodeWithText("Voice replies").performClick()
    rule.onNodeWithTag("settings").performScrollToNode(hasTestTag("budgetField"))
    rule.onNodeWithTag("budgetField").performTextInput("25000")
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
    assertFalse(c.settingsRepo.settings.value.voiceEnabled)
    assertEquals(25_000.0, c.settingsRepo.settings.value.monthlyBudget, 0.0)
    rule.onNodeWithContentDescription("Back").performClick()
    rule.onNodeWithTag("tab-FINANCE").performClick()
    rule.onNodeWithTag("budgetCard").assertIsDisplayed()
  }

  // ------------------------------------------------------------------ inbox

  @Test fun inboxSearchFiltersAndShowsNoResults() {
    launch(containerWith(item("b1", "Electricity bill"), item("d1", "Amazon parcel", IntelCategory.DELIVERY, Priority.HIGH)))
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithTag("inboxSearch").performTextInput("amazon")
    rule.onNodeWithText("Amazon parcel").assertIsDisplayed()
    rule.onAllNodesWithText("Electricity bill").assertCountEquals(0)
    rule.onNodeWithTag("inboxSearch").performTextClearance()
    rule.onNodeWithTag("inboxSearch").performTextInput("zzz")
    rule.onNodeWithText("No results", substring = true).assertIsDisplayed()
  }

  @Test fun doneButtonResolvesItem() {
    val c = containerWith(item("b1", "Electricity bill"))
    launch(c)
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithText("Done").performClick()
    rule.waitForIdle()
    assertEquals(ItemStatus.DONE, c.store.items.value.single().status)
    rule.onNodeWithText("Inbox zero").assertIsDisplayed()
  }

  @Test fun swipeRightMarksDone() {
    val c = containerWith(item("b1", "Electricity bill"))
    launch(c)
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithTag("item-b1").performTouchInput { swipeRight() }
    rule.waitForIdle()
    assertEquals(ItemStatus.DONE, c.store.items.value.single().status)
  }

  @Test fun tappingItemOpensDetailsSheetWithActions() {
    val c = containerWith(item("b1", "Electricity bill"))
    val vm = launch(c)
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithTag("item-b1").performClick()
    rule.waitForIdle()
    assertEquals("b1", vm.uiState.value.openItemId)
    rule.onNodeWithText("Mark done").assertIsDisplayed()
    rule.onNodeWithText("Mark done").performClick()
    rule.waitForIdle()
    assertEquals(ItemStatus.DONE, c.store.items.value.single().status)
    assertEquals(null, vm.uiState.value.openItemId)
  }

  @Test fun completedItemsCanBeRestored() {
    val c = containerWith(item("b1", "Electricity bill").copy(status = ItemStatus.DONE))
    launch(c)
    rule.onNodeWithTag("tab-INTEL").performClick()
    rule.onNodeWithText("Show completed").performClick()
    rule.onNodeWithText("Restore").performClick()
    rule.waitForIdle()
    assertEquals(ItemStatus.ACTIVE, c.store.items.value.single().status)
  }

  // ------------------------------------------------------------------ home + money

  @Test fun homeShowsUrgentItemsAndSeeAllGoesToInbox() {
    launch(containerWith(item("b1", "Electricity bill")))
    rule.onNodeWithText("Needs attention").assertIsDisplayed()
    rule.onNodeWithText("Electricity bill").assertIsDisplayed()
    rule.onNodeWithText("See all").performClick()
    rule.onNodeWithTag("inboxSearch").assertIsDisplayed()
  }

  @Test fun moneyShowsBillsAndTransactions() {
    val spend = item("f1", "Swiggy order", IntelCategory.FINANCE, Priority.NORMAL).copy(merchant = "Swiggy", dueDate = null)
    launch(containerWith(item("b1", "Electricity bill"), spend))
    rule.onNodeWithTag("tab-FINANCE").performClick()
    // Overview: bill due soon and the payment in Recent.
    rule.onNodeWithText("Due this week").assertIsDisplayed()
    rule.onNodeWithTag("screen-money").performScrollToNode(hasText("Swiggy"))
    rule.onNodeWithText("Swiggy").assertIsDisplayed()
    // Tapping the payment opens the shared details sheet.
    rule.onNodeWithText("Swiggy").performClick()
    rule.onNodeWithText("Swiggy order").assertIsDisplayed()
  }

  @Test fun moneyViewsSwitch() {
    val spend = item("f1", "Swiggy order", IntelCategory.FINANCE, Priority.NORMAL).copy(merchant = "Swiggy", dueDate = null)
    launch(containerWith(item("b1", "Electricity bill"), spend))
    rule.onNodeWithTag("tab-FINANCE").performClick()
    rule.onNodeWithText("Spending").performClick()
    rule.onNodeWithText("Food & dining").assertIsDisplayed()
    rule.onNodeWithText("Bills").performClick()
    rule.onNodeWithText("1 bill open").assertIsDisplayed()
  }

  // ------------------------------------------------------------------ ask

  @Test fun askSendsQuestionShowsReplyAndClears() {
    val c = containerWith()
    launch(c)
    rule.onNodeWithTag("tab-ASK").performClick()
    rule.onNode(hasSetTextAction() and hasText("Ask Jarvis anything…")).performTextInput("Any bills due?")
    rule.onNodeWithContentDescription("Send").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Reply to Any bills due?").assertIsDisplayed()
    rule.onNodeWithContentDescription("Clear chat").performClick()
    rule.onNodeWithText("How can I help, Tony?").assertIsDisplayed()
  }

  @Test fun askSuggestionChipSendsImmediately() {
    launch(containerWith())
    rule.onNodeWithTag("tab-ASK").performClick()
    rule.onNodeWithText("What did I spend today?").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Reply to What did I spend today?").assertIsDisplayed()
  }

  @Test fun askWithoutKeyOffersSettings() {
    launch(containerWith())
    rule.onNodeWithTag("tab-ASK").performClick()
    rule.onNodeWithText("Add key").performClick()
    rule.onNodeWithTag("settings").assertIsDisplayed()
  }

  // ------------------------------------------------------------------ errors

  @Test fun scanErrorShowsSnackbar() {
    val c = FakeContainer(
      settingsRepo = FakeSettingsRepo(onboarded),
      organizer = object : com.jarvis.hud.ui.FakeOrganizer() {
        override suspend fun organize(events: List<com.jarvis.hud.model.RawEvent>, settings: JarvisSettings) =
          throw IllegalStateException("Network unreachable")
      },
    )
    launch(c)
    rule.onNodeWithText("Network unreachable").assertIsDisplayed()
    rule.onNodeWithText("OK").performClick()
    rule.waitForIdle()
    rule.onAllNodesWithText("Network unreachable").assertCountEquals(0)
  }

  @Test fun refreshButtonRescans() {
    val c = containerWith()
    launch(c)
    val before = c.briefings.calls
    rule.onNodeWithContentDescription("Refresh").performClick()
    rule.waitForIdle()
    assertEquals(before + 1, c.briefings.calls)
  }
}
