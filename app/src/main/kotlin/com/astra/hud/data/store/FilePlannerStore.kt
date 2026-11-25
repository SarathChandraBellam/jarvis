package com.astra.hud.data.store

import android.content.Context
import com.astra.hud.contracts.PlannerStore
import com.astra.hud.model.BillingCadence
import com.astra.hud.model.CycleState
import com.astra.hud.model.HabitItem
import com.astra.hud.model.MealLog
import com.astra.hud.model.PlannerReminder
import com.astra.hud.model.SleepLog
import com.astra.hud.model.SubscriptionCategory
import com.astra.hud.model.TimerMode
import com.astra.hud.model.TimerSession
import com.astra.hud.model.TrackedSubscription
import com.astra.hud.model.WorkoutLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON-backed store for reminders, custom/paused subscriptions, proactive alert states,
 * and personal wellness logs (meals, sleep, workouts, habits, steps, focus/fasting timer).
 */
class FilePlannerStore(private val file: File) : PlannerStore {

  constructor(context: Context) : this(File(context.applicationContext.filesDir, FILE_NAME))

  private val mutex = Mutex()
  private var dto: PlannerFileDto = readFile()

  private val _reminders = MutableStateFlow(dto.reminders.mapNotNull { it.toModel() })
  override val reminders: StateFlow<List<PlannerReminder>> = _reminders.asStateFlow()

  private val _customSubs = MutableStateFlow(dto.customSubscriptions.mapNotNull { it.toModel() })
  override val customSubscriptions: StateFlow<List<TrackedSubscription>> = _customSubs.asStateFlow()

  private val _pausedIds = MutableStateFlow(dto.pausedSubscriptionIds.toSet())
  override val pausedSubscriptionIds: StateFlow<Set<String>> = _pausedIds.asStateFlow()

  private val _deletedIds = MutableStateFlow(dto.deletedSubscriptionIds.toSet())
  override val deletedSubscriptionIds: StateFlow<Set<String>> = _deletedIds.asStateFlow()

  private val _dismissedAlerts = MutableStateFlow(dto.dismissedAlertKeys.toSet())
  override val dismissedAlertKeys: StateFlow<Set<String>> = _dismissedAlerts.asStateFlow()

  private val _notifiedAlerts = MutableStateFlow(dto.notifiedAlertKeys.toSet())
  override val notifiedAlertKeys: StateFlow<Set<String>> = _notifiedAlerts.asStateFlow()

  private val _meals = MutableStateFlow(dto.meals.map { it.toModel() })
  override val meals: StateFlow<List<MealLog>> = _meals.asStateFlow()

  private val _sleepLogs = MutableStateFlow(dto.sleepLogs.map { it.toModel() })
  override val sleepLogs: StateFlow<List<SleepLog>> = _sleepLogs.asStateFlow()

  private val _workouts = MutableStateFlow(dto.workouts.map { it.toModel() })
  override val workouts: StateFlow<List<WorkoutLog>> = _workouts.asStateFlow()

  private val _habits = MutableStateFlow(
    if (dto.habitsInitialized) dto.habits.map { it.toModel() } else DEFAULT_HABITS,
  )
  override val habits: StateFlow<List<HabitItem>> = _habits.asStateFlow()

  private val _stepsByDate = MutableStateFlow(dto.stepsByDate)
  override val stepsByDate: StateFlow<Map<String, Int>> = _stepsByDate.asStateFlow()

  private val _timerSession = MutableStateFlow(dto.timerSession.toModel())
  override val timerSession: StateFlow<TimerSession> = _timerSession.asStateFlow()

  private val _cycleState = MutableStateFlow(dto.cycleState.toModel())
  override val cycleState: StateFlow<CycleState> = _cycleState.asStateFlow()

  override suspend fun addReminder(reminder: PlannerReminder) = mutate {
    _reminders.value = (_reminders.value.filterNot { it.id == reminder.id } + reminder)
      .sortedWith(compareBy({ it.dateKey }, { it.minutesOfDay }))
  }

  override suspend fun toggleReminder(id: String) = mutate {
    _reminders.value = _reminders.value.map { if (it.id == id) it.copy(isDone = !it.isDone) else it }
  }

  override suspend fun deleteReminder(id: String) = mutate {
    _reminders.value = _reminders.value.filterNot { it.id == id }
  }

  override suspend fun addSubscription(sub: TrackedSubscription) = mutate {
    _deletedIds.value = _deletedIds.value - sub.id
    _customSubs.value = (_customSubs.value.filterNot { it.id == sub.id } + sub)
  }

