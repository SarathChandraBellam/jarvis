package com.jarvis.hud.testdata

import com.jarvis.hud.data.source.ContentHash
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.RawEvent

/**
 * A realistic Indian phone inbox for tests: bank SMS, app notifications, bills, deliveries,
 * OTPs and six weeks of past payments. Two payments are reported twice (bank SMS + bank app,
 * bank SMS + Paytm), exactly as a real phone sees them. Timestamps are relative to `nowMillis`.
 */
class SampleInbox {
  private data class Seed(
    val source: DataSource,
    val sender: String,
    val title: String,
    val body: String,
    val minutesAgo: Long,
  )

  private val seeds = listOf(
    Seed(DataSource.SMS, "VM-HDFCBK", "VM-HDFCBK",
      "Rs.2,450.00 debited from A/c XX4821 on 22-11 to VPA swiggy@icici (UPI Ref 432918765510). Not you? Call 18002586161 - HDFC Bank", 12),
    Seed(DataSource.NOTIFICATION, "Swiggy", "Your order is out for delivery",
      "Ravi is on the way with your order from Meghana Foods. Arriving in 18 mins.", 15),
    Seed(DataSource.SMS, "AD-ICICIB", "AD-ICICIB",
      "ICICI Bank Acct XX7703 credited with INR 85,000.00 on 22-Nov-26 by NEFT-ACME TECHNOLOGIES PVT LTD SALARY NOV. Avl Bal INR 1,42,318.55", 38),
    Seed(DataSource.SMS, "VK-AMAZON", "VK-AMAZON",
      "Delivered: Your package with boAt Airdopes 141 has been delivered. Order #408-2219874-1137925. Rate your experience: amzn.in/d/rate", 55),
    Seed(DataSource.SMS, "JD-HDFCBK", "JD-HDFCBK",
      "842913 is the OTP for your transaction of Rs.12,999.00 at FLIPKART. Valid for 5 mins. Do not share it with anyone. - HDFC Bank", 64),
    Seed(DataSource.NOTIFICATION, "WhatsApp", "Mom",
      "Beta, did you book the train tickets for Diwali? Call me when free.", 72),
    Seed(DataSource.NOTIFICATION, "Google Calendar", "Sprint planning in 30 minutes",
      "Sprint planning - Q4 roadmap · 3:00 PM – 4:00 PM · Google Meet", 90),
    Seed(DataSource.SMS, "AX-SBICRD", "AX-SBICRD",
      "SBI Card ending 9012: Total Amt Due Rs.18,742.30, Min Amt Due Rs.940.00. Payment due date 28-Nov-26. Pay now to avoid late fees: sbicard.com/pay", 120),
    Seed(DataSource.NOTIFICATION, "Gmail", "IndiGo: Your booking is confirmed",
      "PNR X7QK2M · 6E-2134 BLR → DEL · 26 Nov, 07:15 – 10:05 · Seat 14C. Web check-in opens 48 hrs before departure.", 150),
    Seed(DataSource.SMS, "VM-BESCOM", "VM-BESCOM",
      "Dear Consumer, electricity bill for A/c 4412098761 is Rs.1,386.00 for Oct-26. Due date 30-Nov-26. Pay via BESCOM Mithra or any UPI app.", 185),
    Seed(DataSource.SMS, "AD-IRCTCI", "AD-IRCTCI",
      "PNR:4521876390, TRN:12628, DOJ:01-12-26, SL, SBC-NDLS, Dep:20:00, Ravi K, B3 42 CNF. IRCTC", 210),
    Seed(DataSource.NOTIFICATION, "Flipkart", "Shipped: Your order is on the way",
      "Your Noise ColorFit Pro 5 will be delivered by Tomorrow, 25 Nov. Track your order in the app.", 240),
    Seed(DataSource.SMS, "VM-PAYTMB", "VM-PAYTMB",
      "Rs.350 sent to Ramesh Kirana Store via UPI from Paytm Payments Bank a/c XX3310. UPI Ref No 432987651201.", 275),
    Seed(DataSource.NOTIFICATION, "WhatsApp", "Office Gang (5 messages)",
      "Priya: Lunch at 1:30? Team outing plan is finalised for Saturday, please confirm by tonight.", 300),
    Seed(DataSource.SMS, "JM-ICICIB", "JM-ICICIB",
      "Your ICICI Bank Credit Card XX5544 has been used for INR 4,299.00 at MYNTRA on 22-Nov-26. Avl Lmt: INR 1,12,450.00", 340),
    Seed(DataSource.NOTIFICATION, "Gmail", "Airtel Postpaid bill for November",
      "Your Airtel Postpaid bill of ₹799 for 98XXXXX210 is generated. Due date: 05 Dec 2026.", 420),
    Seed(DataSource.SMS, "VK-JIOINF", "VK-JIOINF",
      "Your Jio Fiber plan expires on 27-Nov-26. Recharge Rs.999 now to continue uninterrupted service. jio.com/recharge", 480),
    Seed(DataSource.NOTIFICATION, "Swiggy Instamart", "Order delivered",
      "Your Instamart order of ₹612 was delivered. Rate your delivery partner.", 560),
    Seed(DataSource.SMS, "AD-HDFCBK", "AD-HDFCBK",
      "EMI of Rs.15,230.00 for Loan A/c XX0987 will be debited on 25-Nov-26. Please maintain sufficient balance. - HDFC Bank", 720),
    Seed(DataSource.NOTIFICATION, "Zomato", "Your refund has been processed",
      "₹249 has been refunded to your UPI account for order #6732190. It may take 3-5 working days.", 900),

    // Same payments reported again by a second channel. The store must merge these with the bank
    // SMS above instead of counting the money twice.
    Seed(DataSource.NOTIFICATION, "HDFC Bank", "Money sent",
      "₹2,450.00 paid to Swiggy from A/c XX4821.", 11),
    Seed(DataSource.NOTIFICATION, "Paytm", "Payment successful",
      "Paid ₹350 to Ramesh Kirana Store. UPI Ref: 432987651201", 275),
  ) + history()

