package com.jarvis.hud.contracts

import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.ChatMessage
import com.jarvis.hud.model.ConnectionTestResult
import com.jarvis.hud.model.CycleState
import com.jarvis.hud.model.DailyBriefing
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.DeviceTelemetry
import com.jarvis.hud.model.HabitItem
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.MealLog
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.PermissionStatus
import com.jarvis.hud.model.PlannerReminder
import com.jarvis.hud.model.ProactiveAlert
import com.jarvis.hud.model.RawEvent
import com.jarvis.hud.model.SleepLog
import com.jarvis.hud.model.TimerMode
import com.jarvis.hud.model.TimerSession
import com.jarvis.hud.model.TrackedSubscription
import com.jarvis.hud.model.WorkoutLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PhoneDataSource {
  suspend fun readSms(sinceMillis: Long, limit: Int = 200): List<RawEvent>
  suspend fun readCalendar(fromMillis: Long, toMillis: Long): List<CalendarEntry>
  suspend fun readRecentCalls(limit: Int = 30): List<CallSummary>
  suspend fun readAppUsage(sinceMillis: Long, top: Int = 10): List<AppUsageStat>
  fun readTelemetry(): DeviceTelemetry
}

/** Optional hardware step-counter reader (falls back cleanly when no sensor/permission). */
interface StepSensorSource {
  suspend fun readTodaySteps(): Int?
}

interface NotificationFeed {
  val events: Flow<RawEvent>
  fun snapshot(): List<RawEvent>
}

interface PermissionChecker {
  fun current(): PermissionStatus
}

interface SettingsRepository {
  val settings: StateFlow<JarvisSettings>
  suspend fun update(settings: JarvisSettings)
}

interface OpenRouterClient {
  suspend fun chat(apiKey: String, modelId: String, systemPrompt: String, messages: List<Pair<String, String>>): Result<String>
  suspend fun listModels(apiKey: String): Result<List<OpenRouterModelInfo>>
  suspend fun testConnection(apiKey: String, modelId: String): ConnectionTestResult
}

interface Redactor {
  fun redact(text: String): String
}

interface IntelOrganizer {
  suspend fun organize(events: List<RawEvent>, settings: JarvisSettings): List<OrganizedItem>
}

interface BriefingGenerator {
  suspend fun generate(
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    telemetry: DeviceTelemetry,
    settings: JarvisSettings,
  ): DailyBriefing
}

interface AskAssistant {
  suspend fun answer(
    prompt: String,
    history: List<ChatMessage>,
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    settings: JarvisSettings,
  ): ChatMessage
}

interface IntelStore {
  val items: StateFlow<List<OrganizedItem>>
  suspend fun upsert(items: List<OrganizedItem>)
  suspend fun setStatus(itemId: String, status: ItemStatus)
  suspend fun isProcessed(contentHash: String): Boolean
  suspend fun markProcessed(hashes: Collection<String>)
  suspend fun saveBriefing(briefing: DailyBriefing)
  suspend fun loadBriefing(dateKey: String): DailyBriefing?
  suspend fun clear()
}

/** Persists reminders, custom/paused subscriptions, proactive alert state, and personal wellness logs. */
interface PlannerStore {
  val reminders: StateFlow<List<PlannerReminder>>
  val customSubscriptions: StateFlow<List<TrackedSubscription>>
  val pausedSubscriptionIds: StateFlow<Set<String>>
  val deletedSubscriptionIds: StateFlow<Set<String>>
  val dismissedAlertKeys: StateFlow<Set<String>>
  val notifiedAlertKeys: StateFlow<Set<String>>

  val meals: StateFlow<List<MealLog>>
  val sleepLogs: StateFlow<List<SleepLog>>
  val workouts: StateFlow<List<WorkoutLog>>
  val habits: StateFlow<List<HabitItem>>
  val stepsByDate: StateFlow<Map<String, Int>>
  val timerSession: StateFlow<TimerSession>
  val cycleState: StateFlow<CycleState>

  suspend fun addReminder(reminder: PlannerReminder)
  suspend fun toggleReminder(id: String)
  suspend fun deleteReminder(id: String)

  suspend fun addSubscription(sub: TrackedSubscription)
  suspend fun toggleSubscriptionPaused(id: String)
  suspend fun deleteSubscription(id: String)

  suspend fun dismissAlert(dedupKey: String)
  suspend fun markNotified(dedupKeys: Collection<String>)

  suspend fun addMeal(meal: MealLog)
  suspend fun deleteMeal(id: String)
  suspend fun saveSleep(sleep: SleepLog)
  suspend fun addWorkout(workout: WorkoutLog)
  suspend fun deleteWorkout(id: String)
  suspend fun toggleHabit(habitId: String, dateKey: String)
  suspend fun addHabit(habit: HabitItem)
  suspend fun deleteHabit(habitId: String)
  suspend fun recordSteps(dateKey: String, steps: Int)
  suspend fun startTimer(mode: TimerMode, targetMinutes: Int, nowMillis: Long, dateKey: String)
  suspend fun stopTimer(nowMillis: Long, dateKey: String)
  suspend fun togglePeriodDay(dateKey: String)
  suspend fun setCycleLength(days: Int)
}

interface ProactiveNotifier {
  fun notifyAlerts(alerts: List<ProactiveAlert>, settings: JarvisSettings, alreadyNotified: Set<String>): List<String>
  fun sendTestNotification(callsign: String): Boolean
}

interface Speaker {
  val isSpeaking: StateFlow<Boolean>
  fun speak(text: String)
  fun stop()
  fun shutdown()
}

interface JarvisContainer {
  val phoneData: PhoneDataSource
  val stepSensor: StepSensorSource
  val notifications: NotificationFeed
  val permissions: PermissionChecker
  val settingsRepo: SettingsRepository
  val openRouter: OpenRouterClient
  val organizer: IntelOrganizer
  val briefings: BriefingGenerator
  val assistant: AskAssistant
  val store: IntelStore
  val plannerStore: PlannerStore
  val notifier: ProactiveNotifier
  val speaker: Speaker
}

fun JarvisSettings.isEnabled(source: DataSource) = source in enabledSources
