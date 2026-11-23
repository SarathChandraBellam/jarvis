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

/**
 * Organized intelligence categories rendered on the J.A.R.V.I.S. HUD.
 */
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

/**
 * Tactical urgency level for organized items.
 */
enum class Priority(val label: String) {
  CRITICAL("CRITICAL"),
  HIGH("HIGH"),
  NORMAL("NORMAL"),
  LOW("LOW"),
}

/**
 * Lifecycle state for an actionable or tracked item.
 */
enum class ItemStatus {
  ACTIVE,
  DONE,
  DISMISSED,
}

/**
 * Primary bottom-dock HUD sections.
 */
enum class HudTab(val label: String) {
  HUD("Home"),
  FINANCE("Money"),
  INTEL("Inbox"),
  ASK("Ask"),
}

/**
 * Raw message, notification, or device event captured locally.
 */
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

/**
 * Structured intelligence item produced by OpenRouter AI or local deterministic parser.
 */
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
  /** Who sent the underlying event (SMS address or app name); identifies the delivery channel. */
  val sender: String? = null,
  /** Last 4 digits of the bank account / card involved, when the message names one. */
  val accountTail: String? = null,
  /** UPI / bank transaction reference number, when the message carries one. */
  val reference: String? = null,
)

/**
 * J.A.R.V.I.S.-style daily tactical briefing.
 */
data class DailyBriefing(
  val dateKey: String,
  val headline: String,
  val summary: String,
  val highlights: List<String>,
  val modelUsed: String,
  val createdAt: Long,
)

/**
 * Message in the Ask J.A.R.V.I.S. console.
 */
data class ChatMessage(
  val id: String,
  val isFromUser: Boolean,
  val content: String,
  val timestamp: Long,
  val modelUsed: String? = null,
)

/**
 * Calendar schedule event read from CalendarContract.
 */
data class CalendarEntry(
  val id: String,
  val title: String,
  val startTime: Long,
  val endTime: Long,
  val location: String?,
)

/**
 * Recent phone call entry read from CallLog.Calls.
 */
data class CallSummary(
  val id: String,
  val nameOrNumber: String,
  val callType: String,
  val durationSec: Long,
  val timestamp: Long,
)

/**
 * Per-app foreground screen time summary.
 */
data class AppUsageStat(
  val packageName: String,
  val appName: String,
  val foregroundMinutes: Long,
)

/**
 * Live device battery and storage telemetry.
 */
data class DeviceTelemetry(
  val batteryPercent: Int,
  val isCharging: Boolean,
  val freeStorageGb: Float,
  val totalStorageGb: Float,
)

/**
 * Metadata for an OpenRouter model returned by /api/v1/models.
 */
data class OpenRouterModelInfo(
  val id: String,
  val name: String,
  val contextLength: Int? = null,
  val isFree: Boolean = false,
)

/**
 * Snapshot of Android permissions and special access grants.
 */
data class PermissionStatus(
  val smsGranted: Boolean,
  val notificationAccessGranted: Boolean,
  val calendarGranted: Boolean,
  val callLogGranted: Boolean,
  val usageAccessGranted: Boolean,
) {
  val grantedCount: Int
    get() = listOf(
      smsGranted,
      notificationAccessGranted,
      calendarGranted,
      callLogGranted,
      usageAccessGranted,
    ).count { it }
}

/**
 * User configuration persisted locally with AES-GCM encrypted API key.
 */
data class JarvisSettings(
  val apiKey: String = "",
  val modelId: String = "google/gemini-2.5-flash",
  val userCallsign: String = "Sir",
  val voiceEnabled: Boolean = true,
  val redactSensitiveData: Boolean = true,
  val enabledSources: Set<DataSource> = DataSource.entries.toSet(),
  val aiShareSources: Set<DataSource> = DataSource.entries.toSet(),
  /** False until the user finishes (or skips) the welcome screen. */
  val onboardingDone: Boolean = false,
  /** Monthly spending budget in ₹; 0 = no budget. */
  val monthlyBudget: Double = 0.0,
)

/**
 * Result of testing the user's OpenRouter API key and model uplink.
 */
data class ConnectionTestResult(
  val success: Boolean,
  val message: String,
  val latencyMs: Long = 0L,
)

/**
 * Single immutable UI state driving the entire J.A.R.V.I.S. HUD.
 */
data class JarvisUiState(
  val selectedTab: HudTab = HudTab.HUD,
  val showSettings: Boolean = false,
  val isSyncing: Boolean = false,
  val syncStatusLine: String = "Ready",
  val isSpeaking: Boolean = false,
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
  val briefing: DailyBriefing? = null,
  val items: List<OrganizedItem> = emptyList(),
  val calendarEntries: List<CalendarEntry> = emptyList(),
  val recentCalls: List<CallSummary> = emptyList(),
  val appUsageStats: List<AppUsageStat> = emptyList(),
  val chatMessages: List<ChatMessage> = emptyList(),
  val availableModels: List<OpenRouterModelInfo> = emptyList(),
  val isLoadingModels: Boolean = false,
  val connectionTestResult: ConnectionTestResult? = null,
  val lastError: String? = null,
  /** Item shown in the details sheet, if any. */
  val openItemId: String? = null,
) {
  val openItem: OrganizedItem? get() = openItemId?.let { id -> items.firstOrNull { it.id == id } }
}

/**
 * UI callbacks wired to JarvisViewModel and MainActivity permission launchers.
 */
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
}
