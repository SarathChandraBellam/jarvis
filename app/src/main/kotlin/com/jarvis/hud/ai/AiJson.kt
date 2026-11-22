package com.jarvis.hud.ai

import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.RawEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Tolerant parsing of model JSON output (fenced or not). Never throws. */
object AiJson {

  val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
  }

  /** Strips ```json fences and surrounding prose, returning the outermost JSON array/object text. */
  fun extractJson(raw: String): String? {
    var s = raw.trim()
    FENCE.find(s)?.let { s = it.groupValues[1].trim() }
    val start = s.indexOfFirst { it == '[' || it == '{' }
    if (start < 0) return null
    val open = s[start]
    val close = if (open == '[') ']' else '}'
    val end = s.lastIndexOf(close)
    if (end <= start) return null
    return s.substring(start, end + 1)
  }

  /**
   * Parses a JSON array of organized items (or an object wrapping one under "items") and maps
   * entries back to their source events by rawEventId. Entries with unknown ids or invalid
   * categories are dropped so the caller can fall back to the local parser.
   */
  fun parseOrganizedItems(raw: String, events: List<RawEvent>): Map<String, OrganizedItem> {
    val text = extractJson(raw) ?: return emptyMap()
    val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return emptyMap()
    val array: JsonArray = when (element) {
      is JsonArray -> element
      is JsonObject -> (element["items"] ?: element["results"] ?: element["data"]) as? JsonArray ?: JsonArray(listOf(element))
      else -> return emptyMap()
    }
    val byId = events.associateBy { it.id }
    val out = LinkedHashMap<String, OrganizedItem>()
    for (entry in array) {
      val obj = entry as? JsonObject ?: continue
      val dto = runCatching { json.decodeFromJsonElement(AiItemDto.serializer(), normalize(obj)) }.getOrNull() ?: continue
      val event = byId[dto.rawEventId] ?: continue
      val category = parseCategory(dto.category) ?: continue
      val title = dto.title?.trim().orEmpty()
      if (title.isEmpty()) continue
      out[event.id] = OrganizedItem(
        id = LocalIntelParser.itemId(event),
        rawEventId = event.id,
        category = category,
        title = IntelFormatting.truncate(title, 80),
        summary = IntelFormatting.truncate(dto.summary?.ifBlank { null } ?: event.body, 240),
        amount = dto.amount?.takeIf { it >= 0 },
        currency = dto.currency?.takeIf { it.isNotBlank() && it.length <= 4 }?.let { if (it.equals("INR", true) || it.equals("Rs", true)) "₹" else it } ?: "₹",
        isCredit = dto.isCredit ?: false,
        merchant = dto.merchant?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) },
        dueDate = dto.dueDate?.trim()?.takeIf { ISO_DAY.matches(it) },
        priority = parsePriority(dto.priority) ?: Priority.NORMAL,
        timestamp = event.timestamp,
        source = event.source,
      )
    }
    return out
  }

  /** Parses a briefing object {headline, summary, highlights[]}. Returns null on garbage/blank. */
  fun parseBriefing(raw: String): BriefingDto? {
    val text = extractJson(raw) ?: return null
    val dto = runCatching { json.decodeFromString(BriefingDto.serializer(), text) }.getOrNull() ?: return null
    if (dto.headline.isBlank() || dto.summary.isBlank()) return null
    return dto.copy(highlights = dto.highlights.filter { it.isNotBlank() }.take(6))
  }

  fun parseCategory(value: String?): IntelCategory? {
    val v = value?.trim()?.uppercase()?.removeSuffix("S") ?: return null
    return IntelCategory.entries.firstOrNull { it.name == v || it.name.removeSuffix("S") == v || it.label.uppercase().removeSuffix("S") == v }
  }

  fun parsePriority(value: String?): Priority? {
    val v = value?.trim()?.uppercase() ?: return null
    return when (v) {
      "URGENT" -> Priority.CRITICAL
      "MEDIUM" -> Priority.NORMAL
      else -> Priority.entries.firstOrNull { it.name == v }
    }
  }

  /** Coerces numeric strings like "₹1,234.50" into numbers and string booleans into booleans. */
  private fun normalize(obj: JsonObject): JsonObject {
    val map = obj.toMutableMap()
    map["amount"]?.let { a ->
      val prim = a as? JsonPrimitive
      if (prim != null && prim.isString) {
        val num = prim.contentOrNull?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        map["amount"] = if (num != null) JsonPrimitive(num) else JsonNull
      } else if (prim != null && prim.doubleOrNull == null && prim.contentOrNull != null) {
        map["amount"] = JsonNull
      }
    }
    map["isCredit"]?.let { b ->
      val c = (b as? JsonPrimitive)?.contentOrNull?.lowercase()
      if (c != null) map["isCredit"] = JsonPrimitive(c == "true" || c == "credit" || c == "yes")
    }
    listOf("title", "summary", "merchant", "dueDate", "category", "priority", "currency", "rawEventId").forEach { key ->
      val e: JsonElement? = map[key]
      if (e is JsonPrimitive && !e.isString && e !is JsonNull) map[key] = JsonPrimitive(e.jsonPrimitive.content)
    }
    return JsonObject(map)
  }

  private val FENCE = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```")
  private val ISO_DAY = Regex("""\d{4}-\d{2}-\d{2}""")
}

@Serializable
internal data class AiItemDto(
  val rawEventId: String,
  val category: String? = null,
  val title: String? = null,
  val summary: String? = null,
  val amount: Double? = null,
  val currency: String? = null,
  val isCredit: Boolean? = null,
  val merchant: String? = null,
  val dueDate: String? = null,
  val priority: String? = null,
)

@Serializable
data class BriefingDto(
  val headline: String = "",
  val summary: String = "",
  val highlights: List<String> = emptyList(),
)
