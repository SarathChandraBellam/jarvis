package com.astra.hud.ai

import com.astra.hud.contracts.Redactor

/**
 * Regex-based PII masking applied before any text leaves the device.
 * Masks: emails, PAN, account numbers (keep last 4), card numbers (keep last 4),
 * Indian phone numbers, Aadhaar numbers, and OTPs (4–8 digits near otp/code/verification).
 */
class RegexRedactor : Redactor {

  override fun redact(text: String): String {
    if (text.isBlank()) return text
    var out = text
    out = EMAIL.replace(out, "[email]")
    out = PAN.replace(out, "[PAN]")
    out = ACCOUNT.replace(out) { m -> m.groupValues[1] + "XX" + m.groupValues[2].filter(Char::isDigit).takeLast(4) }
    out = CARD.replace(out) { m ->
      val digits = m.value.filter(Char::isDigit)
      "XXXX XXXX XXXX " + digits.takeLast(4)
    }
    out = PHONE.replace(out, "[phone]")
    out = AADHAAR.replace(out) { m -> "XXXX XXXX " + m.value.filter(Char::isDigit).takeLast(4) }
    out = maskOtps(out)
    return out
  }

  private fun maskOtps(text: String): String {
    val keywords = OTP_KEYWORD.findAll(text).toList()
    if (keywords.isEmpty()) return text
    val sb = StringBuilder(text)
    // Walk numbers right-to-left so indices stay valid while masking.
    val candidates = OTP_NUMBER.findAll(text).toList().asReversed()
    for (num in candidates) {
      val near = keywords.any { kw ->
        num.range.first >= kw.range.first - OTP_WINDOW_BEFORE && num.range.first <= kw.range.last + OTP_WINDOW_AFTER
      }
      if (!near) continue
      val prefix = text.substring(maxOf(0, num.range.first - 5), num.range.first).lowercase()
      if (CURRENCY_PREFIX.containsMatchIn(prefix)) continue
      sb.replace(num.range.first, num.range.last + 1, "*".repeat(num.value.length))
    }
    return sb.toString()
  }

  companion object {
    private const val OTP_WINDOW_BEFORE = 40
    private const val OTP_WINDOW_AFTER = 60

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val PAN = Regex("""\b[A-Z]{5}[0-9]{4}[A-Z]\b""")
    private val ACCOUNT = Regex(
      """(?i)\b((?:a/c|ac|acct|account)(?:\s*(?:no\.?|number))?\s*[:.\-]?\s*)([Xx*]*\d{4,18})\b""",
    )
    private val CARD = Regex("""(?<![\d*X])(?:\d[ \-]?){12,18}\d(?!\d)""")
    private val PHONE = Regex("""(?<![\d\w])(?:\+91[\s\-]?|91[\s\-]|0)?[6-9]\d{4}[\s\-]?\d{5}(?!\d)""")
    private val AADHAAR = Regex("""(?<!\d)[2-9]\d{3}[\s\-]?\d{4}[\s\-]?\d{4}(?!\d)""")
    private val OTP_KEYWORD = Regex(
      """(?i)\b(otp|one[\s\-]time[\s\-]pass(?:word|code)|verification|passcode|code|pin)\b""",
    )
    private val OTP_NUMBER = Regex("""(?<![\d.,])\d{4,8}(?![\d.,]*\d)""")
    private val CURRENCY_PREFIX = Regex("""(₹|rs\.?|inr)\s*$""")
  }
}
