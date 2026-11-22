package com.jarvis.hud.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.PermissionStatus
import com.jarvis.hud.ui.theme.HudColors

/**
 * Sensor-array access panel: per-permission status and grant buttons for whatever is missing.
 */
@Composable
fun PermissionBanner(
  permissions: PermissionStatus,
  onRequestRuntime: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val count = permissions.grantedCount
  val accent = when {
    count == 5 -> HudColors.Green
    count >= 3 -> HudColors.Amber
    else -> HudColors.Red
  }
  HudPanel(
    modifier = modifier,
    title = "Sensor Array",
    accent = accent,
    trailing = { Text("$count/5 ONLINE", style = MaterialTheme.typography.labelSmall, color = accent) },
  ) {
    HudBar(count / 5f, color = accent, segments = 5)
    Spacer(Modifier.height(10.dp))
    val rows = listOf(
      "SMS" to permissions.smsGranted,
      "NOTIFICATIONS" to permissions.notificationAccessGranted,
      "CALENDAR" to permissions.calendarGranted,
      "CALL LOG" to permissions.callLogGranted,
      "SCREEN TIME" to permissions.usageAccessGranted,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      rows.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth()) {
          pair.forEach { (label, ok) ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                null,
                tint = if (ok) HudColors.Green else HudColors.TextMuted,
                modifier = Modifier.size(13.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(label, style = MaterialTheme.typography.labelSmall, color = if (ok) HudColors.Text else HudColors.TextMuted)
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }
    }
    if (count < 5) {
      Spacer(Modifier.height(12.dp))
      val runtimeMissing = !permissions.smsGranted || !permissions.calendarGranted || !permissions.callLogGranted
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (runtimeMissing) {
          HudButton("Grant SMS / Calendar / Calls", onRequestRuntime, Modifier.fillMaxWidth(), accent = accent)
        }
        if (!permissions.notificationAccessGranted) {
          HudButton("Enable notification access", onOpenNotificationAccess, Modifier.fillMaxWidth(), accent = accent, filled = false)
        }
        if (!permissions.usageAccessGranted) {
          HudButton("Enable usage access", onOpenUsageAccess, Modifier.fillMaxWidth(), accent = accent, filled = false)
        }
      }
    }
  }
}
