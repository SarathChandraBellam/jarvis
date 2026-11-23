package com.jarvis.hud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.DayPart
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.ui.components.HeroFigure
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.MiniBars
import com.jarvis.hud.ui.components.QuietLine
import com.jarvis.hud.ui.components.QuietMetric
import com.jarvis.hud.ui.components.QuietSection
import com.jarvis.hud.ui.components.SegmentBar
import com.jarvis.hud.ui.components.ShareRow
import com.jarvis.hud.ui.components.rampColor
import com.jarvis.hud.ui.theme.HudColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val DAY_LETTER = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Whole rupees with Indian grouping: ₹1,23,456. */
private fun rupees(v: Double) = com.jarvis.hud.ui.components.HudFormat.money(v.roundToLong().toDouble())

@Composable
fun FinanceScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val zone = remember { ZoneId.systemDefault() }
  val s = remember(state.items) { SpendInsights.from(state.items, System.currentTimeMillis(), zone) }
  val bills = remember(state.items) {
    state.items
      .filter { it.category == IntelCategory.BILL && it.status == ItemStatus.ACTIVE }
      .sortedWith(compareBy(nullsLast()) { it.dueDate })
  }
  val transactions = remember(state.items) {
    state.items.filter { it.category == IntelCategory.FINANCE && it.status != ItemStatus.DISMISSED }
      .sortedByDescending { it.timestamp }
  }

  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = screenPadding(contentPadding).let {
      PaddingValues(start = 20.dp, end = 20.dp, top = it.calculateTopPadding() + 8.dp, bottom = it.calculateBottomPadding())
    },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item(key = "hero") { Overview(s) }

    if (!s.hasData) {
      item(key = "empty") {
        Text(
          "No transactions yet. Run a scan and your spending will appear here.",
          style = MaterialTheme.typography.bodyMedium,
          color = HudColors.TextDim,
        )
      }
    }

    if (s.hasData) {
      item(key = "week") {
        QuietSection("Last 7 days", trailing = "Today ${rupees(s.todaySpent)}") {
          MiniBars(
            values = s.last7Days.map { it.amount },
            labels = s.last7Days.map { DAY_LETTER.format(it.date) },
          )
        }
      }
    }

    if (s.categories.isNotEmpty()) {
      item(key = "categories") {
        QuietSection("Where it goes", trailing = s.monthLabel) {
          s.categories.forEachIndexed { i, c ->
            ShareRow(
              label = c.key.label,
              detail = "${(c.fraction * 100).roundToLong()}%",
              value = rupees(c.amount),
              fraction = c.fraction / s.categories.first().fraction,
              color = rampColor(i),
            )
          }
        }
      }
    }

    if (s.dayParts.isNotEmpty()) {
      item(key = "patterns") { Patterns(s) }
    }

    if (s.merchants.isNotEmpty()) {
      item(key = "merchants") {
        QuietSection("Top merchants", trailing = s.monthLabel) {
          val max = s.merchants.first().amount
          s.merchants.forEach { m ->
            ShareRow(
              label = m.name,
              detail = if (m.count == 1) "1 payment" else "${m.count} payments",
              value = rupees(m.amount),
              fraction = (m.amount / max).toFloat(),
              color = HudColors.Cyan.copy(alpha = 0.55f),
            )
          }
        }
      }
    }

    if (s.recurring.isNotEmpty()) {
      item(key = "recurring") {
        QuietSection("Recurring", trailing = "${rupees(s.recurring.sumOf { it.amount })} / month") {
          s.recurring.forEach { r ->
            QuietLine("${r.name}  ·  last ${DAY_MONTH.format(r.lastSeen)}", rupees(r.amount))
          }
        }
      }
    }

    if (s.income.isNotEmpty()) {
      item(key = "income") {
        QuietSection("Money in", trailing = rupees(s.monthReceived)) {
          s.income.forEachIndexed { i, share ->
            ShareRow(
              label = share.key.label,
              detail = "${(share.fraction * 100).roundToLong()}%",
              value = rupees(share.amount),
              fraction = share.fraction,
              color = rampColor(i, HudColors.Green),
            )
          }
        }
      }
    }

    item(key = "bills") {
      QuietSection("Upcoming bills", trailing = if (bills.isEmpty()) "None" else rupees(bills.sumOf { it.amount ?: 0.0 })) {
        if (bills.isEmpty()) {
          Text("Nothing due.", style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          bills.forEach { ItemCard(it, actions::onMarkItemStatus, compact = true) }
        }
      }
    }

    item(key = "txHeader") {
      QuietSection("Transactions", trailing = "${transactions.size}") {}
    }
    items(transactions, key = { "tx-" + it.id }) { TransactionRow(it, zone) { actions.onMarkItemStatus(it.id, ItemStatus.DISMISSED) } }
    item(key = "end") { Spacer(Modifier.height(8.dp)) }
  }
}

