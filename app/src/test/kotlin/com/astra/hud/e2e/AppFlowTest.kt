package com.astra.hud.e2e

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
import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.Priority
import com.astra.hud.model.UserGender
import com.astra.hud.ui.FakeContainer
import com.astra.hud.ui.FakeSettingsRepo
import com.astra.hud.ui.FakeStore
import com.astra.hud.ui.AstraApp
import com.astra.hud.ui.AstraViewModel
import com.astra.hud.ui.emptyContainer
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
 * End-to-end UI flows: the real [AstraApp] + real [AstraViewModel], driven by taps and typing,
 * with only the phone data / AI / storage behind fakes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val onboarded = AstraSettings(onboardingDone = true, userCallsign = "Alex")

  private fun launch(c: FakeContainer): AstraViewModel {
    lateinit var vm: AstraViewModel
    rule.setContent {
      vm = remember { AstraViewModel(c) }
      val state by vm.uiState.collectAsStateWithLifecycle()
      AstraApp(state, vm)
    }
    rule.waitForIdle()
    return vm
  }

  private fun item(id: String, title: String, cat: IntelCategory = IntelCategory.BILL, p: Priority = Priority.CRITICAL) =
    OrganizedItem(id, null, cat, title, "Summary of $title", amount = 499.0, dueDate = "25 Nov",
      priority = p, timestamp = System.currentTimeMillis() - 60_000, source = DataSource.SMS)

  private fun containerWith(vararg items: OrganizedItem, settings: AstraSettings = onboarded) =
    FakeContainer(
      phoneData = emptyContainer().phoneData,
      notifications = emptyContainer().notifications,
      settingsRepo = FakeSettingsRepo(settings),
      store = FakeStore().apply { this.items.value = items.toList() },
    )

  // ------------------------------------------------------------------ onboarding

  @Test fun firstLaunchShowsWelcomeAndSavesName() {
    val c = containerWith(settings = AstraSettings())
    launch(c)
    rule.onNodeWithText("Meet ASTRA").assertIsDisplayed()
    rule.onNodeWithTag("onboardingName").performTextInput("Maya")
    rule.onNodeWithTag("onboardingStart").performScrollTo().performClick()
    rule.waitForIdle()
    assertTrue(c.settingsRepo.settings.value.onboardingDone)
    assertEquals("Maya", c.settingsRepo.settings.value.userCallsign)
    rule.onNodeWithText("Maya", substring = true).assertIsDisplayed()
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
  }

  @Test fun skipOnboardingKeepsDefaultName() {
    val c = containerWith(settings = AstraSettings())
    c.permissions.status = com.astra.hud.model.PermissionStatus(false, false, false, false, false)
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
    rule.onNodeWithText("How can I help, Alex?").assertIsDisplayed()
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

  @Test fun togglesAndProactiveSettingsSave() {
    val c = containerWith()
    launch(c)
    rule.onNodeWithTag("openSettings").performClick()
    rule.onNodeWithTag("settings").performScrollToNode(hasText("Voice replies"))
    rule.onNodeWithText("Voice replies").performClick()
    rule.onNodeWithTag("settings").performScrollToNode(hasText("1 day before"))
    rule.onNodeWithText("1 day before").performClick()
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
    assertFalse(c.settingsRepo.settings.value.voiceEnabled)
    assertEquals(1, c.settingsRepo.settings.value.renewalLeadDays)
    rule.onNodeWithContentDescription("Back").performClick()
    rule.onNodeWithTag("screen-home").assertIsDisplayed()
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
    rule.onNodeWithTag("screen-home").performScrollToNode(hasText("Needs attention"))
    rule.onNodeWithText("Needs attention").assertIsDisplayed()
    rule.onNodeWithText("Electricity bill").assertIsDisplayed()
    rule.onNodeWithText("See all").performClick()
    rule.onNodeWithTag("inboxSearch").assertIsDisplayed()
  }

  @Test fun subsShowsSubscriptionsAndBills() {
    val netflix = item("s1", "Netflix autopay", IntelCategory.FINANCE, Priority.NORMAL).copy(merchant = "Netflix", dueDate = null)
    launch(containerWith(item("b1", "Electricity bill"), netflix))
    rule.onNodeWithTag("tab-FINANCE").performClick()
    rule.onNodeWithTag("screen-money").performScrollToNode(hasText("Due this week"))
    rule.onNodeWithText("Due this week").assertIsDisplayed()
    rule.onNodeWithText("Electricity bill").assertIsDisplayed()
  }

  @Test fun subsViewsSwitchAndPersistSelection() {
    val c = containerWith(
      item("b1", "Electricity bill"),
      item("s1", "Netflix autopay", IntelCategory.FINANCE, Priority.NORMAL).copy(merchant = "Netflix", dueDate = null),
    )
    launch(c)
    rule.onNodeWithTag("tab-FINANCE").performClick()
    assertEquals("FINANCE", c.settingsRepo.settings.value.selectedTabName)
    rule.onNodeWithText("Renewals").performClick()
    rule.waitForIdle()
    assertEquals(1, c.settingsRepo.settings.value.subsViewIndex)
    rule.onNodeWithText("All tracked subscriptions").assertIsDisplayed()
    rule.onNodeWithText("Bills").performClick()
    rule.waitForIdle()
    assertEquals(2, c.settingsRepo.settings.value.subsViewIndex)
    rule.onNodeWithText("1 bill open").assertIsDisplayed()
  }

  // ------------------------------------------------------------------ ask

  @Test fun askSendsQuestionShowsReplyAndClears() {
    val c = containerWith()
    launch(c)
    rule.onNodeWithTag("tab-ASK").performClick()
    rule.onNode(hasSetTextAction() and hasText("Ask ASTRA anything…")).performTextInput("Any bills due?")
    rule.onNodeWithContentDescription("Send").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Reply to Any bills due?").assertIsDisplayed()
    rule.onNodeWithContentDescription("Clear chat").performClick()
    rule.onNodeWithText("How can I help, Alex?").assertIsDisplayed()
  }

  @Test fun askSuggestionChipSendsImmediately() {
    launch(containerWith())
    rule.onNodeWithTag("tab-ASK").performClick()
    rule.onNodeWithText("What subscriptions renew soon?").performClick()
    rule.waitForIdle()
    rule.onNodeWithText("Reply to What subscriptions renew soon?").assertIsDisplayed()
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
      organizer = object : com.astra.hud.ui.FakeOrganizer() {
        override suspend fun organize(events: List<com.astra.hud.model.RawEvent>, settings: AstraSettings) =
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

  @Test fun maleProfileHidesPeriodTrackerAndFemaleShowsIt() {
    val c = containerWith(settings = AstraSettings())
    launch(c)
    rule.onNodeWithTag("onboardingName").performTextInput("Arjun")
    rule.onNodeWithTag("onboardingGender-MALE").performScrollTo().performClick()
    rule.onNodeWithTag("onboardingStart").performScrollTo().performClick()
    rule.waitForIdle()

    assertEquals(UserGender.MALE, c.settingsRepo.settings.value.userGender)
    assertFalse(c.settingsRepo.settings.value.isPeriodTrackerActive)
    rule.onAllNodesWithText("Cycle").assertCountEquals(0)
    rule.onAllNodesWithText("Log period").assertCountEquals(0)
    rule.onNodeWithText("Log sleep").assertIsDisplayed()

    // Switch to Female in Settings -> Cycle & period tracker appear
    rule.onNodeWithTag("openSettings").performClick()
    rule.onNodeWithTag("settingsGender-FEMALE").performScrollTo().performClick()
    rule.mainClock.advanceTimeBy(1_000)
    rule.waitForIdle()
    rule.onNodeWithContentDescription("Back").performClick()
    rule.waitForIdle()

    assertEquals(UserGender.FEMALE, c.settingsRepo.settings.value.userGender)
    assertTrue(c.settingsRepo.settings.value.isPeriodTrackerActive)
    rule.onNodeWithText("Cycle").assertIsDisplayed()
    rule.onNodeWithText("Log period").assertIsDisplayed()
  }
}
