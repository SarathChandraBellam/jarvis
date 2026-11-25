package com.astra.hud.ai

import com.astra.hud.contracts.AskAssistant
import com.astra.hud.contracts.OpenRouterClient
import com.astra.hud.model.CalendarEntry
import com.astra.hud.model.ChatMessage
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.OrganizedItem
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ask-ASTRA console backend. With an API key, chats via OpenRouter using a persona
 * system prompt plus compact HUD context and the last [HISTORY_TURNS] turns. Without a key
 * (or if the uplink fails) answers simple intents locally.
 */
class AstraAskAssistant(
  private val client: OpenRouterClient,
  private val clock: () -> Long = System::currentTimeMillis,
  private val zone: ZoneId = ZoneId.systemDefault(),
) : AskAssistant {

  override suspend fun answer(
    prompt: String,
    history: List<ChatMessage>,
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    settings: AstraSettings,
  ): ChatMessage = withContext(Dispatchers.Default) {
    val now = clock()
    val ctx = IntelContext(items, calendar, now, zone)
    val callsign = settings.userCallsign.ifBlank { "Sir" }

    if (settings.apiKey.isNotBlank()) {
      val trimmedHistory = history
        .let { h -> if (h.lastOrNull()?.let { it.isFromUser && it.content.trim() == prompt.trim() } == true) h.dropLast(1) else h }
        .takeLast(HISTORY_TURNS)
      val messages = trimmedHistory.map { (if (it.isFromUser) "user" else "assistant") to it.content } + ("user" to prompt)
      val result = try {
        client.chat(settings.apiKey, settings.modelId, systemPrompt(ctx, callsign), messages)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
      result.getOrNull()?.takeIf { it.isNotBlank() }?.let {
        return@withContext reply(it.trim(), now, settings.modelId)
      }
      val local = localAnswer(prompt, ctx, callsign)
      val reason = result.exceptionOrNull()?.message ?: "no response"
      val text = if (local != null) "$local\n\n(Uplink unavailable: $reason Answered from local data.)"
      else "My apologies, $callsign. The OpenRouter uplink failed: $reason"
      return@withContext reply(text, now, "local")
    }

    val local = localAnswer(prompt, ctx, callsign)
      ?: "I can answer basic questions about spending, bills, deliveries and your schedule offline, $callsign. " +
      "For anything more, please configure your OpenRouter API key in settings."
    reply(local, now, "local")
  }

  private fun reply(text: String, now: Long, model: String) = ChatMessage(
    id = "msg-" + UUID.randomUUID().toString(),
    isFromUser = false,
    content = text,
    timestamp = now,
    modelUsed = model,
  )

  internal fun systemPrompt(ctx: IntelContext, callsign: String): String = """
    You are ASTRA, a personal AI assistant: composed, precise, quietly witty, unfailingly polite.
    Address the user as "$callsign". Answer in at most 4 short sentences unless asked for detail.
    Ground every fact in the HUD context below; if the data does not contain the answer, say so plainly.
    Use ₹ with Indian digit grouping. Never reveal OTPs or full account/card numbers.
    --- HUD CONTEXT ---
    ${ctx.compactContext(40).trimEnd().replace("\n", "\n    ")}
  """.trimIndent()

  internal fun localAnswer(prompt: String, ctx: IntelContext, callsign: String): String? {
    val p = prompt.lowercase()
    return when {
      SPEND.containsMatchIn(p) -> {
        if (ctx.debitsToday.isEmpty()) {
          "No debits recorded today, $callsign. Your wallet is enjoying a quiet day."
        } else {
          val top = ctx.debitsToday.sortedByDescending { it.amount ?: 0.0 }.take(3)
            .joinToString("; ") { "${IntelFormatting.inr(it.amount ?: 0.0, it.currency)}${it.merchant?.let { m -> " at $m" } ?: ""}" }
          "You've spent ${IntelFormatting.inr(ctx.spentToday)} today across ${ctx.debitsToday.size} transaction" +
            "${if (ctx.debitsToday.size == 1) "" else "s"}, $callsign. Largest: $top."
        }
      }
      BILLS.containsMatchIn(p) -> {
        if (ctx.bills.isEmpty()) {
          "No pending bills on record, $callsign."
        } else {
          val list = ctx.bills.take(5).joinToString("; ") { b ->
            b.title + (b.dueDate?.takeIf { d -> !b.title.contains(d) }?.let { " (due $it)" } ?: "")
          }
          "${ctx.bills.size} bill${if (ctx.bills.size == 1) "" else "s"} pending, $callsign: $list."
        }
      }
      DELIVERIES.containsMatchIn(p) -> {
        if (ctx.deliveries.isEmpty()) {
          "No deliveries being tracked at the moment, $callsign."
        } else {
          "Tracking ${ctx.deliveries.size} deliver${if (ctx.deliveries.size == 1) "y" else "ies"}, $callsign: " +
            ctx.deliveries.take(5).joinToString("; ") { it.title } + "."
        }
      }
      SCHEDULE.containsMatchIn(p) -> {
        val entries = ctx.todaysCalendar.filter { it.endTime >= clock() }.ifEmpty { ctx.upcomingCalendar }.take(5)
        if (entries.isEmpty()) {
          "Your calendar is clear, $callsign."
        } else {
          "On your schedule, $callsign: " + entries.joinToString("; ") { ctx.describeEntry(it) } + "."
        }
      }
      PRIORITY.containsMatchIn(p) -> {
        val top = ctx.ranked.take(3)
        if (top.isEmpty()) "Nothing requires your attention, $callsign."
        else "Top priorities, $callsign: " + top.joinToString("; ") { "[${it.priority.label}] ${it.title}" } + "."
      }
      GREETING.containsMatchIn(p) -> "At your service, $callsign. Ask me about spending, bills, deliveries or your schedule."
      else -> null
    }
  }

  companion object {
    const val HISTORY_TURNS = 10
    private val SPEND = Regex("""\b(spen[dt]|spending|expense|expenses|money|debit(?:ed|s)?|how much)\b""")
    private val BILLS = Regex("""\b(bills?|dues?|payments? due|emi|statement)\b""")
    private val DELIVERIES = Regex("""\b(deliver(?:y|ies)?|package|parcel|orders?|shipment|courier)\b""")
    private val SCHEDULE = Regex("""\b(schedule|calendar|meeting|meetings|agenda|appointments?|today|tomorrow|next)\b""")
    private val PRIORITY = Regex("""\b(urgent|critical|priority|priorities|important|attention)\b""")
    private val GREETING = Regex("""^\s*(hi|hello|hey|yo|good (morning|afternoon|evening))\b""")
  }
}
