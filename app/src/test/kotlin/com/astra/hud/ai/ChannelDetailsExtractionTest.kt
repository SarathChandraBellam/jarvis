package com.astra.hud.ai

import com.astra.hud.model.DataSource
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.RawEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelDetailsExtractionTest {

  private val parser = LocalIntelParser(clock = { 1_795_000_000_000L })

  @Test fun accountTail_commonBankFormats() {
    val cases = mapOf(
      "Rs.2,450.00 debited from A/c XX4821 on 22-11" to "4821",
      "ICICI Bank Acct XX7703 credited with INR 85,000.00" to "7703",
      "Your ICICI Bank Credit Card XX5544 has been used for INR 4,299.00" to "5544",
      "SBI Card ending 9012: Total Amt Due" to "9012",
      "from Paytm Payments Bank a/c XX3310." to "3310",
      "A/c no. XXXXXX1234 debited for Rs 500" to "1234",
      "a/c **5678 debited" to "5678",
      "Account 123456789012 credited" to "9012",
      "Card ending with 4455 charged Rs 99" to "4455",
      "₹2,450.00 paid to Swiggy from A/c XX4821." to "4821",
    )
    cases.forEach { (text, expected) -> assertEquals(text, expected, parser.extractAccountTail(text)) }
  }

  @Test fun accountTail_absent() {
    assertNull(parser.extractAccountTail("Paid ₹350 to Ramesh Kirana Store."))
    assertNull(parser.extractAccountTail("Your card has been dispatched"))
  }

  @Test fun reference_commonFormats() {
    val cases = mapOf(
      "to VPA swiggy@icici (UPI Ref 432918765510). Not you?" to "432918765510",
      "Paid ₹350 to Ramesh Kirana Store. UPI Ref: 432987651201" to "432987651201",
      "UPI Ref No 432987651201." to "432987651201",
      "IMPS Ref no. 612345678901 credited" to "612345678901",
      "UTR: SBIN226330123456 NEFT credit" to "SBIN226330123456",
      "RRN 401234567890 txn successful" to "401234567890",
      "Txn ID T2611221234567 paid" to "T2611221234567",
    )
    cases.forEach { (text, expected) -> assertEquals(text, expected, parser.extractReference(text)) }
  }

  @Test fun reference_ignoresWordsThatOnlyStartWithRef() {
    assertNull(parser.extractReference("₹249 has been refunded to your UPI account"))
    assertNull(parser.extractReference("Refer a friend and win"))
    assertNull(parser.extractReference("Ref: ABCDEF")) // no digits → not a transaction reference
  }

  @Test fun parsedTransaction_carriesSenderAccountAndReference() {
    val e = RawEvent(
      "r1", DataSource.SMS, "VM-HDFCBK", "VM-HDFCBK",
      "Rs.2,450.00 debited from A/c XX4821 on 22-11 to VPA swiggy@icici (UPI Ref 432918765510). - HDFC Bank",
      1L, "h1",
    )
    val item = parser.parse(e)
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals("VM-HDFCBK", item.sender)
    assertEquals("4821", item.accountTail)
    assertEquals("432918765510", item.reference)
  }

  @Test fun nonFinanceItem_hasSenderButNoTransactionDetails() {
    val e = RawEvent("r2", DataSource.NOTIFICATION, "WhatsApp", "Mom", "Call me when free", 1L, "h2")
    val item = parser.parse(e)
    assertEquals("WhatsApp", item.sender)
    assertNull(item.accountTail)
    assertNull(item.reference)
  }

  @Test fun upcomingDebit_isABillNotMoneySpent() {
    val e = RawEvent(
      "r3", DataSource.SMS, "AD-HDFCBK", "",
      "EMI of Rs.15,230.00 for Loan A/c XX0987 will be debited on 25-Nov-26. Please maintain sufficient balance.",
      1L, "h3",
    )
    val item = LocalIntelParser(clock = { 1_795_000_000_000L }, zone = java.time.ZoneId.of("Asia/Kolkata")).parse(e)
    assertEquals(IntelCategory.BILL, item.category)
    assertEquals(15230.0, item.amount!!, 0.0)
    assertEquals("2026-11-25", item.dueDate)
  }

  @Test fun cardUsedFor_isASpend() {
    val e = RawEvent(
      "r4", DataSource.SMS, "JM-ICICIB", "",
      "Your ICICI Bank Credit Card XX5544 has been used for INR 4,299.00 at MYNTRA on 22-Nov-26. Avl Lmt: INR 1,12,450.00",
      1L, "h4",
    )
    val item = parser.parse(e)
    assertEquals(IntelCategory.FINANCE, item.category)
    assertEquals(4299.0, item.amount!!, 0.0)
    assertEquals(false, item.isCredit)
    assertEquals("MYNTRA", item.merchant)
    assertEquals("5544", item.accountTail)
  }
}
