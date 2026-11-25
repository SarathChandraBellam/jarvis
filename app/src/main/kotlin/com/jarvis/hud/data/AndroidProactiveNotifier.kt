package com.jarvis.hud.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jarvis.hud.MainActivity
import com.jarvis.hud.contracts.ProactiveNotifier
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.ProactiveAlert
import com.jarvis.hud.model.ProactiveKind

/**
 * Posts calm, grouped Android proactive notifications for subscription renewals,
 * bills due, morning briefings, reminders, and gentle focus nudges.
 */
class AndroidProactiveNotifier(context: Context) : ProactiveNotifier {
  private val app = context.applicationContext

  override fun notifyAlerts(
    alerts: List<ProactiveAlert>,
    settings: JarvisSettings,
    alreadyNotified: Set<String>,
  ): List<String> {
    if (!settings.proactiveNotificationsEnabled || !canPost()) return emptyList()
    ensureChannels()
    val manager = NotificationManagerCompat.from(app)
    val fresh = alerts.filter { it.dedupKey !in alreadyNotified && it.priority != Priority.LOW }.take(3)
    val posted = mutableListOf<String>()
    fresh.forEach { alert ->
      val channelId = when (alert.kind) {
        ProactiveKind.RENEWAL, ProactiveKind.BILL -> CH_RENEWALS
        ProactiveKind.BRIEFING -> CH_BRIEFING
        else -> CH_NUDGES
      }
      val notif = NotificationCompat.Builder(app, channelId)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(alert.title)
        .setContentText(alert.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
        .setPriority(
          if (alert.priority == Priority.CRITICAL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
        )
        .setAutoCancel(true)
        .setContentIntent(launchIntent())
        .build()
      try {
        manager.notify(alert.dedupKey.hashCode(), notif)
        posted += alert.dedupKey
      } catch (_: SecurityException) {
        // Permission revoked between check and post.
      }
    }
    return posted
  }

  override fun sendTestNotification(callsign: String): Boolean {
    if (!canPost()) return false
    ensureChannels()
    val name = callsign.ifBlank { "Sir" }
    val notif = NotificationCompat.Builder(app, CH_NUDGES)
      .setSmallIcon(android.R.drawable.ic_popup_reminder)
      .setContentTitle("Jarvis proactive alerts are active")
      .setContentText("Good day, $name — I'll gently nudge you before subscription renewals, bills, and planned reminders.")
      .setAutoCancel(true)
      .setContentIntent(launchIntent())
      .build()
    return try {
      NotificationManagerCompat.from(app).notify(9001, notif)
      true
    } catch (_: SecurityException) {
      false
    }
  }

  private fun canPost(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }
    return NotificationManagerCompat.from(app).areNotificationsEnabled()
  }

  private fun launchIntent(): PendingIntent {
    val intent = Intent(app, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  private fun ensureChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channels = listOf(
      NotificationChannel(CH_RENEWALS, "Subscriptions & Bills", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Upcoming subscription renewals, trials, and bills due soon"
      },
      NotificationChannel(CH_BRIEFING, "Morning Briefing", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Calm daily briefing summary"
      },
      NotificationChannel(CH_NUDGES, "Reminders & Focus", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Planned reminders, deliveries, missed calls, and focus nudges"
      },
    )
    nm.createNotificationChannels(channels)
  }

  companion object {
    const val CH_RENEWALS = "jarvis_renewals"
    const val CH_BRIEFING = "jarvis_briefing"
    const val CH_NUDGES = "jarvis_nudges"
  }
}
