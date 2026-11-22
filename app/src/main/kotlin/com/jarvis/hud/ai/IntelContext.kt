package com.jarvis.hud.ai

import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Aggregations over organized items shared by the briefing generator and the assistant. */
internal class IntelContext(
  private val items: List<OrganizedItem>,
  private val calendar: List<CalendarEntry>,
  private val now: Long,
  private val zone: ZoneId,
) {
  val today: LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

  val active: List<OrganizedItem> = items.filter { it.status == ItemStatus.ACTIVE }

  /** Active items sorted by priority (CRITICAL first) then newest first. */
  val ranked: List<OrganizedItem> = active.sortedWith(compareBy<OrganizedItem> { it.priority.ordinal }.thenByDescending { it.timestamp })

  fun isToday(ts: Long) = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate() == today

  val debitsToday: List<OrganizedItem> =
    items.filter { it.category == IntelCategory.FINANCE && !it.isCredit && it.amount != null && isToday(it.timestamp) }

  val spentToday: Double = debitsToday.sumOf { it.amount ?: 0.0 }

  val creditsToday: Double =
    items.filter { it.category == IntelCategory.FINANCE && it.isCredit && isToday(it.timestamp) }.sumOf { it.amount ?: 0.0 }

  val critical: List<OrganizedItem> = active.filter { it.priority == com.jarvis.hud.model.Priority.CRITICAL }

  val bills: List<OrganizedItem> = active.filter { it.category == IntelCategory.BILL }
    .sortedWith(compareBy<OrganizedItem> { it.dueDate ?: "9999-99-99" }.thenBy { it.priority.ordinal })

  val deliveries: List<OrganizedItem> = active.filter { it.category == IntelCategory.DELIVERY }.sortedByDescending { it.timestamp }

  val upcomingCalendar: List<CalendarEntry> = calendar.filter { it.endTime >= now }.sortedBy { it.startTime }

  val todaysCalendar: List<CalendarEntry> = calendar.filter { isToday(it.startTime) }.sortedBy { it.startTime }

  val nextEntry: CalendarEntry? = upcomingCalendar.firstOrNull()

  fun time(ts: Long): String = TIME.format(Instant.ofEpochMilli(ts).atZone(zone))

  fun describeEntry(e: CalendarEntry): String {
    val day = Instant.ofEpochMilli(e.startTime).atZone(zone).toLocalDate()
    val dayLabel = when (day) {
      today -> ""
      today.plusDays(1) -> "tomorrow "
      else -> DAY.format(day) + " "
    }
    return "${e.title} ${dayLabel}at ${time(e.startTime)}" + (e.location?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
  }

  /** Compact one-line-per-item context for LLM prompts. */
  fun compactContext(maxItems: Int = 40): String = buildString {
    appendLine("NOW: ${DATETIME.format(Instant.ofEpochMilli(now).atZone(zone))}")
    appendLine("SPENT_TODAY: ${IntelFormatting.inr(spentToday)}; RECEIVED_TODAY: ${IntelFormatting.inr(creditsToday)}")
    appendLine("ITEMS (priority|category|title|amount|merchant|due|time):")
    ranked.take(maxItems).forEach { i ->
      append("- ").append(i.priority.name).append('|').append(i.category.name).append('|').append(i.title)
      i.amount?.let { append('|').append(if (i.isCredit) "+" else "-").append(IntelFormatting.inr(it, i.currency)) } ?: append("|")
      append('|').append(i.merchant ?: "")
      append('|').append(i.dueDate ?: "")
      append('|').append(DATETIME.format(Instant.ofEpochMilli(i.timestamp).atZone(zone)))
      appendLine()
    }
    if (todaysCalendar.isEmpty() && upcomingCalendar.isEmpty()) {
      appendLine("CALENDAR: none")
    } else {
      appendLine("CALENDAR:")
      (todaysCalendar + upcomingCalendar).distinctBy { it.id }.take(12).forEach { appendLine("- ${describeEntry(it)}") }
    }
  }

  companion object {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)
  }
}
