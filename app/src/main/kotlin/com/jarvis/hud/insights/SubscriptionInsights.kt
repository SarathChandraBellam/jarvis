package com.jarvis.hud.insights

import com.jarvis.hud.model.BillingCadence
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TrackedSubscription
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Extracts and summarizes recurring subscriptions & plans from SMS/notifications
 * while ignoring one-off daily spends (meals, cabs, groceries, shopping).
 */
data class SubscriptionInsights(
  val today: LocalDate,
  val active: List<TrackedSubscription>,
  val paused: List<TrackedSubscription>,
  val monthlyBurn: Double,
  val yearlyBurn: Double,
  val upcoming7Days: List<TrackedSubscription>,
  val trials: List<TrackedSubscription>,
  val categories: List<Share<SubscriptionCategory>>,
) {
  val all: List<TrackedSubscription> get() = active + paused

  companion object {
    fun build(
      items: List<OrganizedItem>,
      customSubscriptions: List<TrackedSubscription>,
      pausedIds: Set<String>,
      deletedIds: Set<String>,
      now: Long = System.currentTimeMillis(),
      zone: ZoneId = ZoneId.systemDefault(),
    ): SubscriptionInsights {
      val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
      val detected = detectFromItems(items, today, zone)

      // Merge auto-detected and user-added subscriptions; custom overrides detected with same id/name.
      val byKey = linkedMapOf<String, TrackedSubscription>()
      detected.forEach { s -> byKey[s.name.lowercase()] = s }
      customSubscriptions.forEach { s -> byKey[s.name.lowercase()] = s }

      val resolved = byKey.values
        .filterNot { it.id in deletedIds }
        .map { sub -> if (sub.id in pausedIds) sub.copy(isPaused = true) else sub }
        .sortedWith(compareBy<TrackedSubscription>({ it.isPaused }, { daysUntil(it.nextRenewalDate, today) ?: 999L }, { -it.monthlyEquivalent }))

      val active = resolved.filterNot { it.isPaused }
      val paused = resolved.filter { it.isPaused }
      val monthlyBurn = active.sumOf { it.monthlyEquivalent }
      val upcoming = active.filter {
        val d = daysUntil(it.nextRenewalDate, today)
        d != null && d in 0..7
      }
      val trials = active.filter { it.isTrial }

      val byCat = active.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.monthlyEquivalent } }
      val totalCat = byCat.values.sum().takeIf { it > 0.0 } ?: 1.0
      val shares = byCat.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .map { (cat, amt) -> Share(cat, amt, (amt / totalCat).toFloat()) }

      return SubscriptionInsights(
        today = today,
        active = active,
        paused = paused,
        monthlyBurn = monthlyBurn,
        yearlyBurn = monthlyBurn * 12.0,
        upcoming7Days = upcoming,
        trials = trials,
        categories = shares,
      )
    }

    internal fun detectFromItems(
      items: List<OrganizedItem>,
      today: LocalDate,
      zone: ZoneId = ZoneId.systemDefault(),
    ): List<TrackedSubscription> {
      val activeMoneyOrBills = items.filter {
        it.status != ItemStatus.DISMISSED &&
          (it.category == IntelCategory.FINANCE || it.category == IntelCategory.BILL) &&
          !it.isCredit &&
          (it.amount ?: 0.0) > 0.0
      }

      fun dateOf(item: OrganizedItem): LocalDate =
        Instant.ofEpochMilli(item.timestamp).atZone(zone).toLocalDate()

      val result = linkedMapOf<String, TrackedSubscription>()

      // 1. Multi-month identical-amount debits (excluding unknown merchants).
      val debits = activeMoneyOrBills.filter { it.category == IntelCategory.FINANCE }
      debits.groupBy { SpendInsights.displayMerchant(it) }
        .filterKeys { it != SpendInsights.UNKNOWN_MERCHANT }
        .forEach { (name, list) ->
          val clusters = mutableListOf<MutableList<OrganizedItem>>()
          list.sortedBy { it.timestamp }.forEach { item ->
            val amt = item.amount!!
            val cluster = clusters.firstOrNull { cl -> abs(cl.first().amount!! - amt) <= cl.first().amount!! * 0.03 }
            if (cluster != null) cluster += item else clusters += mutableListOf(item)
          }
          clusters.forEach { cl ->
            val months = cl.map { YearMonth.from(dateOf(it)) }.toSet().size
            if (months >= 2) {
              val latest = cl.last()
              val lastDate = dateOf(latest)
              val next = nextMonthlyDate(lastDate, today)
              val cat = categorizeSubscription(name, latest)
              val cadence = inferCadence(latest, cl)
              result[name.lowercase()] = TrackedSubscription(
                id = "auto-" + slug(name),
                name = name,
                amount = latest.amount!!,
                currency = latest.currency,
                cadence = cadence,
                category = cat,
                nextRenewalDate = next.toString(),
                isTrial = isTrialText(latest),
                isAutoDetected = true,
                sourceNote = "Detected across $months months",
              )
            }
          }
        }

      // 2. Single-message explicit subscription / autopay / OTT / telecom plan signals.
      activeMoneyOrBills.sortedByDescending { it.timestamp }.forEach { item ->
        val text = listOfNotNull(item.merchant, item.title, item.summary, item.sender).joinToString(" ").lowercase()
        val matchedBrand = SUBSCRIPTION_BRANDS.entries.firstOrNull { (k, _) -> word(k).containsMatchIn(text) }
        val hasSubSignal = SUBSCRIPTION_SIGNALS.containsMatchIn(text)
        if (matchedBrand != null || hasSubSignal) {
          val serviceName = matchedBrand?.value
            ?: item.merchant?.takeIf { it.isNotBlank() }?.let { prettyName(it) }
            ?: SpendInsights.billerName(item)
          val key = serviceName.lowercase()
          if (key !in result && !NON_SUB_MERCHANTS.containsMatchIn(key)) {
            val due = item.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
              ?: nextMonthlyDate(dateOf(item), today)
            result[key] = TrackedSubscription(
              id = "auto-" + slug(serviceName),
              name = serviceName,
              amount = item.amount!!,
              currency = item.currency,
              cadence = inferCadence(item, emptyList()),
              category = categorizeSubscription(serviceName, item),
              nextRenewalDate = due.toString(),
              isTrial = isTrialText(item),
              isAutoDetected = true,
              sourceNote = if (hasSubSignal) "Auto-pay / subscription alert" else "Detected from ${item.source.label}",
            )
          }
        }
      }

      return result.values.toList()
    }

    internal fun daysUntil(isoDate: String?, today: LocalDate): Long? {
      val d = isoDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
      return ChronoUnit.DAYS.between(today, d)
    }

    private fun nextMonthlyDate(last: LocalDate, today: LocalDate): LocalDate {
      var candidate = last.plusMonths(1)
      var guard = 0
      while (candidate.isBefore(today) && guard < 12) {
        candidate = candidate.plusMonths(1)
        guard++
      }
      return candidate
    }

    private fun inferCadence(item: OrganizedItem, history: List<OrganizedItem>): BillingCadence {
      val text = listOfNotNull(item.title, item.summary).joinToString(" ").lowercase()
      return when {
        Regex("""\b(annual|yearly|per year|/yr|12 months)\b""").containsMatchIn(text) -> BillingCadence.YEARLY
        Regex("""\b(quarterly|3 months)\b""").containsMatchIn(text) -> BillingCadence.QUARTERLY
        Regex("""\b(weekly|per week|/wk)\b""").containsMatchIn(text) -> BillingCadence.WEEKLY
        history.size >= 2 && (item.amount ?: 0.0) >= 3500.0 && Regex("""\b(prime|icloud|apple|google one|github|adobe|annual)\b""").containsMatchIn(text) -> BillingCadence.YEARLY
        else -> BillingCadence.MONTHLY
      }
    }

    private fun isTrialText(item: OrganizedItem): Boolean {
      val text = listOfNotNull(item.title, item.summary).joinToString(" ").lowercase()
      return Regex("""\b(free trial|trial ends|trial period|introductory)\b""").containsMatchIn(text)
    }

    internal fun categorizeSubscription(name: String, item: OrganizedItem?): SubscriptionCategory {
      val text = (name + " " + listOfNotNull(item?.title, item?.summary).joinToString(" ")).lowercase()
      return when {
        STREAMING_KW.containsMatchIn(text) -> SubscriptionCategory.STREAMING
        SOFTWARE_KW.containsMatchIn(text) -> SubscriptionCategory.SOFTWARE
        TELECOM_KW.containsMatchIn(text) -> SubscriptionCategory.TELECOM
        FITNESS_KW.containsMatchIn(text) -> SubscriptionCategory.FITNESS
        else -> SubscriptionCategory.UTILITIES
      }
    }

    private fun prettyName(raw: String): String =
      raw.trim().split(Regex("""\s+""")).joinToString(" ") { w ->
        w.lowercase().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
      }

    private fun slug(s: String) = s.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')

    private fun kw(vararg words: String) =
      Regex("""(?<![a-z0-9])(?:${words.joinToString("|") { Regex.escape(it) }})(?![a-z0-9])""")

    private fun word(w: String) = kw(w)

    private val SUBSCRIPTION_SIGNALS = kw(
      "subscription", "subscribed", "autopay", "auto-pay", "auto debit", "auto-debit",
      "e-mandate", "mandate", "upi mandate", "recurring", "renews", "renewal",
      "membership", "free trial", "trial ends", "ott", "postpaid", "broadband", "fiber", "fibernet",
    )

    private val NON_SUB_MERCHANTS = kw("swiggy", "zomato", "uber", "ola", "zepto", "blinkit", "bigbasket", "croma", "flipkart", "dmart", "apollo")

    private val SUBSCRIPTION_BRANDS = linkedMapOf(
      "netflix" to "Netflix",
      "spotify" to "Spotify",
      "youtube premium" to "YouTube Premium",
      "youtube" to "YouTube Premium",
      "amazon prime" to "Amazon Prime",
      "prime video" to "Amazon Prime",
      "hotstar" to "Disney+ Hotstar",
      "disney+" to "Disney+ Hotstar",
      "sonyliv" to "SonyLIV",
      "zee5" to "ZEE5",
      "apple one" to "Apple One",
      "apple music" to "Apple Music",
      "icloud" to "iCloud+",
      "google one" to "Google One",
      "chatgpt" to "ChatGPT Plus",
      "openai" to "OpenAI",
      "claude" to "Claude Pro",
      "anthropic" to "Claude Pro",
      "cursor" to "Cursor",
      "github" to "GitHub",
      "adobe" to "Adobe Creative",
      "notion" to "Notion",
      "figma" to "Figma",
      "1password" to "1Password",
      "dropbox" to "Dropbox",
      "microsoft 365" to "Microsoft 365",
      "jiofiber" to "JioFiber",
      "jio fiber" to "JioFiber",
      "jio" to "Jio",
      "airtel" to "Airtel",
      "act fibernet" to "ACT Fibernet",
      "cult.fit" to "cult.fit",
      "cultfit" to "cult.fit",
      "swiggy one" to "Swiggy One",
      "zomato gold" to "Zomato Gold",
      "playstation" to "PlayStation Plus",
      "xbox" to "Xbox Game Pass",
    )

    private val STREAMING_KW = kw("netflix", "spotify", "youtube", "prime", "hotstar", "disney", "sonyliv", "zee5", "apple music", "playstation", "xbox")
    private val SOFTWARE_KW = kw("chatgpt", "openai", "claude", "anthropic", "cursor", "github", "icloud", "google one", "adobe", "notion", "figma", "1password", "dropbox", "microsoft", "apple one")
    private val TELECOM_KW = kw("jio", "jiofiber", "airtel", "vi", "vodafone", "act fibernet", "broadband", "postpaid", "fiber", "wifi", "mobile")
    private val FITNESS_KW = kw("cult", "gym", "fitness", "yoga", "swiggy one", "zomato gold", "membership", "club")
  }
}
