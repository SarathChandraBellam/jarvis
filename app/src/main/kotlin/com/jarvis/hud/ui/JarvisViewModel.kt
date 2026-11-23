package com.jarvis.hud.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jarvis.hud.contracts.JarvisContainer
import com.jarvis.hud.contracts.isEnabled
import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.ChatMessage
import com.jarvis.hud.model.ConnectionTestResult
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.RawEvent
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates the J.A.R.V.I.S. HUD purely through the [JarvisContainer] interfaces.
 *
 * Permission-related actions ([onRequestRuntimePermissions], [onOpenNotificationAccessSettings],
 * [onOpenUsageAccessSettings]) are intentional no-ops here: they need an Activity, so MainActivity
 * supplies its own [JarvisActions] (e.g. `object : JarvisActions by viewModel { override ... }`)
 * that launches the system permission flows and then calls [refreshPermissions] from onResume.
 */
class JarvisViewModel(
  private val c: JarvisContainer,
  private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel(), JarvisActions {

  private val _uiState = MutableStateFlow(
    JarvisUiState(settings = c.settingsRepo.settings.value),
  )
  val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null
  private var idCounter = 0L

  /** Settings handed to [onUpdateSettings] but not yet persisted; lets a follow-up action see them. */
  private var pendingSettings: JarvisSettings? = null
  private val settings: JarvisSettings get() = pendingSettings ?: c.settingsRepo.settings.value

  init {
    refreshPermissions()
    refreshTelemetry()

    viewModelScope.launch {
      c.settingsRepo.settings.collect { s -> _uiState.update { it.copy(settings = s) } }
    }
    viewModelScope.launch {
      c.store.items.collect { items -> _uiState.update { it.copy(items = visibleSorted(items)) } }
    }
    viewModelScope.launch {
      c.speaker.isSpeaking.collect { speaking -> _uiState.update { it.copy(isSpeaking = speaking) } }
    }
    viewModelScope.launch {
      safely { c.store.loadBriefing(todayKey())?.let { b -> _uiState.update { it.copy(briefing = b) } } }
    }
    viewModelScope.launch {
      c.notifications.events.collect { event -> safely { organizeLiveEvent(event) } }
    }
    onScanAndOrganize()
  }

  // ---------------------------------------------------------------- refresh helpers

  private var permissionsLoaded = false

  /** Re-reads permissions; if the user just granted something new, scans so the data shows up. */
  fun refreshPermissions() {
    runCatching { c.permissions.current() }
      .onSuccess { p ->
        val before = _uiState.value.permissions.grantedCount
        _uiState.update { it.copy(permissions = p) }
        if (permissionsLoaded && p.grantedCount > before) onScanAndOrganize()
        permissionsLoaded = true
      }
      .onFailure { e -> setError(e) }
  }

  fun refreshTelemetry() {
    runCatching { c.phoneData.readTelemetry() }
      .onSuccess { t -> _uiState.update { it.copy(telemetry = t) } }
      .onFailure { e -> setError(e) }
  }

  // ---------------------------------------------------------------- navigation

  override fun onSelectTab(tab: HudTab) = _uiState.update { it.copy(selectedTab = tab) }

  override fun onOpenSettings() = _uiState.update { it.copy(showSettings = true) }

  override fun onCloseSettings() = _uiState.update { it.copy(showSettings = false) }

  override fun onDismissError() = _uiState.update { it.copy(lastError = null) }

  override fun onOpenItem(itemId: String?) = _uiState.update { it.copy(openItemId = itemId) }

  override fun onClearChat() = _uiState.update { it.copy(chatMessages = emptyList()) }

  // ---------------------------------------------------------------- scan pipeline

  /** Starts a full scan unless one is already running (concurrent scans are ignored). */
  override fun onScanAndOrganize() {
    if (scanJob?.isActive == true) return
    scanJob = viewModelScope.launch { performScan() }
  }

  /** Cancels any running scan, runs [before], then scans again. */
  private fun restartScan(before: suspend () -> Unit = {}) {
    val previous = scanJob
    scanJob = viewModelScope.launch {
      previous?.cancelAndJoin()
      safely { before() }
      performScan()
    }
  }

  private suspend fun performScan() {
    _uiState.update { it.copy(isSyncing = true, lastError = null) }
    try {
      val s = settings
      val now = clock()
      val zone = ZoneId.systemDefault()
      val startOfToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone)
      val midnight = startOfToday.toInstant().toEpochMilli()

      status(STATUS_READING_SMS)
      val sms: List<RawEvent> =
        if (s.isEnabled(DataSource.SMS)) c.phoneData.readSms(sinceMillis = now - SEVEN_DAYS_MS) else emptyList()

      status(STATUS_READING_NOTIFICATIONS)
      val gathered: List<RawEvent> =
        sms + (if (s.isEnabled(DataSource.NOTIFICATION)) c.notifications.snapshot() else emptyList())
      val fresh = gathered
        .filter { s.isEnabled(it.source) }
        .distinctBy { it.contentHash }
        .filterNot { c.store.isProcessed(it.contentHash) }

      status(STATUS_ORGANIZING)
      val organized: List<OrganizedItem> =
        if (fresh.isEmpty()) emptyList() else c.organizer.organize(fresh, s)
      val before = c.store.items.value.size
      if (organized.isNotEmpty()) c.store.upsert(organized)
      if (fresh.isNotEmpty()) c.store.markProcessed(fresh.map { it.contentHash })
      // Duplicate channel reports of one payment merge in the store, so count what was actually added.
      val added = (c.store.items.value.size - before).coerceIn(0, organized.size)

      status(STATUS_BRIEFING)
      val calendar: List<CalendarEntry> = if (!s.isEnabled(DataSource.CALENDAR)) emptyList() else {
        c.phoneData.readCalendar(
          fromMillis = midnight,
          toMillis = startOfToday.plusDays(3).toInstant().toEpochMilli(),
        )
      }
      val calls: List<CallSummary> =
        if (s.isEnabled(DataSource.CALL)) c.phoneData.readRecentCalls() else emptyList()
      val usage: List<AppUsageStat> =
        if (s.isEnabled(DataSource.USAGE)) c.phoneData.readAppUsage(sinceMillis = midnight) else emptyList()
      refreshTelemetry()
      _uiState.update { it.copy(calendarEntries = calendar, recentCalls = calls, appUsageStats = usage) }

      val activeItems = c.store.items.value.filter { it.status != ItemStatus.DISMISSED }
      val briefing = c.briefings.generate(activeItems, calendar, _uiState.value.telemetry, s)
      c.store.saveBriefing(briefing)

      _uiState.update {
        it.copy(briefing = briefing, syncStatusLine = syncedLine(added))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      _uiState.update {
        it.copy(lastError = e.message ?: e.toString(), syncStatusLine = STATUS_FAILED)
      }
    } finally {
      _uiState.update { it.copy(isSyncing = false) }
    }
  }

  private suspend fun organizeLiveEvent(event: RawEvent) {
    val s = settings
    if (!s.isEnabled(event.source)) return
    if (c.store.isProcessed(event.contentHash)) return
    val organized = c.organizer.organize(listOf(event), s)
    if (organized.isNotEmpty()) c.store.upsert(organized)
    c.store.markProcessed(listOf(event.contentHash))
  }

  // ---------------------------------------------------------------- voice

  /** Explicit user toggle: speaks "headline. summary" or stops current speech. */
  override fun onToggleSpeakBriefing() {
    if (c.speaker.isSpeaking.value) {
      c.speaker.stop()
      return
    }
    val b = _uiState.value.briefing ?: return
    c.speaker.speak("${b.headline}. ${b.summary}")
  }

  override fun onSpeakText(text: String) {
    if (settings.voiceEnabled && text.isNotBlank()) c.speaker.speak(text)
  }

  // ---------------------------------------------------------------- items

  override fun onMarkItemStatus(itemId: String, status: ItemStatus) {
    if (status != ItemStatus.ACTIVE) _uiState.update { if (it.openItemId == itemId) it.copy(openItemId = null) else it }
    viewModelScope.launch { safely { c.store.setStatus(itemId, status) } }
  }

  // ---------------------------------------------------------------- chat

  override fun onSendChatMessage(prompt: String) {
    val text = prompt.trim()
    if (text.isEmpty()) return
    val history = _uiState.value.chatMessages
    val userMsg = ChatMessage(id = nextId("user"), isFromUser = true, content = text, timestamp = clock())
    _uiState.update { it.copy(chatMessages = it.chatMessages + userMsg, isAskingAi = true) }
    viewModelScope.launch {
      try {
        val st = _uiState.value
        val reply = c.assistant.answer(text, history, st.items, st.calendarEntries, settings)
        _uiState.update { it.copy(chatMessages = it.chatMessages + reply) }
        onSpeakText(reply.content)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        setError(e)
      } finally {
        _uiState.update { it.copy(isAskingAi = false) }
      }
    }
  }

  // ---------------------------------------------------------------- settings

  override fun onUpdateSettings(settings: JarvisSettings) {
    val previous = this.settings
    pendingSettings = settings
    viewModelScope.launch {
      val ok = safely { c.settingsRepo.update(settings) }
      if (pendingSettings === settings) pendingSettings = null
      if (!ok) {
        // Show what is really persisted, not the draft that failed to save.
        _uiState.update { it.copy(settings = c.settingsRepo.settings.value) }
      } else if (previous.enabledSources != settings.enabledSources ||
        (!previous.onboardingDone && settings.onboardingDone)
      ) {
        restartScan()
      }
    }
  }

  override fun onTestOpenRouterConnection() {
    val s = settings
    if (s.apiKey.isBlank()) {
      _uiState.update { it.copy(connectionTestResult = noKeyResult()) }
      return
    }
    _uiState.update { it.copy(connectionTestResult = null) }
    viewModelScope.launch {
      val result = try {
        c.openRouter.testConnection(s.apiKey, s.modelId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        ConnectionTestResult(success = false, message = e.message ?: "Connection failed")
      }
      _uiState.update { it.copy(connectionTestResult = result) }
    }
  }

  override fun onFetchOpenRouterModels() {
    val s = settings
    if (s.apiKey.isBlank()) {
      _uiState.update { it.copy(connectionTestResult = noKeyResult()) }
      return
    }
    _uiState.update { it.copy(isLoadingModels = true) }
    viewModelScope.launch {
      try {
        c.openRouter.listModels(s.apiKey)
          .onSuccess { models ->
            val sorted = models.sortedWith(compareBy({ !it.isFree }, { it.name.lowercase() }))
            _uiState.update { it.copy(availableModels = sorted) }
          }
          .onFailure { e ->
            _uiState.update {
              it.copy(
                connectionTestResult = ConnectionTestResult(
                  success = false,
                  message = e.message ?: "Couldn't load the model list",
                ),
              )
            }
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        setError(e)
      } finally {
        _uiState.update { it.copy(isLoadingModels = false) }
      }
    }
  }

  override fun onClearAiCache() {
    restartScan {
      c.store.clear()
      _uiState.update { it.copy(briefing = null) }
    }
  }

  // ---------------------------------------------------------------- permissions (Activity-owned)

  /** No-op: MainActivity overrides with an ActivityResult permission launcher. */
  override fun onRequestRuntimePermissions() = Unit

  /** No-op: MainActivity overrides by launching the notification-listener settings screen. */
  override fun onOpenNotificationAccessSettings() = Unit

  /** No-op: MainActivity overrides by launching the usage-access settings screen. */
  override fun onOpenUsageAccessSettings() = Unit

  override fun onCleared() {
    runCatching { c.speaker.stop() }
    super.onCleared()
  }

  // ---------------------------------------------------------------- internals

  private fun status(line: String) = _uiState.update { it.copy(syncStatusLine = line) }

  private fun setError(e: Throwable) =
    _uiState.update { it.copy(lastError = e.message ?: e.toString()) }

  /** Runs [block], routing any non-cancellation failure to lastError. Returns true on success. */
  private suspend fun safely(block: suspend () -> Unit): Boolean = try {
    block()
    true
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    setError(e)
    false
  }

  private fun todayKey(): String =
    Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

  private fun nextId(prefix: String) = "$prefix-${clock()}-${idCounter++}"

  private fun noKeyResult() = ConnectionTestResult(success = false, message = NO_KEY_MESSAGE)

  companion object {
    private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

    const val STATUS_READY = "Ready"
    const val STATUS_READING_SMS = "Reading messages…"
    const val STATUS_READING_NOTIFICATIONS = "Checking notifications…"
    const val STATUS_ORGANIZING = "Organizing…"
    const val STATUS_BRIEFING = "Writing your briefing…"
    const val STATUS_FAILED = "Sync failed"
    const val NO_KEY_MESSAGE = "Add an OpenRouter API key first"

    fun syncedLine(newItems: Int) = when (newItems) {
      0 -> "Up to date"
      1 -> "Up to date · 1 new item"
      else -> "Up to date · $newItems new items"
    }

    /** ACTIVE first, then priority CRITICAL→LOW, then newest; DISMISSED items are hidden. */
    internal fun visibleSorted(items: List<OrganizedItem>): List<OrganizedItem> =
      items
        .filter { it.status != ItemStatus.DISMISSED }
        .sortedWith(
          compareBy<OrganizedItem>({ it.status != ItemStatus.ACTIVE }, { it.priority.ordinal })
            .thenByDescending { it.timestamp },
        )

    fun factory(c: JarvisContainer): ViewModelProvider.Factory = viewModelFactory {
      initializer { JarvisViewModel(c) }
    }
  }
}
