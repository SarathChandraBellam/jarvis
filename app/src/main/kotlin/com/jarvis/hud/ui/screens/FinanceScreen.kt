package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SpendCategory
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.ui.finance.BillRow
import com.jarvis.hud.ui.finance.CategoryBreakdown
import com.jarvis.hud.ui.finance.DayHeader
import com.jarvis.hud.ui.finance.Divider
import com.jarvis.hud.ui.finance.Fin
import com.jarvis.hud.ui.finance.PaceChart
import com.jarvis.hud.ui.finance.SectionHeader
import com.jarvis.hud.ui.finance.SegmentedTabs
import com.jarvis.hud.ui.finance.SpendCalendar
import com.jarvis.hud.ui.finance.TransactionRow
import com.jarvis.hud.ui.finance.dayLabel
import com.jarvis.hud.ui.finance.rupees
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Finance, organised as three questions:
 *  - Overview: how much have I spent, and is that more than usual? What's due, what just happened?
 *  - Spending: where did it go and when? (tap a category or a day to filter the payments)
 *  - Bills: what do I owe, by when, and what repeats every month?
 */
@Composable
fun FinanceScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val zone = remember { ZoneId.systemDefault() }
  val s = remember(state.items) { SpendInsights.from(state.items, System.currentTimeMillis(), zone) }
  val payments = remember(state.items) {
    state.items.filter { it.category == IntelCategory.FINANCE && it.status != ItemStatus.DISMISSED && it.amount != null }
      .sortedByDescending { it.timestamp }
  }
  val bills = remember(state.items) {
    state.items.filter { it.category == IntelCategory.BILL && it.status == ItemStatus.ACTIVE }
      .sortedWith(compareBy(nullsLast()) { it.dueDate })
  }

  var view by rememberSaveable { mutableStateOf(0) }
  var category by remember { mutableStateOf<SpendCategory?>(null) }
  var day by remember { mutableStateOf<LocalDate?>(null) }
  var open by remember { mutableStateOf<String?>(null) }
  val dateOf: (OrganizedItem) -> LocalDate = { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
  val toggle: (String) -> Unit = { id -> open = if (open == id) null else id }
  val hide: (OrganizedItem) -> Unit = { actions.onMarkItemStatus(it.id, ItemStatus.DISMISSED) }

  val inset = screenPadding(contentPadding)
  LazyColumn(
    Modifier.fillMaxSize().background(Fin.Surface),
    contentPadding = PaddingValues(
      start = 20.dp,
      end = 20.dp,
      top = inset.calculateTopPadding() + 4.dp,
      bottom = inset.calculateBottomPadding() + 8.dp,
    ),
  ) {
    item(key = "tabs") {
      SegmentedTabs(listOf("Overview", "Spending", "Bills"), view, { view = it })
      Spacer(Modifier.height(24.dp))
    }

    when (view) {
      0 -> overview(s, payments, bills, zone, open, toggle, hide, actions) { view = it }
      1 -> {
        item(key = "where") {
          SectionHeader("Where it went in ${s.monthLabel}")
          if (s.categories.isEmpty()) Text("No spending yet this month.", style = Fin.Small)
          CategoryBreakdown(s.categories, category) { category = it }
          Spacer(Modifier.height(28.dp))
          SectionHeader("When")
          SpendCalendar(s, day) { day = it }
          Spacer(Modifier.height(28.dp))
        }
        val filtered = payments.filter { p ->
          (category == null || (!p.isCredit && SpendInsights.categorize(p) == category)) &&
            (day == null || dateOf(p) == day) &&
            (day != null || !dateOf(p).isBefore(s.month.atDay(1)))
        }
        item(key = "ledgerHeader") {
          val scope = listOfNotNull(category?.label, day?.let { dayLabel(it, s.today) }).joinToString(" · ")
          SectionHeader(
            if (scope.isEmpty()) "Payments this month" else scope,
            action = if (scope.isEmpty()) null else "Clear",
            onAction = { category = null; day = null },
          )
          if (filtered.isEmpty()) Text("Nothing here.", style = Fin.Small)
        }
        ledger(filtered, s.today, zone, open, toggle, hide, dateOf)
      }
      else -> billsView(s, bills, actions)
    }
  }
}

