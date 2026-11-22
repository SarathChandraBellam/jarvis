package com.jarvis.hud.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.ui.components.EmptyLine
import com.jarvis.hud.ui.components.EmptyState
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.startOfMonth
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.JCard
import com.jarvis.hud.ui.components.Meter
import com.jarvis.hud.ui.components.Refreshable
import androidx.compose.ui.platform.testTag
import com.jarvis.hud.ui.components.SectionHeader
import com.jarvis.hud.ui.theme.HudColors
import kotlin.math.abs

private data class FinanceSummary(
  val todayDebit: Double,
  val monthDebit: Double,
  val weekDebit: Double,
  val weekCredit: Double,
  /** Oldest → today, 7 entries: (day start millis, debit total). */
  val perDay: List<Pair<Long, Double>>,
  val merchants: List<Pair<String, Double>>,
  val bills: List<OrganizedItem>,
  val transactions: List<OrganizedItem>,
)

private fun summarize(items: List<OrganizedItem>, now: Long): FinanceSummary {
  val tx = items.filter { it.category == IntelCategory.FINANCE && it.status != ItemStatus.DISMISSED }
  val money = tx.filter { it.amount != null }
  val today = HudFormat.startOfDay(now)
  val weekStart = HudFormat.startOfDay(now, 6)
  fun sum(from: Long, credit: Boolean) =
    money.filter { it.timestamp >= from && it.isCredit == credit }.sumOf { it.amount ?: 0.0 }
  val perDay = (6 downTo 0).map { ago ->
    val start = HudFormat.startOfDay(now, ago)
    val end = start + 24 * 3_600_000L
    start to money.filter { !it.isCredit && it.timestamp in start until end }.sumOf { it.amount ?: 0.0 }
  }
  val merchants = money
    .filter { !it.isCredit && it.timestamp >= weekStart }
    .groupBy { (it.merchant ?: it.title).trim() }
    .mapValues { (_, v) -> v.sumOf { it.amount ?: 0.0 } }
    .entries.sortedByDescending { it.value }
    .take(5)
    .map { it.key to it.value }
  val bills = items
    .filter { it.category == IntelCategory.BILL && it.status == ItemStatus.ACTIVE }
    .sortedWith(compareBy(nullsLast()) { it.dueDate })
  return FinanceSummary(
    todayDebit = sum(today, false),
    monthDebit = sum(HudFormat.startOfMonth(now), false),
    weekDebit = sum(weekStart, false),
    weekCredit = sum(weekStart, true),
    perDay = perDay,
    merchants = merchants,
    bills = bills,
    transactions = tx.sortedByDescending { it.timestamp },
  )
}

