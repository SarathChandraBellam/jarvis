package com.jarvis.hud.data

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.hud.data.source.ContentHash
import com.jarvis.hud.data.source.NotificationHub
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.RawEvent

/**
 * Captures posted notifications and forwards them to [NotificationHub] as
 * [DataSource.NOTIFICATION] [RawEvent]s. Skips our own package, ongoing and group-summary
 * notifications, and notifications without text.
 */
class JarvisNotificationListener : NotificationListenerService() {
  private val labelCache = HashMap<String, String>()

  override fun onListenerConnected() {
    super.onListenerConnected()
    val active = try {
      activeNotifications
    } catch (_: Exception) {
      null
    } ?: return
    active.sortedBy { it.postTime }.forEach(::handle)
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn != null) handle(sbn)
  }

  private fun handle(sbn: StatusBarNotification) {
    try {
      toRawEvent(sbn)?.let(NotificationHub::publish)
    } catch (_: Exception) {
      // Never crash the listener on a malformed notification.
    }
  }

  private fun toRawEvent(sbn: StatusBarNotification): RawEvent? {
    if (sbn.packageName == packageName) return null
    if (sbn.isOngoing) return null
    val n = sbn.notification ?: return null
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
    if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return null

    val extras = n.extras ?: return null
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
    val body = if (bigText.length > text.length) bigText else text
    if (body.isEmpty()) return null

    val sender = appLabel(sbn.packageName)
    val ts = sbn.postTime
    val hash = ContentHash.of(DataSource.NOTIFICATION, sender, body, ts)
    return RawEvent(
      id = "notif-${hash.take(16)}",
      source = DataSource.NOTIFICATION,
      sender = sender,
      title = title.ifEmpty { sender },
      body = body,
      timestamp = ts,
      contentHash = hash,
    )
  }

  private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
    try {
      val pm = packageManager
      val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(pkg, 0)
      }
      pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
      pkg
    }
  }
}
