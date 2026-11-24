package com.jarvis.hud.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jarvis.hud.contracts.JarvisContainer
import com.jarvis.hud.contracts.isEnabled
import com.jarvis.hud.insights.ProactivePlanner
import com.jarvis.hud.insights.SubscriptionInsights
import com.jarvis.hud.insights.WellnessEngine
import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.BillingCadence
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.ChatMessage
import com.jarvis.hud.model.ConnectionTestResult
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.HabitItem
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.MealLog
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.PlannerReminder
import com.jarvis.hud.model.RawEvent
import com.jarvis.hud.model.SleepLog
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TimerMode
import com.jarvis.hud.model.TrackedSubscription
import com.jarvis.hud.model.WorkoutLog
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
 * Orchestrates Jarvis purely through [JarvisContainer] interfaces.
 * Persists UI selections, reminders, subscriptions, proactive alert state, and personal wellness logs.
 */
class JarvisViewModel(
  private val c: JarvisContainer,
  private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel(), JarvisActions {

  private val initialSettings = c.settingsRepo.settings.value
  private val initialTab = HudTab.entries.firstOrNull { it.name == initialSettings.selectedTabName } ?: HudTab.HUD

  private val _uiState = MutableStateFlow(
    JarvisUiState(
      selectedTab = initialTab,
      settings = initialSettings,
      reminders = c.plannerStore.reminders.value,
      meals = c.plannerStore.meals.value,
      sleepLogs = c.plannerStore.sleepLogs.value,
      workouts = c.plannerStore.workouts.value,
      habits = c.plannerStore.habits.value,
      stepsByDate = c.plannerStore.stepsByDate.value,
      timerSession = c.plannerStore.timerSession.value,
      cycleState = c.plannerStore.cycleState.value,
    ),
  )
  val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null
  private var idCounter = 0L

  private var pendingSettings: JarvisSettings? = null
  private val settings: JarvisSettings get() = pendingSettings ?: c.settingsRepo.settings.value

  init {
    refreshPermissions()
    refreshTelemetry()

    viewModelScope.launch {
      c.settingsRepo.settings.collect { s ->
        _uiState.update { it.copy(settings = s) }
        recomputeDerived()
      }
    }
    viewModelScope.launch {
      c.store.items.collect { items ->
        _uiState.update { it.copy(items = visibleSorted(items)) }
        recomputeDerived()
      }
    }
    viewModelScope.launch {
      c.plannerStore.reminders.collect { rems ->
        _uiState.update { it.copy(reminders = rems) }
        recomputeDerived()
      }
    }
    viewModelScope.launch { c.plannerStore.meals.collect { m -> _uiState.update { it.copy(meals = m) } } }
    viewModelScope.launch { c.plannerStore.sleepLogs.collect { s -> _uiState.update { it.copy(sleepLogs = s) } } }
    viewModelScope.launch { c.plannerStore.workouts.collect { w -> _uiState.update { it.copy(workouts = w) } } }
    viewModelScope.launch { c.plannerStore.habits.collect { h -> _uiState.update { it.copy(habits = h) } } }
    viewModelScope.launch { c.plannerStore.stepsByDate.collect { st -> _uiState.update { it.copy(stepsByDate = st) } } }
    viewModelScope.launch { c.plannerStore.timerSession.collect { t -> _uiState.update { it.copy(timerSession = t) } } }
    viewModelScope.launch {
      c.plannerStore.cycleState.collect { cy ->
        _uiState.update { it.copy(cycleState = cy) }
        recomputeDerived()
      }
    }
    viewModelScope.launch { c.plannerStore.customSubscriptions.collect { recomputeDerived() } }
    viewModelScope.launch { c.plannerStore.pausedSubscriptionIds.collect { recomputeDerived() } }
    viewModelScope.launch { c.plannerStore.deletedSubscriptionIds.collect { recomputeDerived() } }
    viewModelScope.launch { c.plannerStore.dismissedAlertKeys.collect { recomputeDerived() } }
    viewModelScope.launch {
      c.speaker.isSpeaking.collect { speaking -> _uiState.update { it.copy(isSpeaking = speaking) } }
    }
    viewModelScope.launch {
      safely {
        c.store.loadBriefing(todayKey())?.let { b ->
          _uiState.update { it.copy(briefing = b) }
          recomputeDerived()
        }
      }
    }
    viewModelScope.launch {
      c.notifications.events.collect { event -> safely { organizeLiveEvent(event) } }
    }
    recomputeDerived()
    onScanAndOrganize()
  }

  private fun recomputeDerived() {
    val st = _uiState.value
    val now = clock()
    val subs = SubscriptionInsights.build(
      items = st.items,
      customSubscriptions = c.plannerStore.customSubscriptions.value,
      pausedIds = c.plannerStore.pausedSubscriptionIds.value,
      deletedIds = c.plannerStore.deletedSubscriptionIds.value,
      now = now,
    ).all
    val alerts = ProactivePlanner.buildAlerts(
      subscriptions = subs,
      items = st.items,
      reminders = c.plannerStore.reminders.value,
      recentCalls = st.recentCalls,
      appUsage = st.appUsageStats,
      briefing = st.briefing,
      settings = st.settings,
      dismissedKeys = c.plannerStore.dismissedAlertKeys.value,
      cycleState = c.plannerStore.cycleState.value,
      now = now,
    )
    _uiState.update {
      it.copy(
        subscriptions = subs,
        reminders = c.plannerStore.reminders.value,
        proactiveAlerts = alerts,
      )
    }
  }

  private var permissionsLoaded = false

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

  // ---------------------------------------------------------------- navigation & persisted selections

  override fun onSelectTab(tab: HudTab) {
    _uiState.update { it.copy(selectedTab = tab) }
    val cur = settings
    if (cur.selectedTabName != tab.name) onUpdateSettings(cur.copy(selectedTabName = tab.name))
  }

  override fun onSelectTodayView(index: Int) {
    val maxIdx = if (settings.isPeriodTrackerActive) 4 else 3
    val clamped = index.coerceIn(0, maxIdx)
    val cur = settings
    if (cur.todayViewIndex != clamped) onUpdateSettings(cur.copy(todayViewIndex = clamped))
  }

  override fun onSelectSubsView(index: Int) {
    val clamped = index.coerceIn(0, 2)
    val cur = settings
    if (cur.subsViewIndex != clamped) onUpdateSettings(cur.copy(subsViewIndex = clamped))
  }

  override fun onSelectInboxFilter(categoryName: String?, showCompleted: Boolean) {
    val cur = settings
    if (cur.inboxCategoryName != categoryName || cur.inboxShowCompleted != showCompleted) {
      onUpdateSettings(cur.copy(inboxCategoryName = categoryName, inboxShowCompleted = showCompleted))
    }
  }

  override fun onOpenSettings() = _uiState.update { it.copy(showSettings = true) }
  override fun onCloseSettings() = _uiState.update { it.copy(showSettings = false) }
  override fun onDismissError() = _uiState.update { it.copy(lastError = null) }
  override fun onOpenItem(itemId: String?) = _uiState.update { it.copy(openItemId = itemId) }
  override fun onClearChat() = _uiState.update { it.copy(chatMessages = emptyList()) }

  // ---------------------------------------------------------------- planner, subscriptions & proactive alerts

  override fun onAddReminder(title: String, minutesOfDay: Int, dateKey: String) {
    val clean = title.trim()
    if (clean.isEmpty()) return
    val rem = PlannerReminder(
      id = nextId("rem"),
      title = clean,
      minutesOfDay = minutesOfDay.coerceIn(0, 1439),
      dateKey = dateKey,
    )
    viewModelScope.launch { safely { c.plannerStore.addReminder(rem) } }
  }

  override fun onToggleReminder(reminderId: String) {
    viewModelScope.launch { safely { c.plannerStore.toggleReminder(reminderId) } }
  }

  override fun onDeleteReminder(reminderId: String) {
    viewModelScope.launch { safely { c.plannerStore.deleteReminder(reminderId) } }
  }

  override fun onAddSubscription(
    name: String,
    amount: Double,
    cadence: BillingCadence,
    category: SubscriptionCategory,
    nextRenewalDate: String?,
    isTrial: Boolean,
  ) {
    val clean = name.trim()
    if (clean.isEmpty() || amount <= 0.0) return
    val sub = TrackedSubscription(
      id = nextId("sub"),
      name = clean,
      amount = amount,
      cadence = cadence,
      category = category,
      nextRenewalDate = nextRenewalDate?.takeIf { it.isNotBlank() },
      isTrial = isTrial,
      isAutoDetected = false,
      sourceNote = "Added manually",
    )
    viewModelScope.launch { safely { c.plannerStore.addSubscription(sub) } }
  }

  override fun onToggleSubscriptionPaused(subId: String) {
    viewModelScope.launch { safely { c.plannerStore.toggleSubscriptionPaused(subId) } }
  }

  override fun onDeleteSubscription(subId: String) {
    viewModelScope.launch { safely { c.plannerStore.deleteSubscription(subId) } }
  }

  override fun onDismissProactiveAlert(dedupKey: String) {
    viewModelScope.launch { safely { c.plannerStore.dismissAlert(dedupKey) } }
  }

  override fun onSendTestProactiveAlert() {
    val ok = c.notifier.sendTestNotification(settings.userCallsign)
    if (!ok) {
      _uiState.update {
        it.copy(lastError = "Enable Notifications permission in Phone access to receive system alerts.")
      }
    }
  }

  // ---------------------------------------------------------------- wellness (meals, sleep, workouts, habits, timer, steps)

  override fun onLogMeal(description: String, dateKey: String) {
    val clean = description.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      safely {
        val s = settings
        val est = WellnessEngine.estimateMeal(clean, s.apiKey, s.modelId, c.openRouter)
        c.plannerStore.addMeal(
          MealLog(
            id = nextId("meal"),
            title = est.title,
            calories = est.calories,
            proteinG = est.proteinG,
            carbsG = est.carbsG,
            fatG = est.fatG,
            dateKey = dateKey,
            timestamp = clock(),
            note = est.note,
          ),
        )
      }
    }
  }

  override fun onDeleteMeal(mealId: String) {
    viewModelScope.launch { safely { c.plannerStore.deleteMeal(mealId) } }
  }

  override fun onLogSleep(hours: Float, bedtimeLabel: String, wakeLabel: String, qualityScore: Int, dateKey: String) {
    val totalMin = (hours.coerceIn(1f, 16f) * 60f).toInt()
    viewModelScope.launch {
      safely {
        c.plannerStore.saveSleep(
          SleepLog(
            id = "sleep-$dateKey",
            dateKey = dateKey,
            totalMinutes = totalMin,
            bedtimeLabel = bedtimeLabel.ifBlank { "11:00 PM" },
            wakeLabel = wakeLabel.ifBlank { "7:00 AM" },
            qualityScore = qualityScore.coerceIn(10, 100),
          ),
        )
      }
    }
  }

  override fun onLogWorkout(description: String, durationMinutes: Int, dateKey: String) {
    val clean = description.trim().ifEmpty { "Movement" }
    val mins = durationMinutes.coerceIn(5, 360)
    val kcal = WellnessEngine.estimateWorkoutCalories(clean, mins)
    viewModelScope.launch {
      safely {
        c.plannerStore.addWorkout(
          WorkoutLog(
            id = nextId("wk"),
            title = clean,
            durationMinutes = mins,
            caloriesBurned = kcal,
            dateKey = dateKey,
            timestamp = clock(),
          ),
        )
      }
    }
  }

  override fun onDeleteWorkout(workoutId: String) {
    viewModelScope.launch { safely { c.plannerStore.deleteWorkout(workoutId) } }
  }

  override fun onToggleHabitToday(habitId: String, dateKey: String) {
    viewModelScope.launch { safely { c.plannerStore.toggleHabit(habitId, dateKey) } }
  }

  override fun onAddHabit(title: String) {
    val clean = title.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch { safely { c.plannerStore.addHabit(HabitItem(nextId("hab"), clean)) } }
  }

  override fun onDeleteHabit(habitId: String) {
    viewModelScope.launch { safely { c.plannerStore.deleteHabit(habitId) } }
  }

  override fun onStartTimer(mode: TimerMode, targetMinutes: Int) {
    viewModelScope.launch { safely { c.plannerStore.startTimer(mode, targetMinutes, clock(), todayKey()) } }
  }

  override fun onStopTimer() {
    viewModelScope.launch { safely { c.plannerStore.stopTimer(clock(), todayKey()) } }
  }

  override fun onLogManualSteps(steps: Int, dateKey: String) {
    viewModelScope.launch { safely { c.plannerStore.recordSteps(dateKey, steps) } }
  }

  override fun onTogglePeriodDay(dateKey: String) {
    viewModelScope.launch { safely { c.plannerStore.togglePeriodDay(dateKey) } }
  }

  override fun onUpdateCycleLength(days: Int) {
    viewModelScope.launch { safely { c.plannerStore.setCycleLength(days) } }
  }

  // ---------------------------------------------------------------- scan pipeline

  override fun onScanAndOrganize() {
    if (scanJob?.isActive == true) return
    scanJob = viewModelScope.launch { performScan() }
  }

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

      // Read hardware step counter (works without restricted Usage Stats).
      val sensorSteps = runCatching { c.stepSensor.readTodaySteps() }.getOrNull()
      if (sensorSteps != null) {
        val current = c.plannerStore.stepsByDate.value[todayKey()] ?: 0
        if (sensorSteps > current) c.plannerStore.recordSteps(todayKey(), sensorSteps)
      }

      refreshTelemetry()
      _uiState.update { it.copy(calendarEntries = calendar, recentCalls = calls, appUsageStats = usage) }

      val activeItems = c.store.items.value.filter { it.status != ItemStatus.DISMISSED }
      val briefing = c.briefings.generate(activeItems, calendar, _uiState.value.telemetry, s)
      c.store.saveBriefing(briefing)

      _uiState.update {
        it.copy(briefing = briefing, syncStatusLine = syncedLine(added))
      }
      recomputeDerived()

      val newlyPosted = c.notifier.notifyAlerts(
        alerts = _uiState.value.proactiveAlerts,
        settings = s,
        alreadyNotified = c.plannerStore.notifiedAlertKeys.value,
      )
      if (newlyPosted.isNotEmpty()) c.plannerStore.markNotified(newlyPosted)
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

  override fun onMarkItemStatus(itemId: String, status: ItemStatus) {
    if (status != ItemStatus.ACTIVE) _uiState.update { if (it.openItemId == itemId) it.copy(openItemId = null) else it }
    viewModelScope.launch { safely { c.store.setStatus(itemId, status) } }
  }

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
    val adjusted = if (previous.isPeriodTrackerActive != settings.isPeriodTrackerActive &&
      settings.todayViewIndex == previous.todayViewIndex
    ) {
      val remapped = if (settings.isPeriodTrackerActive) {
        if (settings.todayViewIndex >= 2) settings.todayViewIndex + 1 else settings.todayViewIndex
      } else {
        when {
          settings.todayViewIndex == 2 -> 0
          settings.todayViewIndex > 2 -> settings.todayViewIndex - 1
          else -> settings.todayViewIndex
        }
      }
      settings.copy(todayViewIndex = remapped.coerceIn(0, if (settings.isPeriodTrackerActive) 4 else 3))
    } else {
      settings
    }
    pendingSettings = adjusted
    viewModelScope.launch {
      val ok = safely { c.settingsRepo.update(adjusted) }
      if (pendingSettings === adjusted) pendingSettings = null
      if (!ok) {
        _uiState.update { it.copy(settings = c.settingsRepo.settings.value) }
      } else if (previous.enabledSources != adjusted.enabledSources ||
        (!previous.onboardingDone && adjusted.onboardingDone)
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

  override fun onRequestRuntimePermissions() = Unit
  override fun onOpenNotificationAccessSettings() = Unit
  override fun onOpenUsageAccessSettings() = Unit

  override fun onCleared() {
    runCatching { c.speaker.stop() }
    super.onCleared()
  }

  private fun status(line: String) = _uiState.update { it.copy(syncStatusLine = line) }
  private fun setError(e: Throwable) = _uiState.update { it.copy(lastError = e.message ?: e.toString()) }

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