/** Money tab: weekly overview, spend chart, bills, merchants, transactions. */
@Composable
fun FinanceScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val s = remember(state.items) { summarize(state.items, System.currentTimeMillis()) }
  Refreshable(state.isSyncing, actions::onScanAndOrganize, Modifier.fillMaxSize()) {
  LazyColumn(
    Modifier.fillMaxSize().testTag("screen-money"),
    contentPadding = screenPadding(contentPadding),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "overview") {
      JCard(padding = 18.dp) {
        Text("Spent in the last 7 days", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
        Spacer(Modifier.height(4.dp))
        Text(HudFormat.money(s.weekDebit), style = MaterialTheme.typography.displaySmall, color = HudColors.Text)
        Spacer(Modifier.height(2.dp))
        Text("${HudFormat.money(s.todayDebit)} today", style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          FlowTile("Money in", s.weekCredit, Icons.Outlined.SouthWest, HudColors.Green, Modifier.weight(1f))
          FlowTile("Money out", s.weekDebit, Icons.Outlined.NorthEast, HudColors.Red, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        val net = s.weekCredit - s.weekDebit
        Text(
          "Net this week: ${if (net >= 0) "+" else "−"}${HudFormat.money(abs(net))}",
          style = MaterialTheme.typography.labelLarge,
          color = if (net >= 0) HudColors.Green else HudColors.Amber,
        )
      }
    }
    if (state.settings.monthlyBudget > 0) {
      item(key = "budget") { BudgetCard(s.monthDebit, state.settings.monthlyBudget) }
    }
    item(key = "chart") {
      SectionHeader("Daily spending")
      Spacer(Modifier.height(8.dp))
      JCard { SpendChart(s.perDay) }
    }
    item(key = "bills") {
      SectionHeader("Upcoming bills", trailing = if (s.bills.isEmpty()) null else "${s.bills.size} due")
    }
    if (s.bills.isEmpty()) {
      item(key = "billsEmpty") { JCard { EmptyLine("No bills pending.") } }
    } else {
      items(s.bills, key = { "bill-" + it.id }) { ItemCard(it, actions::onMarkItemStatus, onClick = { actions.onOpenItem(it.id) }) }
    }
    item(key = "merchants") {
      SectionHeader("Where it went")
      Spacer(Modifier.height(8.dp))
      JCard {
        if (s.merchants.isEmpty()) EmptyLine("No spending this week.")
        val max = s.merchants.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
        s.merchants.forEachIndexed { i, (name, amt) ->
          if (i > 0) Spacer(Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text, maxLines = 1,
              overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(HudFormat.money(amt), style = MaterialTheme.typography.labelLarge, color = HudColors.Text)
          }
          Spacer(Modifier.height(6.dp))
          Meter((amt / max).toFloat(), HudColors.Violet)
        }
      }
    }
    item(key = "txHeader") { SectionHeader("Transactions", trailing = "${s.transactions.size}") }
    if (s.transactions.isEmpty()) {
      item(key = "txEmpty") {
        JCard {
          EmptyState(
            Icons.Outlined.AccountBalanceWallet,
            "No transactions yet",
            "Allow SMS access and refresh. Bank and UPI alerts will show up here.",
          )
        }
      }
    }
    items(s.transactions, key = { "tx-" + it.id }) { ItemCard(it, actions::onMarkItemStatus, compact = true, onClick = { actions.onOpenItem(it.id) }) }
  }
  }
}

@Composable
private fun FlowTile(label: String, amount: Double, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier) {
  JCard(modifier, color = HudColors.SurfaceHigh, borderColor = Color.Transparent, padding = 12.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 6.dp))
      Text(label, style = MaterialTheme.typography.labelMedium, color = HudColors.TextDim)
    }
    Spacer(Modifier.height(4.dp))
    Text(HudFormat.money(amount), style = MaterialTheme.typography.titleMedium, color = HudColors.Text, maxLines = 1)
  }
}

@Composable
private fun SpendChart(perDay: List<Pair<Long, Double>>) {
  val max = perDay.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
  Column {
    Row(Modifier.fillMaxWidth()) {
      perDay.forEach { (_, v) ->
        Text(
          if (v > 0) HudFormat.moneyCompact(v) else "",
          style = MaterialTheme.typography.labelSmall,
          color = if (v == max && v > 0) HudColors.Amber else HudColors.TextMuted,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
      val n = perDay.size.coerceAtLeast(1)
      val slot = size.width / n
      val barW = slot * 0.5f
      val radius = CornerRadius(barW / 2, barW / 2)
      perDay.forEachIndexed { i, (_, v) ->
        val x = slot * i + (slot - barW) / 2
        // Track.
        drawRoundRect(HudColors.SurfaceHigh, Offset(x, 0f), Size(barW, size.height), radius)
        val h = ((v / max).toFloat() * size.height).coerceAtLeast(if (v > 0) barW else 0f)
        if (h > 0f) {
          val color = when {
            v == max -> HudColors.Amber
            i == n - 1 -> HudColors.Cyan
            else -> HudColors.Cyan.copy(alpha = 0.55f)
          }
          drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), radius)
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
      perDay.forEachIndexed { i, (day, _) ->
        Text(
          if (i == perDay.lastIndex) "Today" else HudFormat.dayShort(day),
          style = MaterialTheme.typography.labelSmall,
          color = if (i == perDay.lastIndex) HudColors.Cyan else HudColors.TextMuted,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun BudgetCard(spent: Double, budget: Double) {
  val frac = (spent / budget).toFloat()
  val color = when {
    frac >= 1f -> HudColors.Red
    frac >= 0.8f -> HudColors.Amber
    else -> HudColors.Green
  }
  val left = budget - spent
  JCard(Modifier.testTag("budgetCard")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Monthly budget", style = MaterialTheme.typography.titleSmall, color = HudColors.Text, modifier = Modifier.weight(1f))
      Text("${(frac * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.labelLarge, color = color)
    }
    Spacer(Modifier.height(10.dp))
    Meter(frac, color, height = 10.dp)
    Spacer(Modifier.height(8.dp))
    Text(
      if (left >= 0) "${HudFormat.money(left)} left of ${HudFormat.money(budget)} this month"
      else "${HudFormat.money(-left)} over your ${HudFormat.money(budget)} budget",
      style = MaterialTheme.typography.bodySmall,
      color = if (left >= 0) HudColors.TextDim else HudColors.Red,
    )
  }
}
