package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SubscriptionInsights
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.ProactiveAlert
import com.jarvis.hud.model.ProactiveKind
import com.jarvis.hud.ui.components.EmptyLine
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.IconBadge
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.ListRow
import com.jarvis.hud.ui.components.Meter
import com.jarvis.hud.ui.components.Refreshable
import com.jarvis.hud.ui.components.SectionHeader
import com.jarvis.hud.ui.components.SetupCard
import com.jarvis.hud.ui.components.jarvisTextFieldColors
import com.jarvis.hud.ui.finance.rupees
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BaselineRangeBar
import com.jarvis.hud.ui.viz.BentoCard
import com.jarvis.hud.ui.viz.ConsistencyDay
import com.jarvis.hud.ui.viz.ConsistencyStrip
import com.jarvis.hud.ui.viz.DayPillItem
import com.jarvis.hud.ui.viz.DaySelectorStrip
import com.jarvis.hud.ui.viz.DayTimelineBar
import com.jarvis.hud.ui.viz.HexRadarChart
import com.jarvis.hud.ui.viz.RadarAxis
import com.jarvis.hud.ui.viz.ScoreRingSpec
import com.jarvis.hud.ui.viz.ScoreRingsCard
import com.jarvis.hud.ui.viz.SparklineAreaCard
import com.jarvis.hud.ui.viz.TimelineDot
import com.jarvis.hud.ui.viz.TimelineSpan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Calm personal Today screen: 7-day sand context strip, blush insight banner,
 * 3 score rings, proactive nudges, Smart Day Planner with quick reminders,
 * 24-hour day timeline, Focus & Screen-Time Insights, Daily Balance hex radar,
 * and priority inbox items.
 */
