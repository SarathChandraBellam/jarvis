package com.astra.hud.ui

import com.astra.hud.contracts.AskAssistant
import com.astra.hud.contracts.BriefingGenerator
import com.astra.hud.contracts.IntelOrganizer
import com.astra.hud.contracts.IntelStore
import com.astra.hud.contracts.AstraContainer
import com.astra.hud.contracts.NotificationFeed
import com.astra.hud.contracts.OpenRouterClient
import com.astra.hud.contracts.PermissionChecker
import com.astra.hud.contracts.PhoneDataSource
import com.astra.hud.contracts.PlannerStore
import com.astra.hud.contracts.ProactiveNotifier
import com.astra.hud.contracts.SettingsRepository
import com.astra.hud.contracts.Speaker
import com.astra.hud.contracts.StepSensorSource
import com.astra.hud.model.AppUsageStat
import com.astra.hud.model.CalendarEntry
import com.astra.hud.model.CallSummary
import com.astra.hud.model.ChatMessage
import com.astra.hud.model.ConnectionTestResult
import com.astra.hud.model.CycleState
import com.astra.hud.model.DailyBriefing
import com.astra.hud.model.DataSource
import com.astra.hud.model.DeviceTelemetry
import com.astra.hud.model.HabitItem
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.MealLog
import com.astra.hud.model.OpenRouterModelInfo
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.PermissionStatus
import com.astra.hud.model.PlannerReminder
import com.astra.hud.model.ProactiveAlert
import com.astra.hud.model.RawEvent
import com.astra.hud.model.SleepLog
import com.astra.hud.model.TimerMode
import com.astra.hud.model.TimerSession
import com.astra.hud.model.TrackedSubscription
import com.astra.hud.model.WorkoutLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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
  var sms: List<RawEvent> = listOf(rawEvent("d1", DataSource.SMS), rawEvent("d3", DataSource.SMS))
  var calendar = listOf(CalendarEntry("c1", "Standup", 1L, 2L, null))
  var calls = listOf(CallSummary("k1", "Mom", "INCOMING", 60, 1L))
  var usage = listOf(AppUsageStat("com.x", "X", 42))
  var smsCalls = 0
  override suspend fun readSms(sinceMillis: Long, limit: Int): List<RawEvent> { smsCalls++; return sms }
  override suspend fun readCalendar(fromMillis: Long, toMillis: Long): List<CalendarEntry> = calendar
  override suspend fun readRecentCalls(limit: Int): List<CallSummary> = calls
  override suspend fun readAppUsage(sinceMillis: Long, top: Int): List<AppUsageStat> = usage
  override fun readTelemetry() = DeviceTelemetry(50, true, 10f, 100f)
}

class FakeStepSensor : StepSensorSource {
  var steps: Int? = 4200
  override suspend fun readTodaySteps(): Int? = steps
}

class FakeNotifications : NotificationFeed {
  val flow = MutableSharedFlow<RawEvent>(extraBufferCapacity = 16)
  var buffered: List<RawEvent> = listOf(rawEvent("d2", DataSource.NOTIFICATION))
  override val events = flow
  override fun snapshot(): List<RawEvent> = buffered
}

class FakePermissions : PermissionChecker {
  var status = PermissionStatus(true, false, true, false, false, true, true)
  override fun current() = status
}

