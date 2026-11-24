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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SpendCategory
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.ui.components.EmptyState
import com.jarvis.hud.ui.components.Meter
import com.jarvis.hud.ui.components.Refreshable
import com.jarvis.hud.ui.finance.BillRow
import com.jarvis.hud.ui.finance.CategoryBreakdown
import com.jarvis.hud.ui.finance.DayHeader
import com.jarvis.hud.ui.finance.DayPartBreakdown
import com.jarvis.hud.ui.finance.Divider
import com.jarvis.hud.ui.finance.Fin
import com.jarvis.hud.ui.finance.PaceChart
import com.jarvis.hud.ui.finance.SectionHeader
import com.jarvis.hud.ui.finance.SegmentedTabs
import com.jarvis.hud.ui.finance.SpendCalendar
import com.jarvis.hud.ui.finance.TransactionRow
import com.jarvis.hud.ui.finance.dayLabel
import com.jarvis.hud.ui.finance.rupees
import com.jarvis.hud.ui.finance.rupeesShort
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.viz.BentoCard
import com.jarvis.hud.ui.viz.ConsistencyDay
import com.jarvis.hud.ui.viz.ConsistencyStrip
import com.jarvis.hud.ui.viz.DonutArcRing
import com.jarvis.hud.ui.viz.SparklineAreaCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Money tab with bento cards, donut budget arc, 2x2 sparkline pulse grid,
 * continuous category phase ring, time-of-day baseline ticks, and calm calendar.
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
  val dateOf: (OrganizedItem) -> LocalDate = { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
  val open: (OrganizedItem) -> Unit = { actions.onOpenItem(it.id) }
  val paid: (OrganizedItem) -> Unit = { actions.onMarkItemStatus(it.id, ItemStatus.DONE) }

  val inset = screenPadding(contentPadding)
  Refreshable(state.isSyncing, actions::onScanAndOrganize, Modifier.fillMaxSize()) {
    LazyColumn(
      Modifier.fillMaxSize().testTag("screen-money"),
      contentPadding = PaddingValues(
        start = 18.dp,
        end = 18.dp,
        top = inset.calculateTopPadding() + 4.dp,
        bottom = inset.calculateBottomPadding() + 12.dp,
      ),
    ) {
      item(key = "tabs") {
        SegmentedTabs(listOf("Overview", "Spending", "Bills"), view, { view = it })
        Spacer(Modifier.height(20.dp))
      }

      if (payments.isEmpty() && bills.isEmpty()) {
        item(key = "empty") {
          if (state.settings.monthlyBudget > 0) BudgetCard(s.monthSpent, state.settings.monthlyBudget)
          EmptyState(
            Icons.Outlined.AccountBalanceWallet,
            "No transactions yet",
            "Allow SMS access and refresh. Bank and UPI alerts will show up here.",
          )
        }
        return@LazyColumn
      }

      when (view) {
        0 -> overview(s, payments, bills, state.settings.monthlyBudget, zone, open, paid) { view = it }
        1 -> {
          item(key = "where") {
            SectionHeader("Where it went in ${s.monthLabel}")
            if (s.categories.isEmpty()) Text("No spending yet this month.", style = Fin.Small)
            CategoryBreakdown(s.categories, category) { category = it }
            Spacer(Modifier.height(22.dp))
            if (s.dayParts.isNotEmpty()) {
              SectionHeader("Rhythm by time of day")
              DayPartBreakdown(s.dayParts)
              Spacer(Modifier.height(22.dp))
            }
            SectionHeader("When")
            SpendCalendar(s, day) { day = it }
            Spacer(Modifier.height(22.dp))
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
          filtered.groupBy(dateOf).forEach { (date, dayItems) ->
            item(key = "d-$date") { DayHeader(date, s.today, dayItems.filter { !it.isCredit }.sumOf { it.amount ?: 0.0 }) }
            items(dayItems, key = { "t-" + it.id }) { p -> TransactionRow(p, zone, onClick = { open(p) }) }
          }
        }
        else -> billsView(s, bills, open, paid)
      }
    }
  }
}

private fun LazyListScope.overview(
  s: SpendInsights,
  payments: List<OrganizedItem>,
  bills: List<OrganizedItem>,
  budget: Double,
  zone: ZoneId,
  open: (OrganizedItem) -> Unit,
  paid: (OrganizedItem) -> Unit,
  goTo: (Int) -> Unit,
) {
  item(key = "hero") {
    HeroMoneyBento(s, budget)
    Spacer(Modifier.height(16.dp))
  }

  item(key = "pace") {
    PaceChart(s)
    Spacer(Modifier.height(16.dp))
  }

  item(key = "sparkGrid") {
    val weekSeries = s.last7Days.map { it.amount.toFloat() }
    val monthSeries = s.monthDays.takeLast(14).ifEmpty { listOf(0f) }.map { it.toFloat() }
    val cumSeries = remember(s) { s.monthDays.runningReduce { a, b -> a + b }.takeLast(14).map { it.toFloat() } }
    val incomeTotal = s.monthReceived

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SparklineAreaCard(
          title = "7-day spend",
          value = rupeesShort(s.last7Days.sumOf { it.amount }),
          subtitle = "Today ${rupeesShort(s.todaySpent)}",
          series = weekSeries,
          tint = HudColors.Sky,
          modifier = Modifier.weight(1f),
          onClick = { goTo(1) },
        )
        SparklineAreaCard(
          title = "Daily average",
          value = rupeesShort(s.dailyAverage),
          subtitle = "${s.monthTransactions} payments",
          series = monthSeries,
          tint = HudColors.Amber,
          modifier = Modifier.weight(1f),
          onClick = { goTo(1) },
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SparklineAreaCard(
          title = "Month-end pace",
          value = rupeesShort(s.projectedMonth),
          subtitle = "At current rate",
          series = cumSeries,
          tint = HudColors.Lavender,
          modifier = Modifier.weight(1f),
        )
        SparklineAreaCard(
          title = "Came in",
          value = rupeesShort(incomeTotal),
          subtitle = if (s.income.isNotEmpty()) s.income.first().key.label else "Credits this month",
          series = listOf(0f, (incomeTotal * 0.2f).toFloat(), (incomeTotal * 0.55f).toFloat(), incomeTotal.toFloat()),
          tint = HudColors.Mint,
          modifier = Modifier.weight(1f),
        )
      }
    }
    Spacer(Modifier.height(16.dp))
  }

  item(key = "weekStrip") {
    val dailyTarget = if (budget > 0) budget / s.month.lengthOfMonth() else s.dailyAverage.coerceAtLeast(1.0)
    val days = s.last7Days.map { d ->
      val label = d.date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.ENGLISH)
      val c = when {
        d.amount <= 0.0 -> HudColors.SurfaceHighest
        d.amount <= dailyTarget * 0.9 -> HudColors.Mint
        d.amount <= dailyTarget * 1.4 -> HudColors.Amber
        else -> HudColors.Coral
      }
      ConsistencyDay(label, c, active = d.amount > 0.0, tooltip = "${d.date}: ${rupees(d.amount)}")
    }
    val underDays = s.last7Days.count { it.amount <= dailyTarget }
    ConsistencyStrip(
      title = "7-day spend rhythm",
      days = days,
      caption = "$underDays of 7 days stayed within your ${rupees(dailyTarget)}/day baseline.",
    )
    Spacer(Modifier.height(16.dp))
  }

  val soon = bills.filter { b ->
    val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    due == null || ChronoUnit.DAYS.between(s.today, due) <= 7
  }.take(3)
  if (soon.isNotEmpty()) {
    item(key = "soon") {
      SectionHeader("Due this week", action = "All bills", onAction = { goTo(2) })
      BentoCard(padding = 12.dp) {
        soon.forEach { b -> BillRow(b, s.today, onPaid = { paid(b) }, onClick = { open(b) }) }
      }
      Spacer(Modifier.height(16.dp))
    }
  }

  if (payments.isNotEmpty()) {
    item(key = "recentHeader") { SectionHeader("Recent", action = "All payments", onAction = { goTo(1) }) }
    item(key = "recentCard") {
      BentoCard(padding = 10.dp) {
        payments.take(6).forEach { p -> TransactionRow(p, zone, onClick = { open(p) }, today = s.today) }
      }
    }
  }
}

