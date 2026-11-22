package com.jarvis.hud.ai

import com.jarvis.hud.contracts.IntelOrganizer
import com.jarvis.hud.contracts.OpenRouterClient
import com.jarvis.hud.contracts.Redactor
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.RawEvent
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * AI-first organizer: events whose source is in settings.aiShareSources are sent (redacted if
 * configured) to OpenRouter in batches of [batchSize]; everything else, and any AI failure or
 * missing entry, goes through [LocalIntelParser]. Output order matches input order.
 */
class HybridIntelOrganizer(
  private val client: OpenRouterClient,
  private val redactor: Redactor = RegexRedactor(),
  private val localParser: LocalIntelParser = LocalIntelParser(),
  private val batchSize: Int = MAX_BATCH,
) : IntelOrganizer {

  override suspend fun organize(events: List<RawEvent>, settings: JarvisSettings): List<OrganizedItem> =
    withContext(Dispatchers.Default) {
      if (events.isEmpty()) return@withContext emptyList()
      val aiResults = HashMap<String, OrganizedItem>()
      if (settings.apiKey.isNotBlank()) {
        val eligible = events.filter { it.source in settings.aiShareSources }
        for (chunk in eligible.chunked(batchSize.coerceIn(1, MAX_BATCH))) {
          aiResults.putAll(organizeBatch(chunk, settings))
        }
      }
      events.map { event -> aiResults[event.id] ?: safeLocal(event) }
    }

  private suspend fun organizeBatch(chunk: List<RawEvent>, settings: JarvisSettings): Map<String, OrganizedItem> {
    return try {
      val payload = buildUserPayload(chunk, settings.redactSensitiveData)
      val result = client.chat(settings.apiKey, settings.modelId, SYSTEM_PROMPT, listOf("user" to payload))
      result.getOrNull()?.let { AiJson.parseOrganizedItems(it, chunk) } ?: emptyMap()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      emptyMap()
    }
  }

  internal fun buildUserPayload(chunk: List<RawEvent>, redact: Boolean): String {
    val arr = JsonArray(
      chunk.map { e ->
        fun clean(s: String) = if (redact) redactor.redact(s) else s
        buildJsonObject {
          put("rawEventId", e.id)
          put("source", e.source.name)
          put("sender", clean(e.sender))
          put("title", clean(e.title))
          put("body", clean(IntelFormatting.truncate(e.body, 600)))
          put("time", Instant.ofEpochMilli(e.timestamp).toString())
        }
      },
    )
    return "Today is ${Instant.now()}. Organize these ${chunk.size} events:\n$arr"
  }

  private fun safeLocal(event: RawEvent): OrganizedItem = localParser.parse(event)

  companion object {
    const val MAX_BATCH = 25

    val SYSTEM_PROMPT = """
      You are the intelligence-triage subsystem of J.A.R.V.I.S., running for a user in India.
      You receive a JSON array of raw phone events (SMS, notifications, calendar, calls).
      Respond with ONLY a JSON array - no prose, no markdown fences. One object per input event:
      {
        "rawEventId": string (copy exactly from input),
        "category": one of "FINANCE","BILL","DELIVERY","TRAVEL","ACTION","MESSAGE","SCHEDULE","SYSTEM",
        "title": string, max 60 chars, crisp HUD label (e.g. "₹4,250 debited · Swiggy"),
        "summary": string, max 160 chars, one factual sentence,
        "amount": number or null (rupees, no symbols or commas),
        "currency": "₹" unless clearly another currency,
        "isCredit": boolean (true only when money came IN),
        "merchant": string or null,
        "dueDate": "yyyy-MM-dd" or null,
        "priority": one of "CRITICAL","HIGH","NORMAL","LOW"
      }
      Rules: bank debits/credits/UPI -> FINANCE. Bills, EMIs, card statements with a due date -> BILL
      (CRITICAL if due within 2 days, else HIGH). Shipping/out for delivery/delivered -> DELIVERY.
      PNR, flights, trains, boarding -> TRAVEL. OTPs and verification codes -> SYSTEM with LOW priority
      and never repeat the code. Meetings/reminders -> SCHEDULE. Tasks needing a reply/decision -> ACTION.
      Promotions -> MESSAGE with LOW priority. Never invent facts; masked values (XXXX, ****) stay masked.
    """.trimIndent()
  }
}
