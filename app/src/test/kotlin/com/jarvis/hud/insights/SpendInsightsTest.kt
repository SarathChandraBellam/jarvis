package com.jarvis.hud.insights

import com.jarvis.hud.ai.LocalIntelParser
import com.jarvis.hud.testdata.SampleInbox
import com.jarvis.hud.data.store.FileIntelStore
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendInsightsTest {

  private val zone = ZoneId.of("Asia/Kolkata")
  // Monday 23 Nov 2026, 20:00 IST.
  private val now = at(2026, 11, 23, 20)

  private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
    LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

  private var n = 0
  private fun debit(amount: Double, ts: Long, merchant: String? = null, summary: String = "") = OrganizedItem(
    "i${n++}", null, IntelCategory.FINANCE, "₹$amount debited", summary, amount = amount,
    merchant = merchant, timestamp = ts, source = DataSource.SMS,
  )
  private fun credit(amount: Double, ts: Long, summary: String) =
    debit(amount, ts, summary = summary).copy(isCredit = true)

  @Test fun monthToDate_vsSameSpanLastMonth() {
    val items = listOf(
      debit(1000.0, at(2026, 11, 2)),
      debit(2000.0, at(2026, 11, 20)),
      debit(1500.0, at(2026, 10, 5)),
      debit(500.0, at(2026, 10, 23, 23)), // last day of the comparable span
      debit(9000.0, at(2026, 10, 24)), // after the comparable span → ignored
    )
    val s = SpendInsights.from(items, now, zone)
    assertEquals("November", s.monthLabel)
    assertEquals(3000.0, s.monthSpent, 0.0)
    assertEquals(2000.0, s.lastMonthSameSpan, 0.0)
    assertEquals(0.5, s.monthDelta!!, 1e-9)
    assertEquals(3000.0 / 23, s.dailyAverage, 1e-9)
    assertEquals(3000.0 / 23 * 30, s.projectedMonth, 1e-9)
    assertEquals(2, s.monthTransactions)
    assertEquals(2000.0, s.largest!!.amount!!, 0.0)
  }

  @Test fun noHistory_meansNoDelta() {
    val s = SpendInsights.from(listOf(debit(10.0, at(2026, 11, 23))), now, zone)
    assertNull(s.monthDelta)
    assertTrue(s.hasData)
  }

  @Test fun emptyInput_isSafe() {
    val s = SpendInsights.from(emptyList(), now, zone)
    assertFalse(s.hasData)
    assertEquals(0.0, s.monthSpent, 0.0)
    assertEquals(7, s.last7Days.size)
    assertTrue(s.categories.isEmpty())
    assertTrue(s.dayParts.isEmpty())
    assertNull(s.largest)
  }

  @Test fun dismissedCreditsAndNonFinance_areNotSpend() {
    val items = listOf(
      debit(100.0, at(2026, 11, 23)),
      debit(999.0, at(2026, 11, 23)).copy(status = ItemStatus.DISMISSED),
      debit(500.0, at(2026, 11, 23)).copy(category = IntelCategory.BILL),
      credit(85000.0, at(2026, 11, 1), "Salary NOV"),
    )
    val s = SpendInsights.from(items, now, zone)
    assertEquals(100.0, s.monthSpent, 0.0)
    assertEquals(100.0, s.todaySpent, 0.0)
    assertEquals(85000.0, s.monthReceived, 0.0)
  }

  @Test fun last7Days_bucketsByLocalDate() {
    val s = SpendInsights.from(
      listOf(debit(10.0, at(2026, 11, 17)), debit(20.0, at(2026, 11, 23, 1)), debit(5.0, at(2026, 11, 16))),
      now, zone,
    )
    assertEquals(LocalDate.of(2026, 11, 17), s.last7Days.first().date)
    assertEquals(10.0, s.last7Days.first().amount, 0.0)
    assertEquals(20.0, s.last7Days.last().amount, 0.0)
  }

  @Test fun categoriesAreSharesSortedLargestFirst() {
    val items = listOf(
      debit(300.0, at(2026, 11, 3), merchant = "swiggy@icici"),
      debit(100.0, at(2026, 11, 4), merchant = "ZOMATO"),
      debit(600.0, at(2026, 11, 5), merchant = "AMAZON"),
    )
    val s = SpendInsights.from(items, now, zone)
    assertEquals(listOf(SpendCategory.SHOPPING, SpendCategory.FOOD), s.categories.map { it.key })
    assertEquals(0.6f, s.categories[0].fraction, 1e-6f)
    assertEquals(400.0, s.categories[1].amount, 0.0)
  }

  @Test fun categorize_byMerchantAndMessage() {
    fun cat(merchant: String?, summary: String = "") = SpendInsights.categorize(debit(1.0, now, merchant, summary))
    assertEquals(SpendCategory.FOOD, cat("swiggy@icici"))
    assertEquals(SpendCategory.GROCERIES, cat("Swiggy Instamart"))
    assertEquals(SpendCategory.GROCERIES, cat("Ramesh Kirana Store"))
    assertEquals(SpendCategory.FUEL, cat("hpcl.fuel@icici"))
    assertEquals(SpendCategory.TRANSPORT, cat("uber@axisbank"))
    assertEquals(SpendCategory.BILLS, cat("jiofiber@sbi", "Rs.999 debited to VPA jiofiber@sbi"))
    assertEquals(SpendCategory.BILLS, cat("BESCOM"))
    assertEquals(SpendCategory.ENTERTAINMENT, cat("NETFLIX"))
    assertEquals(SpendCategory.HEALTH, cat("APOLLO PHARMACY"))
    assertEquals(SpendCategory.SHOPPING, cat("MYNTRA"))
    assertEquals(SpendCategory.TRANSFERS, cat("Rahul", "Rs.5,000 sent to Rahul via IMPS"))
    assertEquals(SpendCategory.OTHER, cat("Some Shop"))
    assertEquals(SpendCategory.OTHER, cat(null))
    // Whole words only: "current" is not "rent", "olay" is not "Ola".
    assertEquals(SpendCategory.OTHER, cat("Current Affairs Ltd"))
    assertEquals(SpendCategory.OTHER, cat("OLAY STORE"))
  }

  @Test fun displayMerchant_isHumanReadable() {
    fun name(m: String?, title: String = "₹1 debited") =
      SpendInsights.displayMerchant(debit(1.0, now, m).copy(title = title))
    assertEquals("Swiggy", name("swiggy@icici"))
    assertEquals("Swiggy Instamart", name("Swiggy Instamart"))
    assertEquals("Myntra", name("MYNTRA"))
    assertEquals("Ankit Sharma", name("ankit.sharma@okhdfcbank"))
    assertEquals("Ramesh Kirana Store", name("Ramesh Kirana Store"))
    assertEquals("Indian Oil", name("IOCL PETROL PUMP"))
    assertEquals("Amazon", name(null, title = "₹1 debited · Amazon"))
    assertEquals(SpendInsights.UNKNOWN_MERCHANT, name(null))
    assertEquals(SpendInsights.UNKNOWN_MERCHANT, name("9876543210@ybl"))
  }

  @Test fun topMerchants_groupDifferentSpellings() {
    val items = listOf(
      debit(100.0, at(2026, 11, 3), "swiggy@icici"),
      debit(200.0, at(2026, 11, 4), "SWIGGY"),
      debit(50.0, at(2026, 11, 5), "Zomato"),
    )
    val top = SpendInsights.from(items, now, zone).merchants
    assertEquals("Swiggy", top.first().name)
    assertEquals(300.0, top.first().amount, 0.0)
    assertEquals(2, top.first().count)
  }

  @Test fun dayParts_andWeekdayWeekendAverages() {
    val items = listOf(
      debit(100.0, at(2026, 11, 23, 8)), // Mon morning
      debit(300.0, at(2026, 11, 22, 23)), // Sun night
      debit(600.0, at(2026, 11, 21, 19)), // Sat evening
      debit(1000.0, at(2026, 10, 1, 13)), // older than 28 days → ignored
    )
    val s = SpendInsights.from(items, now, zone)
    assertEquals(listOf(DayPart.MORNING, DayPart.EVENING, DayPart.NIGHT), s.dayParts.map { it.key })
    assertEquals(0.6f, s.dayParts[1].fraction, 1e-6f)
    // 28 days ending Mon 23 Nov = 8 weekend days + 20 weekdays.
    assertEquals(900.0 / 8, s.weekendAverage, 1e-9)
    assertEquals(100.0 / 20, s.weekdayAverage, 1e-9)
    assertEquals(DayPart.NIGHT, DayPart.of(2))
    assertEquals(DayPart.AFTERNOON, DayPart.of(12))
  }

  @Test fun incomeSplit() {
    val items = listOf(
      credit(85000.0, at(2026, 11, 1), "credited by NEFT-ACME SALARY NOV"),
      credit(249.0, at(2026, 11, 20), "₹249 has been refunded to your UPI account"),
      credit(500.0, at(2026, 11, 21), "received from Rahul"),
    )
    val income = SpendInsights.from(items, now, zone).income
    assertEquals(listOf(IncomeKind.SALARY, IncomeKind.REFUND, IncomeKind.OTHER), income.map { it.key })
  }

  @Test fun recurring_sameMerchantSameAmountAcrossMonths() {
    val items = listOf(
      debit(649.0, at(2026, 11, 20), "NETFLIX"),
      debit(649.0, at(2026, 10, 21), "NETFLIX"),
      debit(649.0, at(2026, 9, 21), "NETFLIX"),
      debit(386.0, at(2026, 11, 3), "swiggy@icici"),
      debit(1460.0, at(2026, 10, 3), "swiggy@icici"), // different amounts → not a subscription
      debit(120.0, at(2026, 11, 2), "Chai Point"),
      debit(120.0, at(2026, 11, 9), "Chai Point"), // same month only → not recurring
    )
    val rec = SpendInsights.from(items, now, zone).recurring
    assertEquals(1, rec.size)
    assertEquals("Netflix", rec[0].name)
    assertEquals(3, rec[0].months)
    assertEquals(LocalDate.of(2026, 11, 20), rec[0].lastSeen)
  }

  @Test fun sampleInbox_producesAFullDashboard() = runTest {
    val zoneSys = ZoneId.systemDefault()
    val demoNow = LocalDateTime.of(2026, 11, 23, 23, 30).atZone(zoneSys).toInstant().toEpochMilli()
    val parser = LocalIntelParser(clock = { demoNow }, zone = zoneSys)
    val store = FileIntelStore(File(Files.createTempDirectory("jarvis").toFile(), "s.json"))
    store.upsert(SampleInbox().rawEvents(demoNow).map(parser::parse))

    val s = SpendInsights.from(store.items.value, demoNow, zoneSys)
    assertTrue("spent ${s.monthSpent}", s.monthSpent > 0)
    assertNotNull("delta", s.monthDelta)
    assertTrue("categories ${s.categories}", s.categories.size >= 5)
    assertTrue(s.merchants.isNotEmpty())
    assertTrue("recurring ${s.recurring}", s.recurring.any { it.name == "Netflix" })
    assertTrue("recurring ${s.recurring}", s.recurring.any { it.name == "Jio" })
    assertTrue("income ${s.income}", s.income.any { it.key == IncomeKind.SALARY })
    assertEquals(7_099.0, s.todaySpent, 0.001)
  }

  @Test fun dailySeries_forThisAndLastMonth() {
    val s = SpendInsights.from(
      listOf(debit(100.0, at(2026, 11, 1)), debit(50.0, at(2026, 11, 23, 9)), debit(70.0, at(2026, 10, 31))),
      now, zone,
    )
    assertEquals(23, s.monthDays.size)
    assertEquals(100.0, s.monthDays.first(), 0.0)
    assertEquals(50.0, s.monthDays.last(), 0.0)
    assertEquals(31, s.lastMonthDays.size)
    assertEquals(70.0, s.lastMonthDays.last(), 0.0)
    assertEquals(java.time.YearMonth.of(2026, 11), s.month)
    assertEquals(LocalDate.of(2026, 11, 23), s.today)
  }

  @Test fun billerName_fromMessageOrSender() {
    fun bill(title: String, summary: String, merchant: String? = null, sender: String? = null) =
      debit(1.0, now, merchant, summary).copy(category = IntelCategory.BILL, title = title, sender = sender)
    assertEquals("SBI Card", SpendInsights.billerName(bill("Credit card bill", "SBI Card ending 9012: Total Amt Due Rs.18,742", "SBICRD", "AX-SBICRD")))
    assertEquals("Airtel", SpendInsights.billerName(bill("Mobile bill", "Your Airtel Postpaid bill of 799", "GMAIL", "Gmail")))
    assertEquals("BESCOM", SpendInsights.billerName(bill("Electricity bill", "electricity bill", "BESCOM")))
    assertEquals("Loan EMI", SpendInsights.billerName(bill("EMI due", "EMI of Rs.15,230.00 for Loan A/c XX0987")))
    assertEquals("Acme", SpendInsights.billerName(bill("Bill due", "pay soon", "ACME")))
    assertEquals("Bill due", SpendInsights.billerName(bill("Bill due · ₹500", "pay soon")))
    assertEquals("HDFC Credit Card", SpendInsights.billerName(bill("HDFC credit card bill", "Total due ₹18,420")))
  }
}
