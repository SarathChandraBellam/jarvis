package com.jarvis.hud.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.PermissionStatus
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BentoCard

private data class Access(
  val title: String,
  val why: String,
  val icon: ImageVector,
  val granted: Boolean,
  val grant: () -> Unit,
)

private fun accessList(
  p: PermissionStatus,
  onRequestRuntime: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  includePostNotifications: Boolean = false,
) = buildList {
  add(Access("SMS", "Subscription alerts, bills and deliveries", Icons.Outlined.Sms, p.smsGranted, onRequestRuntime))
  add(Access("Notifications", "App alerts, renewals and messages", Icons.Outlined.Notifications, p.notificationAccessGranted, onOpenNotificationAccess))
  add(Access("Calendar", "Today's schedule and planner", Icons.Outlined.CalendarMonth, p.calendarGranted, onRequestRuntime))
  add(Access("Call log", "Recent and missed call nudges", Icons.Outlined.Call, p.callLogGranted, onRequestRuntime))
  add(Access("Screen time", "Focus insights and gentle nudges", Icons.Outlined.HourglassBottom, p.usageAccessGranted, onOpenUsageAccess))
  if (includePostNotifications) {
    add(Access("Proactive alerts", "Renewal reminders and morning digests", Icons.Outlined.NotificationsActive, p.notificationsPostGranted, onRequestRuntime))
  }
}

@Composable
fun PermissionList(
  permissions: PermissionStatus,
  onRequestRuntime: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  modifier: Modifier = Modifier,
  onlyMissing: Boolean = false,
  includePostNotifications: Boolean = false,
) {
  val rows = accessList(permissions, onRequestRuntime, onOpenNotificationAccess, onOpenUsageAccess, includePostNotifications)
    .filter { !onlyMissing || !it.granted }
  Column(modifier) {
    rows.forEach { a ->
      ListRow(
        title = a.title,
        subtitle = a.why,
        icon = a.icon,
        tint = if (a.granted) HudColors.Sage else HudColors.SlateBlue,
        trailing = {
          if (a.granted) {
            Icon(Icons.Outlined.CheckCircle, "Allowed", tint = HudColors.Sage, modifier = Modifier.size(22.dp))
          } else {
            FilledTonalButton(
              onClick = a.grant,
              contentPadding = PaddingValues(horizontal = 16.dp),
              colors = ButtonDefaults.filledTonalButtonColors(containerColor = HudColors.BlushSoft, contentColor = HudColors.Text),
            ) { Text("Allow") }
          }
        },
      )
    }
  }
}

@Composable
fun SetupCard(
  permissions: PermissionStatus,
  onRequestRuntime: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val granted = permissions.grantedCount
  if (granted == 5 && permissions.notificationsPostGranted) return
  BentoCard(modifier = modifier, pastelBg = HudColors.SlateBlueSoft) {
    Text("Finish setting up", style = JarvisType.SectionSerif, color = HudColors.Text)
    Spacer(Modifier.height(4.dp))
    Text(
      "$granted of 5 sources connected. Jarvis stays local on this phone.",
      style = MaterialTheme.typography.bodySmall,
      color = HudColors.TextDim,
    )
    Spacer(Modifier.height(10.dp))
    Meter(granted / 5f, HudColors.SlateBlue)
    Spacer(Modifier.height(6.dp))
    PermissionList(permissions, onRequestRuntime, onOpenNotificationAccess, onOpenUsageAccess, onlyMissing = true)
  }
}
