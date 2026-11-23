package com.jarvis.hud.insights

import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

/** Where money goes, inferred from merchant / message text. */
enum class SpendCategory(val label: String) {
  FOOD("Food & dining"),
  GROCERIES("Groceries"),
  SHOPPING("Shopping"),
  TRANSPORT("Travel & transport"),
  FUEL("Fuel"),
  BILLS("Bills & utilities"),
  ENTERTAINMENT("Entertainment"),
  HEALTH("Health"),
  TRANSFERS("People & transfers"),
  OTHER("Other"),
}

/** Kind of money coming in. */
enum class IncomeKind(val label: String) {
  SALARY("Salary"),
  REFUND("Refunds & cashback"),
  OTHER("Other credits"),
}

/** Part of the day a payment happened in. */
enum class DayPart(val label: String) {
  MORNING("Morning"),
  AFTERNOON("Afternoon"),
  EVENING("Evening"),
  NIGHT("Night"),
  ;

  companion object {
    /** 05–11 morning, 12–16 afternoon, 17–21 evening, 22–04 night. */
    fun of(hour: Int): DayPart = when (hour) {
      in 5..11 -> MORNING
      in 12..16 -> AFTERNOON
      in 17..21 -> EVENING
      else -> NIGHT
    }
  }
}

data class Share<K>(val key: K, val amount: Double, val fraction: Float)

data class MerchantSpend(val name: String, val amount: Double, val count: Int)

data class DaySpend(val date: LocalDate, val amount: Double)

data class Recurring(val name: String, val amount: Double, val months: Int, val lastSeen: LocalDate)