@Composable
private fun HeroMoneyBento(s: SpendInsights, budget: Double) {
  val hasBudget = budget > 0
  val frac = if (hasBudget) (s.monthSpent / budget).toFloat() else {
    val ref = maxOf(s.lastMonthSameSpan, s.monthSpent, 1.0)
    (s.monthSpent / ref).toFloat()
  }
  val ringColor = when {
    hasBudget && frac >= 1f -> Fin.Critical
    hasBudget && frac >= 0.8f -> Fin.Warning
    hasBudget -> Fin.Positive
    else -> HudColors.Amber
  }
  val ringLabel = if (hasBudget) "${(frac * 100).roundToInt()}%" else "${s.monthTransactions}"
  val ringSub = if (hasBudget) "used" else "txns"
  val d = s.monthDelta
  val (deltaLine, tone) = when {
    d == null -> "Building your baseline for ${s.monthLabel}" to Fin.Ink2
    abs(d) < 0.01 -> "Right in step with last month" to Fin.Ink2
    d > 0 -> "${rupees(s.monthSpent - s.lastMonthSameSpan)} above last month (+${(d * 100).roundToLong()}%)" to Fin.Warning
    else -> "${rupees(s.lastMonthSameSpan - s.monthSpent)} calmer than last month (${(d * 100).roundToLong()}%)" to Fin.Positive
  }

  BentoCard(tint = ringColor, padding = 18.dp, modifier = if (hasBudget) Modifier.testTag("budgetCard") else Modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Spent in ${s.monthLabel}", style = Fin.Small)
        Spacer(Modifier.height(4.dp))
        Text(rupees(s.monthSpent), style = Fin.Hero)
        Spacer(Modifier.height(4.dp))
        Text(deltaLine, style = Fin.Small.copy(color = tone))
      }
      Spacer(Modifier.width(12.dp))
      DonutArcRing(
        fraction = frac.coerceIn(0f, 1f),
        centerText = ringLabel,
        subText = ringSub,
        color = ringColor,
        size = 92.dp,
        strokeWidth = 9.dp,
        partialSweep = true,
      )
    }
    if (hasBudget) {
      val left = budget - s.monthSpent
      Spacer(Modifier.height(14.dp))
      Meter(frac, ringColor, height = 7.dp)
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          if (left >= 0) "${rupees(left)} left of ${rupeesShort(budget)}" else "${rupees(-left)} over budget",
          style = Fin.Small.copy(color = if (left >= 0) Fin.Ink2 else Fin.Critical),
        )
        Text("Goal ${rupeesShort(budget)}", style = Fin.Tiny)
      }
    }
  }
}

