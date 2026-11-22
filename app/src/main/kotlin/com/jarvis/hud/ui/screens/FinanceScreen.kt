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
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.ui.components.HudBar
import com.jarvis.hud.ui.components.HudEmptyLine
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.HudPanel
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.StatTile
import com.jarvis.hud.ui.theme.HudColors

private data class FinanceSummary(
  val todayDebit: Double,
  val todayCredit: Double,
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
    .groupBy { (it.merchant ?: it.title).trim().uppercase() }
    .mapValues { (_, v) -> v.sumOf { it.amount ?: 0.0 } }
    .entries.sortedByDescending { it.value }
    .take(5)
    .map { it.key to it.value }
  val bills = items
    .filter { it.category == IntelCategory.BILL && it.status == ItemStatus.ACTIVE }
    .sortedWith(compareBy(nullsLast()) { it.dueDate })
  return FinanceSummary(
    todayDebit = sum(today, false),
    todayCredit = sum(today, true),
    weekDebit = sum(weekStart, false),
    weekCredit = sum(weekStart, true),
    perDay = perDay,
    merchants = merchants,
    bills = bills,
    transactions = tx.sortedByDescending { it.timestamp },
  )
}

@Composable
fun FinanceScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val s = remember(state.items) { summarize(state.items, System.currentTimeMillis()) }
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = screenPadding(contentPadding),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item(key = "totals") {
      HudPanel(title = "Ledger") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          StatTile("Today out", HudFormat.money(s.todayDebit), Modifier.weight(1f), HudColors.Red, icon = Icons.Outlined.ArrowUpward)
          StatTile("Today in", HudFormat.money(s.todayCredit), Modifier.weight(1f), HudColors.Green, icon = Icons.Outlined.ArrowDownward)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          StatTile("7-day out", HudFormat.money(s.weekDebit), Modifier.weight(1f), HudColors.Red)
          StatTile("7-day in", HudFormat.money(s.weekCredit), Modifier.weight(1f), HudColors.Green)
        }
        Spacer(Modifier.height(10.dp))
        val net = s.weekCredit - s.weekDebit
        Text(
          "NET 7D ${if (net >= 0) "+" else "−"}${HudFormat.money(kotlin.math.abs(net))}",
          style = MaterialTheme.typography.labelMedium,
          color = if (net >= 0) HudColors.Green else HudColors.Amber,
        )
      }
    }
    item(key = "chart") {
      HudPanel(title = "Spend vector // 7D") { SpendChart(s.perDay) }
    }
    item(key = "bills") {
      HudPanel(title = "Upcoming bills", accent = HudColors.Amber,
        trailing = { Text("${s.bills.size}", style = MaterialTheme.typography.labelSmall, color = HudColors.Amber) }) {
        if (s.bills.isEmpty()) HudEmptyLine("No bills pending")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          s.bills.forEach { ItemCard(it, actions::onMarkItemStatus) }
        }
      }
    }
    item(key = "merchants") {
      HudPanel(title = "Top merchants // 7D", accent = HudColors.Violet) {
        if (s.merchants.isEmpty()) HudEmptyLine("No merchant spend")
        val max = s.merchants.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
        s.merchants.forEachIndexed { i, (name, amt) ->
          Column(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("%02d".format(i + 1), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
              Spacer(Modifier.width(8.dp))
              Text(name, style = MaterialTheme.typography.labelMedium, color = HudColors.Text, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              Text(HudFormat.money(amt), style = MaterialTheme.typography.labelLarge, color = HudColors.Violet)
            }
            Spacer(Modifier.height(4.dp))
            HudBar((amt / max).toFloat(), color = HudColors.Violet, segments = 30)
          }
        }
      }
    }
    item(key = "txHeader") {
      Text(
        "TRANSACTIONS // ${s.transactions.size}",
        style = MaterialTheme.typography.labelMedium,
        color = HudColors.Cyan,
        modifier = Modifier.padding(top = 4.dp),
      )
    }
    if (s.transactions.isEmpty()) {
      item(key = "txEmpty") { HudEmptyLine("No transactions detected. Run a scan.") }
    }
    items(s.transactions, key = { "tx-" + it.id }) { ItemCard(it, actions::onMarkItemStatus) }
  }
}

@Composable
private fun SpendChart(perDay: List<Pair<Long, Double>>) {
  val max = perDay.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
  Column {
    Row(Modifier.fillMaxWidth()) {
      perDay.forEach { (_, v) ->
        Text(
          if (v > 0) HudFormat.moneyCompact(v) else "—",
          style = MaterialTheme.typography.labelSmall,
          color = if (v == max && v > 0) HudColors.Amber else HudColors.TextDim,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Spacer(Modifier.height(4.dp))
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
      val n = perDay.size.coerceAtLeast(1)
      val slot = size.width / n
      val barW = slot * 0.46f
      // Guide lines.
      val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
      for (g in 1..3) {
        val y = size.height * g / 4f
        drawLine(HudColors.BorderDim, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
      }
      drawLine(HudColors.Border, Offset(0f, size.height), Offset(size.width, size.height), 2f)
      perDay.forEachIndexed { i, (_, v) ->
        val h = (v / max).toFloat() * (size.height - 6f)
        val x = slot * i + (slot - barW) / 2
        val isPeak = v == max && v > 0
        val color = if (isPeak) HudColors.Amber else if (i == n - 1) HudColors.Cyan else HudColors.CyanDeep
        if (h > 0f) {
          drawRoundRect(
            Brush.verticalGradient(listOf(color, color.copy(alpha = 0.25f)), startY = size.height - h, endY = size.height),
            topLeft = Offset(x, size.height - h),
            size = Size(barW, h),
            cornerRadius = CornerRadius(2f, 2f),
          )
          drawLine(color, Offset(x - 2f, size.height - h), Offset(x + barW + 2f, size.height - h), 3f)
        } else {
          drawLine(HudColors.TextMuted, Offset(x, size.height - 2f), Offset(x + barW, size.height - 2f), 2f)
        }
      }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
      perDay.forEachIndexed { i, (day, _) ->
        Text(
          if (i == perDay.lastIndex) "TODAY" else HudFormat.dayShort(day),
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
