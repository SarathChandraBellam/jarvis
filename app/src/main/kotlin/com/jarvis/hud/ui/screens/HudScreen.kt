package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.Priority
import com.jarvis.hud.ui.components.ArcReactor
import com.jarvis.hud.ui.components.HudBar
import com.jarvis.hud.ui.components.HudButton
import com.jarvis.hud.ui.components.HudEmptyLine
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.HudPanel
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.PermissionBanner
import com.jarvis.hud.ui.components.StatTile
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.HudMono

@Composable
fun HudScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val urgent = state.items
    .filter { it.status == ItemStatus.ACTIVE && (it.priority == Priority.CRITICAL || it.priority == Priority.HIGH) }
    .sortedWith(compareBy({ it.priority.ordinal }, { -it.timestamp }))
    .take(6)
  val dayStart = HudFormat.startOfDay()
  val dayEnd = dayStart + 24 * 3_600_000L
  val today = state.calendarEntries
    .filter { it.startTime < dayEnd && it.endTime >= dayStart }
    .sortedBy { it.startTime }

  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = screenPadding(contentPadding),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item(key = "reactor") { ReactorHeader(state) }
    item(key = "briefing") { BriefingPanel(state, actions) }
    item(key = "actions") {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HudButton(
          text = if (state.isSyncing) "Scanning…" else "Scan & Organize",
          onClick = actions::onScanAndOrganize,
          enabled = !state.isSyncing,
          icon = Icons.Outlined.Radar,
          modifier = Modifier.weight(1f),
        )
        HudButton(
          text = "Sim alert",
          onClick = actions::onInjectSimulatedAlert,
          accent = HudColors.Amber,
          icon = Icons.Outlined.Bolt,
          filled = false,
        )
      }
    }
    item(key = "telemetry") { TelemetryPanel(state) }
    item(key = "perms") {
      PermissionBanner(
        state.permissions,
        actions::onRequestRuntimePermissions,
        actions::onOpenNotificationAccessSettings,
        actions::onOpenUsageAccessSettings,
      )
    }
    item(key = "urgentHeader") {
      HudPanel(title = "Priority Threats", accent = if (urgent.any { it.priority == Priority.CRITICAL }) HudColors.Red else HudColors.Amber,
        trailing = { Text("${urgent.size}", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim) }) {
        if (urgent.isEmpty()) {
          HudEmptyLine("No critical or high-priority intel")
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            urgent.forEach { ItemCard(it, actions::onMarkItemStatus, compact = true) }
          }
        }
      }
    }
    item(key = "calendar") {
      HudPanel(title = "Today's Schedule", trailing = { Text("${today.size} EVT", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim) }) {
        if (today.isEmpty()) HudEmptyLine("Calendar clear")
        today.forEach { e ->
          Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(54.dp)) {
              Text(HudFormat.time(e.startTime), style = MaterialTheme.typography.labelLarge, color = HudColors.Cyan)
              Text(HudFormat.time(e.endTime), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
            }
            Box(Modifier.width(2.dp).height(30.dp).background(HudColors.Border))
            Spacer(Modifier.width(10.dp))
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
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        HudPanel(Modifier.weight(1f), title = "Comms") {
          if (state.recentCalls.isEmpty()) HudEmptyLine("No calls")
          state.recentCalls.take(5).forEach { c ->
            val type = c.callType.uppercase()
            val (icon, color) = when {
              "MISS" in type || "REJECT" in type -> Icons.AutoMirrored.Outlined.CallMissed to HudColors.Red
              "OUT" in type -> Icons.AutoMirrored.Outlined.CallMade to HudColors.Cyan
              else -> Icons.AutoMirrored.Outlined.CallReceived to HudColors.Green
            }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
              Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(6.dp))
              Column {
                Text(c.nameOrNumber, style = MaterialTheme.typography.bodySmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "${HudFormat.relative(c.timestamp)} · ${HudFormat.duration(c.durationSec)}",
                  style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1,
                )
              }
            }
          }
        }
        HudPanel(Modifier.weight(1f), title = "Screen Time", accent = HudColors.Violet) {
          val top = state.appUsageStats.sortedByDescending { it.foregroundMinutes }.take(5)
          val max = (top.maxOfOrNull { it.foregroundMinutes } ?: 1L).coerceAtLeast(1L)
          if (top.isEmpty()) HudEmptyLine("No usage data")
          top.forEach { u ->
            Column(Modifier.padding(vertical = 3.dp)) {
              Row {
                Text(u.appName, style = MaterialTheme.typography.bodySmall, color = HudColors.Text, maxLines = 1,
                  overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(HudFormat.minutes(u.foregroundMinutes), style = MaterialTheme.typography.labelSmall, color = HudColors.Violet)
              }
              Spacer(Modifier.height(3.dp))
              HudBar(u.foregroundMinutes / max.toFloat(), color = HudColors.Violet, segments = 12)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ReactorHeader(state: JarvisUiState) {
  Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    ArcReactor(size = 172.dp, active = state.isSyncing || state.isSpeaking)
    Spacer(Modifier.height(12.dp))
    Text(
      "${HudFormat.greeting()}, ${state.settings.userCallsign.ifBlank { "Sir" }}.",
      style = MaterialTheme.typography.headlineSmall,
      color = HudColors.Text,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    val active = state.items.count { it.status == ItemStatus.ACTIVE }
    Text(
      "${active} ACTIVE INTEL · ${state.permissions.grantedCount}/5 SENSORS" +
        if (state.settings.demoModeEnabled) " · DEMO" else "",
      style = MaterialTheme.typography.labelSmall,
      color = HudColors.TextDim,
    )
  }
}

@Composable
private fun BriefingPanel(state: JarvisUiState, actions: JarvisActions) {
  val b = state.briefing
  HudPanel(
    title = "Daily Briefing",
    trailing = {
      IconButton(onClick = actions::onToggleSpeakBriefing, enabled = b != null, modifier = Modifier.size(32.dp)) {
        val speaking = state.isSpeaking
        Icon(
          if (speaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp,
          contentDescription = if (speaking) "Stop speaking" else "Speak briefing",
          tint = if (speaking) HudColors.Amber else HudColors.Cyan,
        )
      }
    },
  ) {
    if (b == null) {
      Text(
        "No briefing compiled yet. Run SCAN & ORGANIZE and I'll assemble one.",
        style = MaterialTheme.typography.bodyMedium,
        color = HudColors.TextDim,
      )
      return@HudPanel
    }
    Text(b.headline, style = MaterialTheme.typography.titleMedium, color = HudColors.Cyan, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(b.summary, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text)
    if (b.highlights.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      b.highlights.forEach { h ->
        Row(Modifier.padding(vertical = 2.dp)) {
          Text("▸ ", color = HudColors.Cyan, fontFamily = HudMono, style = MaterialTheme.typography.bodySmall)
          Text(h, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      "COMPILED ${HudFormat.time(b.createdAt)} // ${b.modelUsed.uppercase()}",
      style = MaterialTheme.typography.labelSmall,
      color = HudColors.TextMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.clickable(role = Role.Button, onClick = actions::onToggleSpeakBriefing),
    )
  }
}

@Composable
private fun TelemetryPanel(state: JarvisUiState) {
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
  HudPanel(title = "Telemetry") {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatTile(
        "Power", "${t.batteryPercent}%",
        Modifier.weight(1f),
        accent = batteryColor,
        sub = if (t.isCharging) "CHARGING" else "ON BATTERY",
        icon = if (t.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
      )
      StatTile(
        "Storage free", String.format(java.util.Locale.US, "%.1f GB", t.freeStorageGb),
        Modifier.weight(1f),
        accent = storageColor,
        sub = String.format(java.util.Locale.US, "OF %.0f GB", t.totalStorageGb),
        icon = Icons.Outlined.SdStorage,
      )
    }
    Spacer(Modifier.height(10.dp))
    Text("POWER CELL", style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    Spacer(Modifier.height(4.dp))
    HudBar(t.batteryPercent / 100f, color = batteryColor)
    Spacer(Modifier.height(8.dp))
    Text("STORAGE ${(frac * 100).toInt()}% USED", style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    Spacer(Modifier.height(4.dp))
    HudBar(frac, color = storageColor)
  }
}

/** Shared list padding: scaffold insets + HUD gutter. */
internal fun screenPadding(inner: PaddingValues) = PaddingValues(
  start = 16.dp,
  end = 16.dp,
  top = inner.calculateTopPadding() + 12.dp,
  bottom = inner.calculateBottomPadding() + 24.dp,
)
