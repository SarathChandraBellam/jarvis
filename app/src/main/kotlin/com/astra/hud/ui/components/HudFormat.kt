package com.astra.hud.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.astra.hud.model.IntelCategory
import com.astra.hud.model.Priority
import com.astra.hud.ui.theme.HudColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatting + mapping helpers shared across screens. */
object HudFormat {
  private val enIn: Locale = Locale.forLanguageTag("en-IN")

  /** "₹1,23,456" (en-IN grouping); shows paise only when non-integral. */
  fun money(amount: Double, currency: String = "₹"): String {
    val nf = NumberFormat.getNumberInstance(enIn).apply {
      isGroupingUsed = true
      val whole = amount == Math.floor(amount)
      minimumFractionDigits = if (whole) 0 else 2
      maximumFractionDigits = if (whole) 0 else 2
    }
    return currency + nf.format(amount)
  }

  /** Compact money for chart labels: ₹1.2K, ₹3.4L. */
  fun moneyCompact(amount: Double): String = when {
    amount >= 1_00_000 -> "₹" + String.format(Locale.US, "%.1fL", amount / 1_00_000)
    amount >= 1_000 -> "₹" + String.format(Locale.US, "%.1fK", amount / 1_000)
    else -> "₹" + amount.toLong()
  }

  fun time(millis: Long): String = SimpleDateFormat("h:mm a", Locale.UK).format(Date(millis))

  fun dayShort(millis: Long): String = SimpleDateFormat("EEE", Locale.UK).format(Date(millis))

  fun dateTime(millis: Long): String = SimpleDateFormat("d MMM, h:mm a", Locale.UK).format(Date(millis))

  fun longDate(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.UK).format(Date(millis))

  fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - millis
    val min = diff / 60_000
    return when {
      diff < 0 -> dateTime(millis)
      min < 1 -> "Just now"
      min < 60 -> "${min}m ago"
      min < 60 * 24 -> "${min / 60}h ago"
      min < 60 * 24 * 7 -> "${min / (60 * 24)}d ago"
      else -> dateTime(millis)
    }
  }

  fun duration(sec: Long): String = when {
    sec <= 0 -> "—"
    sec < 60 -> "${sec}s"
    sec < 3600 -> "${sec / 60}m ${sec % 60}s"
    else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
  }

  fun minutes(min: Long): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

  fun greeting(now: Long = System.currentTimeMillis()): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
      in 5..11 -> "Good morning"
      in 12..16 -> "Good afternoon"
      else -> "Good evening"
    }
  }

  fun startOfDay(now: Long = System.currentTimeMillis(), daysAgo: Int = 0): Long =
    Calendar.getInstance().apply {
      timeInMillis = now
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis
}

fun IntelCategory.icon(): ImageVector = when (this) {
  IntelCategory.FINANCE -> Icons.Outlined.AccountBalanceWallet
  IntelCategory.BILL -> Icons.AutoMirrored.Outlined.ReceiptLong
  IntelCategory.DELIVERY -> Icons.Outlined.LocalShipping
  IntelCategory.TRAVEL -> Icons.Outlined.Flight
  IntelCategory.ACTION -> Icons.Outlined.TaskAlt
  IntelCategory.MESSAGE -> Icons.Outlined.ChatBubbleOutline
  IntelCategory.SCHEDULE -> Icons.Outlined.Event
  IntelCategory.SYSTEM -> Icons.Outlined.Memory
}

fun IntelCategory.accent(): Color = when (this) {
  IntelCategory.FINANCE -> HudColors.Sage
  IntelCategory.BILL -> HudColors.Sand
  IntelCategory.DELIVERY -> HudColors.SlateBlue
  IntelCategory.TRAVEL -> HudColors.Lavender
  IntelCategory.ACTION -> HudColors.Peach
  IntelCategory.MESSAGE -> HudColors.TextDim
  IntelCategory.SCHEDULE -> HudColors.SlateBlue
  IntelCategory.SYSTEM -> HudColors.TextDim
}

fun Priority.color(): Color = when (this) {
  Priority.CRITICAL -> HudColors.Blush
  Priority.HIGH -> HudColors.Sand
  Priority.NORMAL -> HudColors.SlateBlue
  Priority.LOW -> HudColors.TextMuted
}

/** "Urgent" / "High" / … for display. */
fun Priority.displayLabel(): String = when (this) {
  Priority.CRITICAL -> "Urgent"
  Priority.HIGH -> "High"
  Priority.NORMAL -> "Normal"
  Priority.LOW -> "Low"
}

/** Midnight on the 1st of the month containing [now]. */
fun HudFormat.startOfMonth(now: Long = System.currentTimeMillis()): Long =
  Calendar.getInstance().apply {
    timeInMillis = HudFormat.startOfDay(now)
    set(Calendar.DAY_OF_MONTH, 1)
  }.timeInMillis
