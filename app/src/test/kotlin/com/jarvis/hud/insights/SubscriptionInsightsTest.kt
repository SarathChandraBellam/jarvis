package com.jarvis.hud.insights

import com.jarvis.hud.model.BillingCadence
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TrackedSubscription
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionInsightsTest {
  private val zone = ZoneId.of("Asia/Kolkata")
  private val today = LocalDate.of(2026, 11, 24)
  private val now = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

  private fun item(
    id: String,
    title: String,
    summary: String,
    amount: Double,
    merchant: String? = null,
    daysAgo: Long = 2,
    category: IntelCategory = IntelCategory.FINANCE,
    dueDate: String? = null,
  ) = OrganizedItem(
    id = id,
    rawEventId = null,
    category = category,
    title = title,
    summary = summary,
    amount = amount,
    merchant = merchant,
    dueDate = dueDate,
    timestamp = today.minusDays(daysAgo).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
    source = DataSource.SMS,
  )

  @Test fun detectsSubscriptionsAndIgnoresOneOffDailySpends() {
    val items = listOf(
      item("1", "₹649 debited · Netflix", "UPI autopay mandate executed", 649.0, merchant = "Netflix"),
      item("2", "₹119 debited · Spotify", "Subscription renewed", 119.0, merchant = "Spotify"),
      item("3", "₹486 debited · Swiggy", "Paid to Swiggy via UPI", 486.0, merchant = "Swiggy"),
      item("4", "₹312 debited · Uber", "Paid to Uber via UPI", 312.0, merchant = "Uber"),
      item("5", "₹24,999 debited · Croma", "Paid at Croma store", 24999.0, merchant = "Croma"),
    )

    val res = SubscriptionInsights.build(
      items = items,
      customSubscriptions = emptyList(),
      pausedIds = emptySet(),
      deletedIds = emptySet(),
      now = now,
      zone = zone,
    )

    assertEquals(setOf("Netflix", "Spotify"), res.active.map { it.name }.toSet())
    assertEquals(768.0, res.monthlyBurn, 0.01)
  }

  @Test fun detectsMultiMonthRecurringAndManualSubscriptionsWithPauseAndDelete() {
    val items = listOf(
      item("r1", "₹999 debited · CloudHost", "Paid to CloudHost", 999.0, merchant = "CloudHost", daysAgo = 35),
      item("r2", "₹999 debited · CloudHost", "Paid to CloudHost", 999.0, merchant = "CloudHost", daysAgo = 5),
    )
    val custom = TrackedSubscription(
      id = "manual-1",
      name = "Claude Pro",
      amount = 1999.0,
      cadence = BillingCadence.MONTHLY,
      category = SubscriptionCategory.SOFTWARE,
      nextRenewalDate = today.plusDays(2).toString(),
      isTrial = true,
      isAutoDetected = false,
    )

    val active = SubscriptionInsights.build(
      items = items,
      customSubscriptions = listOf(custom),
      pausedIds = setOf("auto-cloudhost"),
      deletedIds = emptySet(),
      now = now,
      zone = zone,
    )

    assertEquals(1, active.active.size)
    assertEquals("Claude Pro", active.active.single().name)
    assertEquals(1, active.paused.size)
    assertEquals("Cloudhost", active.paused.single().name)
    assertEquals(1, active.upcoming7Days.size)
    assertEquals(1, active.trials.size)
    assertEquals(1999.0, active.monthlyBurn, 0.01)
    assertFalse(active.active.single().isPaused)

    val deleted = SubscriptionInsights.build(
      items = items,
      customSubscriptions = listOf(custom),
      pausedIds = emptySet(),
      deletedIds = setOf("manual-1"),
      now = now,
      zone = zone,
    )
    assertTrue(deleted.all.none { it.id == "manual-1" })
  }
}