private fun LazyListScope.overview(
  s: SpendInsights,
  payments: List<OrganizedItem>,
  bills: List<OrganizedItem>,
  zone: ZoneId,
  open: String?,
  onToggle: (String) -> Unit,
  hide: (OrganizedItem) -> Unit,
  actions: JarvisActions,
  goTo: (Int) -> Unit,
) {
  item(key = "hero") {
    Text("Spent in ${s.monthLabel}", style = Fin.Small)
    Text(rupees(s.monthSpent), style = Fin.Hero)
    val d = s.monthDelta
    val (line, tone) = when {
      d == null -> "Nothing to compare with last month yet" to Fin.Ink2
      abs(d) < 0.01 -> "About the same as this time last month" to Fin.Ink2
      d > 0 -> "${rupees(s.monthSpent - s.lastMonthSameSpan)} more than this time last month (+${(d * 100).roundToLong()}%)" to Fin.Warning
      else -> "${rupees(s.lastMonthSameSpan - s.monthSpent)} less than this time last month (${(d * 100).roundToLong()}%)" to Fin.Positive
    }
    Text(line, style = Fin.Small.copy(color = tone))
    Spacer(Modifier.height(24.dp))
    PaceChart(s)
    Spacer(Modifier.height(24.dp))
    Divider()
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Stat("Per day", rupees(s.dailyAverage), Modifier.weight(1f))
      Stat("Month-end pace", rupees(s.projectedMonth), Modifier.weight(1f))
      Stat("Came in", rupees(s.monthReceived), Modifier.weight(1f))
    }
    Divider()
    Spacer(Modifier.height(20.dp))
  }

  val soon = bills.filter { b ->
    val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    due == null || ChronoUnit.DAYS.between(s.today, due) <= 7
  }.take(3)
  if (soon.isNotEmpty()) {
    item(key = "soon") {
      SectionHeader("Due this week", action = "All bills", onAction = { goTo(2) })
      soon.forEach { b ->
        BillRow(b, s.today, onPaid = { actions.onMarkItemStatus(b.id, ItemStatus.DONE) }, onHide = { hide(b) })
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  item(key = "recentHeader") { SectionHeader("Recent", action = "All payments", onAction = { goTo(1) }) }
  items(payments.take(6), key = { "r-" + it.id }) { p ->
    TransactionRow(p, zone, open == p.id, { onToggle(p.id) }, { hide(p) }, today = s.today)
  }
}

private fun LazyListScope.ledger(
  list: List<OrganizedItem>,
  today: LocalDate,
  zone: ZoneId,
  open: String?,
  onToggle: (String) -> Unit,
  hide: (OrganizedItem) -> Unit,
  dateOf: (OrganizedItem) -> LocalDate,
) {
  list.groupBy(dateOf).forEach { (date, dayItems) ->
    item(key = "d-$date") { DayHeader(date, today, dayItems.filter { !it.isCredit }.sumOf { it.amount ?: 0.0 }) }
    items(dayItems, key = { "t-" + it.id }) { p ->
      TransactionRow(p, zone, open == p.id, { onToggle(p.id) }, { hide(p) })
    }
  }
}

private fun LazyListScope.billsView(s: SpendInsights, bills: List<OrganizedItem>, actions: JarvisActions) {
  item(key = "billsTop") {
    Text("Due", style = Fin.Small)
    Text(rupees(bills.sumOf { it.amount ?: 0.0 }), style = Fin.Hero)
    Text(
      if (bills.isEmpty()) "Nothing due" else "${bills.size} bill${if (bills.size == 1) "" else "s"} open",
      style = Fin.Small,
    )
    Spacer(Modifier.height(20.dp))
  }
  val (soon, later) = bills.partition { b ->
    val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    due != null && ChronoUnit.DAYS.between(s.today, due) <= 7
  }
  fun group(title: String, list: List<OrganizedItem>, key: String) {
    if (list.isEmpty()) return
    item(key = key) {
      SectionHeader(title)
      list.forEach { b ->
        BillRow(
          b, s.today,
          onPaid = { actions.onMarkItemStatus(b.id, ItemStatus.DONE) },
          onHide = { actions.onMarkItemStatus(b.id, ItemStatus.DISMISSED) },
        )
      }
      Spacer(Modifier.height(20.dp))
    }
  }
  group("This week", soon, "b-soon")
  group("Later", later, "b-later")

  if (s.recurring.isNotEmpty()) {
    item(key = "recurring") {
      SectionHeader("Every month")
      Text("Charged the same amount in more than one month: subscriptions, plans, rent.", style = Fin.Small)
      Spacer(Modifier.height(8.dp))
      s.recurring.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
          Column(Modifier.weight(1f)) {
            Text(r.name, style = Fin.Body)
            Text("Seen in ${r.months} months · last ${dayLabel(r.lastSeen, s.today)}", style = Fin.Small)
          }
          Text(rupees(r.amount), style = Fin.Body)
        }
      }
      Divider(Modifier.padding(top = 8.dp))
      Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Committed each month", style = Fin.Small, modifier = Modifier.weight(1f))
        Text(rupees(s.recurring.sumOf { it.amount }), style = Fin.BodyStrong)
      }
    }
  }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(label, style = Fin.Small)
    Spacer(Modifier.height(2.dp))
    Text(value, style = Fin.Figure)
  }
}
