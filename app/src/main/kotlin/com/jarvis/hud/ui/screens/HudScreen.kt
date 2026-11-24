package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.Priority
import com.jarvis.hud.ui.components.EmptyLine
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.IconBadge
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.ListRow
import com.jarvis.hud.ui.components.Meter
import com.jarvis.hud.ui.components.Refreshable
import com.jarvis.hud.ui.components.SectionHeader
import com.jarvis.hud.ui.components.SetupCard
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BaselineRangeBar
import com.jarvis.hud.ui.viz.BentoCard
import com.jarvis.hud.ui.viz.DayTimelineBar
import com.jarvis.hud.ui.viz.HexRadarChart
import com.jarvis.hud.ui.viz.RadarAxis
import com.jarvis.hud.ui.viz.ScoreRingSpec
import com.jarvis.hud.ui.viz.ScoreRingsCard
import com.jarvis.hud.ui.viz.TimelineDot
import com.jarvis.hud.ui.viz.TimelineSpan
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Calm personal bento Home tab: interactive daily insight banner, 3 status rings,
 * 24-hour unified day timeline, 2x3 quick-pulse grid, 6-axis Daily Pulse hex radar,
 * attention items, schedule, calls, screen time, and device telemetry.
 */
@Composable
fun HudScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val now = System.currentTimeMillis()
  val zone = remember { ZoneId.systemDefault() }
  val dayStart = HudFormat.startOfDay(now)
  val dayEnd = dayStart + 24 * 3_600_000L
  val dayMillis = 24f * 3_600_000f
  val active = remember(state.items) { state.items.filter { it.status == ItemStatus.ACTIVE } }
  val urgent = remember(active) {
    active
      .filter { it.priority == Priority.CRITICAL || it.priority == Priority.HIGH }
      .sortedWith(compareBy({ it.priority.ordinal }, { -it.timestamp }))
      .take(4)
  }
  val insights = remember(state.items) { SpendInsights.from(state.items, now, zone) }
  val spentToday = remember(state.items, dayStart) {
    state.items
      .filter { it.category == IntelCategory.FINANCE && !it.isCredit && it.amount != null && it.timestamp >= dayStart && it.status != ItemStatus.DISMISSED }
      .sumOf { it.amount ?: 0.0 }
  }
  val billsDue = active.count { it.category == IntelCategory.BILL }
  val today = remember(state.calendarEntries, dayStart, dayEnd) {
    state.calendarEntries.filter { it.startTime < dayEnd && it.endTime >= dayStart }.sortedBy { it.startTime }
  }
  val totalUsageMin = state.appUsageStats.sumOf { it.foregroundMinutes }

  Refreshable(state.isSyncing, actions::onScanAndOrganize, Modifier.fillMaxSize()) {
    LazyColumn(
      Modifier.fillMaxSize().testTag("screen-home"),
      contentPadding = screenPadding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "briefing") { BriefingCard(state, actions) }

      item(key = "scoreRings") {
        val budget = state.settings.monthlyBudget
        val budgetScore = if (budget > 0) ((1.0 - (insights.monthSpent / budget).coerceIn(0.0, 1.0)) * 100).roundToInt() else 78
        val billScore = (100 - billsDue * 20).coerceIn(15, 100)
        val inboxScore = (100 - active.size * 6).coerceIn(20, 100)
        val guidance = when {
          urgent.isNotEmpty() -> "Take it steady today. ${urgent.size} priority item${if (urgent.size == 1) "" else "s"} waiting below."
          billsDue > 0 -> "Smooth day ahead — $billsDue open bill${if (billsDue == 1) "" else "s"} on your radar."
          else -> "Calm rhythm today. Everything looks tidy."
        }
        ScoreRingsCard(
          rings = listOf(
            ScoreRingSpec("Budget", if (budget > 0) "$budgetScore" else "—", budgetScore / 100f, HudColors.Sky) {
              actions.onSelectTab(HudTab.FINANCE)
            },
            ScoreRingSpec("Bills", "$billScore", billScore / 100f, HudColors.Amber) {
              actions.onSelectTab(HudTab.FINANCE)
            },
            ScoreRingSpec("Inbox", "$inboxScore", inboxScore / 100f, HudColors.Lavender) {
              actions.onSelectTab(HudTab.INTEL)
            },
          ),
          guidance = guidance,
        )
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

      item(key = "dayTimeline") {
        val spans = buildList {
          today.forEach { e ->
            val s = ((e.startTime - dayStart) / dayMillis).coerceIn(0f, 1f)
            val en = ((e.endTime - dayStart) / dayMillis).coerceIn(s, 1f)
            add(TimelineSpan(s, en, HudColors.Lavender))
          }
          state.recentCalls.filter { it.timestamp in dayStart..dayEnd }.forEach { c ->
            val s = ((c.timestamp - dayStart) / dayMillis).coerceIn(0f, 1f)
            val en = (s + (c.durationSec * 1000f / dayMillis).coerceAtLeast(0.015f)).coerceAtMost(1f)
            add(TimelineSpan(s, en, if ("MISS" in c.callType.uppercase()) HudColors.Coral else HudColors.Mint))
          }
        }
        val todayPayments = state.items.filter {
          it.category == IntelCategory.FINANCE && !it.isCredit && it.timestamp in dayStart..dayEnd && it.status != ItemStatus.DISMISSED
        }
        val maxAmt = todayPayments.maxOfOrNull { it.amount ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0
        val dots = todayPayments.map { p ->
          val frac = ((p.timestamp - dayStart) / dayMillis).coerceIn(0f, 1f)
          val r = 4.2f + (((p.amount ?: 0.0) / maxAmt).toFloat() * 3.5f)
          TimelineDot(frac, HudColors.Amber, r)
        }
        val nowFrac = ((now - dayStart) / dayMillis).coerceIn(0f, 1f)
        val todayCallCount = state.recentCalls.count { it.timestamp in dayStart..dayEnd }
        val summaryLine = buildList {
          if (today.isNotEmpty()) add("${today.size} event${if (today.size == 1) "" else "s"}")
          if (todayPayments.isNotEmpty()) add("${todayPayments.size} payment${if (todayPayments.size == 1) "" else "s"}")
          if (todayCallCount > 0) add("$todayCallCount call${if (todayCallCount == 1) "" else "s"}")
        }.joinToString(" · ").ifEmpty { "Quiet day so far" }

        DayTimelineBar(
          spans = spans,
          dots = dots,
          nowFrac = nowFrac,
          summaryLine = summaryLine,
          legend = listOf(
            "Events" to HudColors.Lavender,
            "Spend" to HudColors.Amber,
            "Calls" to HudColors.Mint,
            "Now" to HudColors.Cream,
          ),
        )
      }

      item(key = "bentoGrid") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoMiniTile(
              title = "Spent today",
              value = HudFormat.money(spentToday),
              sub = if (insights.dailyAverage > 0) "Avg ${HudFormat.moneyCompact(insights.dailyAverage)}/day" else "No debits yet",
              icon = Icons.Outlined.Payments,
              tint = HudColors.Mint,
              modifier = Modifier.weight(1f),
            ) { actions.onSelectTab(HudTab.FINANCE) }
            BentoMiniTile(
              title = "Bills due",
              value = billsDue.toString(),
              sub = if (billsDue == 0) "All clear" else "Tap to review",
              icon = Icons.AutoMirrored.Outlined.ReceiptLong,
              tint = HudColors.Amber,
              modifier = Modifier.weight(1f),
            ) { actions.onSelectTab(HudTab.FINANCE) }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoMiniTile(
              title = "To review",
              value = active.size.toString(),
              sub = if (urgent.isNotEmpty()) "${urgent.size} urgent" else "Inbox calm",
              icon = Icons.Outlined.Inbox,
              tint = HudColors.Sky,
              modifier = Modifier.weight(1f),
            ) { actions.onSelectTab(HudTab.INTEL) }
            BentoMiniTile(
              title = "Schedule",
              value = "${today.size} today",
              sub = today.firstOrNull()?.let { HudFormat.time(it.startTime) + " " + it.title } ?: "Open calendar",
              icon = Icons.Outlined.CalendarMonth,
              tint = HudColors.Lavender,
              modifier = Modifier.weight(1f),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoMiniTile(
              title = "Screen time",
              value = if (totalUsageMin > 0) HudFormat.minutes(totalUsageMin) else "—",
              sub = state.appUsageStats.maxByOrNull { it.foregroundMinutes }?.appName ?: "Usage access",
              icon = Icons.Outlined.HourglassBottom,
              tint = HudColors.Peach,
              modifier = Modifier.weight(1f),
            )
            BentoMiniTile(
              title = "Phone",
              value = "${state.telemetry.batteryPercent}%",
              sub = String.format(java.util.Locale.US, "%.0f GB free", state.telemetry.freeStorageGb),
              icon = if (state.telemetry.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
              tint = if (state.telemetry.batteryPercent <= 20) HudColors.Coral else HudColors.Mint,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      item(key = "setup") {
        SetupCard(
          state.permissions,
          actions::onRequestRuntimePermissions,
          actions::onOpenNotificationAccessSettings,
          actions::onOpenUsageAccessSettings,
        )
      }

      item(key = "pulseRadar") {
        val budget = state.settings.monthlyBudget
        val spendFrac = if (budget > 0) (1.0 - (insights.monthSpent / budget).coerceIn(0.0, 1.0)).toFloat() else 0.76f
        val billsFrac = (1f - billsDue * 0.2f).coerceIn(0.2f, 1f)
        val inboxFrac = (1f - active.size * 0.06f).coerceIn(0.25f, 1f)
        val schedFrac = if (today.isEmpty()) 0.85f else (1f - (today.size - 1) * 0.1f).coerceIn(0.4f, 0.95f)
        val focusFrac = (1f - (totalUsageMin / 360f)).coerceIn(0.2f, 1f)
        val missedCount = state.recentCalls.count { "MISS" in it.callType.uppercase() }
        val callsFrac = (1f - missedCount * 0.2f).coerceIn(0.3f, 1f)

        val axes = listOf(
          RadarAxis("Spend", spendFrac, if (budget > 0) "${(spendFrac * 100).roundToInt()}% left" else HudFormat.moneyCompact(insights.monthSpent)),
          RadarAxis("Bills", billsFrac, if (billsDue == 0) "All clear" else "$billsDue open"),
          RadarAxis("Inbox", inboxFrac, "${active.size} active"),
          RadarAxis("Focus", focusFrac, if (totalUsageMin > 0) HudFormat.minutes(totalUsageMin) else "No usage data"),
          RadarAxis("Schedule", schedFrac, "${today.size} today"),
          RadarAxis("Calls", callsFrac, if (missedCount == 0) "0 missed" else "$missedCount missed"),
        )
        val overall = (axes.map { it.fraction }.average() * 100).roundToInt()
        val label = when {
          overall >= 80 -> "Calm & steady"
          overall >= 60 -> "Balanced pace"
          else -> "Busy day"
        }
        HexRadarChart(axes = axes, score = overall, headline = label)
      }

      item(key = "today") {
        SectionHeader("Today", trailing = if (today.isEmpty()) null else "${today.size} events")
        Spacer(Modifier.height(6.dp))
        BentoCard {
          if (today.isEmpty()) EmptyLine("No events on your calendar today.")
          today.forEachIndexed { i, e ->
            if (i > 0) Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.width(72.dp)) {
                Text(HudFormat.time(e.startTime), style = MaterialTheme.typography.labelLarge, color = HudColors.Amber)
                Text(HudFormat.time(e.endTime), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
              }
              Box(Modifier.width(3.dp).height(34.dp).background(HudColors.Lavender.copy(alpha = 0.7f), CircleShape))
              Spacer(Modifier.width(12.dp))
              Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                e.location?.takeIf { it.isNotBlank() }?.let {
                  Text(it, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
          }
        }
      }

      item(key = "calls") {
        SectionHeader("Recent calls")
        Spacer(Modifier.height(6.dp))
        BentoCard(padding = 14.dp) {
          if (state.recentCalls.isEmpty()) EmptyLine("No recent calls.", Modifier.padding(4.dp))
          state.recentCalls.take(5).forEach { c ->
            val type = c.callType.uppercase()
            val (icon, color, label) = when {
              "MISS" in type || "REJECT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMissed, HudColors.Coral, "Missed")
              "OUT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMade, HudColors.Sky, "Outgoing")
              else -> Triple(Icons.AutoMirrored.Outlined.CallReceived, HudColors.Mint, "Incoming")
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

      item(key = "usage") {
        val top = state.appUsageStats.sortedByDescending { it.foregroundMinutes }.take(5)
        SectionHeader("Screen time", trailing = if (totalUsageMin > 0) HudFormat.minutes(totalUsageMin) + " today" else null)
        Spacer(Modifier.height(6.dp))
        BentoCard {
          if (top.isEmpty()) EmptyLine("No screen time data yet.")
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

      item(key = "device") { DeviceCard(state) }
    }
  }
}

@Composable
private fun BentoMiniTile(
  title: String,
  value: String,
  sub: String,
  icon: ImageVector,
  tint: Color,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  BentoCard(modifier = modifier, tint = tint, onClick = onClick, padding = 14.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconBadge(icon, tint, size = 30.dp)
      Spacer(Modifier.width(8.dp))
      Text(title, style = MaterialTheme.typography.labelMedium, color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(10.dp))
    Text(value, style = JarvisType.MetricMedium, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(2.dp))
    Text(sub, style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun BriefingCard(state: JarvisUiState, actions: JarvisActions) {
  val b = state.briefing
  var highlightIdx by remember(b?.dateKey) { mutableIntStateOf(0) }
  val canCycle = b != null && b.highlights.size > 1

  BentoCard(
    tint = HudColors.Amber,
    onClick = if (canCycle) {
      { highlightIdx = (highlightIdx + 1) % b.highlights.size }
    } else null,
    padding = 18.dp,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconBadge(Icons.Outlined.AutoAwesome, HudColors.Amber, size = 30.dp)
      Spacer(Modifier.width(10.dp))
      Text("Daily briefing", style = MaterialTheme.typography.labelLarge, color = HudColors.Amber, modifier = Modifier.weight(1f))
      if (b != null) {
        Text("Updated ${HudFormat.time(b.createdAt)}", style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
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
          .background(HudColors.SurfaceHigh)
          .border(1.dp, HudColors.OutlineSoft, RoundedCornerShape(14.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(7.dp).background(HudColors.Amber, CircleShape))
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box(
        Modifier
          .clip(CircleShape)
          .background(HudColors.Cream.copy(alpha = 0.14f))
          .border(1.dp, HudColors.Cream.copy(alpha = 0.28f), CircleShape)
          .clickable(role = Role.Button, onClick = actions::onToggleSpeakBriefing)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            if (state.isSpeaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
            null,
            tint = HudColors.Cream,
            modifier = Modifier.size(16.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            if (state.isSpeaking) "Stop" else "Listen",
            style = MaterialTheme.typography.labelLarge,
            color = HudColors.Cream,
          )
        }
      }
    }
  }
}

@Composable
private fun DeviceCard(state: JarvisUiState) {
  val t = state.telemetry
  val batteryColor = when {
    t.isCharging -> HudColors.Mint
    t.batteryPercent <= 15 -> HudColors.Coral
    t.batteryPercent <= 30 -> HudColors.Amber
    else -> HudColors.Sky
  }
  val used = (t.totalStorageGb - t.freeStorageGb).coerceAtLeast(0f)
  val frac = if (t.totalStorageGb > 0) used / t.totalStorageGb else 0f
  val storageColor = if (frac > 0.9f) HudColors.Coral else if (frac > 0.75f) HudColors.Amber else HudColors.Sky
  SectionHeader("This phone")
  Spacer(Modifier.height(6.dp))
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
      title = String.format(java.util.Locale.US, "%.1f GB free", t.freeStorageGb),
      subtitle = String.format(java.util.Locale.US, "%d%% of %.0f GB used", (frac * 100).toInt(), t.totalStorageGb),
      icon = Icons.Outlined.SdStorage,
      tint = storageColor,
    )
    Meter(frac, storageColor)
  }
}

/** Shared list padding: scaffold insets + screen gutter. */
internal fun screenPadding(inner: PaddingValues) = PaddingValues(
  start = 18.dp,
  end = 18.dp,
  top = inner.calculateTopPadding() + 6.dp,
  bottom = inner.calculateBottomPadding() + 18.dp,
)
