package com.astra.hud.insights

import com.astra.hud.model.AppUsageStat
import com.astra.hud.model.BillingCadence
import com.astra.hud.model.CallSummary
import com.astra.hud.model.DailyBriefing
import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.PlannerReminder
import com.astra.hud.model.ProactiveKind
import com.astra.hud.model.SubscriptionCategory
import com.astra.hud.model.TrackedSubscription
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactivePlannerTest {
  private val zone = ZoneId.of("Asia/Kolkata")
  private val today = LocalDate.of(2026, 11, 24)
  private val now = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

  @Test fun surfacesRenewalsBillsRemindersFocusAndRespectsDismissals() {
    val sub = TrackedSubscription(
      id = "sub-1",
      name = "Netflix",
      amount = 649.0,
      cadence = BillingCadence.MONTHLY,
      category = SubscriptionCategory.STREAMING,
      nextRenewalDate = today.plusDays(2).toString(),
    )
    val bill = OrganizedItem(
      id = "bill-1",
      rawEventId = null,
      category = IntelCategory.BILL,
      title = "Airtel postpaid",
      summary = "Due soon",
      amount = 799.0,
      dueDate = today.plusDays(1).toString(),
      timestamp = now - 3_600_000L,
      source = DataSource.SMS,
    )
    val reminder = PlannerReminder("rem-1", "Call dentist", minutesOfDay = 10 * 60 + 30, dateKey = today.toString())
    val missed = CallSummary("c1", "Mom", "MISSED", 0, now - 1_800_000L)
    val usage = listOf(AppUsageStat("pkg", "YouTube", 150))
    val briefing = DailyBriefing(today.toString(), "Good morning", "All set", emptyList(), "local", now)

    val alerts = ProactivePlanner.buildAlerts(
      subscriptions = listOf(sub),
      items = listOf(bill),
      reminders = listOf(reminder),
      recentCalls = listOf(missed),
      appUsage = usage,
      briefing = briefing,
      settings = AstraSettings(renewalLeadDays = 3, morningBriefingHour = 8, dailyFocusGoalMinutes = 120),
      dismissedKeys = emptySet(),
      now = now,
      zone = zone,
    )

    val kinds = alerts.map { it.kind }.toSet()
    assertTrue(ProactiveKind.RENEWAL in kinds)
    assertTrue(ProactiveKind.BILL in kinds)
    assertTrue(ProactiveKind.REMINDER in kinds)
    assertTrue(ProactiveKind.CALL in kinds)
    assertTrue(ProactiveKind.FOCUS in kinds)
    assertTrue(ProactiveKind.BRIEFING in kinds)

    val firstKey = alerts.first().dedupKey
    val afterDismiss = ProactivePlanner.buildAlerts(
      subscriptions = listOf(sub),
      items = listOf(bill),
      reminders = listOf(reminder),
      recentCalls = listOf(missed),
      appUsage = usage,
      briefing = briefing,
      settings = AstraSettings(renewalLeadDays = 3, morningBriefingHour = 8, dailyFocusGoalMinutes = 120),
      dismissedKeys = setOf(firstKey),
      now = now,
      zone = zone,
    )
    assertEquals(alerts.size - 1, afterDismiss.size)
  }
}