private val TX_TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/** One quiet ledger line: merchant, category · time, signed amount, and a small dismiss control. */
@Composable
private fun TransactionRow(item: OrganizedItem, zone: ZoneId, onDismiss: () -> Unit) {
  val credit = item.isCredit
  val name = SpendInsights.displayMerchant(item).takeIf { it != SpendInsights.UNKNOWN_MERCHANT }
    ?: if (credit) "Money received" else "Payment"
  val kind = if (credit) SpendInsights.incomeKind(item).label else SpendInsights.categorize(item).label
  val time = TX_TIME.format(Instant.ofEpochMilli(item.timestamp).atZone(zone))
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(2.dp))
      Text("$kind · $time", style = MaterialTheme.typography.bodySmall, color = HudColors.TextMuted, maxLines = 1)
    }
    Spacer(Modifier.width(12.dp))
    Text(
      (if (credit) "+" else "−") + com.jarvis.hud.ui.components.HudFormat.money(item.amount ?: 0.0, item.currency),
      style = MaterialTheme.typography.bodyMedium,
      color = if (credit) HudColors.Green else HudColors.Text,
      maxLines = 1,
    )
    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
      Icon(Icons.Outlined.Close, contentDescription = "Hide $name", tint = HudColors.TextMuted, modifier = Modifier.size(14.dp))
    }
  }
}

@Composable
private fun Overview(s: SpendInsights) {
  val delta = s.monthDelta
  val caption = when {
    delta == null -> "Spent so far this month"
    abs(delta) < 0.005 -> "Same as this point last month"
    delta > 0 -> "↑ ${(delta * 100).roundToLong()}% vs this point last month"
    else -> "↓ ${(abs(delta) * 100).roundToLong()}% vs this point last month"
  }
  val captionColor = when {
    delta == null || abs(delta) < 0.005 -> HudColors.TextDim
    delta > 0 -> HudColors.Amber
    else -> HudColors.Green
  }
  Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
    HeroFigure("Spent in ${s.monthLabel}", rupees(s.monthSpent), caption, captionColor)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      QuietMetric("Per day", rupees(s.dailyAverage), Modifier.weight(1f))
      QuietMetric("On pace for", rupees(s.projectedMonth), Modifier.weight(1f))
      QuietMetric("Received", rupees(s.monthReceived), Modifier.weight(1f), valueColor = HudColors.Green)
    }
  }
}

@Composable
private fun Patterns(s: SpendInsights) {
  QuietSection("Patterns", trailing = "Last 4 weeks") {
    Text("When you spend", style = MaterialTheme.typography.bodySmall, color = HudColors.TextMuted)
    Spacer(Modifier.height(10.dp))
    val order = s.dayParts.sortedByDescending { it.amount }.map { it.key }
    SegmentBar(
      DayPart.entries.mapNotNull { part ->
        s.dayParts.firstOrNull { it.key == part }?.let { Triple(part.label, it.fraction, rampColor(order.indexOf(part))) }
      },
    )
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      QuietMetric("Weekday avg", rupees(s.weekdayAverage), Modifier.weight(1f))
      QuietMetric("Weekend avg", rupees(s.weekendAverage), Modifier.weight(1f))
      QuietMetric("Payments", "${s.monthTransactions}", Modifier.weight(1f))
    }
    val note = weekendNote(s.weekdayAverage, s.weekendAverage)
    val largest = s.largest
    if (note != null || largest != null) Spacer(Modifier.height(16.dp))
    if (note != null) {
      Text(note, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
    }
    if (largest != null) {
      Spacer(Modifier.height(4.dp))
      Text(
        "Largest this month: ${rupees(largest.amount ?: 0.0)} · ${SpendInsights.displayMerchant(largest)}",
        style = MaterialTheme.typography.bodySmall,
        color = HudColors.TextDim,
      )
    }
  }
}

private fun weekendNote(weekday: Double, weekend: Double): String? {
  if (weekday <= 0 || weekend <= 0) return null
  val ratio = weekend / weekday
  return when {
    ratio >= 1.25 -> "You spend %.1f× more per day on weekends.".format(Locale.ENGLISH, ratio)
    ratio <= 0.8 -> "You spend %.1f× more per day on weekdays.".format(Locale.ENGLISH, 1 / ratio)
    else -> "Spending is even across the week."
  }
}
