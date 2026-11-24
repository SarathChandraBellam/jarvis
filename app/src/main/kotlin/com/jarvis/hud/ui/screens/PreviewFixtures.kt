package com.jarvis.hud.ui.screens

import com.jarvis.hud.model.AppUsageStat
import com.jarvis.hud.model.BillingCadence
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.CallSummary
import com.jarvis.hud.model.ChatMessage
import com.jarvis.hud.model.DailyBriefing
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.PermissionStatus
import com.jarvis.hud.model.PlannerReminder
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.ProactiveAlert
import com.jarvis.hud.model.ProactiveKind
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TrackedSubscription

/** Fake data + no-op actions for @Preview and ScreenshotTest. Not used at runtime. */
object PreviewFixtures {
  val noOpActions: JarvisActions = object : JarvisActions {
    override fun onSelectTab(tab: HudTab) {}
    override fun onOpenSettings() {}
    override fun onCloseSettings() {}
    override fun onScanAndOrganize() {}
    override fun onToggleSpeakBriefing() {}
    override fun onSpeakText(text: String) {}
    override fun onMarkItemStatus(itemId: String, status: ItemStatus) {}
    override fun onSendChatMessage(prompt: String) {}
    override fun onUpdateSettings(settings: JarvisSettings) {}
    override fun onTestOpenRouterConnection() {}
    override fun onFetchOpenRouterModels() {}
    override fun onClearAiCache() {}
    override fun onRequestRuntimePermissions() {}
    override fun onOpenNotificationAccessSettings() {}
    override fun onOpenUsageAccessSettings() {}
    override fun onDismissError() {}
    override fun onOpenItem(itemId: String?) {}
    override fun onClearChat() {}
  }