/** Everything the Finance tab shows, computed once from organized items. */
data class SpendInsights(
  val month: YearMonth,
  val today: LocalDate,
  val monthLabel: String,
  val monthSpent: Double,
  /** Spend over the same days of last month (1st … today's day-of-month). */
  val lastMonthSameSpan: Double,
  /** +0.12 = 12% more than last month at this point; null when there is nothing to compare. */
  val monthDelta: Double?,
  val dailyAverage: Double,
  val projectedMonth: Double,
  val monthReceived: Double,
  val monthTransactions: Int,
  val largest: OrganizedItem?,
  val categories: List<Share<SpendCategory>>,
  val merchants: List<MerchantSpend>,
  val last7Days: List<DaySpend>,
  val todaySpent: Double,
  val dayParts: List<Share<DayPart>>,
  /** Average spend per weekday vs per weekend day over the last 28 days. */
  val weekdayAverage: Double,
  val weekendAverage: Double,
  val income: List<Share<IncomeKind>>,
  val recurring: List<Recurring>,
  /** Spend per day of this month, index 0 = the 1st, through today. */
  val monthDays: List<Double>,
  /** Spend per day of last month, every day of that month. */
  val lastMonthDays: List<Double>,
) {
  val hasData: Boolean get() = monthSpent > 0 || monthReceived > 0 || last7Days.any { it.amount > 0 }

  companion object {
    private const val PATTERN_DAYS = 28L
    private const val RECURRING_LOOKBACK_DAYS = 100L

    fun from(items: List<OrganizedItem>, now: Long, zone: ZoneId = ZoneId.systemDefault()): SpendInsights {
      val money = items.filter {
        it.category == IntelCategory.FINANCE && it.status != ItemStatus.DISMISSED && (it.amount ?: 0.0) > 0.0
      }
      val nowZ = Instant.ofEpochMilli(now).atZone(zone)
      val today = nowZ.toLocalDate()
      fun dateOf(i: OrganizedItem): LocalDate = Instant.ofEpochMilli(i.timestamp).atZone(zone).toLocalDate()
      fun timeOf(i: OrganizedItem): ZonedDateTime = Instant.ofEpochMilli(i.timestamp).atZone(zone)

      val debits = money.filter { !it.isCredit && dateOf(it) <= today }
      val credits = money.filter { it.isCredit && dateOf(it) <= today }

      val thisMonth = YearMonth.from(today)
      val monthStart = thisMonth.atDay(1)
      val monthDebits = debits.filter { !dateOf(it).isBefore(monthStart) }
      val monthSpent = monthDebits.sumOf { it.amount!! }

      val lastMonth = thisMonth.minusMonths(1)
      val lastSpanEnd = lastMonth.atDay(minOf(today.dayOfMonth, lastMonth.lengthOfMonth()))
      val lastSpan = debits.filter { d -> dateOf(d).let { !it.isBefore(lastMonth.atDay(1)) && !it.isAfter(lastSpanEnd) } }
        .sumOf { it.amount!! }
      val delta = if (lastSpan > 0) (monthSpent - lastSpan) / lastSpan else null

      val daily = monthSpent / today.dayOfMonth
      val categories = shares(monthDebits.groupBy { categorize(it) }.mapValues { (_, v) -> v.sumOf { it.amount!! } })

      val merchants = monthDebits
        .groupBy { displayMerchant(it) }
        .map { (name, v) -> MerchantSpend(name, v.sumOf { it.amount!! }, v.size) }
        .sortedByDescending { it.amount }
        .take(5)

      val last7 = (6L downTo 0L).map { ago ->
        val d = today.minusDays(ago)
        DaySpend(d, debits.filter { dateOf(it) == d }.sumOf { it.amount!! })
      }

      val patternStart = today.minusDays(PATTERN_DAYS - 1)
      val recent = debits.filter { !dateOf(it).isBefore(patternStart) }
      val dayParts = shares(
        recent.groupBy { DayPart.of(timeOf(it).hour) }.mapValues { (_, v) -> v.sumOf { it.amount!! } },
        order = DayPart.entries,
      )
      val spanDays = (0 until PATTERN_DAYS).map { patternStart.plusDays(it) }
      val weekendDays = spanDays.count { it.isWeekend() }.coerceAtLeast(1)
      val weekdayDays = (spanDays.size - spanDays.count { it.isWeekend() }).coerceAtLeast(1)
      val weekendSpend = recent.filter { dateOf(it).isWeekend() }.sumOf { it.amount!! }
      val weekdaySpend = recent.filterNot { dateOf(it).isWeekend() }.sumOf { it.amount!! }

      val monthCredits = credits.filter { !dateOf(it).isBefore(monthStart) }
      val income = shares(
        monthCredits.groupBy { incomeKind(it) }.mapValues { (_, v) -> v.sumOf { it.amount!! } },
        order = IncomeKind.entries,
      )

      val byDate = debits.groupBy { dateOf(it) }.mapValues { (_, v) -> v.sumOf { it.amount!! } }
      return SpendInsights(
        month = thisMonth,
        today = today,
        monthLabel = thisMonth.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
        monthSpent = monthSpent,
        lastMonthSameSpan = lastSpan,
        monthDelta = delta,
        dailyAverage = daily,
        projectedMonth = daily * thisMonth.lengthOfMonth(),
        monthReceived = monthCredits.sumOf { it.amount!! },
        monthTransactions = monthDebits.size,
        largest = monthDebits.maxByOrNull { it.amount!! },
        categories = categories,
        merchants = merchants,
        last7Days = last7,
        todaySpent = last7.last().amount,
        dayParts = dayParts,
        weekdayAverage = weekdaySpend / weekdayDays,
        weekendAverage = weekendSpend / weekendDays,
        income = income,
        recurring = recurring(debits.filter { !dateOf(it).isBefore(today.minusDays(RECURRING_LOOKBACK_DAYS)) }, ::dateOf),
        monthDays = (1..today.dayOfMonth).map { byDate[thisMonth.atDay(it)] ?: 0.0 },
        lastMonthDays = (1..lastMonth.lengthOfMonth()).map { byDate[lastMonth.atDay(it)] ?: 0.0 },
      )
    }

    /**
     * A payment is recurring when the same merchant charges (about) the same amount in at least
     * two different calendar months — subscriptions, rent, EMIs, plans.
     */
    internal fun recurring(debits: List<OrganizedItem>, dateOf: (OrganizedItem) -> LocalDate): List<Recurring> =
      debits.groupBy { displayMerchant(it) }
        .filterKeys { it != UNKNOWN_MERCHANT }
        .flatMap { (name, list) ->
          val clusters = mutableListOf<MutableList<OrganizedItem>>()
          list.sortedBy { it.timestamp }.forEach { item ->
            val c = clusters.firstOrNull { cl -> abs(cl.first().amount!! - item.amount!!) <= cl.first().amount!! * 0.02 }
            if (c != null) c += item else clusters += mutableListOf(item)
          }
          clusters.mapNotNull { cl ->
            val months = cl.map { YearMonth.from(dateOf(it)) }.toSet().size
            if (months < 2) null else Recurring(name, cl.last().amount!!, months, dateOf(cl.last()))
          }
        }
        .sortedByDescending { it.amount }

    private fun <K> shares(totals: Map<K, Double>, order: List<K>? = null): List<Share<K>> {
      val sum = totals.values.sum()
      if (sum <= 0) return emptyList()
      val keys = order?.filter { (totals[it] ?: 0.0) > 0 } ?: totals.entries.sortedByDescending { it.value }.map { it.key }
      return keys.map { k -> Share(k, totals.getValue(k), (totals.getValue(k) / sum).toFloat()) }
    }

    private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    // ---------------------------------------------------------------- classification

    const val UNKNOWN_MERCHANT = "Unknown"

    private fun haystack(i: OrganizedItem) = listOfNotNull(i.merchant, i.title, i.summary).joinToString(" ").lowercase()

    fun categorize(item: OrganizedItem): SpendCategory {
      val text = haystack(item)
      val merchant = item.merchant?.lowercase().orEmpty()
      // Brand / merchant keywords first (merchant name is the strongest signal), then the message.
      for (source in listOf(merchant, text)) {
        if (source.isBlank()) continue
        CATEGORY_KEYWORDS.firstOrNull { (_, re) -> re.containsMatchIn(source) }?.let { return it.first }
      }
      if (TRANSFER.containsMatchIn(text)) return SpendCategory.TRANSFERS
      return SpendCategory.OTHER
    }

    fun incomeKind(item: OrganizedItem): IncomeKind {
      val text = haystack(item)
      return when {
        SALARY.containsMatchIn(text) -> IncomeKind.SALARY
        REFUND.containsMatchIn(text) -> IncomeKind.REFUND
        else -> IncomeKind.OTHER
      }
    }

    /** Who a bill is from: a known biller named in the message, else the tidied sender. */
    fun billerName(item: OrganizedItem): String {
      val text = listOfNotNull(item.title, item.summary, item.merchant, item.sender).joinToString(" ").lowercase()
      BILLERS.entries.firstOrNull { (k, _) -> word(k).containsMatchIn(text) }?.let { return it.value }
      return item.merchant?.takeIf { it.isNotBlank() }?.let { m ->
        m.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
      } ?: "Bill"
    }

    private val BILLERS = linkedMapOf(
      "sbi card" to "SBI Card", "sbicrd" to "SBI Card", "hdfc bank credit card" to "HDFC Credit Card",
      "icici bank credit card" to "ICICI Credit Card", "axis bank credit card" to "Axis Credit Card",
      "airtel" to "Airtel", "jio" to "Jio", "vodafone" to "Vi", "bescom" to "BESCOM", "tneb" to "TNEB",
      "msedcl" to "MSEDCL", "tata power" to "Tata Power", "adani electricity" to "Adani Electricity",
      "act fibernet" to "ACT Fibernet", "lic" to "LIC", "loan a/c" to "Loan EMI", "emi" to "Loan EMI",
      "hdfcbk" to "HDFC Bank", "icicib" to "ICICI Bank",
    )

    /** Human merchant name: known brand, else a tidied UPI handle or title-cased name. */
    fun displayMerchant(item: OrganizedItem): String {
      val raw = item.merchant?.trim().orEmpty()
      val probe = (raw.ifBlank { item.title }).lowercase()
      BRANDS.entries.firstOrNull { (k, _) -> word(k).containsMatchIn(probe) }?.let { return it.value }
      if (raw.isBlank()) return UNKNOWN_MERCHANT
      val base = if ('@' in raw) raw.substringBefore('@').replace(Regex("""[._\-\d]+"""), " ").trim() else raw
      if (base.isBlank()) return UNKNOWN_MERCHANT
      return base.split(Regex("""\s+""")).joinToString(" ") { w ->
        if (w.length <= 3 && w.all(Char::isUpperCase)) w else w.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
      }
    }

    /** Whole-word, case-insensitive-by-construction (callers lowercase) alternation. */
    private fun kw(vararg words: String) =
      Regex("""(?<![a-z0-9])(?:${words.joinToString("|") { Regex.escape(it) }})(?![a-z0-9])""")

    private fun word(w: String) = kw(w)

    private val CATEGORY_KEYWORDS: List<Pair<SpendCategory, Regex>> = listOf(
      SpendCategory.GROCERIES to kw("instamart", "blinkit", "zepto", "bigbasket", "dmart", "d-mart", "kirana", "grocery", "groceries", "supermarket", "jiomart", "more retail", "reliance fresh", "ratnadeep", "nature's basket"),
      SpendCategory.FOOD to kw("swiggy", "zomato", "eatsure", "domino's", "dominos", "mcdonald's", "mcdonalds", "kfc", "starbucks", "cafe", "restaurant", "dining", "biryani", "pizza", "chaayos", "third wave", "burger king", "food"),
      SpendCategory.FUEL to kw("hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum", "hindustan petroleum", "shell", "petrol", "fuel", "filling station"),
      SpendCategory.TRANSPORT to kw("uber", "ola", "rapido", "irctc", "indigo", "air india", "vistara", "akasa", "spicejet", "makemytrip", "goibibo", "cleartrip", "redbus", "metro", "fastag", "namma yatri"),
      SpendCategory.BILLS to kw("airtel", "jio", "jio fiber", "jiofiber", "vodafone", "bescom", "tneb", "msedcl", "tata power", "adani electricity", "bses", "electricity", "broadband", "act fibernet", "postpaid", "recharge", "insurance", "lic of india", "rent", "emi", "gas", "piped gas", "water board", "maintenance"),
      SpendCategory.ENTERTAINMENT to kw("netflix", "hotstar", "prime video", "spotify", "youtube premium", "bookmyshow", "pvr", "inox", "sonyliv", "zee5", "steam", "playstation"),
      SpendCategory.HEALTH to kw("apollo", "pharmeasy", "1mg", "netmeds", "medplus", "pharmacy", "hospital", "clinic", "diagnostic", "cult.fit", "cultfit", "practo", "pharmeasy"),
      SpendCategory.SHOPPING to kw("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "tata cliq", "croma", "reliance digital", "decathlon", "ikea", "h&m", "zara", "lenskart", "uniqlo"),
    )

    private val TRANSFER = kw("sent to", "transferred to", "transfer to", "imps", "neft", "rtgs", "p2p")
    private val SALARY = kw("salary", "payroll", "stipend")
    private val REFUND = kw("refund", "refunded", "cashback", "reversed", "reversal")

    private val BRANDS = linkedMapOf(
      "swiggy instamart" to "Swiggy Instamart", "instamart" to "Swiggy Instamart", "swiggy" to "Swiggy",
      "zomato" to "Zomato", "blinkit" to "Blinkit", "zepto" to "Zepto", "bigbasket" to "BigBasket",
      "amazon" to "Amazon", "amzn" to "Amazon", "amazon pay" to "Amazon", "flipkart" to "Flipkart", "myntra" to "Myntra", "ajio" to "AJIO",
      "nykaa" to "Nykaa", "uber" to "Uber", "ola" to "Ola", "rapido" to "Rapido", "irctc" to "IRCTC",
      "indigo" to "IndiGo", "netflix" to "Netflix", "spotify" to "Spotify", "hotstar" to "Disney+ Hotstar",
      "bookmyshow" to "BookMyShow", "airtel" to "Airtel", "jiofiber" to "Jio", "jio" to "Jio", "bescom" to "BESCOM",
      "hpcl" to "HPCL", "bpcl" to "BPCL", "indian oil" to "Indian Oil", "iocl" to "Indian Oil",
      "apollo" to "Apollo", "dmart" to "DMart", "starbucks" to "Starbucks", "cult.fit" to "cult.fit",
    )
  }
}
