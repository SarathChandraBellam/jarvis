package com.jarvis.hud.insights

import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.DailyBriefing
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.PlannerReminder
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.ProactiveAlert
import com.jarvis.hud.model.ProactiveKind
import com.jarvis.hud.model.TrackedSubscription
import com.jarvis.hud.ui.components.HudFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure Kotlin proactive alert engine: decides which gentle, timely nudges Jarvis should surface
 * in-app and via local Android notifications.
 */
object ProactivePlanner {

  fun buildAlerts(
    subscriptions: List<TrackedSubscription>,
    items: List<OrganizedItem>,
    reminders: List<PlannerReminder>,
    recentCalls: List<CallSummary>,
    appUsage: List<AppUsageStat>,
    briefing: DailyBriefing?,
    settings: JarvisSettings,
    dismissedKeys: Set<String>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
  ): List<ProactiveAlert> {
    val nowZ = Instant.ofEpochMilli(now).atZone(zone)
    val today = nowZ.toLocalDate()
    val todayKey = today.toString()
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val alerts = mutableListOf<ProactiveAlert>()

    // 1. Upcoming subscription renewals & free-trial endings.
    val lead = settings.renewalLeadDays.coerceIn(1, 14).toLong()
    subscriptions.filterNot { it.isPaused }.forEach { sub ->
      val days = SubscriptionInsights.daysUntil(sub.nextRenewalDate, today)
      if (days != null && days in 0..lead) {
        val whenText = when (days) {
          0L -> "renews today"
          1L -> "renews tomorrow"
          else -> "renews in $days days"
        }
        val title = if (sub.isTrial) "${sub.name} free trial $whenText" else "${sub.name} $whenText"
        val body = "${HudFormat.money(sub.amount, sub.currency)} · ${sub.cadence.label}. Review or pause in Subs if you don't need it."
        alerts += ProactiveAlert(
          id = "pa-sub-${sub.id}",
          dedupKey = "sub:${sub.id}:${sub.nextRenewalDate}:$todayKey",
          kind = ProactiveKind.RENEWAL,
          title = title,
          body = body,
          priority = if (days <= 1L || sub.isTrial) Priority.CRITICAL else Priority.HIGH,
          timestamp = now,
          targetTab = HudTab.FINANCE,
        )
      }
    }

    // 2. Open bills due soon or overdue.
    items.filter { it.category == IntelCategory.BILL && it.status == ItemStatus.ACTIVE }.forEach { bill ->
      val due = bill.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
      val days = due?.let { ChronoUnit.DAYS.between(today, it) }
      if (days != null && days <= lead) {
        val name = SpendInsights.billerName(bill)
        val amt = bill.amount?.let { HudFormat.money(it, bill.currency) + " · " } ?: ""
        val whenText = when {
          days < 0 -> "overdue by ${-days}d"
          days == 0L -> "due today"
          days == 1L -> "due tomorrow"
          else -> "due in $days days"
        }
        alerts += ProactiveAlert(
          id = "pa-bill-${bill.id}",
          dedupKey = "bill:${bill.id}:$todayKey",
          kind = ProactiveKind.BILL,
          title = "$name $whenText",
          body = "${amt}Tap Subs to mark paid once cleared.",
          priority = if (days <= 1L) Priority.CRITICAL else Priority.HIGH,
          timestamp = bill.timestamp,
          targetTab = HudTab.FINANCE,
        )
      }
    }

    // 3. Deliveries out for delivery today.
    items.filter {
      it.category == IntelCategory.DELIVERY &&
        it.status == ItemStatus.ACTIVE &&
        (it.priority == Priority.HIGH || it.priority == Priority.CRITICAL)
    }.take(2).forEach { del ->
      alerts += ProactiveAlert(
        id = "pa-del-${del.id}",
        dedupKey = "del:${del.id}:$todayKey",
        kind = ProactiveKind.DELIVERY,
        title = del.title,
        body = del.summary.ifBlank { "Package update waiting in your Inbox." },
        priority = Priority.HIGH,
        timestamp = del.timestamp,
        targetTab = HudTab.INTEL,
      )
    }

    // 4. Missed calls from today.
    recentCalls
      .filter { "MISS" in it.callType.uppercase() && it.timestamp >= dayStart }
      .take(2)
      .forEach { call ->
        alerts += ProactiveAlert(
          id = "pa-call-${call.id}",
          dedupKey = "call:${call.id}:$todayKey",
          kind = ProactiveKind.CALL,
          title = "Missed call from ${call.nameOrNumber}",
          body = "Called ${HudFormat.relative(call.timestamp, now)}. Consider a quick callback.",
          priority = Priority.NORMAL,
          timestamp = call.timestamp,
          targetTab = HudTab.HUD,
        )
      }

    // 5. Planner reminders scheduled for today that aren't done yet.
    val nowMinutes = nowZ.hour * 60 + nowZ.minute
    reminders
      .filter { !it.isDone && it.dateKey == todayKey && it.notifyProactively && it.minutesOfDay <= nowMinutes + 60 }
      .forEach { rem ->
        val hh = rem.minutesOfDay / 60
        val mm = rem.minutesOfDay % 60
        val ampm = if (hh < 12) "AM" else "PM"
        val h12 = when {
          hh == 0 -> 12
          hh > 12 -> hh - 12
          else -> hh
        }
        val timeStr = String.format("%d:%02d %s", h12, mm, ampm)
        alerts += ProactiveAlert(
          id = "pa-rem-${rem.id}",
          dedupKey = "rem:${rem.id}:$todayKey",
          kind = ProactiveKind.REMINDER,
          title = rem.title,
          body = "Planned for $timeStr today.",
          priority = if (rem.minutesOfDay <= nowMinutes) Priority.HIGH else Priority.NORMAL,
          timestamp = now,
          targetTab = HudTab.HUD,
        )
      }

    // 6. Gentle screen-time focus nudge.
    val totalUsage = appUsage.sumOf { it.foregroundMinutes }
    val goal = settings.dailyFocusGoalMinutes.coerceAtLeast(30)
    if (totalUsage > goal) {
      val topApp = appUsage.maxByOrNull { it.foregroundMinutes }
      val over = totalUsage - goal
      alerts += ProactiveAlert(
        id = "pa-focus-$todayKey",
        dedupKey = "focus:$todayKey",
        kind = ProactiveKind.FOCUS,
        title = "Gentle focus check-in",
        body = "${HudFormat.minutes(totalUsage)} screen time today (${HudFormat.minutes(over)} over your calm limit" +
          (topApp?.let { ", mostly ${it.appName}" } ?: "") + "). A short breather helps.",
        priority = Priority.NORMAL,
        timestamp = now,
        targetTab = HudTab.HUD,
      )
    }

    // 7. Morning briefing digest alert.
    if (briefing != null && nowZ.hour >= settings.morningBriefingHour.coerceIn(5, 12)) {
      alerts += ProactiveAlert(
        id = "pa-brief-$todayKey",
        dedupKey = "briefing:$todayKey",
        kind = ProactiveKind.BRIEFING,
        title = briefing.headline,
        body = briefing.summary,
        priority = Priority.LOW,
        timestamp = briefing.createdAt,
        targetTab = HudTab.HUD,
      )
    }

    return alerts
      .filterNot { it.dedupKey in dismissedKeys }
      .sortedWith(compareBy<ProactiveAlert>({ it.priority.ordinal }, { -it.timestamp }))
  }
}