  /** ~6 weeks of past spending so month-to-date, patterns and recurring payments have data. */
  private fun history(): List<Seed> {
    fun upi(amount: String, vpa: String, ref: Long, daysAgo: Int, atMinute: Int) = Seed(
      DataSource.SMS, "VM-HDFCBK", "VM-HDFCBK",
      "Rs.$amount debited from A/c XX4821 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161 - HDFC Bank",
      daysAgo * DAY_MIN + atMinute,
    )
    fun card(amount: String, merchant: String, daysAgo: Int, atMinute: Int) = Seed(
      DataSource.SMS, "JM-ICICIB", "JM-ICICIB",
      "Your ICICI Bank Credit Card XX5544 has been used for INR $amount at $merchant. Avl Lmt: INR 98,000.00",
      daysAgo * DAY_MIN + atMinute,
    )
    return listOf(
      upi("486.00", "zomato@hdfcbank", 432811000101, 1, 140),
      card("1,240.00", "BIGBASKET", 1, 610),
      upi("212.00", "uber@axisbank", 432811000102, 2, 30),
      card("649.00", "NETFLIX", 3, 300),
      upi("2,000.00", "hpcl.fuel@icici", 432811000103, 4, 520),
      upi("389.00", "swiggy@icici", 432811000104, 5, 75),
      card("3,499.00", "AMAZON", 6, 200),
      upi("1,850.00", "bescom@okaxis", 432811000105, 8, 660),
      upi("540.00", "dmart@ybl", 432811000106, 9, 400),
      Seed(DataSource.SMS, "VM-HDFCBK", "VM-HDFCBK",
        "Rs.5,000.00 sent to ankit.sharma@okhdfcbank from A/c XX4821 via IMPS. Ref 432811000107 - HDFC Bank",
        10 * DAY_MIN + 90),
      card("899.00", "BOOKMYSHOW", 12, 45),
      upi("312.00", "rapido@ybl", 432811000108, 13, 800),
      card("1,120.00", "APOLLO PHARMACY", 16, 380),
      upi("725.00", "zomato@hdfcbank", 432811000109, 19, 20),
      upi("2,300.00", "hpcl.fuel@icici", 432811000110, 24, 500),
      card("649.00", "NETFLIX", 33, 300),
      upi("999.00", "jiofiber@sbi", 432811000111, 34, 600),
      card("2,799.00", "MYNTRA", 36, 150),
      upi("1,460.00", "swiggy@icici", 432811000112, 38, 60),
      upi("1,180.00", "bigbasket@hdfcbank", 432811000114, 41, 640),
      card("2,150.00", "AMAZON", 43, 220),
      upi("2,100.00", "hpcl.fuel@icici", 432811000115, 45, 510),
      upi("1,780.00", "bescom@okaxis", 432811000116, 47, 660),
      upi("640.00", "zomato@hdfcbank", 432811000117, 48, 70),
      card("1,499.00", "BOOKMYSHOW", 49, 40),
      upi("6,000.00", "ankit.sharma@okhdfcbank", 432811000118, 50, 90),
      upi("999.00", "jiofiber@sbi", 432811000113, 64, 600),
      card("649.00", "NETFLIX", 63, 300),
    )
  }

  fun rawEvents(nowMillis: Long): List<RawEvent> = seeds.mapIndexed { i, s ->
    val ts = nowMillis - s.minutesAgo * MINUTE
    RawEvent(
      id = "demo-${s.source.name.lowercase()}-${i + 1}",
      source = s.source,
      sender = s.sender,
      title = s.title,
      body = s.body,
      timestamp = ts,
      contentHash = ContentHash.of(s.source, s.sender, s.body, ts),
    )
  }

  private companion object {
    const val MINUTE = 60_000L
    const val DAY_MIN = 24 * 60L
  }
}
