package com.jarvis.hud.model

/**
 * Phone data sources collected locally on-device.
 */
enum class DataSource(val label: String) {
  SMS("SMS"),
  NOTIFICATION("Notifications"),
  CALENDAR("Calendar"),
  CALL("Call Logs"),
  USAGE("Screen Time"),
}

enum class IntelCategory(val label: String) {
  FINANCE("Finance"),
  BILL("Bills"),
  DELIVERY("Deliveries"),
  TRAVEL("Travel"),
  ACTION("Action"),
  MESSAGE("Messages"),
  SCHEDULE("Schedule"),
  SYSTEM("System"),
}

enum class Priority(val label: String) {
  CRITICAL("CRITICAL"),
  HIGH("HIGH"),
  NORMAL("NORMAL"),
  LOW("LOW"),
}

enum class ItemStatus {
  ACTIVE,
  DONE,
  DISMISSED,
}

enum class HudTab(val label: String) {
  HUD("Today"),
  FINANCE("Subs"),
  INTEL("Inbox"),
  ASK("Ask"),
}

enum class SubscriptionCategory(val label: String) {
  STREAMING("Streaming & Media"),
  SOFTWARE("Software & AI"),
  TELECOM("Telecom & Wi-Fi"),
  FITNESS("Fitness & Life"),
  UTILITIES("EMI & Utilities"),
}

enum class BillingCadence(val label: String, val monthlyDivisor: Double) {
  WEEKLY("Weekly", 0.25),
  MONTHLY("Monthly", 1.0),
  QUARTERLY("Quarterly", 3.0),
  YEARLY("Yearly", 12.0),
}

data class TrackedSubscription(
  val id: String,
  val name: String,
  val amount: Double,
  val currency: String = "₹",
  val cadence: BillingCadence = BillingCadence.MONTHLY,
  val category: SubscriptionCategory = SubscriptionCategory.STREAMING,
  val nextRenewalDate: String? = null,
  val isTrial: Boolean = false,
  val isPaused: Boolean = false,
  val isAutoDetected: Boolean = true,
  val sourceNote: String? = null,
) {
  val monthlyEquivalent: Double
    get() = if (isPaused) 0.0 else amount / cadence.monthlyDivisor
}

data class PlannerReminder(
  val id: String,
  val title: String,
  val minutesOfDay: Int = 9 * 60,
  val dateKey: String,
  val isDone: Boolean = false,
  val notifyProactively: Boolean = true,
)

enum class UserGender(val label: String) {
  FEMALE("Female"),
  MALE("Male"),
}

enum class ProactiveKind(val label: String) {
  RENEWAL("Renewal"),
  BILL("Bill due"),
  BRIEFING("Briefing"),
  DELIVERY("Delivery"),
  CALL("Missed call"),
  FOCUS("Focus nudge"),
  REMINDER("Reminder"),
  CYCLE("Cycle"),
}

enum class CyclePhase(
  val label: String,
  val tagline: String,
  val energyTip: String,
  val movementTip: String,
  val sleepTip: String,
) {
  MENSTRUAL(
    label = "Menstrual",
    tagline = "soft rest, warm tea, and zero guilt",
    energyTip = "Baseline energy is naturally lower; pace your calendar gently.",
    movementTip = "Light walks, restorative stretching, or extra rest.",
    sleepTip = "Aim for an extra 30 minutes of sleep tonight.",
  ),
  FOLLICULAR(
    label = "Follicular",
    tagline = "fresh clarity and quiet momentum",
    energyTip = "Energy rises steadily — great days for creative focus.",
    movementTip = "Build into brisk walks, cycling, or strength sessions.",
    sleepTip = "Sleep often feels deeper and easier to settle into.",
  ),
  MID_CYCLE(
    label = "Mid-cycle",
    tagline = "mid-cycle glow is real and she's here",
    energyTip = "Peak social and physical readiness; you may feel naturally buoyant.",
    movementTip = "Great window for higher-output workouts or long walks.",
    sleepTip = "Body temperature nudges up slightly — keep your room cool.",
  ),
  LUTEAL(
    label = "Luteal",
    tagline = "a softer score here is your cycle, not failure",
    energyTip = "Lower readiness before your period is expected; take it steady.",
    movementTip = "Moderate movement, yoga, or calm evening walks.",
    sleepTip = "Chamomile at 10, phone away at 10:30 keeps rest steady.",
  ),
}