  override suspend fun toggleSubscriptionPaused(id: String) = mutate {
    val paused = _pausedIds.value
    _pausedIds.value = if (id in paused) paused - id else paused + id
  }

  override suspend fun deleteSubscription(id: String) = mutate {
    _customSubs.value = _customSubs.value.filterNot { it.id == id }
    _deletedIds.value = _deletedIds.value + id
    _pausedIds.value = _pausedIds.value - id
  }

  override suspend fun dismissAlert(dedupKey: String) = mutate {
    _dismissedAlerts.value = (_dismissedAlerts.value + dedupKey).toList().takeLast(MAX_ALERT_KEYS).toSet()
  }

  override suspend fun markNotified(dedupKeys: Collection<String>) {
    if (dedupKeys.isEmpty()) return
    mutate {
      _notifiedAlerts.value = (_notifiedAlerts.value + dedupKeys).toList().takeLast(MAX_ALERT_KEYS).toSet()
    }
  }

  override suspend fun addMeal(meal: MealLog) = mutate {
    _meals.value = (_meals.value.filterNot { it.id == meal.id } + meal).sortedByDescending { it.timestamp }.take(200)
  }

  override suspend fun deleteMeal(id: String) = mutate {
    _meals.value = _meals.value.filterNot { it.id == id }
  }

  override suspend fun saveSleep(sleep: SleepLog) = mutate {
    _sleepLogs.value = (_sleepLogs.value.filterNot { it.dateKey == sleep.dateKey } + sleep)
      .sortedByDescending { it.dateKey }
      .take(60)
  }

  override suspend fun addWorkout(workout: WorkoutLog) = mutate {
    _workouts.value = (_workouts.value.filterNot { it.id == workout.id } + workout)
      .sortedByDescending { it.timestamp }
      .take(120)
  }

  override suspend fun deleteWorkout(id: String) = mutate {
    _workouts.value = _workouts.value.filterNot { it.id == id }
  }

  override suspend fun toggleHabit(habitId: String, dateKey: String) = mutate {
    _habits.value = _habits.value.map { h ->
      if (h.id != habitId) h
      else {
        val next = if (dateKey in h.completedDateKeys) h.completedDateKeys - dateKey else h.completedDateKeys + dateKey
        h.copy(completedDateKeys = next)
      }
    }
  }

  override suspend fun addHabit(habit: HabitItem) = mutate {
    _habits.value = _habits.value + habit
  }

  override suspend fun deleteHabit(habitId: String) = mutate {
    _habits.value = _habits.value.filterNot { it.id == habitId }
  }

  override suspend fun recordSteps(dateKey: String, steps: Int) = mutate {
    val clean = steps.coerceAtLeast(0)
    val next = _stepsByDate.value.toMutableMap()
    next[dateKey] = clean
    while (next.size > 45) {
      val oldest = next.keys.minOrNull() ?: break
      next.remove(oldest)
    }
    _stepsByDate.value = next
  }

  override suspend fun startTimer(mode: TimerMode, targetMinutes: Int, nowMillis: Long, dateKey: String) = mutate {
    val prev = _timerSession.value
    val carriedFocus = if (prev.lastDateKey == dateKey) prev.completedFocusMinutesToday else 0
    _timerSession.value = prev.copy(
      isRunning = true,
      mode = mode,
      startedAtMillis = nowMillis,
      targetMinutes = targetMinutes.coerceAtLeast(5),
      completedFocusMinutesToday = carriedFocus,
      lastDateKey = dateKey,
    )
  }

  override suspend fun stopTimer(nowMillis: Long, dateKey: String) = mutate {
    val prev = _timerSession.value
    if (!prev.isRunning) return@mutate
    val elapsedMin = ((nowMillis - prev.startedAtMillis).coerceAtLeast(0L) / 60_000L).toInt()
    val carriedFocus = if (prev.lastDateKey == dateKey) prev.completedFocusMinutesToday else 0
    _timerSession.value = prev.copy(
      isRunning = false,
      completedFocusMinutesToday = if (prev.mode == TimerMode.FOCUS) carriedFocus + elapsedMin else carriedFocus,
      lastFastedMinutes = if (prev.mode == TimerMode.FASTING) elapsedMin else prev.lastFastedMinutes,
      lastDateKey = dateKey,
    )
  }

  override suspend fun togglePeriodDay(dateKey: String) = mutate {
    val cur = _cycleState.value
    val nextDates = if (dateKey in cur.periodStartDates) {
      cur.periodStartDates - dateKey
    } else {
      (cur.periodStartDates + dateKey).sorted()
    }
    _cycleState.value = cur.copy(periodStartDates = nextDates)
  }