private fun LazyListScope.billsView(
  s: SpendInsights,
  bills: List<OrganizedItem>,
  open: (OrganizedItem) -> Unit,
  paid: (OrganizedItem) -> Unit,
) {
  item(key = "billsTop") {
    val totalDue = bills.sumOf { it.amount ?: 0.0 }
    val urgentCount = bills.count { b ->
      val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
      due != null && ChronoUnit.DAYS.between(s.today, due) <= 7
    }
    val ringFrac = if (bills.isEmpty()) 1f else (1f - urgentCount / bills.size.toFloat()).coerceIn(0.15f, 1f)
    BentoCard(tint = if (urgentCount > 0) HudColors.Amber else HudColors.Mint, padding = 18.dp) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Open bills", style = Fin.Small)
          Spacer(Modifier.height(4.dp))
          Text(rupees(totalDue), style = Fin.Hero)
          Spacer(Modifier.height(4.dp))
          Text(
            if (bills.isEmpty()) "Nothing due" else "${bills.size} bill${if (bills.size == 1) "" else "s"} open",
            style = Fin.Small,
          )
        }
        DonutArcRing(
          fraction = ringFrac,
          centerText = "$urgentCount",
          subText = "this wk",
          color = if (urgentCount > 0) HudColors.Amber else HudColors.Mint,
          size = 82.dp,
          strokeWidth = 8.dp,
        )
      }
    }
    Spacer(Modifier.height(18.dp))
  }
  val (soon, later) = bills.partition { b ->
    val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    due != null && ChronoUnit.DAYS.between(s.today, due) <= 7
  }
  fun group(title: String, list: List<OrganizedItem>, key: String) {
    if (list.isEmpty()) return
    item(key = key) {
      SectionHeader(title)
      BentoCard(padding = 12.dp) {
        list.forEach { b -> BillRow(b, s.today, onPaid = { paid(b) }, onClick = { open(b) }) }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
  group("This week", soon, "b-soon")
  group("Later", later, "b-later")

  if (s.recurring.isNotEmpty()) {
    item(key = "recurring") {
      SectionHeader("Every month")
      BentoCard(padding = 16.dp) {
        Text("Charged the same amount in more than one month: subscriptions, plans, rent.", style = Fin.Small)
        Spacer(Modifier.height(10.dp))
        s.recurring.forEach { r ->
          Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(r.name, style = Fin.BodyStrong)
              Text("Seen in ${r.months} months · last ${dayLabel(r.lastSeen, s.today)}", style = Fin.Small)
            }
            Text(rupees(r.amount), style = Fin.BodyStrong)
          }
        }
        Divider(Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
          Text("Committed each month", style = Fin.Small, modifier = Modifier.weight(1f))
          Text(rupees(s.recurring.sumOf { it.amount }), style = Fin.BodyStrong)
        }
      }
    }
  }
}

@Composable
private fun BudgetCard(spent: Double, budget: Double) {
  val frac = (spent / budget).toFloat()
  val color = when {
    frac >= 1f -> Fin.Critical
    frac >= 0.8f -> Fin.Warning
    else -> Fin.Positive
  }
  val left = budget - spent
  BentoCard(Modifier.padding(bottom = 12.dp).testTag("budgetCard"), tint = color) {
    Meter(frac, color, height = 8.dp)
    Spacer(Modifier.height(8.dp))
    Text(
      if (left >= 0) "${rupees(left)} left of your ${rupees(budget)} budget (${(frac * 100).toInt()}% used)"
      else "${rupees(-left)} over your ${rupees(budget)} budget",
      style = Fin.Small.copy(color = if (left >= 0) Fin.Ink2 else Fin.Critical),
    )
  }
}