/** Logged period start dates (ISO yyyy-MM-dd) + cycle configuration. */
data class CycleState(
  val periodStartDates: List<String> = emptyList(),
  val cycleLengthDays: Int = 30,
  val periodDurationDays: Int = 5,
)

data class ProactiveAlert(
  val id: String,
  val dedupKey: String,
  val kind: ProactiveKind,
  val title: String,
  val body: String,
  val priority: Priority = Priority.NORMAL,
  val timestamp: Long,
  val targetTab: HudTab? = null,
)

/** Logged meal with calories and macros (auto-estimated locally or via OpenRouter). */
data class MealLog(
  val id: String,
  val title: String,
  val calories: Int,
  val proteinG: Int,
  val carbsG: Int,
  val fatG: Int,
  val dateKey: String,
  val timestamp: Long,
  val note: String? = null,
)

/** Logged sleep session with stage breakdown (deep/rem/light/awake minutes). */
data class SleepLog(
  val id: String,
  val dateKey: String,
  val totalMinutes: Int,
  val bedtimeLabel: String,
  val wakeLabel: String,
  val qualityScore: Int = 82,
  val deepMinutes: Int = (totalMinutes * 0.18).toInt(),
  val remMinutes: Int = (totalMinutes * 0.28).toInt(),
  val lightMinutes: Int = (totalMinutes * 0.51).toInt(),
  val awakeMinutes: Int = (totalMinutes * 0.03).toInt(),
)

/** Logged movement/workout session (walking, cycling, gym, yoga, run). */
data class WorkoutLog(
  val id: String,
  val title: String,
  val durationMinutes: Int,
  val caloriesBurned: Int,
  val dateKey: String,
  val timestamp: Long,
)

/** Daily habit or supplement chip that can be checked off per day. */
data class HabitItem(
  val id: String,
  val title: String,
  val completedDateKeys: Set<String> = emptySet(),
)

enum class TimerMode(val label: String) {
  FASTING("Fasting"),
  FOCUS("Deep Focus"),
}

/** Active or last-completed fasting / focus timer persisted across restarts. */
data class TimerSession(
  val isRunning: Boolean = false,
  val mode: TimerMode = TimerMode.FASTING,
  val startedAtMillis: Long = 0L,
  val targetMinutes: Int = 16 * 60,
  val completedFocusMinutesToday: Int = 0,
  val lastFastedMinutes: Int = 0,
  val lastDateKey: String = "",
)

data class RawEvent(
  val id: String,
  val source: DataSource,
  val sender: String,
  val title: String,
  val body: String,
  val timestamp: Long,
  val contentHash: String,
  val aiProcessed: Boolean = false,
)

data class OrganizedItem(
  val id: String,
  val rawEventId: String?,
  val category: IntelCategory,
  val title: String,
  val summary: String,
  val amount: Double? = null,
  val currency: String = "₹",
  val isCredit: Boolean = false,
  val merchant: String? = null,
  val dueDate: String? = null,
  val priority: Priority = Priority.NORMAL,
  val status: ItemStatus = ItemStatus.ACTIVE,
  val timestamp: Long,
  val source: DataSource,
  val sender: String? = null,
  val accountTail: String? = null,
  val reference: String? = null,
)

data class DailyBriefing(
  val dateKey: String,
  val headline: String,
  val summary: String,
  val highlights: List<String>,
  val modelUsed: String,
  val createdAt: Long,
)

data class ChatMessage(
  val id: String,
  val isFromUser: Boolean,
  val content: String,
  val timestamp: Long,
  val modelUsed: String? = null,
)

data class CalendarEntry(
  val id: String,
  val title: String,
  val startTime: Long,
  val endTime: Long,
  val location: String?,
)

data class CallSummary(
  val id: String,
  val nameOrNumber: String,
  val callType: String,
  val durationSec: Long,
  val timestamp: Long,
)

data class AppUsageStat(
  val packageName: String,
  val appName: String,
  val foregroundMinutes: Long,
)

data class DeviceTelemetry(
  val batteryPercent: Int,
  val isCharging: Boolean,
  val freeStorageGb: Float,
  val totalStorageGb: Float,
)

data class OpenRouterModelInfo(
  val id: String,
  val name: String,
  val contextLength: Int? = null,
  val isFree: Boolean = false,
)