@Composable
fun HudScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val now = System.currentTimeMillis()
  val zone = remember { ZoneId.systemDefault() }
  val todayDate = remember(now) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
  var selectedDayOffset by remember { mutableIntStateOf(0) }
  var showAddReminder by remember { mutableStateOf(false) }

  val selectedDate = todayDate.plusDays(selectedDayOffset.toLong())
  val selectedDateKey = selectedDate.toString()
  val selStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
  val selEnd = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val dayStart = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
  val dayEnd = todayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val dayMillis = 24f * 3_600_000f

  val active = remember(state.items) { state.items.filter { it.status == ItemStatus.ACTIVE } }
  val urgent = remember(active) {
    active
      .filter { it.priority == Priority.CRITICAL || it.priority == Priority.HIGH }
      .sortedWith(compareBy({ it.priority.ordinal }, { -it.timestamp }))
      .take(4)
  }
  val activeSubs = remember(state.subscriptions) { state.subscriptions.filterNot { it.isPaused } }
  val billsDue = active.count { it.category == IntelCategory.BILL }
  val upcomingSubs = remember(activeSubs, todayDate) {
    activeSubs.filter {
      val d = SubscriptionInsights.daysUntil(it.nextRenewalDate, todayDate)
      d != null && d in 0..7
    }
  }
  val dayEvents = remember(state.calendarEntries, selStart, selEnd) {
    state.calendarEntries.filter { it.startTime < selEnd && it.endTime >= selStart }.sortedBy { it.startTime }
  }
  val dayReminders = remember(state.reminders, selectedDateKey) {
    state.reminders.filter { it.dateKey == selectedDateKey }.sortedBy { it.minutesOfDay }
  }
  val totalUsageMin = state.appUsageStats.sumOf { it.foregroundMinutes }
  val focusGoalMin = state.settings.dailyFocusGoalMinutes.coerceAtLeast(30)

  Refreshable(state.isSyncing, actions::onScanAndOrganize, Modifier.fillMaxSize()) {
    LazyColumn(
      Modifier.fillMaxSize().testTag("screen-home"),
      contentPadding = screenPadding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "daySelector") {
        val weekStart = todayDate.with(DayOfWeek.MONDAY)
        val renewalDates = activeSubs.mapNotNull { it.nextRenewalDate }.toSet()
        val billDates = active.filter { it.category == IntelCategory.BILL }.mapNotNull { it.dueDate }.toSet()
        val reminderDates = state.reminders.filterNot { it.isDone }.map { it.dateKey }.toSet()
        val pills = (0..6).map { offset ->
          val d = weekStart.plusDays(offset.toLong())
          val key = d.toString()
          val hasMark = key in renewalDates || key in billDates || key in reminderDates
          DayPillItem(
            letter = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
            dayNumber = d.dayOfMonth.toString(),
            isSelected = d == selectedDate,
            hasMarker = hasMark,
            onClick = { selectedDayOffset = java.time.temporal.ChronoUnit.DAYS.between(todayDate, d).toInt() },
          )
        }
        val dayTitle = when (selectedDayOffset) {
          0 -> "Today"
          1 -> "Tomorrow"
          -1 -> "Yesterday"
          else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
        }
        DaySelectorStrip(
          eyebrow = if (upcomingSubs.isEmpty()) "Subscriptions calm" else "${upcomingSubs.size} renewal${if (upcomingSubs.size == 1) "" else "s"} this week",
          headline = dayTitle,
          rightHeadline = rupees(activeSubs.sumOf { it.monthlyEquivalent }),
          rightSub = "monthly plans",
          days = pills,
        )
      }

      item(key = "briefing") { BriefingCard(state, actions) }

      item(key = "scoreRings") {
        val renewalScore = (100 - upcomingSubs.size * 15 - billsDue * 12).coerceIn(25, 100)
        val focusScore = (100 - (totalUsageMin * 60 / focusGoalMin).toInt()).coerceIn(20, 100)
        val inboxScore = (100 - active.size * 6).coerceIn(25, 100)
        val guidance = when {
          urgent.isNotEmpty() -> "Take it steady today. ${urgent.size} priority item${if (urgent.size == 1) "" else "s"} waiting below."
          state.proactiveAlerts.isNotEmpty() -> "Gentle rhythm — ${state.proactiveAlerts.size} proactive nudge${if (state.proactiveAlerts.size == 1) "" else "s"} queued."
          else -> "Everything is calm and clear today."
        }
        ScoreRingsCard(
          rings = listOf(
            ScoreRingSpec("Renewals", "$renewalScore", renewalScore / 100f, HudColors.SlateBlue) {
              actions.onSelectTab(HudTab.FINANCE)
            },
            ScoreRingSpec("Focus", "$focusScore", focusScore / 100f, HudColors.Blush),
            ScoreRingSpec("Inbox", "$inboxScore", inboxScore / 100f, HudColors.Lavender) {
              actions.onSelectTab(HudTab.INTEL)
            },
          ),
          guidance = guidance,
        )
      }

      if (state.proactiveAlerts.isNotEmpty()) {
        item(key = "proactive") {
          SectionHeader("Proactive nudges", trailing = "${state.proactiveAlerts.size} active")
          Spacer(Modifier.height(4.dp))
          ProactiveAlertsCard(
            alerts = state.proactiveAlerts.take(3),
            onOpenTab = { it?.let(actions::onSelectTab) },
            onDismiss = actions::onDismissProactiveAlert,
          )
        }
      }

      item(key = "urgentHeader") {
        SectionHeader("Needs attention", trailing = if (active.isNotEmpty()) "See all" else null, onTrailingClick = { actions.onSelectTab(HudTab.INTEL) })
      }
      if (urgent.isEmpty()) {
        item(key = "urgentEmpty") {
          BentoCard { EmptyLine("Nothing urgent. You're all caught up.") }
        }
      } else {
        items(urgent, key = { "u-" + it.id }) { ItemCard(it, actions::onMarkItemStatus, onClick = { actions.onOpenItem(it.id) }) }
      }

      item(key = "planner") {
        SmartDayPlannerCard(
          dateLabel = if (selectedDayOffset == 0) "Today's plan" else selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
          events = dayEvents,
          reminders = dayReminders,
          onToggleReminder = actions::onToggleReminder,
          onDeleteReminder = actions::onDeleteReminder,
          onAddReminderClick = { showAddReminder = true },
        )
      }

      item(key = "dayTimeline") {
        val spans = buildList {
          state.calendarEntries.filter { it.startTime < dayEnd && it.endTime >= dayStart }.forEach { e ->
            val s = ((e.startTime - dayStart) / dayMillis).coerceIn(0f, 1f)
            val en = ((e.endTime - dayStart) / dayMillis).coerceIn(s, 1f)
            add(TimelineSpan(s, en, HudColors.Lavender))
          }
          state.recentCalls.filter { it.timestamp in dayStart..dayEnd }.forEach { c ->
            val s = ((c.timestamp - dayStart) / dayMillis).coerceIn(0f, 1f)
            val en = (s + (c.durationSec * 1000f / dayMillis).coerceAtLeast(0.015f)).coerceAtMost(1f)
            add(TimelineSpan(s, en, if ("MISS" in c.callType.uppercase()) HudColors.Blush else HudColors.SlateBlue))
          }
        }
        val dots = state.reminders.filter { it.dateKey == todayDate.toString() }.map { r ->
          TimelineDot((r.minutesOfDay / 1440f).coerceIn(0.02f, 0.98f), HudColors.Sage, 5f)
        }
        val nowFrac = ((now - dayStart) / dayMillis).coerceIn(0f, 1f)
        val summaryLine = buildList {
          val evCount = state.calendarEntries.count { it.startTime < dayEnd && it.endTime >= dayStart }
          val remCount = state.reminders.count { it.dateKey == todayDate.toString() && !it.isDone }
          if (evCount > 0) add("$evCount event${if (evCount == 1) "" else "s"}")
          if (remCount > 0) add("$remCount reminder${if (remCount == 1) "" else "s"}")
          if (upcomingSubs.isNotEmpty()) add("${upcomingSubs.size} upcoming renewal${if (upcomingSubs.size == 1) "" else "s"}")
        }.joinToString(" · ").ifEmpty { "Open, calm day ahead" }

        DayTimelineBar(
          spans = spans,
          dots = dots,
          nowFrac = nowFrac,
          summaryLine = summaryLine,
          legend = listOf(
            "Events" to HudColors.Lavender,
            "Reminders" to HudColors.Sage,
            "Calls" to HudColors.SlateBlue,
            "Now" to HudColors.Cream,
          ),
        )
      }

      item(key = "focusInsights") {
        FocusInsightsSection(
          totalUsageMin = totalUsageMin,
          focusGoalMin = focusGoalMin,
          appUsageStats = state.appUsageStats,
          activeSubsCount = activeSubs.size,
          monthlyBurn = activeSubs.sumOf { it.monthlyEquivalent },
          batteryPercent = state.telemetry.batteryPercent,
          freeStorageGb = state.telemetry.freeStorageGb,
          onOpenSubs = { actions.onSelectTab(HudTab.FINANCE) },
        )
      }

      item(key = "pulseRadar") {
        val subsFrac = (1f - upcomingSubs.size * 0.14f).coerceIn(0.25f, 1f)
        val billsFrac = (1f - billsDue * 0.2f).coerceIn(0.2f, 1f)
        val inboxFrac = (1f - active.size * 0.06f).coerceIn(0.25f, 1f)
        val schedFrac = if (dayEvents.isEmpty()) 0.85f else (1f - (dayEvents.size - 1) * 0.1f).coerceIn(0.4f, 0.95f)
        val focusFrac = (1f - (totalUsageMin / (focusGoalMin * 2f))).coerceIn(0.2f, 1f)
        val doneRems = dayReminders.count { it.isDone }
        val routineFrac = if (dayReminders.isEmpty()) 0.80f else (doneRems / dayReminders.size.toFloat()).coerceIn(0.25f, 1f)

        val axes = listOf(
          RadarAxis("Renewals", subsFrac, "${upcomingSubs.size} due 7d"),
          RadarAxis("Bills", billsFrac, if (billsDue == 0) "All clear" else "$billsDue open"),
          RadarAxis("Inbox", inboxFrac, "${active.size} active"),
          RadarAxis("Focus", focusFrac, if (totalUsageMin > 0) HudFormat.minutes(totalUsageMin) else "Calm"),
          RadarAxis("Schedule", schedFrac, "${dayEvents.size} events"),
          RadarAxis("Routine", routineFrac, if (dayReminders.isEmpty()) "Add task" else "$doneRems/${dayReminders.size} done"),
        )
        val overall = (axes.map { it.fraction }.average() * 100).roundToInt()
        val label = when {
          overall >= 80 -> "Calm & steady"
          overall >= 60 -> "Balanced pace"
          else -> "Busy rhythm"
        }
        HexRadarChart(axes = axes, score = overall, headline = label)
      }

      item(key = "setup") {
        SetupCard(
          state.permissions,
          actions::onRequestRuntimePermissions,
          actions::onOpenNotificationAccessSettings,
          actions::onOpenUsageAccessSettings,
        )
      }

      item(key = "calls") {
        SectionHeader("Recent calls")
        Spacer(Modifier.height(4.dp))
        BentoCard(padding = 14.dp) {
          if (state.recentCalls.isEmpty()) EmptyLine("No recent calls.", Modifier.padding(4.dp))
          state.recentCalls.take(5).forEach { c ->
            val type = c.callType.uppercase()
            val (icon, color, label) = when {
              "MISS" in type || "REJECT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMissed, HudColors.Blush, "Missed")
              "OUT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMade, HudColors.SlateBlue, "Outgoing")
              else -> Triple(Icons.AutoMirrored.Outlined.CallReceived, HudColors.Sage, "Incoming")
            }
            ListRow(
              title = c.nameOrNumber,
              subtitle = "$label · ${HudFormat.relative(c.timestamp)}",
              icon = icon,
              tint = color,
              trailing = { Text(HudFormat.duration(c.durationSec), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted) },
            )
          }
        }
      }

      item(key = "device") { DeviceCard(state) }
    }
  }

  if (showAddReminder) {
    AddReminderSheet(
      dateKey = selectedDateKey,
      onAdd = { title, mins ->
        actions.onAddReminder(title, mins, selectedDateKey)
        showAddReminder = false
      },
      onDismiss = { showAddReminder = false },
    )
  }
}

