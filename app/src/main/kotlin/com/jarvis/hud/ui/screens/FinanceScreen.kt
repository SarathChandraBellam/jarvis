package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SubscriptionInsights
import com.jarvis.hud.model.BillingCadence
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TrackedSubscription
import com.jarvis.hud.ui.components.EmptyState
import com.jarvis.hud.ui.components.Refreshable
import com.jarvis.hud.ui.components.jarvisTextFieldColors
import com.jarvis.hud.ui.finance.BillRow
import com.jarvis.hud.ui.finance.Fin
import com.jarvis.hud.ui.finance.RenewalCalendar
import com.jarvis.hud.ui.finance.SectionHeader
import com.jarvis.hud.ui.finance.SegmentedTabs
import com.jarvis.hud.ui.finance.SubscriptionCategoryRing
import com.jarvis.hud.ui.finance.SubscriptionRow
import com.jarvis.hud.ui.finance.dayLabel
import com.jarvis.hud.ui.finance.rupees
import com.jarvis.hud.ui.finance.rupeesShort
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BentoCard
import com.jarvis.hud.ui.viz.DonutArcRing
import com.jarvis.hud.ui.viz.SparklineAreaCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Subscriptions & Bills hub: automatically extracts recurring subscriptions from messages
 * (ignoring one-off daily spends), visualizes category share with a continuous pastel phase ring,
 * highlights upcoming renewals on a pastel calendar, and lets the user add/pause/remove plans.
 */
@Composable
fun FinanceScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val zone = remember { ZoneId.systemDefault() }
  val subInsights = remember(state.items, state.subscriptions) {
    val active = state.subscriptions.filterNot { it.isPaused }
    val paused = state.subscriptions.filter { it.isPaused }
    val today = LocalDate.now(zone)
    val monthly = active.sumOf { it.monthlyEquivalent }
    val byCat = active.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.monthlyEquivalent } }
    val totalCat = byCat.values.sum().takeIf { it > 0.0 } ?: 1.0
    SubscriptionInsights(
      today = today,
      active = active,
      paused = paused,
      monthlyBurn = monthly,
      yearlyBurn = monthly * 12.0,
      upcoming7Days = active.filter {
        val d = SubscriptionInsights.daysUntil(it.nextRenewalDate, today)
        d != null && d in 0..7
      },
      trials = active.filter { it.isTrial },
      categories = byCat.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .map { (c, amt) -> com.jarvis.hud.insights.Share(c, amt, (amt / totalCat).toFloat()) },
    )
  }

  val bills = remember(state.items) {
    state.items.filter { it.category == IntelCategory.BILL && it.status == ItemStatus.ACTIVE }
      .sortedWith(compareBy(nullsLast()) { it.dueDate })
  }

  val view = state.settings.subsViewIndex.coerceIn(0, 2)
  var selectedCategory by remember { mutableStateOf<SubscriptionCategory?>(null) }
  var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
  var showAddSub by remember { mutableStateOf(false) }

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
        bottom = inset.calculateBottomPadding() + 14.dp,
      ),
    ) {
      item(key = "tabs") {
        SegmentedTabs(listOf("Overview", "Renewals", "Bills"), view, { actions.onSelectSubsView(it) })
        Spacer(Modifier.height(18.dp))
      }

      if (subInsights.all.isEmpty() && bills.isEmpty()) {
        item(key = "empty") {
          EmptyState(
            Icons.Outlined.Autorenew,
            "No subscriptions yet",
            "Refresh SMS & notifications to auto-detect plans, or add your subscriptions manually.",
          ) {
            Button(
              onClick = { showAddSub = true },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
            ) {
              Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Add subscription")
            }
          }
        }
        return@LazyColumn
      }

      when (view) {
        0 -> subsOverview(
          s = subInsights,
          bills = bills,
          selectedCategory = selectedCategory,
          onSelectCategory = { selectedCategory = it },
          onAddClick = { showAddSub = true },
          onTogglePause = actions::onToggleSubscriptionPaused,
          onDeleteSub = actions::onDeleteSubscription,
          open = open,
          paid = paid,
          goTo = { actions.onSelectSubsView(it) },
        )
        1 -> renewalsView(
          s = subInsights,
          bills = bills,
          selectedDay = selectedDay,
          onSelectDay = { selectedDay = it },
          onAddClick = { showAddSub = true },
          onTogglePause = actions::onToggleSubscriptionPaused,
          onDeleteSub = actions::onDeleteSubscription,
        )
        else -> billsView(subInsights.today, bills, open, paid)
      }
    }
  }

  if (showAddSub) {
    AddSubscriptionSheet(
      today = subInsights.today,
      onAdd = { name, amt, cadence, cat, date, trial ->
        actions.onAddSubscription(name, amt, cadence, cat, date, trial)
        showAddSub = false
      },
      onDismiss = { showAddSub = false },
    )
  }
}