data class PermissionStatus(
  val smsGranted: Boolean,
  val notificationAccessGranted: Boolean,
  val calendarGranted: Boolean,
  val callLogGranted: Boolean,
  val usageAccessGranted: Boolean,
  val notificationsPostGranted: Boolean = true,
  val activityRecognitionGranted: Boolean = true,
  val microphoneGranted: Boolean = false,
  val healthConnectGranted: Boolean = false,
) {
  val grantedCount: Int
    get() = listOf(
      notificationAccessGranted,
      healthConnectGranted,
      microphoneGranted,
      calendarGranted,
      notificationsPostGranted,
    ).count { it }
}

/** Explicit mapping of an ingested message/notification ID to its AI classification. */
data class MessageClassification(
  val messageId: String,
  val category: IntelCategory,
  val priority: Priority,
  val source: DataSource,
  val sender: String,
  val title: String,
  val classifiedBy: String = "local",
  val timestamp: Long = 0L,
)

/** Unified health telemetry pulled from Android Health Connect (Garmin Connect / watch) or phone sensors. */
data class HealthSnapshot(
  val stepsToday: Int? = null,
  val sleepMinutesLastNight: Int? = null,
  val bedtimeLabel: String? = null,
  val wakeLabel: String? = null,
  val heartRateBpm: Int? = null,
  val activeCaloriesKcal: Int? = null,
  val recentWorkouts: List<WorkoutLog> = emptyList(),
  val sourceLabel: String = "Device sensors",
  val connected: Boolean = false,
  val garminDetected: Boolean = false,
  val syncedAtMillis: Long = 0L,
)

/** Structured action proposed by J.A.R.V.I.S. from natural speech or text. */
data class AgentAction(
  val type: String,
  val params: Map<String, String> = emptyMap(),
)

/** Result of an agentic decision turn (spoken reply + zero or more autonomous actions). */
data class AgentDecision(
  val reply: String,
  val actions: List<AgentAction> = emptyList(),
  val modelUsed: String = "local",
)

/** Audit log entry shown on the HUD when J.A.R.V.I.S. autonomously logs or updates state. */
data class AgentActionLog(
  val id: String,
  val actionType: String,
  val summary: String,
  val timestamp: Long,
  val succeeded: Boolean = true,
)

data class JarvisSettings(
  val apiKey: String = "",
  val modelId: String = "google/gemini-2.5-flash",
  val userCallsign: String = "Sir",
  val voiceEnabled: Boolean = true,
  val redactSensitiveData: Boolean = true,
  val enabledSources: Set<DataSource> = DataSource.entries.toSet(),
  val aiShareSources: Set<DataSource> = DataSource.entries.toSet(),
  val onboardingDone: Boolean = false,
  val monthlyBudget: Double = 0.0,
  /** Persisted UI selections across restarts & app version updates. */
  val selectedTabName: String = HudTab.HUD.name,
  val todayViewIndex: Int = 0,
  val subsViewIndex: Int = 0,
  val inboxCategoryName: String? = null,
  val inboxShowCompleted: Boolean = false,
  /** Proactive notifications & personal wellness goals. */
  val proactiveNotificationsEnabled: Boolean = true,
  val renewalLeadDays: Int = 3,
  val morningBriefingHour: Int = 8,
  val dailyFocusGoalMinutes: Int = 120,
  val dailyCalorieGoal: Int = 1800,
  val dailyProteinGoalG: Int = 110,
  val dailyCarbsGoalG: Int = 180,
  val dailyFatGoalG: Int = 60,
  val dailyStepGoal: Int = 8000,
  val sleepGoalMinutes: Int = 480,
  val userGender: UserGender = UserGender.FEMALE,
  val cycleTrackingEnabled: Boolean = true,
) {
  /** Period & cycle tracking is active only for Female profiles with cycle tracking enabled. */
  val isPeriodTrackerActive: Boolean
    get() = userGender == UserGender.FEMALE && cycleTrackingEnabled
}

data class ConnectionTestResult(
  val success: Boolean,
  val message: String,
  val latencyMs: Long = 0L,
)

