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
import androidx.compose.material.icons.outlined.Sms
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
) = listOf(
  Access("SMS", "Bank alerts, bills and OTP-free summaries", Icons.Outlined.Sms, p.smsGranted, onRequestRuntime),
  Access("Notifications", "Deliveries, messages and app alerts", Icons.Outlined.Notifications, p.notificationAccessGranted, onOpenNotificationAccess),
  Access("Calendar", "Today's schedule", Icons.Outlined.CalendarMonth, p.calendarGranted, onRequestRuntime),
  Access("Call log", "Recent and missed calls", Icons.Outlined.Call, p.callLogGranted, onRequestRuntime),
  Access("Screen time", "Which apps you used today", Icons.Outlined.HourglassBottom, p.usageAccessGranted, onOpenUsageAccess),
)

/**
 * Lists each data source Jarvis can read with an "Allow" button for the missing ones.
 * [onlyMissing] hides rows that are already granted (used on Home).
 */
@Composable
fun PermissionList(
  permissions: PermissionStatus,
  onRequestRuntime: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  modifier: Modifier = Modifier,
  onlyMissing: Boolean = false,
) {
  val rows = accessList(permissions, onRequestRuntime, onOpenNotificationAccess, onOpenUsageAccess)
    .filter { !onlyMissing || !it.granted }
  Column(modifier) {
    rows.forEach { a ->
      ListRow(
        title = a.title,
        subtitle = a.why,
        icon = a.icon,
        tint = if (a.granted) HudColors.Green else HudColors.Cyan,
        trailing = {
          if (a.granted) {
            Icon(Icons.Outlined.CheckCircle, "Allowed", tint = HudColors.Green, modifier = Modifier.size(22.dp))
          } else {
            FilledTonalButton(onClick = a.grant, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Allow") }
          }
        },
      )
    }
  }
}

/** Home-screen card nudging the user to finish granting access. Hidden when everything is granted. */
@Composable
fun SetupCard(
  permissions: PermissionStatus,
  onRequestRuntime: () -> Unit,
  onOpenNotificationAccess: () -> Unit,
  onOpenUsageAccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val granted = permissions.grantedCount
  if (granted == 5) return
  JCard(modifier) {
    Text("Finish setting up", style = MaterialTheme.typography.titleMedium, color = HudColors.Text)
    Spacer(Modifier.height(2.dp))
    Text(
      "$granted of 5 sources connected. Jarvis only reads data on this phone.",
      style = MaterialTheme.typography.bodySmall,
      color = HudColors.TextDim,
    )
    Spacer(Modifier.height(10.dp))
    Meter(granted / 5f, HudColors.Cyan)
    Spacer(Modifier.height(6.dp))
    PermissionList(permissions, onRequestRuntime, onOpenNotificationAccess, onOpenUsageAccess, onlyMissing = true)
  }
}
