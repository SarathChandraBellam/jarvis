package com.jarvis.hud.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexRedactorTest {

  private val r = RegexRedactor()

  @Test fun masksOtpBeforeAndAfterKeyword() {
    assertFalse(r.redact("482913 is your OTP for login").contains("482913"))
    assertFalse(r.redact("Your verification code is 7731. Do not share").contains("7731"))
    assertFalse(r.redact("Use OTP: 55667788 to verify").contains("55667788"))
  }

  @Test fun keepsAmountsNearOtpKeyword() {
    val out = r.redact("OTP for txn of Rs 5000 at Amazon is 123456")
    assertTrue(out.contains("Rs 5000"))
    assertFalse(out.contains("123456"))
  }

  @Test fun leavesNumbersWithoutKeywordAlone() {
    assertEquals("Order 1234 shipped", r.redact("Order 1234 shipped"))
  }

  @Test fun masksCardKeepingLast4() {
    val out = r.redact("Card 4111 1111 1111 1234 was charged")
    assertTrue(out.contains("1234"))
    assertFalse(out.contains("4111"))
    assertFalse(r.redact("card 5555555555554444 used").contains("555555555555"))
  }

  @Test fun masksAccountNumbers() {
    assertTrue(r.redact("A/c XX1234 debited").contains("XX1234"))
    val out = r.redact("Account No. 001234567890 credited")
    assertTrue(out.contains("XX7890"))
    assertFalse(out.contains("001234567890"))
  }

  @Test fun masksEmail() {
    val out = r.redact("Mail tony.stark@starkindustries.com now")
    assertFalse(out.contains("tony.stark"))
    assertTrue(out.contains("[email]"))
  }

  @Test fun keepsVpa() {
    assertTrue(r.redact("to VPA swiggy@icici").contains("swiggy@icici"))
  }

  @Test fun masksIndianPhones() {
    assertFalse(r.redact("Call +91 98765 43210 now").contains("98765"))
    assertFalse(r.redact("Call 9876543210").contains("9876543210"))
    assertFalse(r.redact("Call 09876543210").contains("9876543210"))
  }

  @Test fun masksPan() {
    val out = r.redact("PAN ABCDE1234F linked")
    assertFalse(out.contains("ABCDE1234F"))
  }

  @Test fun masksAadhaarKeepingLast4() {
    val out = r.redact("Aadhaar 2345 6789 0123 verified")
    assertFalse(out.contains("2345 6789"))
    assertTrue(out.contains("0123"))
  }

  @Test fun blankPassthrough() {
    assertEquals("", r.redact(""))
  }
}
