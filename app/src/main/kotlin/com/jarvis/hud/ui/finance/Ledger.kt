package com.jarvis.hud.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.model.OrganizedItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DUE_MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

internal fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
  today -> "Today"
  today.minusDays(1) -> "Yesterday"
  else -> DAY_HEADER.format(d)
}

/** Day heading with that day's net outflow on the right. */
@Composable
internal fun DayHeader(date: LocalDate, today: LocalDate, spent: Double) {
  Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
    Text(dayLabel(date, today), style = Fin.Small.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
    if (spent > 0) Text(rupees(spent), style = Fin.Small)
  }
}

/** One payment: category dot, who, what kind and when, amount. Tap opens the details sheet. */
@Composable
internal fun TransactionRow(item: OrganizedItem, zone: ZoneId, onClick: () -> Unit, today: LocalDate? = null) {
  val credit = item.isCredit
  val name = SpendInsights.displayMerchant(item).takeIf { it != SpendInsights.UNKNOWN_MERCHANT }
    ?: if (credit) "Money received" else "Payment"
  val category = SpendInsights.categorize(item)
  val kind = if (credit) SpendInsights.incomeKind(item).label else category.label
  val at = Instant.ofEpochMilli(item.timestamp).atZone(zone)
  // In a list that is not grouped by day (Overview's "Recent"), say which day as well.
  val time = (today?.let { dayLabel(at.toLocalDate(), it) + ", " } ?: "") + CLOCK.format(at)
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Money in is a hollow green ring, so it never reads as a spending category's dot.
    Box(
      Modifier.size(8.dp).clip(CircleShape).then(
        if (credit) Modifier.border(1.5.dp, Fin.Positive, CircleShape) else Modifier.background(Fin.color(category)),
      ),
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(name, style = Fin.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("$kind · $time", style = Fin.Small, maxLines = 1)
    }
    Spacer(Modifier.width(12.dp))
    Text(
      (if (credit) "+" else "") + rupees(item.amount ?: 0.0),
      style = Fin.Body.copy(color = if (credit) Fin.Positive else Fin.Ink),
    )
  }
}

/** Upcoming bill: a date block, who it's from, how soon, and "Mark paid". Tap opens the details sheet. */
@Composable
internal fun BillRow(item: OrganizedItem, today: LocalDate, onPaid: () -> Unit, onClick: () -> Unit) {
  val due = item.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
  val days = due?.let { ChronoUnit.DAYS.between(today, it) }
  val (when_, tone) = when {
    days == null -> "No due date" to Fin.Ink2
    days < 0 -> "Overdue by ${-days} day${if (days == -1L) "" else "s"}" to Fin.Critical
    days == 0L -> "Due today" to Fin.Critical
    days == 1L -> "Due tomorrow" to Fin.Critical
    days <= 7 -> "Due in $days days" to Fin.Warning
    else -> "Due in $days days" to Fin.Ink2
  }
  val kind = item.title.substringBefore(" · ").trim()
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      Modifier.width(48.dp).clip(RoundedCornerShape(10.dp)).background(Fin.Raised).padding(vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(due?.dayOfMonth?.toString() ?: "—", style = Fin.Figure)
      Text(due?.let { DUE_MONTH.format(it) } ?: "", style = Fin.Tiny)
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      val name = SpendInsights.billerName(item)
      Text(name, style = Fin.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (!kind.equals(name, ignoreCase = true)) Text(kind, style = Fin.Small, maxLines = 1)
      Text(when_, style = Fin.Small.copy(color = tone))
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(item.amount?.let(::rupees) ?: "", style = Fin.BodyStrong)
      Text(
        "Mark paid",
        style = Fin.Small.copy(color = Fin.Link, fontWeight = FontWeight.Medium),
        modifier = Modifier.clickable(role = Role.Button, onClick = onPaid).padding(vertical = 2.dp),
      )
    }
  }
}