private fun LazyListScope.subsOverview(
  s: SubscriptionInsights,
  bills: List<OrganizedItem>,
  selectedCategory: SubscriptionCategory?,
  onSelectCategory: (SubscriptionCategory?) -> Unit,
  onAddClick: () -> Unit,
  onTogglePause: (String) -> Unit,
  onDeleteSub: (String) -> Unit,
  open: (OrganizedItem) -> Unit,
  paid: (OrganizedItem) -> Unit,
  goTo: (Int) -> Unit,
) {
  item(key = "hero") {
    val activeCount = s.active.size
    BentoCard(pastelBg = HudColors.SandSoft, padding = 18.dp) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Monthly subscriptions", style = Fin.Small)
          Spacer(Modifier.height(4.dp))
          Text(rupees(s.monthlyBurn), style = Fin.Hero)
          Spacer(Modifier.height(4.dp))
          Text(
            "${rupeesShort(s.yearlyBurn)} / year · $activeCount active plan${if (activeCount == 1) "" else "s"}",
            style = Fin.Small,
          )
        }
        Box(
          Modifier
            .clip(CircleShape)
            .background(HudColors.Cream)
            .clickable(role = Role.Button, onClick = onAddClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = HudColors.OnCream, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add", style = MaterialTheme.typography.labelLarge, color = HudColors.OnCream)
          }
        }
      }
    }
    Spacer(Modifier.height(14.dp))
  }

  item(key = "sparkGrid") {
    val soonTotal = s.upcoming7Days.sumOf { it.amount }
    val billsTotal = bills.sumOf { it.amount ?: 0.0 }
    val catSeries = s.categories.map { it.amount.toFloat() }.ifEmpty { listOf(0f, 0f) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SparklineAreaCard(
          title = "Due next 7d",
          value = "${s.upcoming7Days.size} renewal${if (s.upcoming7Days.size == 1) "" else "s"}",
          subtitle = rupees(soonTotal),
          series = catSeries,
          tint = HudColors.Blush,
          pastelBg = HudColors.BlushSoft,
          modifier = Modifier.weight(1f),
          onClick = { goTo(1) },
        )
        SparklineAreaCard(
          title = "Open bills",
          value = "${bills.size} due",
          subtitle = rupees(billsTotal),
          series = bills.map { (it.amount ?: 0.0).toFloat() }.ifEmpty { listOf(0f, 0f) },
          tint = HudColors.Lavender,
          pastelBg = HudColors.LavenderSoft,
          modifier = Modifier.weight(1f),
          onClick = { goTo(2) },
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SparklineAreaCard(
          title = "Active plans",
          value = "${s.active.size}",
          subtitle = if (s.paused.isEmpty()) "Auto-detected & tracked" else "${s.paused.size} paused",
          series = listOf(1f, (s.active.size * 0.5f).coerceAtLeast(1f), s.active.size.toFloat()),
          tint = HudColors.Sage,
          pastelBg = HudColors.SageSoft,
          modifier = Modifier.weight(1f),
          onClick = { goTo(1) },
        )
        SparklineAreaCard(
          title = "Trial watch",
          value = "${s.trials.size}",
          subtitle = if (s.trials.isEmpty()) "No free trials ending" else s.trials.first().name,
          series = listOf(0f, s.trials.size.toFloat()),
          tint = HudColors.SlateBlue,
          pastelBg = HudColors.SlateBlueSoft,
          modifier = Modifier.weight(1f),
          onClick = { goTo(1) },
        )
      }
    }
    Spacer(Modifier.height(16.dp))
  }

  if (s.categories.isNotEmpty()) {
    item(key = "ring") {
      SectionHeader("Where recurring money goes", action = if (selectedCategory != null) "Clear" else null, onAction = { onSelectCategory(null) })
      SubscriptionCategoryRing(s.categories, selectedCategory, onSelectCategory)
      Spacer(Modifier.height(16.dp))
    }
  }

  val filteredSubs = s.active.filter { selectedCategory == null || it.category == selectedCategory }
  if (filteredSubs.isNotEmpty()) {
    item(key = "subsHeader") {
      SectionHeader(
        selectedCategory?.label ?: "Active subscriptions",
        action = "All renewals",
        onAction = { goTo(1) },
      )
      BentoCard(padding = 12.dp) {
        filteredSubs.take(5).forEach { sub ->
          SubscriptionRow(
            sub = sub,
            today = s.today,
            onTogglePause = { onTogglePause(sub.id) },
            onDelete = { onDeleteSub(sub.id) },
          )
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }

  val soonBills = bills.filter { b ->
    val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    due == null || ChronoUnit.DAYS.between(s.today, due) <= 7
  }.take(3)
  if (soonBills.isNotEmpty()) {
    item(key = "soonBills") {
      SectionHeader("Due this week", action = "All bills", onAction = { goTo(2) })
      BentoCard(padding = 12.dp) {
        soonBills.forEach { b -> BillRow(b, s.today, onPaid = { paid(b) }, onClick = { open(b) }) }
      }
    }
  }
}

private fun LazyListScope.renewalsView(
  s: SubscriptionInsights,
  bills: List<OrganizedItem>,
  selectedDay: LocalDate?,
  onSelectDay: (LocalDate?) -> Unit,
  onAddClick: () -> Unit,
  onTogglePause: (String) -> Unit,
  onDeleteSub: (String) -> Unit,
) {
  item(key = "cal") {
    RenewalCalendar(s.today, s.all, bills, selectedDay, onSelectDay)
    Spacer(Modifier.height(18.dp))
  }

  val daySubs = if (selectedDay == null) {
    s.all
  } else {
    s.all.filter { it.nextRenewalDate == selectedDay.toString() }
  }

  item(key = "listHeader") {
    SectionHeader(
      title = selectedDay?.let { "Renewing on ${dayLabel(it, s.today)}" } ?: "All tracked subscriptions",
      action = if (selectedDay != null) "Clear" else "+ Add",
      onAction = { if (selectedDay != null) onSelectDay(null) else onAddClick() },
    )
    if (daySubs.isEmpty()) {
      BentoCard { Text("No renewals on this day.", style = Fin.Small) }
    } else {
      BentoCard(padding = 12.dp) {
        daySubs.forEach { sub ->
          SubscriptionRow(
            sub = sub,
            today = s.today,
            onTogglePause = { onTogglePause(sub.id) },
            onDelete = { onDeleteSub(sub.id) },
          )
        }
      }
    }
  }
}

private fun LazyListScope.billsView(
  today: LocalDate,
  bills: List<OrganizedItem>,
  open: (OrganizedItem) -> Unit,
  paid: (OrganizedItem) -> Unit,
) {
  item(key = "billsTop") {
    val totalDue = bills.sumOf { it.amount ?: 0.0 }
    val urgentCount = bills.count { b ->
      val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
      due != null && ChronoUnit.DAYS.between(today, due) <= 7
    }
    val ringFrac = if (bills.isEmpty()) 1f else (1f - urgentCount / bills.size.toFloat()).coerceIn(0.15f, 1f)
    BentoCard(pastelBg = HudColors.LavenderSoft, padding = 18.dp) {
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
          color = HudColors.Lavender,
          size = 82.dp,
          strokeWidth = 8.dp,
        )
      }
    }
    Spacer(Modifier.height(18.dp))
  }
  val (soon, later) = bills.partition { b ->
    val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    due != null && ChronoUnit.DAYS.between(today, due) <= 7
  }
  fun group(title: String, list: List<OrganizedItem>, key: String) {
    if (list.isEmpty()) return
    item(key = key) {
      SectionHeader(title)
      BentoCard(padding = 12.dp) {
        list.forEach { b -> BillRow(b, today, onPaid = { paid(b) }, onClick = { open(b) }) }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
  group("This week", soon, "b-soon")
  group("Later", later, "b-later")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSubscriptionSheet(
  today: LocalDate,
  onAdd: (String, Double, BillingCadence, SubscriptionCategory, String?, Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var amount by remember { mutableStateOf("") }
  var cadence by remember { mutableStateOf(BillingCadence.MONTHLY) }
  var category by remember { mutableStateOf(SubscriptionCategory.STREAMING) }
  var dayOfMonth by remember { mutableStateOf(today.dayOfMonth.toString()) }
  var isTrial by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = HudColors.Surface,
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .navigationBarsPadding()
        .padding(bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Add subscription", style = JarvisType.TitleSerif, color = HudColors.Text)
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Service name") },
        placeholder = { Text("Netflix, ChatGPT Plus, JioFiber…") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = jarvisTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = amount,
          onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(8) },
          label = { Text("Amount") },
          prefix = { Text("₹") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          shape = MaterialTheme.shapes.medium,
          colors = jarvisTextFieldColors(),
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = dayOfMonth,
          onValueChange = { v -> dayOfMonth = v.filter { it.isDigit() }.take(2) },
          label = { Text("Renewal day") },
          placeholder = { Text("1–28") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          shape = MaterialTheme.shapes.medium,
          colors = jarvisTextFieldColors(),
          modifier = Modifier.width(120.dp),
        )
      }
      Text("Billing cycle", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BillingCadence.entries.forEach { c ->
          FilterChip(
            selected = cadence == c,
            onClick = { cadence = c },
            label = { Text(c.label) },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = HudColors.SandSoft,
              selectedLabelColor = HudColors.Text,
            ),
          )
        }
      }
      Text("Category", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SubscriptionCategory.entries.forEach { cat ->
          FilterChip(
            selected = category == cat,
            onClick = { category = cat },
            label = { Text(cat.label) },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = Fin.subColor(cat).copy(alpha = 0.18f),
              selectedLabelColor = HudColors.Text,
            ),
          )
        }
      }
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("This is a free trial", style = MaterialTheme.typography.bodyMedium, color = HudColors.Text, modifier = Modifier.weight(1f))
        Switch(
          checked = isTrial,
          onCheckedChange = { isTrial = it },
          colors = SwitchDefaults.colors(checkedThumbColor = HudColors.OnCream, checkedTrackColor = HudColors.Cream),
        )
      }
      val parsedAmt = amount.toDoubleOrNull() ?: 0.0
      Button(
        onClick = {
          val dom = (dayOfMonth.toIntOrNull() ?: today.dayOfMonth).coerceIn(1, today.lengthOfMonth())
          var renewal = today.withDayOfMonth(dom)
          if (renewal.isBefore(today)) renewal = renewal.plusMonths(1)
          onAdd(name, parsedAmt, cadence, category, renewal.toString(), isTrial)
        },
        enabled = name.isNotBlank() && parsedAmt > 0.0,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
      ) {
        Text("Save subscription")
      }
    }
  }
}