  override suspend fun setCycleLength(days: Int) = mutate {
    _cycleState.value = _cycleState.value.copy(cycleLengthDays = days.coerceIn(21, 45))
  }

  private suspend fun mutate(block: () -> Unit) {
    mutex.withLock {
      block()
      dto = PlannerFileDto(
        version = 2,
        reminders = _reminders.value.map(ReminderDto::from),
        customSubscriptions = _customSubs.value.map(SubDto::from),
        pausedSubscriptionIds = _pausedIds.value.toList(),
        deletedSubscriptionIds = _deletedIds.value.toList(),
        dismissedAlertKeys = _dismissedAlerts.value.toList(),
        notifiedAlertKeys = _notifiedAlerts.value.toList(),
        meals = _meals.value.map(MealDto::from),
        sleepLogs = _sleepLogs.value.map(SleepDto::from),
        workouts = _workouts.value.map(WorkoutDto::from),
        habits = _habits.value.map(HabitDto::from),
        habitsInitialized = true,
        stepsByDate = _stepsByDate.value,
        timerSession = TimerDto.from(_timerSession.value),
        cycleState = CycleDto.from(_cycleState.value),
      )
      val snapshot = dto
      withContext(Dispatchers.IO) { writeFile(snapshot) }
    }
  }

  private fun readFile(): PlannerFileDto = try {
    if (file.exists()) json.decodeFromString(PlannerFileDto.serializer(), file.readText()) else PlannerFileDto()
  } catch (_: Exception) {
    PlannerFileDto()
  }

  private fun writeFile(snapshot: PlannerFileDto) {
    try {
      file.parentFile?.mkdirs()
      val tmp = File(file.parentFile, file.name + ".tmp")
      tmp.writeText(json.encodeToString(PlannerFileDto.serializer(), snapshot))
      if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
      }
    } catch (_: Exception) {
    }
  }

  companion object {
    const val FILE_NAME = "astra_planner_store.json"
    private const val MAX_ALERT_KEYS = 400
    val DEFAULT_HABITS = listOf(
      HabitItem("hab-water", "2L Water"),
      HabitItem("hab-walk", "20m Walk"),
      HabitItem("hab-screen", "Phone away 10:30 PM"),
    )
    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      explicitNulls = false
    }
  }
}

@Serializable
internal data class PlannerFileDto(
  val version: Int = 2,
  val reminders: List<ReminderDto> = emptyList(),
  val customSubscriptions: List<SubDto> = emptyList(),
  val pausedSubscriptionIds: List<String> = emptyList(),
  val deletedSubscriptionIds: List<String> = emptyList(),
  val dismissedAlertKeys: List<String> = emptyList(),
  val notifiedAlertKeys: List<String> = emptyList(),
  val meals: List<MealDto> = emptyList(),
  val sleepLogs: List<SleepDto> = emptyList(),
  val workouts: List<WorkoutDto> = emptyList(),
  val habits: List<HabitDto> = emptyList(),
  val habitsInitialized: Boolean = false,
  val stepsByDate: Map<String, Int> = emptyMap(),
  val timerSession: TimerDto = TimerDto(),
  val cycleState: CycleDto = CycleDto(),
)

@Serializable
internal data class ReminderDto(
  val id: String,
  val title: String,
  val minutesOfDay: Int = 540,
  val dateKey: String,
  val isDone: Boolean = false,
  val notifyProactively: Boolean = true,
) {
  fun toModel() = PlannerReminder(id, title, minutesOfDay, dateKey, isDone, notifyProactively)

  companion object {
    fun from(r: PlannerReminder) = ReminderDto(r.id, r.title, r.minutesOfDay, r.dateKey, r.isDone, r.notifyProactively)
  }
}

@Serializable
internal data class SubDto(
  val id: String,
  val name: String,
  val amount: Double,
  val currency: String = "₹",
  val cadence: String = BillingCadence.MONTHLY.name,
  val category: String = SubscriptionCategory.STREAMING.name,
  val nextRenewalDate: String? = null,
  val isTrial: Boolean = false,
  val isPaused: Boolean = false,
  val isAutoDetected: Boolean = false,
  val sourceNote: String? = null,
) {
  fun toModel() = TrackedSubscription(
    id = id,
    name = name,
    amount = amount,
    currency = currency,
    cadence = BillingCadence.entries.firstOrNull { it.name == cadence } ?: BillingCadence.MONTHLY,
    category = SubscriptionCategory.entries.firstOrNull { it.name == category } ?: SubscriptionCategory.STREAMING,
    nextRenewalDate = nextRenewalDate,
    isTrial = isTrial,
    isPaused = isPaused,
    isAutoDetected = isAutoDetected,
    sourceNote = sourceNote,
  )

  companion object {
    fun from(s: TrackedSubscription) = SubDto(
      id = s.id,
      name = s.name,
      amount = s.amount,
      currency = s.currency,
      cadence = s.cadence.name,
      category = s.category.name,
      nextRenewalDate = s.nextRenewalDate,
      isTrial = s.isTrial,
      isPaused = s.isPaused,
      isAutoDetected = s.isAutoDetected,
      sourceNote = s.sourceNote,
    )
  }
}

