package com.astra.hud.contracts

import com.astra.hud.model.AgentDecision
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
import com.astra.hud.model.HealthSnapshot
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.AstraUiState
import com.astra.hud.model.MealLog
import com.astra.hud.model.MessageClassification
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

/** Unified Health Connect / Garmin Connect / wearable health telemetry source. */
interface HealthDataSource {
  suspend fun isAvailable(): Boolean
  suspend fun hasPermissions(): Boolean
  suspend fun readTodaySnapshot(): HealthSnapshot
}

/** Voice speech-to-text controller for hands-free talking to ASTRA. */
interface VoiceListener {
  val isListening: StateFlow<Boolean>
  val partialTranscript: StateFlow<String>
  fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
  fun stopListening()
  fun destroy()
}

interface NotificationFeed {
  val events: Flow<RawEvent>
  fun snapshot(): List<RawEvent>
}

interface PermissionChecker {
  fun current(): PermissionStatus
}

interface SettingsRepository {
  val settings: StateFlow<AstraSettings>
  suspend fun update(settings: AstraSettings)
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
  suspend fun organize(events: List<RawEvent>, settings: AstraSettings): List<OrganizedItem>
}

interface BriefingGenerator {
  suspend fun generate(
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    telemetry: DeviceTelemetry,
    settings: AstraSettings,
  ): DailyBriefing
}

interface AskAssistant {
  suspend fun answer(
    prompt: String,
    history: List<ChatMessage>,
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    settings: AstraSettings,
  ): ChatMessage
}

/** Agentic decision engine: interprets voice/text commands into spoken replies + autonomous actions. */
interface AgentEngine {
  suspend fun decide(prompt: String, state: AstraUiState): AgentDecision
}

interface IntelStore {
  val items: StateFlow<List<OrganizedItem>>
  val classificationMap: StateFlow<Map<String, MessageClassification>>
  suspend fun upsert(items: List<OrganizedItem>)
  suspend fun recordClassifications(entries: Collection<MessageClassification>)
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
  fun notifyAlerts(alerts: List<ProactiveAlert>, settings: AstraSettings, alreadyNotified: Set<String>): List<String>
  fun sendTestNotification(callsign: String): Boolean
}

interface Speaker {
  val isSpeaking: StateFlow<Boolean>
  fun speak(text: String)
  fun stop()
  fun shutdown()
}

interface AstraContainer {
  val phoneData: PhoneDataSource
  val stepSensor: StepSensorSource
  val healthData: HealthDataSource
  val voiceListener: VoiceListener
  val notifications: NotificationFeed
  val permissions: PermissionChecker
  val settingsRepo: SettingsRepository
  val openRouter: OpenRouterClient
  val organizer: IntelOrganizer
  val briefings: BriefingGenerator
  val assistant: AskAssistant
  val agent: AgentEngine?
    get() = null
  val store: IntelStore
  val plannerStore: PlannerStore
  val notifier: ProactiveNotifier
  val speaker: Speaker
}

fun AstraSettings.isEnabled(source: DataSource) = source in enabledSources
