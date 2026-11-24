package com.jarvis.hud.data.store

import android.content.Context
import com.jarvis.hud.contracts.PlannerStore
import com.jarvis.hud.model.BillingCadence
import com.jarvis.hud.model.PlannerReminder
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TrackedSubscription
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
 * JSON-backed store for user reminders, custom/paused subscriptions, and proactive alert
 * dismissals so selections and personal plans persist cleanly across restarts & app upgrades.
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

  private suspend fun mutate(block: () -> Unit) {
    mutex.withLock {
      block()
      dto = PlannerFileDto(
        version = 1,
        reminders = _reminders.value.map(ReminderDto::from),
        customSubscriptions = _customSubs.value.map(SubDto::from),
        pausedSubscriptionIds = _pausedIds.value.toList(),
        deletedSubscriptionIds = _deletedIds.value.toList(),
        dismissedAlertKeys = _dismissedAlerts.value.toList(),
        notifiedAlertKeys = _notifiedAlerts.value.toList(),
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
    const val FILE_NAME = "jarvis_planner_store.json"
    private const val MAX_ALERT_KEYS = 400
    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      explicitNulls = false
    }
  }
}

@Serializable
internal data class PlannerFileDto(
  val version: Int = 1,
  val reminders: List<ReminderDto> = emptyList(),
  val customSubscriptions: List<SubDto> = emptyList(),
  val pausedSubscriptionIds: List<String> = emptyList(),
  val deletedSubscriptionIds: List<String> = emptyList(),
  val dismissedAlertKeys: List<String> = emptyList(),
  val notifiedAlertKeys: List<String> = emptyList(),
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