  fun state(tab: HudTab = HudTab.HUD, now: Long = System.currentTimeMillis()): JarvisUiState {
    val h = 3_600_000L
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun dueIn(days: Long) = today.plusDays(days).toString()

    val items = listOf(
      OrganizedItem("b1", null, IntelCategory.BILL, "HDFC credit card bill", "Total due ₹18,420 · min ₹920",
        amount = 18420.0, dueDate = dueIn(2), priority = Priority.CRITICAL, timestamp = now - 2 * h, source = DataSource.SMS),
      OrganizedItem("b2", null, IntelCategory.BILL, "Airtel postpaid", "Bill generated for Nov",
        amount = 799.0, dueDate = dueIn(5), priority = Priority.HIGH, timestamp = now - 20 * h, source = DataSource.SMS),
      OrganizedItem("d1", null, IntelCategory.DELIVERY, "Amazon: out for delivery", "Boat Airdopes 141 arriving by 7 PM",
        priority = Priority.HIGH, timestamp = now - 1 * h, source = DataSource.NOTIFICATION),
      OrganizedItem("t1", null, IntelCategory.TRAVEL, "IndiGo 6E-2134 BLR → DEL", "Web check-in open · Seat 14C",
        dueDate = "24 Nov 06:40", timestamp = now - 5 * h, source = DataSource.SMS),
      OrganizedItem("m1", null, IntelCategory.MESSAGE, "Pepper Potts", "Dinner at 8? Don't be late again.",
        timestamp = now - 30 * 60_000L, source = DataSource.NOTIFICATION),
      OrganizedItem("s1", null, IntelCategory.FINANCE, "₹649 debited · Netflix", "Autopay subscription renewed",
        amount = 649.0, merchant = "Netflix", timestamp = now - 4 * h, source = DataSource.SMS),
    )

    val subs = listOf(
      TrackedSubscription("sub1", "Netflix", 649.0, cadence = BillingCadence.MONTHLY, category = SubscriptionCategory.STREAMING, nextRenewalDate = dueIn(2), sourceNote = "Auto-pay"),
      TrackedSubscription("sub2", "ChatGPT Plus", 1999.0, cadence = BillingCadence.MONTHLY, category = SubscriptionCategory.SOFTWARE, nextRenewalDate = dueIn(4), sourceNote = "Card ending 4821"),
      TrackedSubscription("sub3", "JioFiber", 999.0, cadence = BillingCadence.MONTHLY, category = SubscriptionCategory.TELECOM, nextRenewalDate = dueIn(6), sourceNote = "Broadband"),
      TrackedSubscription("sub4", "cult.fit", 1490.0, cadence = BillingCadence.MONTHLY, category = SubscriptionCategory.FITNESS, nextRenewalDate = dueIn(11), isTrial = true, sourceNote = "Trial ends soon"),
      TrackedSubscription("sub5", "Spotify", 119.0, cadence = BillingCadence.MONTHLY, category = SubscriptionCategory.STREAMING, nextRenewalDate = dueIn(14), sourceNote = "UPI mandate"),
    )

    val reminders = listOf(
      PlannerReminder("r1", "Review Netflix & trial renewals", 10 * 60 + 30, today.toString(), isDone = false),
      PlannerReminder("r2", "Evening walk & tea away from screens", 18 * 60, today.toString(), isDone = true),
    )

    val alerts = listOf(
      ProactiveAlert("pa1", "sub:sub1", ProactiveKind.RENEWAL, "Netflix renews in 2 days", "₹649 · Monthly. Pause in Subs if not needed.", Priority.HIGH, now, HudTab.FINANCE),
      ProactiveAlert("pa2", "bill:b1", ProactiveKind.BILL, "HDFC Credit Card due in 2 days", "₹18,420 · Tap Subs to mark paid.", Priority.CRITICAL, now - h, HudTab.FINANCE),
    )

    return JarvisUiState(
      selectedTab = tab,
      syncStatusLine = "Up to date · 6 new items",
      settings = JarvisSettings(userCallsign = "Tony", apiKey = "sk-or-v1-preview", onboardingDone = true),
      permissions = PermissionStatus(true, false, true, true, false, true),
      briefing = DailyBriefing(
        dateKey = today.toString(),
        headline = "Calm rhythm ahead, Tony.",
        summary = "Your HDFC card bill and Netflix renewal arrive in two days. Two meetings on your planner today.",
        highlights = listOf("Netflix renews in 2 days · ₹649", "Airdopes arriving by 7 PM", "Flight to Delhi on the 24th"),
        modelUsed = "google/gemini-2.5-flash",
        createdAt = now - 15 * 60_000L,
      ),
      items = items,
      subscriptions = subs,
      reminders = reminders,
      proactiveAlerts = alerts,
      calendarEntries = listOf(
        CalendarEntry("c1", "Stand-up with R&D", now - h, now - h / 2, "Workshop"),
        CalendarEntry("c2", "Design review", now + h, now + 2 * h, "Studio"),
      ),
      recentCalls = listOf(
        CallSummary("k1", "Happy Hogan", "INCOMING", 184, now - 2 * h),
        CallSummary("k2", "+91 98xxxxxx21", "MISSED", 0, now - 4 * h),
      ),
      appUsageStats = listOf(
        AppUsageStat("a", "YouTube", 64), AppUsageStat("b", "WhatsApp", 42), AppUsageStat("c", "Chrome", 28),
      ),
      chatMessages = listOf(
        ChatMessage("u1", true, "What subscriptions renew soon?", now - 4 * 60_000L),
        ChatMessage("a1", false, "Netflix (₹649) renews in 2 days and ChatGPT Plus (₹1,999) in 4 days.", now - 3 * 60_000L,
          modelUsed = "google/gemini-2.5-flash"),
      ),
      availableModels = listOf(
        OpenRouterModelInfo("google/gemini-2.5-flash", "Gemini 2.5 Flash", 1_000_000),
        OpenRouterModelInfo("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (free)", 131_072, isFree = true),
      ),
    )
  }
}
