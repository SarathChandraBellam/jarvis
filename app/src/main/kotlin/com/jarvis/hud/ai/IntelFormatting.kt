package com.jarvis.hud.ai

import kotlin.math.abs
import kotlin.math.roundToLong

/** Formatting helpers shared by the parser, briefing generator and assistant. */
object IntelFormatting {

  /** Formats an amount with Indian digit grouping, e.g. 125000.5 -> "₹1,25,000.50". */
  fun inr(amount: Double, symbol: String = "₹"): String {
    val negative = amount < 0
    val paise = (abs(amount) * 100).roundToLong()
    val rupees = paise / 100
    val fraction = paise % 100
    val digits = rupees.toString()
    val grouped = if (digits.length <= 3) {
      digits
    } else {
      val last3 = digits.takeLast(3)
      val rest = digits.dropLast(3)
      rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }
    val frac = if (fraction == 0L) "" else "." + fraction.toString().padStart(2, '0')
    return (if (negative) "-" else "") + symbol + grouped + frac
  }

  fun truncate(text: String, max: Int): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
  }
}
