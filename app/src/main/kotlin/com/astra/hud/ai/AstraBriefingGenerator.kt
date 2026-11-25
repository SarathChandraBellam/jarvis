package com.astra.hud.ai

import com.astra.hud.contracts.BriefingGenerator
import com.astra.hud.contracts.OpenRouterClient
import com.astra.hud.model.CalendarEntry
import com.astra.hud.model.DailyBriefing
import com.astra.hud.model.DeviceTelemetry
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.OrganizedItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the daily ASTRA briefing. Uses OpenRouter when an API key is configured,
 * otherwise (or on any failure) composes a deterministic local briefing.
 */
class AstraBriefingGenerator(
  private val client: OpenRouterClient,
  private val clock: () -> Long = System::currentTimeMillis,
  private val zone: ZoneId = ZoneId.systemDefault(),
) : BriefingGenerator {

  override suspend fun generate(
    items: List<OrganizedItem>,
    calendar: List<CalendarEntry>,
    telemetry: DeviceTelemetry,
    settings: AstraSettings,
  ): DailyBriefing = withContext(Dispatchers.Default) {
    val now = clock()
    val ctx = IntelContext(items, calendar, now, zone)
    val dateKey = DATE_KEY.format(ctx.today)
    if (settings.apiKey.isNotBlank()) {
      aiBriefing(ctx, telemetry, settings, dateKey, now)?.let { return@withContext it }
    }
    localBriefing(ctx, telemetry, settings, dateKey, now)
  }

  private suspend fun aiBriefing(
    ctx: IntelContext,
    telemetry: DeviceTelemetry,
    settings: AstraSettings,
    dateKey: String,
    now: Long,
  ): DailyBriefing? = try {
    val callsign = settings.userCallsign.ifBlank { "Sir" }
    val system = """
      You are ASTRA, a dry-witted, impeccably polite personal AI assistant, briefing your principal,
      addressed as "$callsign". Write a crisp daily tactical briefing from the HUD data provided.
      Respond with ONLY a JSON object, no markdown:
      {"headline": string (max 70 chars, a greeting addressing $callsign),
       "summary": string (2-3 sentences, max 320 chars, spoken aloud - no lists or emojis),
       "highlights": array of 3-5 short strings (max 70 chars each), most urgent first}
      Use ₹ for amounts with Indian digit grouping. Never invent data; never reveal OTPs or full account numbers.
    """.trimIndent()
    val user = buildString {
      append(ctx.compactContext(40))
      appendLine("BATTERY: ${telemetry.batteryPercent}%${if (telemetry.isCharging) " (charging)" else ""}")
      appendLine("STORAGE_FREE: ${"%.1f".format(telemetry.freeStorageGb)} of ${"%.0f".format(telemetry.totalStorageGb)} GB")
    }
    client.chat(settings.apiKey, settings.modelId, system, listOf("user" to user)).getOrNull()
      ?.let { AiJson.parseBriefing(it) }
      ?.let {
        DailyBriefing(
          dateKey = dateKey,
          headline = it.headline.trim(),
          summary = it.summary.trim(),
          highlights = it.highlights.map(String::trim),
          modelUsed = settings.modelId,
          createdAt = now,
        )
      }
  } catch (e: CancellationException) {
    throw e
  } catch (_: Exception) {
    null
  }

  internal fun localBriefing(
    ctx: IntelContext,
    telemetry: DeviceTelemetry,
    settings: AstraSettings,
    dateKey: String,
    now: Long,
  ): DailyBriefing {
    val callsign = settings.userCallsign.ifBlank { "Sir" }
    val hour = Instant.ofEpochMilli(now).atZone(zone).hour
    val greeting = when (hour) {
      in 5..11 -> "Good morning"
      in 12..16 -> "Good afternoon"
      in 17..21 -> "Good evening"
      else -> "Working late"
    }
    val headline = "$greeting, $callsign."

    val parts = mutableListOf<String>()
    val critical = ctx.critical.size
    parts += when (critical) {
      0 -> "No critical items"
      1 -> "1 critical item"
      else -> "$critical critical items"
    }
    parts += if (ctx.spentToday > 0) "${IntelFormatting.inr(ctx.spentToday)} spent today" else "no spending logged today"
    if (ctx.bills.isNotEmpty()) parts += "${ctx.bills.size} bill${if (ctx.bills.size == 1) "" else "s"} pending"
    if (ctx.deliveries.isNotEmpty()) parts += "${ctx.deliveries.size} deliver${if (ctx.deliveries.size == 1) "y" else "ies"} in motion"
    val sentence1 = parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    val sentence2 = ctx.nextEntry?.let { "Next on the schedule: ${ctx.describeEntry(it)}." } ?: "Your calendar is clear."
    val sentence3 = when {
      telemetry.batteryPercent <= 20 && !telemetry.isCharging -> "Battery at ${telemetry.batteryPercent}%; I'd recommend a charger."
      telemetry.isCharging -> "Battery at ${telemetry.batteryPercent}% and charging."
      else -> "Battery at ${telemetry.batteryPercent}%."
    }
    val summary = "$sentence1 $sentence2 $sentence3"

    val highlights = mutableListOf<String>()
    ctx.ranked.take(3).forEach { highlights += "[${it.priority.label}] ${it.title}" }
    ctx.nextEntry?.let { highlights += "Next: ${ctx.describeEntry(it)}" }
    highlights += "Battery ${telemetry.batteryPercent}%${if (telemetry.isCharging) " · charging" else ""}"

    return DailyBriefing(
      dateKey = dateKey,
      headline = headline,
      summary = summary,
      highlights = highlights.map { IntelFormatting.truncate(it, 80) },
      modelUsed = "local",
      createdAt = now,
    )
  }

  companion object {
    val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  }
}
