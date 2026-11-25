package com.astra.hud.ai

import com.astra.hud.model.IntelCategory
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.Priority
import com.astra.hud.model.RawEvent
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Deterministic, offline RawEvent -> OrganizedItem parser tuned for Indian SMS / notifications.
 * Pure Kotlin (no Android APIs) so it is fully unit-testable.
 *
 * @param clock current time in millis, used for due-date urgency
 * @param zone time zone used to interpret dates
 */
class LocalIntelParser(
  private val clock: () -> Long = System::currentTimeMillis,
  private val zone: ZoneId = ZoneId.systemDefault(),
) {

  data class Amount(val value: Double, val currency: String = "₹")

  fun parseAll(events: List<RawEvent>): List<OrganizedItem> = events.map(::parse)

  fun parse(event: RawEvent): OrganizedItem {
    val text = listOf(event.title, event.body).filter { it.isNotBlank() }.distinct().joinToString(" ").trim()
    val lower = text.lowercase()
    val amount = extractAmount(text)
    val txn = transactionDirection(lower)
    val base = OrganizedItem(
      id = itemId(event),
      rawEventId = event.id,
      category = IntelCategory.MESSAGE,
      title = "",
      summary = IntelFormatting.truncate(event.body.ifBlank { event.title }, 180),
      timestamp = event.timestamp,
      source = event.source,
      sender = event.sender.ifBlank { null },
    )

    // 1. OTP / verification codes
    if (isOtp(lower)) {
      return base.copy(
        category = IntelCategory.SYSTEM,
        title = "Verification code from ${displaySender(event)}",
        summary = IntelFormatting.truncate(OTP_DIGITS.replace(event.body) { "*".repeat(it.value.length) }, 180),
        priority = Priority.LOW,
      )
    }

    // 2. Bills / dues
    if (isBill(lower)) {
      val due = extractDueDate(text)
      val today = today()
      val priority = if (due != null && ChronoUnit.DAYS.between(today, due) <= 2) Priority.CRITICAL else Priority.HIGH
      val kind = billKind(lower)
      val title = buildString {
        append(kind)
        amount?.let { append(" · ").append(IntelFormatting.inr(it.value, it.currency)) }
        due?.let { append(" · due ").append(due.toString()) }
      }
      return base.copy(
        category = IntelCategory.BILL,
        title = title,
        amount = amount?.value,
        currency = amount?.currency ?: "₹",
        merchant = extractMerchant(text) ?: billerFromSender(event),
        dueDate = due?.toString(),
        priority = priority,
      )
    }

    // 3. Deliveries
    if (DELIVERY_KEYWORDS.containsMatchIn(lower) && txn == null) {
      val brand = brandIn(lower, DELIVERY_BRANDS) ?: brandIn(event.sender.lowercase(), DELIVERY_BRANDS)
      val status = when {
        lower.contains("out for delivery") -> "Out for delivery"
        Regex("\\bdelivered\\b").containsMatchIn(lower) -> "Delivered"
        Regex("\\b(shipped|dispatched)\\b").containsMatchIn(lower) -> "Shipped"
        Regex("\\b(on the way|arriving|picked up|on its way)\\b").containsMatchIn(lower) -> "On the way"
        else -> "Order update"
      }
      return base.copy(
        category = IntelCategory.DELIVERY,
        title = listOfNotNull(brand, status).joinToString(" · "),
        merchant = brand,
        amount = amount?.value,
        priority = if (status == "Out for delivery" || status == "On the way") Priority.HIGH else Priority.NORMAL,
      )
    }

    // 4. Travel
    val strongTravel = TRAVEL_STRONG.containsMatchIn(lower)
    val brandTravel = brandIn(lower, TRAVEL_BRANDS) ?: brandIn(event.sender.lowercase(), TRAVEL_BRANDS)
    if (strongTravel || (brandTravel != null && txn == null)) {
      val pnr = PNR.find(text)?.groupValues?.get(1)
      val title = buildString {
        append(brandTravel ?: "Travel")
        when {
          pnr != null -> append(" · PNR ").append(pnr)
          lower.contains("boarding") -> append(" · Boarding")
          lower.contains("check-in") || lower.contains("checkin") -> append(" · Check-in")
          else -> append(" · Itinerary")
        }
      }
      return base.copy(
        category = IntelCategory.TRAVEL,
        title = title,
        merchant = brandTravel,
        amount = amount?.value,
        priority = Priority.HIGH,
      )
    }

    // 5. Financial transactions
    if (amount != null && txn != null) {
      val isCredit = txn == Direction.CREDIT
      val merchant = extractMerchant(text)
      val formatted = IntelFormatting.inr(amount.value, amount.currency)
      val title = buildString {
        append(formatted).append(if (isCredit) " credited" else " debited")
        merchant?.let { append(if (isCredit) " · from " else " · ").append(it) }
      }
      return base.copy(
        category = IntelCategory.FINANCE,
        title = title,
        amount = amount.value,
        currency = amount.currency,
        isCredit = isCredit,
        merchant = merchant,
        priority = if (!isCredit && amount.value >= LARGE_DEBIT) Priority.HIGH else Priority.NORMAL,
        accountTail = extractAccountTail(text),
        reference = extractReference(text),
      )
    }

    // 6. Schedule
    if (SCHEDULE_KEYWORDS.containsMatchIn(lower)) {
      return base.copy(
        category = IntelCategory.SCHEDULE,
        title = IntelFormatting.truncate(event.title.ifBlank { "Reminder from ${displaySender(event)}" }, 60),
        priority = Priority.NORMAL,
      )
    }

    // 7. Plain message
    return base.copy(
      category = IntelCategory.MESSAGE,
      title = IntelFormatting.truncate(event.title.ifBlank { displaySender(event) }, 60),
      amount = amount?.value,
      priority = if (PROMO_KEYWORDS.containsMatchIn(lower)) Priority.LOW else Priority.NORMAL,
    )
  }

  // ---------- extraction helpers (internal for tests) ----------

  internal fun extractAmount(text: String): Amount? {
    val matches = AMOUNT.findAll(text).toList()
    if (matches.isEmpty()) return null
    val nonBalance = matches.firstOrNull { m ->
      val before = text.substring(maxOf(0, m.range.first - 22), m.range.first).lowercase()
      !BALANCE_HINT.containsMatchIn(before)
    } ?: matches.first()
    val value = nonBalance.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    return Amount(value, "₹")
  }

  /**
   * Last 4 digits of the account or card a transaction names ("A/c XX1234", "Card ending 1234",
   * "a/c **5678"). Null when the message does not name one.
   */
  fun extractAccountTail(text: String): String? =
    ACCOUNT_TAIL.find(text)?.groupValues?.get(1)?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }

  /** UPI / IMPS / NEFT reference number ("UPI Ref No 412345678901", "Ref: 1234567890"). */
  fun extractReference(text: String): String? =
    REFERENCE.find(text)?.groupValues?.get(1)?.takeIf { ref -> ref.count(Char::isDigit) >= 6 }?.uppercase()

  internal enum class Direction { DEBIT, CREDIT }

  internal fun transactionDirection(lower: String): Direction? {
    val credit = CREDIT_WORDS.find(lower)?.range?.first
    val debit = DEBIT_WORDS.find(lower)?.range?.first
    return when {
      credit == null && debit == null -> null
      credit == null -> Direction.DEBIT
      debit == null -> Direction.CREDIT
      debit <= credit -> Direction.DEBIT
      else -> Direction.CREDIT
    }
  }

  internal fun extractMerchant(text: String): String? {
    VPA.find(text)?.let { return it.groupValues[1] }
    for (m in AT_OR_TO.findAll(text)) {
      val candidate = cleanMerchant(text.substring(m.range.last + 1))
      if (candidate != null) return candidate
    }
    return null
  }

  private fun cleanMerchant(tail: String): String? {
    var s = tail.trimStart()
    val stop = MERCHANT_STOP.find(s)
    if (stop != null) s = s.substring(0, stop.range.first)
    s = s.trim().trimEnd('.', ',', '-', ':', ';').trim()
    if (s.length < 2 || s.length > 40) return null
    val lower = s.lowercase()
    if (MERCHANT_REJECT.any { lower == it || lower.startsWith("$it ") }) return null
    if (s.all { it.isDigit() || it == ' ' || it == ':' || it == '-' || it == '/' }) return null
    if (s.first().isDigit()) return null
    if (s.first().isLowerCase() && !s.contains('@')) return null
    return s
  }

  internal fun extractDueDate(text: String): LocalDate? {
    val lower = text.lowercase()
    val today = today()
    if (Regex("\\bdue\\s+today\\b").containsMatchIn(lower)) return today
    if (Regex("\\bdue\\s+tomorrow\\b").containsMatchIn(lower)) return today.plusDays(1)
    val anchors = DUE_ANCHOR.findAll(text).map { it.range.last + 1 }.toList()
    for (start in anchors) {
      val window = text.substring(start, minOf(text.length, start + 28)).trimStart(' ', ':', '-')
      val cleaned = LEADING_FILLER.replace(window, "")
      parseDateAt(cleaned, today)?.let { return it }
    }
    return null
  }

  internal fun parseDateAt(s: String, today: LocalDate): LocalDate? {
    ISO_DATE.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
      return safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    NUMERIC_DATE.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
      return safeDate(normalizeYear(m.groupValues[3].toInt()), m.groupValues[2].toInt(), m.groupValues[1].toInt())
    }
    DAY_MONTH_NAME.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
      val month = monthOf(m.groupValues[2]) ?: return null
      val day = m.groupValues[1].toInt()
      val yearStr = m.groupValues[3]
      return if (yearStr.isNotEmpty()) safeDate(normalizeYear(yearStr.toInt()), month, day) else inferYear(month, day, today)
    }
    MONTH_NAME_DAY.find(s)?.takeIf { it.range.first == 0 }?.let { m ->
      val month = monthOf(m.groupValues[1]) ?: return null
      val day = m.groupValues[2].toInt()
      val yearStr = m.groupValues[3]
      return if (yearStr.isNotEmpty()) safeDate(normalizeYear(yearStr.toInt()), month, day) else inferYear(month, day, today)
    }
    return null
  }

  private fun inferYear(month: Int, day: Int, today: LocalDate): LocalDate? {
    val candidate = safeDate(today.year, month, day) ?: return null
    return if (candidate.isBefore(today.minusDays(60))) candidate.plusYears(1) else candidate
  }

  private fun normalizeYear(y: Int) = if (y < 100) 2000 + y else y

  private fun safeDate(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

  private fun monthOf(name: String): Int? {
    val key = name.lowercase().take(3)
    return Month.entries.firstOrNull { it.name.lowercase().startsWith(key) }?.value
  }

  private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

  private fun isOtp(lower: String): Boolean {
    val keywords = OTP_KEYWORD.findAll(lower).toList()
    if (keywords.isEmpty()) return false
    return OTP_DIGITS.findAll(lower).any { n ->
      val prefix = lower.substring(maxOf(0, n.range.first - 5), n.range.first)
      !CURRENCY_TAIL.containsMatchIn(prefix) && keywords.any { k ->
        n.range.first >= k.range.first - 40 && n.range.first <= k.range.last + 40
      }
    }
  }

  private fun isBill(lower: String): Boolean {
    if (BILL_PAID.containsMatchIn(lower)) return false
    return BILL_KEYWORDS.containsMatchIn(lower)
  }

  private fun billKind(lower: String): String = when {
    lower.contains("credit card") ||
      Regex("\\bcard\\b").containsMatchIn(lower) && Regex("statement|amt due|amount due|total due|min(?:imum)? due").containsMatchIn(lower) -> "Credit card bill"
    lower.contains("electricity") || Regex("\\b(bescom|tneb|msedcl|tata power|adani electricity|bses|kseb)\\b").containsMatchIn(lower) -> "Electricity bill"
    lower.contains("broadband") || lower.contains("fiber") || lower.contains("fibre") -> "Broadband bill"
    lower.contains("postpaid") || lower.contains("mobile bill") -> "Mobile bill"
    lower.contains("gas") -> "Gas bill"
    lower.contains("water") -> "Water bill"
    lower.contains("emi") || lower.contains("loan") -> "EMI due"
    lower.contains("insurance") || lower.contains("premium") -> "Insurance premium"
    lower.contains("rent") -> "Rent due"
    else -> "Bill due"
  }

  private fun billerFromSender(event: RawEvent): String? =
    event.sender.substringAfterLast('-').trim().takeIf { it.length in 3..20 && it.any(Char::isLetter) }

  private fun displaySender(event: RawEvent): String = event.sender.ifBlank { event.source.label }

  private fun brandIn(lower: String, brands: Map<String, String>): String? =
    brands.entries.firstOrNull { Regex("\\b${Regex.escape(it.key)}\\b").containsMatchIn(lower) }?.value

  companion object {
    private const val LARGE_DEBIT = 10_000.0

    fun itemId(event: RawEvent): String = "item-" + event.contentHash

    private val AMOUNT = Regex("""(?i)(?:₹|\bRs\.?|\bINR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val BALANCE_HINT = Regex("""(bal|balance|limit|avl|available)[^0-9]*$""")
    private val CREDIT_WORDS = Regex("""\b(credited|received|refund(?:ed)?|deposited|cashback|reversed)\b""")
    private val DEBIT_WORDS = Regex("""\b(debited|spent|paid|sent|withdrawn|withdrawal|purchase|deducted|charged|txn of|transaction of|used for|used at)\b""")
    private val ACCOUNT_TAIL = Regex(
      """(?i)\b(?:a/c|ac|acct|account|card|credit card|debit card)(?:\s*(?:no\.?|number))?(?:\s+(?:ending|ending with|ending in|end(?:s|ing)? with))?\s*[:.\-]?\s*(?:no\.?\s*)?([Xx*.\d]*\d{4})(?!\d)""",
    )
    private val REFERENCE = Regex(
      """(?i)\b(?:upi\s*ref(?:erence)?|ref(?:erence)?|rrn|utr|txn\s*id|transaction\s*id|imps\s*ref)\b(?:\s*(?:no\.?|number|id))?\s*[:.#\-]?\s*([A-Z0-9]{6,22})\b""",
    )
    private val VPA = Regex("""(?i)\bVPA\s*[:\-]?\s*([A-Za-z0-9.\-_]+@[A-Za-z0-9]+)""")
    private val AT_OR_TO = Regex("""(?i)\b(?:at|to)\b""")
    private val MERCHANT_STOP = Regex(
      """(?i)(\s+(?:on|via|using|for|ref|refno|upi|txn|avl|bal|info|from|thru|through|is|has|was|by|with|in)\b|[.,;(]|\s+-\s+|\s+\d{2}[-/]\d{2}|$)""",
    )
    private val MERCHANT_REJECT = listOf(
      "your", "you", "a/c", "ac", "acct", "account", "the", "rs", "inr", "be", "a", "an", "our", "this", "be",
    )

    private val OTP_KEYWORD = Regex("""\b(otp|one[\s\-]time[\s\-]pass(?:word|code)|verification code|security code|login code|passcode|is your code|code is)\b""")
    private val OTP_DIGITS = Regex("""(?<![\w.,₹])\d{4,8}(?![\d.,]*\d)""")
    private val CURRENCY_TAIL = Regex("""(₹|rs\.?|inr)\s*$""", RegexOption.IGNORE_CASE)
    private val LEADING_FILLER = Regex("""^(?:(?:is|on|of|date|the)\s*:?\s+)+""", RegexOption.IGNORE_CASE)

    private val BILL_KEYWORDS = Regex(
      """\b(bill|due on|due by|due date|payment due|amount due|amt due|min(?:imum)? (?:amount|amt) due|total due|statement|overdue|is due|pay by|emi|will be (?:auto[\s\-]?)?debited|scheduled (?:for )?debit|autopay (?:due|scheduled))\b""",
    )
    private val BILL_PAID = Regex("""(payment (?:of .{0,30})?(?:received|successful)|thank you for (?:the |your )?payment|bill paid|has been paid|successfully paid)""")
    private val DUE_ANCHOR = Regex("""(?i)\b(?:due\s+(?:on|by|date)|due|pay\s+by|before|by|on)\b\s*:?""")

    private val ISO_DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val NUMERIC_DATE = Regex("""^(\d{1,2})[\-/.](\d{1,2})[\-/.](\d{2,4})\b""")
    private val DAY_MONTH_NAME = Regex(
      """(?i)^(\d{1,2})(?:st|nd|rd|th)?[\s\-]*(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[\s\-,']*(\d{4}|\d{2}(?![\d:]))?""",
    )
    private val MONTH_NAME_DAY = Regex(
      """(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?""",
    )

    private val DELIVERY_KEYWORDS = Regex(
      """\b(shipped|dispatched|out for delivery|delivered|arriving|on the way|on its way|picked up|delivery partner|track your (?:order|package|shipment)|package|parcel|shipment)\b""",
    )
    private val DELIVERY_BRANDS = linkedMapOf(
      "amazon" to "Amazon", "flipkart" to "Flipkart", "swiggy" to "Swiggy", "zomato" to "Zomato",
      "blinkit" to "Blinkit", "zepto" to "Zepto", "myntra" to "Myntra", "bigbasket" to "BigBasket",
      "meesho" to "Meesho", "ajio" to "AJIO", "nykaa" to "Nykaa", "delhivery" to "Delhivery", "bluedart" to "Blue Dart",
    )

    private val TRAVEL_STRONG = Regex("""\b(pnr|flight|boarding|web check-?in|departure|e-?ticket|train no|coach|berth|gate \w+)\b""")
    private val TRAVEL_BRANDS = linkedMapOf(
      "indigo" to "IndiGo", "irctc" to "IRCTC", "air india" to "Air India", "vistara" to "Vistara",
      "akasa" to "Akasa Air", "spicejet" to "SpiceJet", "makemytrip" to "MakeMyTrip", "redbus" to "redBus",
      "uber" to "Uber", "ola" to "Ola", "goibibo" to "Goibibo", "cleartrip" to "Cleartrip",
    )
    private val PNR = Regex("""(?i)\bPNR\s*(?:no\.?|number)?\s*[:\-]?\s*([A-Z0-9]{6,10})\b""")

    private val SCHEDULE_KEYWORDS = Regex("""\b(meeting|reminder|remind|appointment|scheduled|call at|webinar|standup|stand-up|interview|invite|rsvp|event at)\b""")
    private val PROMO_KEYWORDS = Regex("""\b(offer|sale|discount|cashback upto|flat \d+% off|coupon|limited time|t&c apply|win)\b""")
  }
}