@Composable
private fun ProactiveAlertsCard(
  alerts: List<ProactiveAlert>,
  onOpenTab: (HudTab?) -> Unit,
  onDismiss: (String) -> Unit,
) {
  BentoCard(pastelBg = HudColors.BlushSoft, padding = 14.dp) {
    alerts.forEachIndexed { idx, alert ->
      if (idx > 0) Spacer(Modifier.height(10.dp))
      val iconColor = when (alert.kind) {
        ProactiveKind.RENEWAL, ProactiveKind.BILL -> HudColors.Blush
        ProactiveKind.DELIVERY -> HudColors.SlateBlue
        ProactiveKind.FOCUS -> HudColors.Peach
        else -> HudColors.Sage
      }
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(HudColors.Surface)
          .clickable { onOpenTab(alert.targetTab) }
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconBadge(Icons.Outlined.NotificationsActive, iconColor, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(alert.kind.label, style = MaterialTheme.typography.labelSmall, color = iconColor)
          Text(alert.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(alert.body, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onDismiss(alert.dedupKey) }, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Outlined.Close, "Dismiss alert", tint = HudColors.TextMuted, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SmartDayPlannerCard(
  dateLabel: String,
  events: List<com.jarvis.hud.model.CalendarEntry>,
  reminders: List<com.jarvis.hud.model.PlannerReminder>,
  onToggleReminder: (String) -> Unit,
  onDeleteReminder: (String) -> Unit,
  onAddReminderClick: () -> Unit,
) {
  SectionHeader(
    dateLabel,
    trailing = "+ Reminder",
    onTrailingClick = onAddReminderClick,
  )
  Spacer(Modifier.height(4.dp))
  BentoCard(padding = 16.dp) {
    if (events.isEmpty() && reminders.isEmpty()) {
      Text("Clear schedule — add a gentle reminder or focus block.", style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
      Spacer(Modifier.height(10.dp))
      Box(
        Modifier
          .clip(CircleShape)
          .background(HudColors.SandSoft)
          .clickable(role = Role.Button, onClick = onAddReminderClick)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Add, null, tint = HudColors.Text, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Add reminder", style = MaterialTheme.typography.labelLarge, color = HudColors.Text)
        }
      }
    }
    events.forEachIndexed { i, e ->
      if (i > 0) Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
          Text(HudFormat.time(e.startTime), style = MaterialTheme.typography.labelLarge, color = HudColors.Lavender)
          Text(HudFormat.time(e.endTime), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
        }
        Box(Modifier.width(3.dp).height(34.dp).background(HudColors.Lavender, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(e.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          e.location?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
    if (events.isNotEmpty() && reminders.isNotEmpty()) {
      Spacer(Modifier.height(6.dp))
      Box(Modifier.fillMaxWidth().height(1.dp).background(HudColors.OutlineSoft))
      Spacer(Modifier.height(6.dp))
    }
    reminders.forEach { rem ->
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { onToggleReminder(rem.id) }
          .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          if (rem.isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (rem.isDone) HudColors.Sage else HudColors.TextDim,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(
            rem.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (rem.isDone) HudColors.TextMuted else HudColors.Text,
            textDecoration = if (rem.isDone) TextDecoration.LineThrough else null,
          )
          Text(formatMinutesOfDay(rem.minutesOfDay), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
        }
        IconButton(onClick = { onDeleteReminder(rem.id) }, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Outlined.DeleteOutline, "Remove reminder", tint = HudColors.TextMuted, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun FocusInsightsSection(
  totalUsageMin: Long,
  focusGoalMin: Int,
  appUsageStats: List<com.jarvis.hud.model.AppUsageStat>,
  activeSubsCount: Int,
  monthlyBurn: Double,
  batteryPercent: Int,
  freeStorageGb: Float,
  onOpenSubs: () -> Unit,
) {
  SectionHeader("Focus & digital wellbeing", trailing = if (totalUsageMin > 0) HudFormat.minutes(totalUsageMin) else null)
  Spacer(Modifier.height(4.dp))
  val topApp = appUsageStats.maxByOrNull { it.foregroundMinutes }
  val usageSeries = appUsageStats.sortedByDescending { it.foregroundMinutes }.take(5).map { it.foregroundMinutes.toFloat() }.ifEmpty { listOf(15f, 22f, 18f) }
  val remainingMin = (focusGoalMin - totalUsageMin).coerceAtLeast(0L)

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      SparklineAreaCard(
        title = "Screen time",
        value = if (totalUsageMin > 0) HudFormat.minutes(totalUsageMin) else "—",
        subtitle = topApp?.let { "Top: ${it.appName}" } ?: "Grant usage access",
        series = usageSeries,
        tint = HudColors.Peach,
        pastelBg = HudColors.PeachSoft,
        modifier = Modifier.weight(1f),
      )
      SparklineAreaCard(
        title = "Focus cushion",
        value = HudFormat.minutes(remainingMin),
        subtitle = "of ${HudFormat.minutes(focusGoalMin.toLong())} limit",
        series = listOf(focusGoalMin.toFloat(), (focusGoalMin - totalUsageMin * 0.5f).coerceAtLeast(0f), remainingMin.toFloat()),
        tint = HudColors.Sage,
        pastelBg = HudColors.SageSoft,
        modifier = Modifier.weight(1f),
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      SparklineAreaCard(
        title = "Subscriptions",
        value = rupees(monthlyBurn),
        subtitle = "$activeSubsCount active plans",
        series = listOf(1f, activeSubsCount.coerceAtLeast(1).toFloat()),
        tint = HudColors.Blush,
        pastelBg = HudColors.BlushSoft,
        modifier = Modifier.weight(1f),
        onClick = onOpenSubs,
      )
      SparklineAreaCard(
        title = "Phone health",
        value = "$batteryPercent%",
        subtitle = String.format(Locale.US, "%.0f GB free", freeStorageGb),
        series = listOf(72f, 78f, batteryPercent.toFloat()),
        tint = HudColors.SlateBlue,
        pastelBg = HudColors.SlateBlueSoft,
        modifier = Modifier.weight(1f),
      )
    }
  }

  val top = appUsageStats.sortedByDescending { it.foregroundMinutes }.take(5)
  if (top.isNotEmpty()) {
    Spacer(Modifier.height(10.dp))
    BentoCard {
      val max = (top.maxOfOrNull { it.foregroundMinutes } ?: 1L).coerceAtLeast(1L)
      top.forEachIndexed { i, u ->
        if (i > 0) Spacer(Modifier.height(12.dp))
        BaselineRangeBar(
          label = u.appName,
          valueText = HudFormat.minutes(u.foregroundMinutes),
          fraction = u.foregroundMinutes / max.toFloat(),
          typicalFraction = 0.6f,
          color = if (i == 0) HudColors.Peach else HudColors.Lavender,
        )
      }
    }
  }

  Spacer(Modifier.height(10.dp))
  val days = listOf("M", "T", "W", "T", "F", "S", "S").mapIndexed { i, l ->
    val active = i < 5
    val c = when {
      i == 4 && totalUsageMin > focusGoalMin -> HudColors.Blush
      active -> HudColors.Sage
      else -> HudColors.SurfaceHigh
    }
    ConsistencyDay(l, c, active = active, tooltip = "$l focus")
  }
  ConsistencyStrip(
    title = "Weekly focus rhythm",
    days = days,
    caption = if (totalUsageMin <= focusGoalMin) "Inside your calm ${HudFormat.minutes(focusGoalMin.toLong())} daily screen limit." else "Taking short breaks keeps focus gentle.",
  )
}

@Composable
private fun BriefingCard(state: JarvisUiState, actions: JarvisActions) {
  val b = state.briefing
  var highlightIdx by remember(b?.dateKey) { mutableIntStateOf(0) }
  val canCycle = b != null && b.highlights.size > 1

  BentoCard(
    pastelBg = HudColors.BlushSoft,
    onClick = if (canCycle) {
      { highlightIdx = (highlightIdx + 1) % b.highlights.size }
    } else null,
    padding = 18.dp,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconBadge(Icons.Outlined.AutoAwesome, HudColors.Blush, size = 30.dp)
      Spacer(Modifier.width(10.dp))
      Text("Daily briefing", style = MaterialTheme.typography.labelLarge, color = HudColors.Blush, modifier = Modifier.weight(1f))
      if (b != null) {
        Text("Updated ${HudFormat.time(b.createdAt)}", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
      }
    }
    Spacer(Modifier.height(12.dp))
    if (b == null) {
      Text(
        if (state.isSyncing) "Putting your briefing together…" else "No briefing yet. Refresh and I'll put one together.",
        style = MaterialTheme.typography.bodyLarge,
        color = HudColors.TextDim,
      )
      if (!state.isSyncing) {
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(
          onClick = actions::onScanAndOrganize,
          colors = ButtonDefaults.filledTonalButtonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
        ) {
          Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Refresh")
        }
      }
      return@BentoCard
    }
    Text(b.headline, style = JarvisType.TitleSerif, color = HudColors.Text)
    Spacer(Modifier.height(6.dp))
    Text(b.summary, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
    if (b.highlights.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      val activeHighlight = b.highlights[highlightIdx.coerceIn(0, b.highlights.lastIndex)]
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(HudColors.Surface.copy(alpha = 0.85f))
          .border(1.dp, HudColors.OutlineSoft, RoundedCornerShape(14.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(7.dp).background(HudColors.Blush, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(activeHighlight, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text)
          if (canCycle) {
            Spacer(Modifier.height(2.dp))
            Text(
              "tap for another (${highlightIdx + 1}/${b.highlights.size})",
              style = JarvisType.PromptItalic,
              color = HudColors.TextMuted,
            )
          }
        }
      }
    }
    Spacer(Modifier.height(14.dp))
    Box(
      Modifier
        .clip(CircleShape)
        .background(HudColors.Surface)
        .border(1.dp, HudColors.Outline, CircleShape)
        .clickable(role = Role.Button, onClick = actions::onToggleSpeakBriefing)
        .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          if (state.isSpeaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
          null,
          tint = HudColors.Text,
          modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          if (state.isSpeaking) "Stop" else "Listen",
          style = MaterialTheme.typography.labelLarge,
          color = HudColors.Text,
        )
      }
    }
  }
}

@Composable
private fun DeviceCard(state: JarvisUiState) {
  val t = state.telemetry
  val batteryColor = when {
    t.isCharging -> HudColors.Sage
    t.batteryPercent <= 15 -> HudColors.Coral
    t.batteryPercent <= 30 -> HudColors.Sand
    else -> HudColors.SlateBlue
  }
  val used = (t.totalStorageGb - t.freeStorageGb).coerceAtLeast(0f)
  val frac = if (t.totalStorageGb > 0) used / t.totalStorageGb else 0f
  val storageColor = if (frac > 0.9f) HudColors.Coral else if (frac > 0.75f) HudColors.Sand else HudColors.SlateBlue
  SectionHeader("This phone")
  Spacer(Modifier.height(4.dp))
  BentoCard {
    ListRow(
      title = "Battery ${t.batteryPercent}%",
      subtitle = if (t.isCharging) "Charging" else "On battery",
      icon = if (t.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
      tint = batteryColor,
    )
    Meter(t.batteryPercent / 100f, batteryColor)
    Spacer(Modifier.height(12.dp))
    ListRow(
      title = String.format(Locale.US, "%.1f GB free", t.freeStorageGb),
      subtitle = String.format(Locale.US, "%d%% of %.0f GB used", (frac * 100).toInt(), t.totalStorageGb),
      icon = Icons.Outlined.SdStorage,
      tint = storageColor,
    )
    Meter(frac, storageColor)
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReminderSheet(
  dateKey: String,
  onAdd: (String, Int) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var selectedMinutes by remember { mutableIntStateOf(10 * 60) }
  val slots = listOf(
    "9:00 AM" to 9 * 60,
    "11:00 AM" to 11 * 60,
    "2:00 PM" to 14 * 60,
    "5:00 PM" to 17 * 60,
    "8:00 PM" to 20 * 60,
  )

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
      Text("Quick reminder", style = JarvisType.TitleSerif, color = HudColors.Text)
      Text("For $dateKey · Jarvis will nudge you gently.", style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("What would you like to remember?") },
        placeholder = { Text("Review Netflix renewal, evening walk…") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = jarvisTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
      )
      Text("Time", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { (label, mins) ->
          FilterChip(
            selected = selectedMinutes == mins,
            onClick = { selectedMinutes = mins },
            label = { Text(label) },
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = HudColors.SandSoft,
              selectedLabelColor = HudColors.Text,
            ),
          )
        }
      }
      Button(
        onClick = { onAdd(title, selectedMinutes) },
        enabled = title.isNotBlank(),
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
      ) {
        Text("Save reminder")
      }
    }
  }
}

private fun formatMinutesOfDay(mins: Int): String {
  val hh = (mins / 60).coerceIn(0, 23)
  val mm = (mins % 60).coerceIn(0, 59)
  val ampm = if (hh < 12) "AM" else "PM"
  val h12 = when {
    hh == 0 -> 12
    hh > 12 -> hh - 12
    else -> hh
  }
  return String.format(Locale.ENGLISH, "%d:%02d %s", h12, mm, ampm)
}

internal fun screenPadding(inner: PaddingValues) = PaddingValues(
  start = 18.dp,
  end = 18.dp,
  top = inner.calculateTopPadding() + 6.dp,
  bottom = inner.calculateBottomPadding() + 18.dp,
)
