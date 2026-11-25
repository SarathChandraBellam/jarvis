package com.astra.hud.ui.screens

import com.astra.hud.model.AppUsageStat
import com.astra.hud.model.BillingCadence
import com.astra.hud.model.CalendarEntry
import com.astra.hud.model.CallSummary
import com.astra.hud.model.ChatMessage
import com.astra.hud.model.CycleState
import com.astra.hud.model.DailyBriefing
import com.astra.hud.model.DataSource
import com.astra.hud.model.HabitItem
import com.astra.hud.model.HudTab
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.ItemStatus
import com.astra.hud.model.AstraActions
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.AstraUiState
import com.astra.hud.model.MealLog
import com.astra.hud.model.OpenRouterModelInfo
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.PermissionStatus
import com.astra.hud.model.PlannerReminder
import com.astra.hud.model.Priority
import com.astra.hud.model.ProactiveAlert
import com.astra.hud.model.ProactiveKind
import com.astra.hud.model.SleepLog
import com.astra.hud.model.SubscriptionCategory
import com.astra.hud.model.TimerMode
import com.astra.hud.model.TimerSession
import com.astra.hud.model.TrackedSubscription
import com.astra.hud.model.WorkoutLog

object PreviewFixtures {
  val noOpActions: AstraActions = object : AstraActions {
    override fun onSelectTab(tab: HudTab) {}
    override fun onOpenSettings() {}
    override fun onCloseSettings() {}
    override fun onScanAndOrganize() {}
    override fun onToggleSpeakBriefing() {}
    override fun onSpeakText(text: String) {}
    override fun onMarkItemStatus(itemId: String, status: ItemStatus) {}
    override fun onSendChatMessage(prompt: String) {}
    override fun onUpdateSettings(settings: AstraSettings) {}
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

  fun state(tab: HudTab = HudTab.HUD, now: Long = System.currentTimeMillis()): AstraUiState {
    val h = 3_600_000L
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val todayKey = today.toString()
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
      OrganizedItem("m1", null, IntelCategory.MESSAGE, "Maya Rao", "Dinner at 8? Don't be late again.",
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

    val meals = listOf(
      MealLog("ml1", "Poha, idli & 2 eggs", 475, 22, 62, 14, todayKey, now - 4 * h),
      MealLog("ml2", "Chicken biryani bowl", 520, 34, 58, 16, todayKey, now - 1 * h),
    )

    val sleepLogs = listOf(
      SleepLog("sl0", todayKey, 7 * 60 + 15, "11:45 PM", "7:00 AM", qualityScore = 84, deepMinutes = 76, remMinutes = 115, lightMinutes = 228, awakeMinutes = 16),
      SleepLog("sl1", today.minusDays(1).toString(), 6 * 60 + 37, "1:13 AM", "8:01 AM", qualityScore = 76),
      SleepLog("sl2", today.minusDays(2).toString(), 7 * 60 + 45, "11:15 PM", "7:00 AM", qualityScore = 88),
    )

    val workouts = listOf(
      WorkoutLog("wk1", "Evening cycling", 40, 320, todayKey, now - 2 * h),
    )

    val habits = listOf(
      HabitItem("h1", "2L Water", setOf(todayKey, today.minusDays(1).toString())),
      HabitItem("h2", "Magnesium 400mg", setOf(todayKey)),
      HabitItem("h3", "Phone away 10:30 PM", setOf(today.minusDays(1).toString())),
    )

    val stepsMap = mapOf(
      today.minusDays(5).toString() to 6420,
      today.minusDays(4).toString() to 8190,
      today.minusDays(3).toString() to 5310,
      today.minusDays(2).toString() to 9840,
      today.minusDays(1).toString() to 7210,
      todayKey to 8640,
    )

    val cycle = CycleState(
      periodStartDates = listOf(today.minusDays(45).toString(), today.minusDays(15).toString()),
      cycleLengthDays = 30,
      periodDurationDays = 5,
    )

    val reminders = listOf(
      PlannerReminder("r1", "Review Netflix & trial renewals", 10 * 60 + 30, todayKey, isDone = false),
      PlannerReminder("r2", "Evening walk & tea away from screens", 18 * 60, todayKey, isDone = true),
    )

    val alerts = listOf(
      ProactiveAlert("pa1", "sub:sub1", ProactiveKind.RENEWAL, "Netflix renews in 2 days", "₹649 · Monthly. Pause in Subs if not needed.", Priority.HIGH, now, HudTab.FINANCE),
      ProactiveAlert("pa2", "bill:b1", ProactiveKind.BILL, "HDFC Credit Card due in 2 days", "₹18,420 · Tap Subs to mark paid.", Priority.CRITICAL, now - h, HudTab.FINANCE),
    )

    return AstraUiState(
      selectedTab = tab,
      syncStatusLine = "Up to date · 6 new items",
      settings = AstraSettings(userCallsign = "Alex", apiKey = "sk-or-v1-preview", onboardingDone = true),
      permissions = PermissionStatus(true, false, true, true, true, true, true),
      briefing = DailyBriefing(
        dateKey = todayKey,
        headline = "Calm rhythm ahead, Alex.",
        summary = "You slept 7h 15m and logged 8,640 steps. Netflix and HDFC bill arrive in two days.",
        highlights = listOf("Netflix renews in 2 days · ₹649", "Mid-cycle Day 16 · steady energy", "Airdopes arriving by 7 PM"),
        modelUsed = "google/gemini-2.5-flash",
        createdAt = now - 15 * 60_000L,
      ),
      items = items,
      subscriptions = subs,
      reminders = reminders,
      proactiveAlerts = alerts,
      meals = meals,
      sleepLogs = sleepLogs,
      workouts = workouts,
      habits = habits,
      stepsByDate = stepsMap,
      timerSession = TimerSession(isRunning = true, mode = TimerMode.FASTING, startedAtMillis = now - 11 * h, targetMinutes = 16 * 60, completedFocusMinutesToday = 45, lastFastedMinutes = 945, lastDateKey = todayKey),
      cycleState = cycle,
      calendarEntries = listOf(
        CalendarEntry("c1", "Stand-up with R&D", now - h, now - h / 2, "Workshop"),
        CalendarEntry("c2", "Design review", now + h, now + 2 * h, "Studio"),
      ),
      recentCalls = listOf(
        CallSummary("k1", "Ravi Kumar", "INCOMING", 184, now - 2 * h),
        CallSummary("k2", "+91 98xxxxxx21", "MISSED", 0, now - 4 * h),
      ),
      appUsageStats = listOf(
        AppUsageStat("a", "YouTube", 64), AppUsageStat("b", "WhatsApp", 42),
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