class FakeSettingsRepo(initial: AstraSettings = AstraSettings()) : SettingsRepository {
  override val settings = MutableStateFlow(initial)
  override suspend fun update(settings: AstraSettings) { this.settings.value = settings }
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
  var gate: CompletableDeferred<Unit>? = null
  override suspend fun organize(events: List<RawEvent>, settings: AstraSettings): List<OrganizedItem> {
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
    settings: AstraSettings,
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
    settings: AstraSettings,
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

class FakePlannerStore : PlannerStore {
  override val reminders = MutableStateFlow<List<PlannerReminder>>(emptyList())
  override val customSubscriptions = MutableStateFlow<List<TrackedSubscription>>(emptyList())
  override val pausedSubscriptionIds = MutableStateFlow<Set<String>>(emptySet())
  override val deletedSubscriptionIds = MutableStateFlow<Set<String>>(emptySet())
  override val dismissedAlertKeys = MutableStateFlow<Set<String>>(emptySet())
  override val notifiedAlertKeys = MutableStateFlow<Set<String>>(emptySet())
  override val meals = MutableStateFlow<List<MealLog>>(emptyList())
  override val sleepLogs = MutableStateFlow<List<SleepLog>>(emptyList())
  override val workouts = MutableStateFlow<List<WorkoutLog>>(emptyList())
  override val habits = MutableStateFlow<List<HabitItem>>(emptyList())
  override val stepsByDate = MutableStateFlow<Map<String, Int>>(emptyMap())
  override val timerSession = MutableStateFlow(TimerSession())
  override val cycleState = MutableStateFlow(CycleState())

  override suspend fun addReminder(reminder: PlannerReminder) { reminders.value = reminders.value + reminder }
  override suspend fun toggleReminder(id: String) {
    reminders.value = reminders.value.map { if (it.id == id) it.copy(isDone = !it.isDone) else it }
  }
  override suspend fun deleteReminder(id: String) { reminders.value = reminders.value.filterNot { it.id == id } }
  override suspend fun addSubscription(sub: TrackedSubscription) { customSubscriptions.value = customSubscriptions.value + sub }
  override suspend fun toggleSubscriptionPaused(id: String) {
    pausedSubscriptionIds.value = if (id in pausedSubscriptionIds.value) pausedSubscriptionIds.value - id else pausedSubscriptionIds.value + id
  }
  override suspend fun deleteSubscription(id: String) {
    customSubscriptions.value = customSubscriptions.value.filterNot { it.id == id }
    deletedSubscriptionIds.value = deletedSubscriptionIds.value + id
  }
  override suspend fun dismissAlert(dedupKey: String) { dismissedAlertKeys.value = dismissedAlertKeys.value + dedupKey }
  override suspend fun markNotified(dedupKeys: Collection<String>) { notifiedAlertKeys.value = notifiedAlertKeys.value + dedupKeys }
  override suspend fun addMeal(meal: MealLog) { meals.value = meals.value + meal }
  override suspend fun deleteMeal(id: String) { meals.value = meals.value.filterNot { it.id == id } }
  override suspend fun saveSleep(sleep: SleepLog) { sleepLogs.value = sleepLogs.value.filterNot { it.dateKey == sleep.dateKey } + sleep }
  override suspend fun addWorkout(workout: WorkoutLog) { workouts.value = workouts.value + workout }
  override suspend fun deleteWorkout(id: String) { workouts.value = workouts.value.filterNot { it.id == id } }
  override suspend fun toggleHabit(habitId: String, dateKey: String) {
    habits.value = habits.value.map { h ->
      if (h.id != habitId) h else h.copy(completedDateKeys = if (dateKey in h.completedDateKeys) h.completedDateKeys - dateKey else h.completedDateKeys + dateKey)
    }
  }
  override suspend fun addHabit(habit: HabitItem) { habits.value = habits.value + habit }
  override suspend fun deleteHabit(habitId: String) { habits.value = habits.value.filterNot { it.id == habitId } }
  override suspend fun recordSteps(dateKey: String, steps: Int) { stepsByDate.value = stepsByDate.value + (dateKey to steps) }
  override suspend fun startTimer(mode: TimerMode, targetMinutes: Int, nowMillis: Long, dateKey: String) {
    timerSession.value = timerSession.value.copy(isRunning = true, mode = mode, startedAtMillis = nowMillis, targetMinutes = targetMinutes, lastDateKey = dateKey)
  }
  override suspend fun stopTimer(nowMillis: Long, dateKey: String) {
    timerSession.value = timerSession.value.copy(isRunning = false, lastDateKey = dateKey)
  }
  override suspend fun togglePeriodDay(dateKey: String) {
    val cur = cycleState.value
    val next = if (dateKey in cur.periodStartDates) cur.periodStartDates - dateKey else (cur.periodStartDates + dateKey).sorted()
    cycleState.value = cur.copy(periodStartDates = next)
  }
  override suspend fun setCycleLength(days: Int) {
    cycleState.value = cycleState.value.copy(cycleLengthDays = days.coerceIn(21, 45))
  }
}

class FakeNotifier : ProactiveNotifier {
  val sent = mutableListOf<ProactiveAlert>()
  var testCalls = 0
  override fun notifyAlerts(alerts: List<ProactiveAlert>, settings: AstraSettings, alreadyNotified: Set<String>): List<String> {
    val fresh = alerts.filter { it.dedupKey !in alreadyNotified }
    sent += fresh
    return fresh.map { it.dedupKey }
  }
  override fun sendTestNotification(callsign: String): Boolean {
    testCalls++
    return true
  }
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
  override val stepSensor: FakeStepSensor = FakeStepSensor(),
  override val notifications: FakeNotifications = FakeNotifications(),
  override val permissions: FakePermissions = FakePermissions(),
  override val settingsRepo: FakeSettingsRepo = FakeSettingsRepo(),
  override val openRouter: FakeOpenRouter = FakeOpenRouter(),
  override val organizer: FakeOrganizer = FakeOrganizer(),
  override val briefings: FakeBriefings = FakeBriefings(),
  override val assistant: FakeAssistant = FakeAssistant(),
  override val store: FakeStore = FakeStore(),
  override val plannerStore: FakePlannerStore = FakePlannerStore(),
  override val notifier: FakeNotifier = FakeNotifier(),
  override val speaker: FakeSpeaker = FakeSpeaker(),
) : AstraContainer

fun emptyContainer() = FakeContainer(
  phoneData = FakePhoneData().apply { sms = emptyList() },
  notifications = FakeNotifications().apply { buffered = emptyList() },
)
