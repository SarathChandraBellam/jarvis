package com.astra.hud.data.store

import android.content.Context
import com.astra.hud.ai.TransactionDeduper
import com.astra.hud.contracts.IntelStore
import com.astra.hud.model.DailyBriefing
import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.Priority
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
 * JSON-file-backed cache of organized items, processed content hashes and daily briefings.
 * Mutations are serialized with a [Mutex] and written atomically (temp file + rename).
 * Limits: [MAX_ITEMS] items (newest first), [MAX_HASHES] hashes, [MAX_BRIEFINGS] briefings.
 */
class FileIntelStore(private val file: File) : IntelStore {

  constructor(context: Context) : this(File(context.applicationContext.filesDir, FILE_NAME))

  private val mutex = Mutex()
  private var data: StoreFileDto = readFile()
  private val processed = LinkedHashSet(data.processedHashes)
  // Collapse duplicates persisted by earlier versions, which counted each channel separately.
  private val itemsFlow = MutableStateFlow(
    TransactionDeduper.collapse(data.items.mapNotNull { it.toModel() }).items.sortedByDescending { it.timestamp },
  )
  override val items: StateFlow<List<OrganizedItem>> = itemsFlow.asStateFlow()

  override suspend fun upsert(items: List<OrganizedItem>) {
    if (items.isEmpty()) return
    mutate {
      val current = itemsFlow.value
      val statusById = current.associate { it.id to it.status }
      // Re-organizing an item must not resurrect one the user already marked done / dismissed.
      val incoming = items.map { item ->
        val prior = statusById[item.id]
        if (prior != null && prior != ItemStatus.ACTIVE) item.copy(status = prior) else item
      }
      // The same payment reported by SMS + bank app + UPI app collapses into one item.
      val merged = TransactionDeduper.merge(current, incoming).items
      itemsFlow.value = merged.sortedByDescending { it.timestamp }.take(MAX_ITEMS)
    }
  }

  override suspend fun setStatus(itemId: String, status: ItemStatus) {
    mutate {
      itemsFlow.value = itemsFlow.value.map { if (it.id == itemId) it.copy(status = status) else it }
    }
  }

  override suspend fun isProcessed(contentHash: String): Boolean = mutex.withLock { contentHash in processed }

  override suspend fun markProcessed(hashes: Collection<String>) {
    if (hashes.isEmpty()) return
    mutate {
      hashes.forEach { h ->
        processed.remove(h)
        processed.add(h)
      }
      while (processed.size > MAX_HASHES) processed.remove(processed.first())
    }
  }

  override suspend fun saveBriefing(briefing: DailyBriefing) {
    mutate {
      val list = data.briefings.filter { it.dateKey != briefing.dateKey } + BriefingStoreDto.from(briefing)
      data = data.copy(briefings = list.sortedByDescending { it.dateKey }.take(MAX_BRIEFINGS))
    }
  }

  override suspend fun loadBriefing(dateKey: String): DailyBriefing? =
    mutex.withLock { data.briefings.firstOrNull { it.dateKey == dateKey }?.toModel() }

  override suspend fun clear() {
    mutate {
      processed.clear()
      data = StoreFileDto()
      itemsFlow.value = emptyList()
    }
  }

  private suspend fun mutate(block: () -> Unit) {
    mutex.withLock {
      block()
      data = data.copy(
        items = itemsFlow.value.map(ItemStoreDto::from),
        processedHashes = processed.toList(),
      )
      val snapshot = data
      withContext(Dispatchers.IO) { writeFile(snapshot) }
    }
  }

  private fun readFile(): StoreFileDto = try {
    if (file.exists()) json.decodeFromString(StoreFileDto.serializer(), file.readText()) else StoreFileDto()
  } catch (_: Exception) {
    StoreFileDto()
  }

  private fun writeFile(snapshot: StoreFileDto) {
    try {
      file.parentFile?.mkdirs()
      val tmp = File(file.parentFile, file.name + ".tmp")
      tmp.writeText(json.encodeToString(StoreFileDto.serializer(), snapshot))
      if (!tmp.renameTo(file)) {
        file.delete()
        tmp.renameTo(file)
      }
    } catch (_: Exception) {
      // Best effort: in-memory state remains authoritative until the next successful write.
    }
  }

  companion object {
    const val FILE_NAME = "astra_intel_store.json"
    const val MAX_ITEMS = 500
    const val MAX_HASHES = 2000
    const val MAX_BRIEFINGS = 7

    private val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      explicitNulls = false
    }
  }
}

@Serializable
internal data class StoreFileDto(
  val version: Int = 1,
  val items: List<ItemStoreDto> = emptyList(),
  val processedHashes: List<String> = emptyList(),
  val briefings: List<BriefingStoreDto> = emptyList(),
)

@Serializable
internal data class ItemStoreDto(
  val id: String,
  val rawEventId: String? = null,
  val category: String,
  val title: String,
  val summary: String,
  val amount: Double? = null,
  val currency: String = "₹",
  val isCredit: Boolean = false,
  val merchant: String? = null,
  val dueDate: String? = null,
  val priority: String = Priority.NORMAL.name,
  val status: String = ItemStatus.ACTIVE.name,
  val timestamp: Long,
  val source: String,
  val sender: String? = null,
  val accountTail: String? = null,
  val reference: String? = null,
) {
  fun toModel(): OrganizedItem? {
    val cat = IntelCategory.entries.firstOrNull { it.name == category } ?: return null
    val src = DataSource.entries.firstOrNull { it.name == source } ?: return null
    return OrganizedItem(
      id = id,
      rawEventId = rawEventId,
      category = cat,
      title = title,
      summary = summary,
      amount = amount,
      currency = currency,
      isCredit = isCredit,
      merchant = merchant,
      dueDate = dueDate,
      priority = Priority.entries.firstOrNull { it.name == priority } ?: Priority.NORMAL,
      status = ItemStatus.entries.firstOrNull { it.name == status } ?: ItemStatus.ACTIVE,
      timestamp = timestamp,
      source = src,
      sender = sender,
      accountTail = accountTail,
      reference = reference,
    )
  }

  companion object {
    fun from(i: OrganizedItem) = ItemStoreDto(
      id = i.id,
      rawEventId = i.rawEventId,
      category = i.category.name,
      title = i.title,
      summary = i.summary,
      amount = i.amount,
      currency = i.currency,
      isCredit = i.isCredit,
      merchant = i.merchant,
      dueDate = i.dueDate,
      priority = i.priority.name,
      status = i.status.name,
      timestamp = i.timestamp,
      source = i.source.name,
      sender = i.sender,
      accountTail = i.accountTail,
      reference = i.reference,
    )
  }
}

@Serializable
internal data class BriefingStoreDto(
  val dateKey: String,
  val headline: String,
  val summary: String,
  val highlights: List<String> = emptyList(),
  val modelUsed: String = "local",
  val createdAt: Long = 0L,
) {
  fun toModel() = DailyBriefing(dateKey, headline, summary, highlights, modelUsed, createdAt)

  companion object {
    fun from(b: DailyBriefing) = BriefingStoreDto(b.dateKey, b.headline, b.summary, b.highlights, b.modelUsed, b.createdAt)
  }
}