data class JarvisUiState(
  val selectedTab: HudTab = HudTab.HUD,
  val showSettings: Boolean = false,
  val isSyncing: Boolean = false,
  val syncStatusLine: String = "Ready",
  val isSpeaking: Boolean = false,
  val isListening: Boolean = false,
  val liveTranscript: String = "",
  val isAskingAi: Boolean = false,
  val settings: JarvisSettings = JarvisSettings(),
  val permissions: PermissionStatus = PermissionStatus(
    smsGranted = false,
    notificationAccessGranted = false,
    calendarGranted = false,
    callLogGranted = false,
    usageAccessGranted = false,
  ),
  val telemetry: DeviceTelemetry = DeviceTelemetry(
    batteryPercent = 88,
    isCharging = false,
    freeStorageGb = 64.5f,
    totalStorageGb = 128.0f,
  ),
  val healthSnapshot: HealthSnapshot = HealthSnapshot(),
  val briefing: DailyBriefing? = null,
  val items: List<OrganizedItem> = emptyList(),
  val classificationMap: Map<String, MessageClassification> = emptyMap(),
  val recentAgentActions: List<AgentActionLog> = emptyList(),
  val subscriptions: List<TrackedSubscription> = emptyList(),
  val reminders: List<PlannerReminder> = emptyList(),
  val proactiveAlerts: List<ProactiveAlert> = emptyList(),
  val meals: List<MealLog> = emptyList(),
  val sleepLogs: List<SleepLog> = emptyList(),
  val workouts: List<WorkoutLog> = emptyList(),
  val habits: List<HabitItem> = emptyList(),
  val stepsByDate: Map<String, Int> = emptyMap(),
  val timerSession: TimerSession = TimerSession(),
  val cycleState: CycleState = CycleState(),
  val calendarEntries: List<CalendarEntry> = emptyList(),
  val recentCalls: List<CallSummary> = emptyList(),
  val appUsageStats: List<AppUsageStat> = emptyList(),
  val chatMessages: List<ChatMessage> = emptyList(),
  val availableModels: List<OpenRouterModelInfo> = emptyList(),
  val isLoadingModels: Boolean = false,
  val connectionTestResult: ConnectionTestResult? = null,
  val lastError: String? = null,
  val openItemId: String? = null,
) {
  val openItem: OrganizedItem? get() = openItemId?.let { id -> items.firstOrNull { it.id == id } }
}

interface JarvisActions {
  fun onSelectTab(tab: HudTab)
  fun onOpenSettings()
  fun onCloseSettings()
  fun onScanAndOrganize()
  fun onToggleSpeakBriefing()
  fun onSpeakText(text: String)
  fun onMarkItemStatus(itemId: String, status: ItemStatus)
  fun onSendChatMessage(prompt: String)
  fun onUpdateSettings(settings: JarvisSettings)
  fun onTestOpenRouterConnection()
  fun onFetchOpenRouterModels()
  fun onClearAiCache()
  fun onRequestRuntimePermissions()
  fun onOpenNotificationAccessSettings()
  fun onOpenUsageAccessSettings()
  fun onDismissError()
  fun onOpenItem(itemId: String?)
  fun onClearChat()
  fun onToggleVoiceListening() {}
  fun onRequestHealthConnectPermissions() {}
  fun onOpenHealthConnectSettings() {}
  fun onSelectTodayView(index: Int) {}
  fun onSelectSubsView(index: Int) {}
  fun onSelectInboxFilter(categoryName: String?, showCompleted: Boolean) {}
  fun onAddReminder(title: String, minutesOfDay: Int, dateKey: String) {}
  fun onToggleReminder(reminderId: String) {}
  fun onDeleteReminder(reminderId: String) {}
  fun onAddSubscription(
    name: String,
    amount: Double,
    cadence: BillingCadence,
    category: SubscriptionCategory,
    nextRenewalDate: String?,
    isTrial: Boolean,
  ) {}
  fun onToggleSubscriptionPaused(subId: String) {}
  fun onDeleteSubscription(subId: String) {}
  fun onDismissProactiveAlert(dedupKey: String) {}
  fun onSendTestProactiveAlert() {}
  fun onLogMeal(description: String, dateKey: String) {}
  fun onDeleteMeal(mealId: String) {}
  fun onLogSleep(hours: Float, bedtimeLabel: String, wakeLabel: String, qualityScore: Int, dateKey: String) {}
  fun onLogWorkout(description: String, durationMinutes: Int, dateKey: String) {}
  fun onDeleteWorkout(workoutId: String) {}
  fun onToggleHabitToday(habitId: String, dateKey: String) {}
  fun onAddHabit(title: String) {}
  fun onDeleteHabit(habitId: String) {}
  fun onStartTimer(mode: TimerMode, targetMinutes: Int) {}
  fun onStopTimer() {}
  fun onLogManualSteps(steps: Int, dateKey: String) {}
  fun onTogglePeriodDay(dateKey: String) {}
  fun onUpdateCycleLength(days: Int) {}
}