@Serializable
internal data class MealDto(
  val id: String,
  val title: String,
  val calories: Int,
  val proteinG: Int,
  val carbsG: Int,
  val fatG: Int,
  val dateKey: String,
  val timestamp: Long,
  val note: String? = null,
) {
  fun toModel() = MealLog(id, title, calories, proteinG, carbsG, fatG, dateKey, timestamp, note)

  companion object {
    fun from(m: MealLog) = MealDto(m.id, m.title, m.calories, m.proteinG, m.carbsG, m.fatG, m.dateKey, m.timestamp, m.note)
  }
}

@Serializable
internal data class SleepDto(
  val id: String,
  val dateKey: String,
  val totalMinutes: Int,
  val bedtimeLabel: String,
  val wakeLabel: String,
  val qualityScore: Int = 82,
  val deepMinutes: Int = 70,
  val remMinutes: Int = 110,
  val lightMinutes: Int = 200,
  val awakeMinutes: Int = 12,
) {
  fun toModel() = SleepLog(id, dateKey, totalMinutes, bedtimeLabel, wakeLabel, qualityScore, deepMinutes, remMinutes, lightMinutes, awakeMinutes)

  companion object {
    fun from(s: SleepLog) = SleepDto(s.id, s.dateKey, s.totalMinutes, s.bedtimeLabel, s.wakeLabel, s.qualityScore, s.deepMinutes, s.remMinutes, s.lightMinutes, s.awakeMinutes)
  }
}

@Serializable
internal data class WorkoutDto(
  val id: String,
  val title: String,
  val durationMinutes: Int,
  val caloriesBurned: Int,
  val dateKey: String,
  val timestamp: Long,
) {
  fun toModel() = WorkoutLog(id, title, durationMinutes, caloriesBurned, dateKey, timestamp)

  companion object {
    fun from(w: WorkoutLog) = WorkoutDto(w.id, w.title, w.durationMinutes, w.caloriesBurned, w.dateKey, w.timestamp)
  }
}

@Serializable
internal data class HabitDto(
  val id: String,
  val title: String,
  val completedDateKeys: List<String> = emptyList(),
) {
  fun toModel() = HabitItem(id, title, completedDateKeys.toSet())

  companion object {
    fun from(h: HabitItem) = HabitDto(h.id, h.title, h.completedDateKeys.toList())
  }
}

@Serializable
internal data class TimerDto(
  val isRunning: Boolean = false,
  val mode: String = TimerMode.FASTING.name,
  val startedAtMillis: Long = 0L,
  val targetMinutes: Int = 960,
  val completedFocusMinutesToday: Int = 0,
  val lastFastedMinutes: Int = 0,
  val lastDateKey: String = "",
) {
  fun toModel() = TimerSession(
    isRunning = isRunning,
    mode = TimerMode.entries.firstOrNull { it.name == mode } ?: TimerMode.FASTING,
    startedAtMillis = startedAtMillis,
    targetMinutes = targetMinutes,
    completedFocusMinutesToday = completedFocusMinutesToday,
    lastFastedMinutes = lastFastedMinutes,
    lastDateKey = lastDateKey,
  )

  companion object {
    fun from(t: TimerSession) = TimerDto(t.isRunning, t.mode.name, t.startedAtMillis, t.targetMinutes, t.completedFocusMinutesToday, t.lastFastedMinutes, t.lastDateKey)
  }
}

@Serializable
internal data class CycleDto(
  val periodStartDates: List<String> = emptyList(),
  val cycleLengthDays: Int = 30,
  val periodDurationDays: Int = 5,
) {
  fun toModel() = CycleState(periodStartDates, cycleLengthDays, periodDurationDays)

  companion object {
    fun from(c: CycleState) = CycleDto(c.periodStartDates, c.cycleLengthDays, c.periodDurationDays)
  }
}
