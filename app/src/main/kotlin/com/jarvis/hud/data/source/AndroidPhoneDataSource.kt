package com.jarvis.hud.data.source

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.Telephony
import com.jarvis.hud.contracts.PhoneDataSource
import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.DeviceTelemetry
import com.jarvis.hud.model.RawEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads SMS inbox, calendar instances, call log, app usage and device telemetry from the
 * Android framework. Every query checks its permission first and returns an empty list on a
 * missing permission or any provider failure; nothing here throws for expected failures.
 */
class AndroidPhoneDataSource(context: Context) : PhoneDataSource {
  private val appContext = context.applicationContext
  private val resolver get() = appContext.contentResolver

  override suspend fun readSms(sinceMillis: Long, limit: Int): List<RawEvent> = withContext(Dispatchers.IO) {
    if (limit <= 0 || !appContext.hasPermission(Manifest.permission.READ_SMS)) return@withContext emptyList()
    safeQuery {
      resolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
        "${Telephony.Sms.DATE} >= ?",
        arrayOf(sinceMillis.toString()),
        "${Telephony.Sms.DATE} DESC",
      )?.use { c ->
        val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
        val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val out = ArrayList<RawEvent>()
        while (out.size < limit && c.moveToNext()) {
          val body = c.getStringOrNull(bodyIdx).orEmpty()
          if (body.isBlank()) continue
          val address = c.getStringOrNull(addrIdx) ?: "Unknown"
          val ts = c.getLong(dateIdx)
          out += RawEvent(
            id = "sms-${c.getLong(idIdx)}",
            source = DataSource.SMS,
            sender = address,
            title = address,
            body = body,
            timestamp = ts,
            contentHash = ContentHash.of(DataSource.SMS, address, body, ts),
          )
        }
        out
      }
    }
  }

  override suspend fun readCalendar(fromMillis: Long, toMillis: Long): List<CalendarEntry> = withContext(Dispatchers.IO) {
    if (toMillis < fromMillis || !appContext.hasPermission(Manifest.permission.READ_CALENDAR)) {
      return@withContext emptyList()
    }
    safeQuery {
      val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, fromMillis)
        ContentUris.appendId(it, toMillis)
      }.build()
      resolver.query(
        uri,
        arrayOf(
          CalendarContract.Instances.EVENT_ID,
          CalendarContract.Instances.TITLE,
          CalendarContract.Instances.BEGIN,
          CalendarContract.Instances.END,
          CalendarContract.Instances.EVENT_LOCATION,
        ),
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
      )?.use { c ->
        val out = ArrayList<CalendarEntry>()
        while (c.moveToNext()) {
          val begin = c.getLong(2)
          val end = if (c.isNull(3)) begin else c.getLong(3)
          out += CalendarEntry(
            id = "cal-${c.getLong(0)}-$begin",
            title = c.getStringOrNull(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
            startTime = begin,
            endTime = end,
            location = c.getStringOrNull(4)?.takeIf { it.isNotBlank() },
          )
        }
        out
      }
    }
  }

  override suspend fun readRecentCalls(limit: Int): List<CallSummary> = withContext(Dispatchers.IO) {
    if (limit <= 0 || !appContext.hasPermission(Manifest.permission.READ_CALL_LOG)) return@withContext emptyList()
    safeQuery {
      resolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(
          CallLog.Calls._ID,
          CallLog.Calls.CACHED_NAME,
          CallLog.Calls.NUMBER,
          CallLog.Calls.TYPE,
          CallLog.Calls.DURATION,
          CallLog.Calls.DATE,
        ),
        null,
        null,
        "${CallLog.Calls.DATE} DESC",
      )?.use { c ->
        val out = ArrayList<CallSummary>()
        while (out.size < limit && c.moveToNext()) {
          val name = c.getStringOrNull(1)?.takeIf { it.isNotBlank() }
          val number = c.getStringOrNull(2)?.takeIf { it.isNotBlank() }
          out += CallSummary(
            id = "call-${c.getLong(0)}",
            nameOrNumber = name ?: number ?: "Unknown",
            callType = callTypeLabel(c.getInt(3)),
            durationSec = c.getLong(4),
            timestamp = c.getLong(5),
          )
        }
        out
      }
    }
  }

  override suspend fun readAppUsage(sinceMillis: Long, top: Int): List<AppUsageStat> = withContext(Dispatchers.IO) {
    if (top <= 0 || !appContext.hasUsageAccess()) return@withContext emptyList()
    try {
      val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return@withContext emptyList()
      val stats = usm.queryAndAggregateUsageStats(sinceMillis, System.currentTimeMillis())
      val pm = appContext.packageManager
      stats.values
        .asSequence()
        .filter { it.packageName != appContext.packageName }
        .map { it.packageName to it.totalTimeInForeground / 60_000L }
        .filter { (_, minutes) -> minutes > 0 }
        .sortedByDescending { (_, minutes) -> minutes }
        .take(top)
        .map { (pkg, minutes) -> AppUsageStat(pkg, appLabel(pm, pkg), minutes) }
        .toList()
    } catch (_: Exception) {
      emptyList()
    }
  }

  override fun readTelemetry(): DeviceTelemetry {
    var percent = -1
    var charging = false
    try {
      val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
      if (bm != null) {
        percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        charging = bm.isCharging
      }
      if (percent !in 0..100) {
        val intent: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) percent = level * 100 / scale
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        charging = charging || status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL
      }
    } catch (_: Exception) {
      // leave defaults
    }
    var free = 0f
    var total = 0f
    try {
      val stat = StatFs(Environment.getDataDirectory().path)
      free = stat.availableBytes / GB
      total = stat.totalBytes / GB
    } catch (_: Exception) {
      // leave defaults
    }
    return DeviceTelemetry(
      batteryPercent = percent.coerceIn(0, 100),
      isCharging = charging,
      freeStorageGb = free,
      totalStorageGb = total,
    )
  }

  private fun appLabel(pm: PackageManager, pkg: String): String = try {
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

  private inline fun <T> safeQuery(block: () -> List<T>?): List<T> = try {
    block() ?: emptyList()
  } catch (_: SecurityException) {
    emptyList()
  } catch (_: Exception) {
    emptyList()
  }

  private companion object {
    const val GB = 1024f * 1024f * 1024f

    fun callTypeLabel(type: Int): String = when (type) {
      CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
      CallLog.Calls.MISSED_TYPE -> "MISSED"
      CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "REJECTED"
      else -> "INCOMING"
    }

    fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
  }
}
