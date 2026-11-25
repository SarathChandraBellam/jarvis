package com.astra.hud.data.source

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.astra.hud.contracts.PermissionChecker
import com.astra.hud.model.PermissionStatus

/**
 * Reports runtime permission grants plus notification-listener, usage-access,
 * and Android 13+ notification-posting permissions.
 */
class AndroidPermissionChecker(context: Context) : PermissionChecker {
  private val appContext = context.applicationContext

  override fun current(): PermissionStatus {
    val postGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      appContext.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
    val activityGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      appContext.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
      true
    }
    return PermissionStatus(
      smsGranted = appContext.hasPermission(Manifest.permission.READ_SMS),
      notificationAccessGranted = appContext.hasNotificationAccess(),
      calendarGranted = appContext.hasPermission(Manifest.permission.READ_CALENDAR),
      callLogGranted = appContext.hasPermission(Manifest.permission.READ_CALL_LOG),
      usageAccessGranted = appContext.hasUsageAccess(),
      notificationsPostGranted = postGranted,
      activityRecognitionGranted = activityGranted,
    )
  }
}

internal fun Context.hasPermission(permission: String): Boolean =
  ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.hasNotificationAccess(): Boolean =
  NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

internal fun Context.hasUsageAccess(): Boolean = try {
  val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
  val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
  } else {
    @Suppress("DEPRECATION")
    appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
  }
  if (mode == AppOpsManager.MODE_DEFAULT) {
    hasPermission(Manifest.permission.PACKAGE_USAGE_STATS)
  } else {
    mode == AppOpsManager.MODE_ALLOWED
  }
} catch (_: Exception) {
  false
}
