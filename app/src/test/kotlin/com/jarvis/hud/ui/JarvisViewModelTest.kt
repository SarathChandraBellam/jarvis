package com.jarvis.hud.ui

import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.Priority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JarvisViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val now = 1_763_800_000_000L

  @Before fun setUp() = Dispatchers.setMain(dispatcher)
  @After fun tearDown() = Dispatchers.resetMain()

  private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

  private fun vm(c: FakeContainer) = JarvisViewModel(c) { now }

  @Test fun demoScanProducesItemsAndBriefing() = test {
    val c = FakeContainer()
    val vm = vm(c)
    advanceUntilIdle()
    val s = vm.uiState.value
    assertEquals(3, s.items.size)
    assertNotNull(s.briefing)
    assertNotNull(c.store.briefing)
    assertEquals("SYNC COMPLETE // 3 NEW ITEMS", s.syncStatusLine)
    assertFalse(s.isSyncing)
    assertEquals(1, s.calendarEntries.size)
    assertEquals(1, s.recentCalls.size)
    assertEquals(1, s.appUsageStats.size)
    assertEquals(0, c.phoneData.smsCalls)
    assertEquals(c.permissions.status, s.permissions)
    assertEquals(50, s.telemetry.batteryPercent)
  }

  @Test fun disabledSourcesAreFiltered() = test {
    val c = FakeContainer(settingsRepo = FakeSettingsRepo(JarvisSettings(enabledSources = setOf(DataSource.SMS))))
    val vm = vm(c)
    advanceUntilIdle()
    assertEquals(2, vm.uiState.value.items.size)
    assertTrue(vm.uiState.value.calendarEntries.isEmpty())
  }

  @Test fun processedHashesAreSkipped() = test {
    val c = FakeContainer()
    c.store.processed += "hash-d1"
    val vm = vm(c)
    advanceUntilIdle()
    assertEquals(listOf("d2", "d3"), c.organizer.received.map { it.id })
    assertEquals("SYNC COMPLETE // 2 NEW ITEMS", vm.uiState.value.syncStatusLine)

    // Second scan: everything already processed -> organizer not called again.
    vm.onScanAndOrganize()
    advanceUntilIdle()
    assertEquals(1, c.organizer.calls)
    assertEquals("SYNC COMPLETE // 0 NEW ITEMS", vm.uiState.value.syncStatusLine)
  }

  @Test fun dismissedItemsHiddenAndSorted() = test {
    val c = FakeContainer(demoData = FakeDemoData().apply { events = emptyList() })
    fun item(id: String, p: Priority, st: ItemStatus, ts: Long) =
      OrganizedItem(id, null, IntelCategory.ACTION, id, "", priority = p, status = st, timestamp = ts, source = DataSource.SMS)
    c.store.items.value = listOf(
      item("doneCrit", Priority.CRITICAL, ItemStatus.DONE, 5),
      item("low", Priority.LOW, ItemStatus.ACTIVE, 9),
      item("highOld", Priority.HIGH, ItemStatus.ACTIVE, 1),
      item("highNew", Priority.HIGH, ItemStatus.ACTIVE, 2),
      item("gone", Priority.CRITICAL, ItemStatus.ACTIVE, 3),
    )
    val vm = vm(c)
    advanceUntilIdle()
    vm.onMarkItemStatus("gone", ItemStatus.DISMISSED)
    advanceUntilIdle()
    assertEquals(listOf("highNew", "highOld", "low", "doneCrit"), vm.uiState.value.items.map { it.id })
  }

  @Test fun chatAppendsUserThenAssistant() = test {
    val c = FakeContainer()
    val gate = CompletableDeferred<Unit>()
    c.assistant.gate = gate
    val vm = vm(c)
    advanceUntilIdle()
    vm.onSendChatMessage("What's due?")
    runCurrent()
    var s = vm.uiState.value
    assertEquals(1, s.chatMessages.size)
    assertTrue(s.chatMessages[0].isFromUser)
    assertTrue(s.isAskingAi)
    gate.complete(Unit)
    advanceUntilIdle()
    s = vm.uiState.value
    assertEquals(2, s.chatMessages.size)
    assertEquals("Reply to What's due?", s.chatMessages[1].content)
    assertFalse(s.chatMessages[1].isFromUser)
    assertFalse(s.isAskingAi)
    assertEquals(listOf("Reply to What's due?"), c.speaker.spoken)
  }

  @Test fun chatReplyNotSpokenWhenVoiceDisabled() = test {
    val c = FakeContainer(settingsRepo = FakeSettingsRepo(JarvisSettings(voiceEnabled = false)))
    val vm = vm(c)
    advanceUntilIdle()
    vm.onSendChatMessage("hi")
    advanceUntilIdle()
    assertEquals(2, vm.uiState.value.chatMessages.size)
    assertTrue(c.speaker.spoken.isEmpty())
  }

  @Test fun missingApiKeyFailsConnectionTest() = test {
    val c = FakeContainer()
    val vm = vm(c)
    advanceUntilIdle()
    vm.onTestOpenRouterConnection()
    val r = vm.uiState.value.connectionTestResult
    assertNotNull(r)
    assertFalse(r!!.success)
    assertEquals("NO API KEY CONFIGURED", r.message)
    assertEquals(0, c.openRouter.testCalls)
  }

  @Test fun fetchModelsSortsFreeFirstThenName() = test {
    val c = FakeContainer(settingsRepo = FakeSettingsRepo(JarvisSettings(apiKey = "sk-test")))
    c.openRouter.models = listOf(
      OpenRouterModelInfo("b", "Beta"),
      OpenRouterModelInfo("z", "Zeta", isFree = true),
      OpenRouterModelInfo("a", "Alpha"),
      OpenRouterModelInfo("g", "Gamma", isFree = true),
    )
    val vm = vm(c)
    advanceUntilIdle()
    vm.onFetchOpenRouterModels()
    assertTrue(vm.uiState.value.isLoadingModels)
    advanceUntilIdle()
    assertEquals(listOf("g", "z", "a", "b"), vm.uiState.value.availableModels.map { it.id })
    assertFalse(vm.uiState.value.isLoadingModels)
  }

  @Test fun concurrentScansAreGuarded() = test {
    val c = FakeContainer()
    val gate = CompletableDeferred<Unit>()
    c.organizer.gate = gate
    val vm = vm(c) // init kicks off a scan
    runCurrent()
    assertTrue(vm.uiState.value.isSyncing)
    assertEquals("ORGANIZING INTEL…", vm.uiState.value.syncStatusLine)
    vm.onScanAndOrganize()
    vm.onScanAndOrganize()
    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(1, c.organizer.calls)
    assertEquals(1, c.briefings.calls)
    assertFalse(vm.uiState.value.isSyncing)
  }

  @Test fun liveNotificationsAreOrganizedOnce() = test {
    val c = FakeContainer(demoData = FakeDemoData().apply { events = emptyList() })
    val vm = vm(c)
    advanceUntilIdle()
    val ev = rawEvent("live", DataSource.NOTIFICATION)
    c.notifications.flow.emit(ev)
    advanceUntilIdle()
    c.notifications.flow.emit(ev)
    advanceUntilIdle()
    assertEquals(1, c.organizer.calls)
    assertEquals(listOf("item-live"), vm.uiState.value.items.map { it.id })
  }

  @Test fun simulatedAlertUpdatesStatusLine() = test {
    val c = FakeContainer()
    val vm = vm(c)
    advanceUntilIdle()
    vm.onInjectSimulatedAlert()
    advanceUntilIdle()
    assertEquals("INCOMING ALERT // Title alert-$now", vm.uiState.value.syncStatusLine)
    assertEquals(4, vm.uiState.value.items.size)
  }

  @Test fun clearCacheRescans() = test {
    val c = FakeContainer()
    val vm = vm(c)
    advanceUntilIdle()
    vm.onClearAiCache()
    advanceUntilIdle()
    assertEquals(1, c.store.clears)
    assertEquals(2, c.organizer.calls)
    assertEquals(3, vm.uiState.value.items.size)
    assertNotNull(vm.uiState.value.briefing)
  }

  @Test fun demoToggleTriggersRealScan() = test {
    val c = FakeContainer()
    c.phoneData.sms = listOf(rawEvent("real1"))
    val vm = vm(c)
    advanceUntilIdle()
    vm.onUpdateSettings(c.settingsRepo.settings.value.copy(demoModeEnabled = false))
    advanceUntilIdle()
    assertEquals(1, c.phoneData.smsCalls)
    assertFalse(vm.uiState.value.settings.demoModeEnabled)
    assertTrue(vm.uiState.value.items.any { it.id == "item-real1" })
  }

  @Test fun toggleSpeakBriefingSpeaksThenStops() = test {
    val c = FakeContainer()
    val vm = vm(c)
    advanceUntilIdle()
    vm.onToggleSpeakBriefing()
    assertEquals(listOf("Good morning. 3 items"), c.speaker.spoken)
    advanceUntilIdle()
    assertTrue(vm.uiState.value.isSpeaking)
    vm.onToggleSpeakBriefing()
    assertEquals(1, c.speaker.stops)
  }

  @Test fun plainStateUpdates() = test {
    val vm = vm(FakeContainer())
    advanceUntilIdle()
    vm.onSelectTab(HudTab.FINANCE)
    vm.onOpenSettings()
    assertEquals(HudTab.FINANCE, vm.uiState.value.selectedTab)
    assertTrue(vm.uiState.value.showSettings)
    vm.onCloseSettings()
    assertFalse(vm.uiState.value.showSettings)
  }

  @Test fun scanErrorsSurfaceAsLastError() = test {
    val c = FakeContainer(organizer = object : FakeOrganizer() {
      override suspend fun organize(events: List<com.jarvis.hud.model.RawEvent>, settings: JarvisSettings) =
        throw IllegalStateException("uplink down")
    })
    val vm = vm(c)
    advanceUntilIdle()
    assertEquals("uplink down", vm.uiState.value.lastError)
    assertFalse(vm.uiState.value.isSyncing)
    vm.onDismissError()
    assertEquals(null, vm.uiState.value.lastError)
  }
}
