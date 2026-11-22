package com.jarvis.hud.ui

import com.jarvis.hud.contracts.AskAssistant
import com.jarvis.hud.contracts.BriefingGenerator
import com.jarvis.hud.contracts.DemoDataProvider
import com.jarvis.hud.contracts.IntelOrganizer
import com.jarvis.hud.contracts.IntelStore
import com.jarvis.hud.contracts.JarvisContainer
import com.jarvis.hud.contracts.NotificationFeed
import com.jarvis.hud.contracts.OpenRouterClient
import com.jarvis.hud.contracts.PermissionChecker
import com.jarvis.hud.contracts.PhoneDataSource
import com.jarvis.hud.contracts.SettingsRepository
import com.jarvis.hud.contracts.Speaker
import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.ChatMessage
import com.jarvis.hud.model.ConnectionTestResult
import com.jarvis.hud.model.DailyBriefing
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.DeviceTelemetry
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.PermissionStatus
import com.jarvis.hud.model.RawEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun rawEvent(id: String, source: DataSource = DataSource.SMS, ts: Long = 1_000L) = RawEvent(
  id = id,
  source = source,
  sender = "BANK",
  title = "Title $id",
  body = "Body $id",
  timestamp = ts,
  contentHash = "hash-$id",
)

class FakePhoneData : PhoneDataSource {
  var sms: List<RawEvent> = emptyList()
  var smsCalls = 0
  override suspend fun readSms(sinceMillis: Long, limit: Int): List<RawEvent> { smsCalls++; return sms }
  override suspend fun readCalendar(fromMillis: Long, toMillis: Long): List<CalendarEntry> = emptyList()
  override suspend fun readRecentCalls(limit: Int): List<CallSummary> = emptyList()
  override suspend fun readAppUsage(sinceMillis: Long, top: Int): List<AppUsageStat> = emptyList()
  override fun readTelemetry() = DeviceTelemetry(50, true, 10f, 100f)
}

class FakeNotifications : NotificationFeed {
  val flow = MutableSharedFlow<RawEvent>(extraBufferCapacity = 16)
  var buffered: List<RawEvent> = emptyList()
  override val events = flow
  override fun snapshot(): List<RawEvent> = buffered
}

class FakePermissions : PermissionChecker {
  var status = PermissionStatus(true, false, true, false, false)
  override fun current() = status
}

class FakeDemoData : DemoDataProvider {
  var events: List<RawEvent> = listOf(
    rawEvent("d1", DataSource.SMS),
    rawEvent("d2", DataSource.NOTIFICATION),
    rawEvent("d3", DataSource.SMS),
  )
  override fun rawEvents(nowMillis: Long) = events
  override fun calendar(nowMillis: Long) = listOf(CalendarEntry("c1", "Standup", nowMillis, nowMillis + 1, null))
  override fun calls(nowMillis: Long) = listOf(CallSummary("k1", "Mom", "INCOMING", 60, nowMillis))
  override fun appUsage() = listOf(AppUsageStat("com.x", "X", 42))
  override fun simulatedAlert(nowMillis: Long) = rawEvent("alert-$nowMillis", DataSource.NOTIFICATION, nowMillis)
}

class FakeSettingsRepo(initial: JarvisSettings = JarvisSettings()) : SettingsRepository {
  override val settings = MutableStateFlow(initial)
  override suspend fun update(settings: JarvisSettings) { this.settings.value = settings }
}

class FakeOpenRouter : OpenRouterClient {
  var models: List<OpenRouterModelInfo> = emptyList()
  var testCalls = 0
  override suspend fun chat(apiKey: String, modelId: String, systemPrompt: String, messages: List<Pair<String, String>>) =
    Result.success("ok")
  override suspend fun listModels(apiKey: String) = Result.success(models)
  override suspend fun testConnection(apiKey: String, modelId: String): ConnectionTestResult {
    testCalls++
    return ConnectionTestResult(true, "UPLINK OK", 12)
  }
}

open class FakeOrganizer : IntelOrganizer {
  var calls = 0
  val received = mutableListOf<RawEvent>()
  /** When set, organize() suspends until completed (for concurrency tests). */
  var gate: CompletableDeferred<Unit>? = null
  override suspend fun organize(events: List<RawEvent>, settings: JarvisSettings): List<OrganizedItem> {
    calls++
    gate?.await()
    received += events
    return events.map {
      OrganizedItem(
        id = "item-${it.id}",
        rawEventId = it.id,
        category = IntelCategory.MESSAGE,
        title = it.title,
        summary = it.body,
        timestamp = it.timestamp,
        source = it.source,
      )
    }
  }
}

class FakeBriefings : BriefingGenerator {
  var calls = 0
  override suspend fun generate(
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    telemetry: DeviceTelemetry,
    settings: JarvisSettings,
  ): DailyBriefing {
    calls++
    return DailyBriefing("2026-11-22", "Good morning", "${items.size} items", emptyList(), "local", 0L)
  }
}

class FakeAssistant : AskAssistant {
  var gate: CompletableDeferred<Unit>? = null
  override suspend fun answer(
    prompt: String,
    history: List<ChatMessage>,
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    settings: JarvisSettings,
  ): ChatMessage {
    gate?.await()
    return ChatMessage("a-${history.size}", false, "Reply to $prompt", 0L, "local")
  }
}

class FakeStore : IntelStore {
  override val items = MutableStateFlow<List<OrganizedItem>>(emptyList())
  val processed = mutableSetOf<String>()
  var briefing: DailyBriefing? = null
  var clears = 0
  override suspend fun upsert(items: List<OrganizedItem>) {
    val byId = this.items.value.associateBy { it.id }.toMutableMap()
    items.forEach { byId[it.id] = it }
    this.items.value = byId.values.toList()
  }
  override suspend fun setStatus(itemId: String, status: ItemStatus) {
    items.value = items.value.map { if (it.id == itemId) it.copy(status = status) else it }
  }
  override suspend fun isProcessed(contentHash: String) = contentHash in processed
  override suspend fun markProcessed(hashes: Collection<String>) { processed += hashes }
  override suspend fun saveBriefing(briefing: DailyBriefing) { this.briefing = briefing }
  override suspend fun loadBriefing(dateKey: String) = briefing
  override suspend fun clear() { clears++; items.value = emptyList(); processed.clear(); briefing = null }
}

class FakeSpeaker : Speaker {
  override val isSpeaking = MutableStateFlow(false)
  val spoken = mutableListOf<String>()
  var stops = 0
  override fun speak(text: String) { spoken += text; isSpeaking.value = true }
  override fun stop() { stops++; isSpeaking.value = false }
  override fun shutdown() = Unit
}

class FakeContainer(
  override val phoneData: FakePhoneData = FakePhoneData(),
  override val notifications: FakeNotifications = FakeNotifications(),
  override val permissions: FakePermissions = FakePermissions(),
  override val demoData: FakeDemoData = FakeDemoData(),
  override val settingsRepo: FakeSettingsRepo = FakeSettingsRepo(),
  override val openRouter: FakeOpenRouter = FakeOpenRouter(),
  override val organizer: FakeOrganizer = FakeOrganizer(),
  override val briefings: FakeBriefings = FakeBriefings(),
  override val assistant: FakeAssistant = FakeAssistant(),
  override val store: FakeStore = FakeStore(),
  override val speaker: FakeSpeaker = FakeSpeaker(),
) : JarvisContainer
