package com.jarvis.hud.ui.screens

import com.jarvis.hud.model.AppUsageStat
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
import com.jarvis.hud.model.Priority

/** Fake data + no-op actions for @Preview functions. Not used at runtime. */
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
    val d = 24 * h
    fun fin(id: String, title: String, amt: Double, merchant: String, ago: Long, credit: Boolean = false, p: Priority = Priority.NORMAL) =
      OrganizedItem(
        id = id, rawEventId = null, category = IntelCategory.FINANCE, title = title,
        summary = if (credit) "Credited to A/c XX4821" else "Debited from A/c XX4821 via UPI",
        amount = amt, isCredit = credit, merchant = merchant, priority = p, timestamp = now - ago, source = DataSource.SMS,
      )
    val items = listOf(
      OrganizedItem("b1", null, IntelCategory.BILL, "HDFC credit card bill", "Total due ₹18,420 · min ₹920",
        amount = 18420.0, dueDate = "25 Nov", priority = Priority.CRITICAL, timestamp = now - 2 * h, source = DataSource.SMS),
      OrganizedItem("b2", null, IntelCategory.BILL, "Airtel postpaid", "Bill generated for Nov",
        amount = 799.0, dueDate = "28 Nov", priority = Priority.HIGH, timestamp = now - 20 * h, source = DataSource.SMS),
      OrganizedItem("d1", null, IntelCategory.DELIVERY, "Amazon: out for delivery", "Boat Airdopes 141 arriving by 7 PM",
        priority = Priority.HIGH, timestamp = now - 1 * h, source = DataSource.NOTIFICATION),
      OrganizedItem("t1", null, IntelCategory.TRAVEL, "IndiGo 6E-2134 BLR → DEL", "Web check-in open · Seat 14C",
        dueDate = "24 Nov 06:40", timestamp = now - 5 * h, source = DataSource.SMS),
      OrganizedItem("m1", null, IntelCategory.MESSAGE, "Pepper Potts", "Dinner at 8? Don't be late again.",
        timestamp = now - 30 * 60_000L, source = DataSource.NOTIFICATION),
      fin("f1", "Swiggy order", 486.0, "Swiggy", 3 * h),
      fin("f2", "Salary credited", 125000.0, "ACME Corp", 26 * h, credit = true),
      fin("f3", "Uber trip", 312.5, "Uber", 2 * d),
      fin("f4", "Amazon purchase", 2499.0, "Amazon", 3 * d),
      fin("f5", "Zepto groceries", 1243.0, "Zepto", 4 * d),
      fin("f6", "Swiggy order", 612.0, "Swiggy", 5 * d),
      fin("f7", "Large debit alert", 24999.0, "Croma", 6 * h, p = Priority.HIGH),
    )
    return JarvisUiState(
      selectedTab = tab,
      syncStatusLine = "Up to date · 12 new items",
      settings = JarvisSettings(userCallsign = "Tony", apiKey = "sk-or-v1-preview", onboardingDone = true, monthlyBudget = 60000.0),
      permissions = PermissionStatus(true, false, true, true, false),
      briefing = DailyBriefing(
        dateKey = "2026-11-22",
        headline = "One critical bill, one delivery inbound.",
        summary = "Your HDFC card bill of ₹18,420 is due in three days. Amazon is out for delivery and you've spent ₹25,485 today.",
        highlights = listOf("Card bill due 25 Nov", "Airdopes arriving by 7 PM", "Flight to Delhi on the 24th"),
        modelUsed = "google/gemini-2.5-flash",
        createdAt = now - 15 * 60_000L,
      ),
      items = items,
      calendarEntries = listOf(
        CalendarEntry("c1", "Stand-up with R&D", now - h, now - h / 2, "Workshop"),
        CalendarEntry("c2", "Board review", now + h, now + 2 * h, "Stark Tower, 93F"),
      ),
      recentCalls = listOf(
        CallSummary("k1", "Happy Hogan", "INCOMING", 184, now - 2 * h),
        CallSummary("k2", "+91 98xxxxxx21", "MISSED", 0, now - 4 * h),
        CallSummary("k3", "Rhodey", "OUTGOING", 642, now - 9 * h),
      ),
      appUsageStats = listOf(
        AppUsageStat("a", "YouTube", 96), AppUsageStat("b", "WhatsApp", 64), AppUsageStat("c", "Chrome", 41),
      ),
      chatMessages = listOf(
        ChatMessage("u1", true, "What did I spend today?", now - 4 * 60_000L),
        ChatMessage("a1", false, "₹25,485 across three debits today, sir — the bulk being ₹24,999 at Croma.", now - 3 * 60_000L,
          modelUsed = "google/gemini-2.5-flash"),
      ),
      availableModels = listOf(
        OpenRouterModelInfo("google/gemini-2.5-flash", "Gemini 2.5 Flash", 1_000_000),
        OpenRouterModelInfo("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B (free)", 131_072, isFree = true),
      ),
    )
  }
}
