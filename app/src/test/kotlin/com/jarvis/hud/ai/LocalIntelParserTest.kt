package com.jarvis.hud.ai

import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.RawEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntelParserTest {

  private val zone = ZoneId.of("Asia/Kolkata")
  private val now = ZonedDateTime.of(2026, 11, 22, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
  private val parser = LocalIntelParser(clock = { now }, zone = zone)

  private fun sms(body: String, sender: String = "AD-HDFCBK", hash: String = body.hashCode().toString()) = RawEvent(
    id = "raw-$hash",
    source = DataSource.SMS,
    sender = sender,
    title = "",
    body = body,
    timestamp = now,
    contentHash = hash,
  )

  @Test fun upiDebitWithVpa() {
    val item = parser.parse(sms("Rs.4,250.00 debited from A/c XX1234 on 22-11-26 to VPA swiggy@icici (UPI Ref No 432112345678). Not you? Call 18002586161", hash = "h1"))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals(4250.0, item.amount!!, 0.001)
    assertFalse(item.isCredit)
    assertEquals("swiggy@icici", item.merchant)
    assertEquals("item-h1", item.id)
    assertEquals("raw-h1", item.rawEventId)
  }

  @Test fun cardSpendAtMerchant() {
    val item = parser.parse(sms("INR 1,299.50 spent on ICICI Bank Card XX9876 at AMAZON PAY INDIA on 22-Nov-26. Avl Lmt: INR 85,000.00"))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals(1299.5, item.amount!!, 0.001)
    assertEquals("AMAZON PAY INDIA", item.merchant)
    assertFalse(item.isCredit)
  }

  @Test fun salaryCredit() {
    val item = parser.parse(sms("Your A/c XX5678 is credited with INR 1,25,000.00 on 21-11-2026 by NEFT from ACME TECHNOLOGIES. Avl Bal INR 2,10,450.25", sender = "VM-SBIINB"))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertTrue(item.isCredit)
    assertEquals(125000.0, item.amount!!, 0.001)
    assertTrue(item.title.contains("₹1,25,000"))
  }

  @Test fun upiSentToPerson() {
    val item = parser.parse(sms("Sent Rs.500.00 from Kotak Bank AC X4321 to Rahul Kumar on 22-11-26. UPI Ref 612345678901"))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals(500.0, item.amount!!, 0.001)
    assertEquals("Rahul Kumar", item.merchant)
  }

  @Test fun balanceAmountNotPickedFirst() {
    val a = parser.extractAmount("Avl Bal Rs 10,000.00. Rs 250 debited at CAFE COFFEE DAY")
    assertEquals(250.0, a!!.value, 0.001)
  }

  @Test fun creditCardBillDueSoonIsCritical() {
    val item = parser.parse(sms("Your HDFC Bank Credit Card XX1111 statement is generated. Total due: Rs 12,450.00, Min due Rs 620. Pay by 23-11-2026 to avoid charges."))
    assertEquals(IntelCategory.BILL, item.category)
    assertEquals("2026-11-23", item.dueDate)
    assertEquals(Priority.CRITICAL, item.priority)
    assertEquals(12450.0, item.amount!!, 0.001)
    assertTrue(item.title.startsWith("Credit card bill"))
  }

  @Test fun electricityBillDueLaterIsHigh() {
    val item = parser.parse(sms("Dear Consumer, your BESCOM electricity bill of Rs 1,845 for Nov-2026 is due on 05 Dec 2026. Pay via BESCOM Mithra.", sender = "BZ-BESCOM"))
    assertEquals(IntelCategory.BILL, item.category)
    assertEquals("2026-12-05", item.dueDate)
    assertEquals(Priority.HIGH, item.priority)
    assertTrue(item.title.startsWith("Electricity bill"))
  }

  @Test fun billDueDateWithoutYearAndMonthFirst() {
    assertEquals(LocalDate.of(2026, 11, 24), parser.extractDueDate("Airtel postpaid bill Rs 799 due by Nov 24."))
    assertEquals(LocalDate.of(2026, 11, 22), parser.extractDueDate("Your bill is due today"))
    assertEquals(LocalDate.of(2027, 1, 3), parser.extractDueDate("EMI of Rs 5,000 due on 3rd Jan"))
  }

  @Test fun paidBillIsNotABill() {
    val item = parser.parse(sms("Payment of Rs 12,450 received towards your credit card XX1111. Thank you for your payment."))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertTrue(item.isCredit)
  }

  @Test fun amazonOutForDelivery() {
    val item = parser.parse(sms("Out for delivery: Your Amazon package with boAt Airdopes will be delivered today by 8 PM.", sender = "AX-AMAZON"))
    assertEquals(IntelCategory.DELIVERY, item.category)
    assertEquals("Amazon", item.merchant)
    assertEquals(Priority.HIGH, item.priority)
    assertTrue(item.title.contains("Out for delivery"))
  }

  @Test fun flipkartShipped() {
    val item = parser.parse(sms("Your Flipkart order for Redmi Note 14 has been shipped and will arrive by Tue, 25 Nov.", sender = "JD-FLPKRT"))
    assertEquals(IntelCategory.DELIVERY, item.category)
    assertEquals("Flipkart", item.merchant)
    assertTrue(item.title.contains("Shipped"))
  }

  @Test fun swiggyDeliveredNotification() {
    val e = RawEvent("n1", DataSource.NOTIFICATION, "Swiggy", "Order delivered", "Your Swiggy order from Meghana Foods has been delivered. Enjoy!", now, "n1hash")
    val item = parser.parse(e)
    assertEquals(IntelCategory.DELIVERY, item.category)
    assertEquals("Swiggy", item.merchant)
    assertEquals(DataSource.NOTIFICATION, item.source)
  }

  @Test fun irctcPnr() {
    val item = parser.parse(sms("PNR 4521367890, Train 12627 KARNATAKA EXP, Dt 28-11-26, SBC-NDLS, Coach B2 Berth 34. IRCTC", sender = "VM-IRCTCI"))
    assertEquals(IntelCategory.TRAVEL, item.category)
    assertTrue(item.title.contains("PNR 4521367890"))
    assertEquals("IRCTC", item.merchant)
  }

  @Test fun indigoBoarding() {
    val item = parser.parse(sms("IndiGo 6E 2134 BLR-BOM: Boarding starts 14:20 at Gate 12. Web check-in done. Have a great flight!", sender = "AI-INDIGO"))
    assertEquals(IntelCategory.TRAVEL, item.category)
    assertEquals(Priority.HIGH, item.priority)
  }

  @Test fun otpIsLowSystemAndMasked() {
    val item = parser.parse(sms("482913 is your OTP for txn of Rs 2,500.00 at AMAZON on HDFC card XX1111. Valid for 5 mins. Do not share.", sender = "AD-HDFCBK"))
    assertEquals(IntelCategory.SYSTEM, item.category)
    assertEquals(Priority.LOW, item.priority)
    assertFalse(item.summary.contains("482913"))
  }

  @Test fun debitWithDoNotShareOtpFooterIsStillFinance() {
    val item = parser.parse(sms("Rs 750 debited from A/c XX1234 at ZOMATO LTD on 22-11-26. Never share your OTP with anyone."))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals("ZOMATO LTD", item.merchant)
  }

  @Test fun meetingReminderIsSchedule() {
    val e = RawEvent("c1", DataSource.NOTIFICATION, "Google Calendar", "Design review", "Reminder: Design review meeting at 3:00 PM in Room 4B", now, "c1hash")
    val item = parser.parse(e)
    assertEquals(IntelCategory.SCHEDULE, item.category)
    assertEquals("Design review", item.title)
  }

  @Test fun plainMessage() {
    val e = RawEvent("m1", DataSource.SMS, "+919876543210", "", "Hey, are we still on for dinner tonight?", now, "m1hash")
    val item = parser.parse(e)
    assertEquals(IntelCategory.MESSAGE, item.category)
    assertNull(item.amount)
    assertEquals("item-m1hash", item.id)
  }

  @Test fun largeDebitIsHigh() {
    val item = parser.parse(sms("INR 45,000.00 debited from your A/c XX2222 towards IMPS to SHARMA PROPERTIES Ref 123456"))
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals(Priority.HIGH, item.priority)
    assertNotNull(item.merchant)
  }

  @Test fun rupeeSymbolAmount() {
    val a = parser.extractAmount("₹ 99 paid to Blinkit")
    assertEquals(99.0, a!!.value, 0.0)
  }

  @Test fun inrFormatting() {
    assertEquals("₹4,250", IntelFormatting.inr(4250.0))
    assertEquals("₹1,25,000.50", IntelFormatting.inr(125000.5))
    assertEquals("₹999", IntelFormatting.inr(999.0))
    assertEquals("₹1,00,00,000", IntelFormatting.inr(10_000_000.0))
  }
}
