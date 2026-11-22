package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.Priority
import com.jarvis.hud.ui.components.CardShape
import com.jarvis.hud.ui.components.EmptyLine
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.JCard
import com.jarvis.hud.ui.components.ListRow
import com.jarvis.hud.ui.components.Meter
import com.jarvis.hud.ui.components.SectionHeader
import com.jarvis.hud.ui.components.SetupCard
import com.jarvis.hud.ui.components.StatCard
import com.jarvis.hud.ui.theme.HudColors

/** Home tab: greeting, briefing, quick stats, setup, what needs attention, today. */
@Composable
fun HudScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val now = System.currentTimeMillis()
  val dayStart = HudFormat.startOfDay(now)
  val dayEnd = dayStart + 24 * 3_600_000L
  val active = state.items.filter { it.status == ItemStatus.ACTIVE }
  val urgent = active
    .filter { it.priority == Priority.CRITICAL || it.priority == Priority.HIGH }
    .sortedWith(compareBy({ it.priority.ordinal }, { -it.timestamp }))
    .take(4)
  val spentToday = state.items
    .filter { it.category == IntelCategory.FINANCE && !it.isCredit && it.amount != null && it.timestamp >= dayStart && it.status != ItemStatus.DISMISSED }
    .sumOf { it.amount ?: 0.0 }
  val billsDue = active.count { it.category == IntelCategory.BILL }
  val today = state.calendarEntries.filter { it.startTime < dayEnd && it.endTime >= dayStart }.sortedBy { it.startTime }

  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = screenPadding(contentPadding),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "greeting") {
      Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Text(HudFormat.longDate(now), style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
        Spacer(Modifier.height(2.dp))
        Text(
          "${HudFormat.greeting(now)}, ${state.settings.userCallsign.ifBlank { "Sir" }}",
          style = MaterialTheme.typography.headlineMedium,
          color = HudColors.Text,
        )
      }
    }
    item(key = "briefing") { BriefingCard(state, actions) }
    item(key = "stats") {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("Spent today", HudFormat.money(spentToday), Icons.Outlined.Payments, HudColors.Green, Modifier.weight(1f)) {
          actions.onSelectTab(HudTab.FINANCE)
        }
        StatCard("Bills due", billsDue.toString(), Icons.AutoMirrored.Outlined.ReceiptLong, HudColors.Amber, Modifier.weight(1f)) {
          actions.onSelectTab(HudTab.FINANCE)
        }
        StatCard("To review", active.size.toString(), Icons.Outlined.Inbox, HudColors.Cyan, Modifier.weight(1f)) {
          actions.onSelectTab(HudTab.INTEL)
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
    item(key = "urgentHeader") {
      SectionHeader("Needs attention", trailing = if (active.isNotEmpty()) "See all" else null, onTrailingClick = { actions.onSelectTab(HudTab.INTEL) })
    }
    if (urgent.isEmpty()) {
      item(key = "urgentEmpty") {
        JCard { EmptyLine("Nothing urgent. You're all caught up.") }
      }
    } else {
      items(urgent, key = { "u-" + it.id }) { ItemCard(it, actions::onMarkItemStatus) }
    }
    item(key = "today") {
      SectionHeader("Today", trailing = if (today.isEmpty()) null else "${today.size} events")
      Spacer(Modifier.height(8.dp))
      JCard {
        if (today.isEmpty()) EmptyLine("No events on your calendar today.")
        today.forEachIndexed { i, e ->
          if (i > 0) Spacer(Modifier.height(4.dp))
          Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp)) {
              Text(HudFormat.time(e.startTime), style = MaterialTheme.typography.labelLarge, color = HudColors.Cyan)
              Text(HudFormat.time(e.endTime), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
            }
            Box(Modifier.width(3.dp).height(34.dp).background(HudColors.Cyan.copy(alpha = 0.5f), CircleShape))
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
      Spacer(Modifier.height(8.dp))
      JCard(padding = 12.dp) {
        if (state.recentCalls.isEmpty()) EmptyLine("No recent calls.", Modifier.padding(4.dp))
        state.recentCalls.take(5).forEach { c ->
          val type = c.callType.uppercase()
          val (icon, color, label) = when {
            "MISS" in type || "REJECT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMissed, HudColors.Red, "Missed")
            "OUT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMade, HudColors.Cyan, "Outgoing")
            else -> Triple(Icons.AutoMirrored.Outlined.CallReceived, HudColors.Green, "Incoming")
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
      val total = state.appUsageStats.sumOf { it.foregroundMinutes }
      SectionHeader("Screen time", trailing = if (total > 0) HudFormat.minutes(total) + " today" else null)
      Spacer(Modifier.height(8.dp))
      JCard {
        if (top.isEmpty()) EmptyLine("No screen time data yet.")
        val max = (top.maxOfOrNull { it.foregroundMinutes } ?: 1L).coerceAtLeast(1L)
        top.forEachIndexed { i, u ->
          if (i > 0) Spacer(Modifier.height(12.dp))
          Row {
            Text(u.appName, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text, maxLines = 1,
              overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(HudFormat.minutes(u.foregroundMinutes), style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
          }
          Spacer(Modifier.height(6.dp))
          Meter(u.foregroundMinutes / max.toFloat(), HudColors.Violet)
        }
      }
    }
    item(key = "device") { DeviceCard(state) }
  }
}

@Composable
private fun BriefingCard(state: JarvisUiState, actions: JarvisActions) {
  val b = state.briefing
  Column(
    Modifier
      .fillMaxWidth()
      .background(
        Brush.linearGradient(listOf(HudColors.Cyan.copy(alpha = 0.20f), HudColors.Violet.copy(alpha = 0.10f), HudColors.Surface)),
        CardShape,
      )
      .padding(18.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.AutoAwesome, null, tint = HudColors.Cyan, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text("Daily briefing", style = MaterialTheme.typography.labelLarge, color = HudColors.Cyan, modifier = Modifier.weight(1f))
      if (b != null) {
        Text("Updated ${HudFormat.time(b.createdAt)}", style = MaterialTheme.typography.labelMedium, color = HudColors.TextDim)
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
        FilledTonalButton(onClick = actions::onScanAndOrganize) {
          Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Refresh")
        }
      }
      return@Column
    }
    Text(b.headline, style = MaterialTheme.typography.titleLarge, color = HudColors.Text)
    Spacer(Modifier.height(6.dp))
    Text(b.summary, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
    if (b.highlights.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      b.highlights.forEach { h ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
          Box(Modifier.padding(top = 7.dp).size(6.dp).background(HudColors.Cyan, CircleShape))
          Spacer(Modifier.width(10.dp))
          Text(h, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text)
        }
      }
    }
    Spacer(Modifier.height(14.dp))
    OutlinedButton(onClick = actions::onToggleSpeakBriefing) {
      Icon(if (state.isSpeaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(if (state.isSpeaking) "Stop" else "Listen")
    }
  }
}

@Composable
private fun DeviceCard(state: JarvisUiState) {
  val t = state.telemetry
  val batteryColor = when {
    t.isCharging -> HudColors.Green
    t.batteryPercent <= 15 -> HudColors.Red
    t.batteryPercent <= 30 -> HudColors.Amber
    else -> HudColors.Cyan
  }
  val used = (t.totalStorageGb - t.freeStorageGb).coerceAtLeast(0f)
  val frac = if (t.totalStorageGb > 0) used / t.totalStorageGb else 0f
  val storageColor = if (frac > 0.9f) HudColors.Red else if (frac > 0.75f) HudColors.Amber else HudColors.Cyan
  SectionHeader("This phone")
  Spacer(Modifier.height(8.dp))
  JCard {
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
  start = 16.dp,
  end = 16.dp,
  top = inner.calculateTopPadding() + 8.dp,
  bottom = inner.calculateBottomPadding() + 24.dp,
)
