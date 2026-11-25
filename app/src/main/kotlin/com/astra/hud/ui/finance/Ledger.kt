package com.astra.hud.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astra.hud.insights.SpendInsights
import com.astra.hud.insights.SubscriptionInsights
import com.astra.hud.model.OrganizedItem
import com.astra.hud.model.TrackedSubscription
import com.astra.hud.ui.theme.HudColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val DUE_MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

internal fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
  today -> "Today"
  today.minusDays(1) -> "Yesterday"
  else -> DAY_HEADER.format(d)
}

/** One tracked subscription card row with pastel monogram, renewal countdown, and Pause/Delete controls. */
@Composable
internal fun SubscriptionRow(
  sub: TrackedSubscription,
  today: LocalDate,
  onTogglePause: () -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  val accent = if (sub.isPaused) Fin.Ink3 else Fin.subColor(sub.category)
  val days = SubscriptionInsights.daysUntil(sub.nextRenewalDate, today)
  val dueLabel = when {
    sub.isPaused -> "Paused"
    days == null -> sub.cadence.label
    days < 0 -> "Renewed recently"
    days == 0L -> "Renews today"
    days == 1L -> "Renews tomorrow"
    days <= 7 -> "Renews in $days days"
    else -> "Next ${sub.nextRenewalDate}"
  }
  val dueTone = when {
    sub.isPaused -> Fin.Ink3
    sub.isTrial -> HudColors.Blush
    days != null && days <= 3 -> HudColors.Blush
    days != null && days <= 7 -> HudColors.Sand
    else -> Fin.Ink2
  }
  val initial = sub.name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "S"

  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .padding(horizontal = 4.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(accent.copy(alpha = 0.16f)),
      contentAlignment = Alignment.Center,
    ) {
      Text(initial, style = Fin.BodyStrong.copy(color = accent))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          sub.name,
          style = Fin.BodyStrong.copy(color = if (sub.isPaused) Fin.Ink3 else Fin.Ink),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (sub.isTrial) {
          Box(
            Modifier
              .clip(CircleShape)
              .background(HudColors.BlushSoft)
              .padding(horizontal = 7.dp, vertical = 2.dp),
          ) {
            Text("Trial", style = Fin.Tiny.copy(color = HudColors.Blush, fontWeight = FontWeight.SemiBold))
          }
        }
      }
      Text(
        buildString {
          append(dueLabel)
          sub.sourceNote?.let { append(" · ").append(it) }
        },
        style = Fin.Small.copy(color = dueTone),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(
        "${rupees(sub.amount)}/${sub.cadence.shortTag()}",
        style = Fin.BodyStrong.copy(color = if (sub.isPaused) Fin.Ink3 else Fin.Ink),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          Modifier
            .clip(CircleShape)
            .background(HudColors.SurfaceHigh)
            .clickable(role = Role.Button, onClick = onTogglePause)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
          Text(
            if (sub.isPaused) "Resume" else "Pause",
            style = Fin.Tiny.copy(color = Fin.Ink, fontWeight = FontWeight.Medium),
          )
        }
        if (onDelete != null) {
          Box(
            Modifier
              .clip(CircleShape)
              .background(HudColors.BlushSoft)
              .clickable(role = Role.Button, onClick = onDelete)
              .padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            Text("Remove", style = Fin.Tiny.copy(color = HudColors.Blush, fontWeight = FontWeight.Medium))
          }
        }
      }
    }
  }
}

private fun com.astra.hud.model.BillingCadence.shortTag(): String = when (this) {
  com.astra.hud.model.BillingCadence.WEEKLY -> "wk"
  com.astra.hud.model.BillingCadence.MONTHLY -> "mo"
  com.astra.hud.model.BillingCadence.QUARTERLY -> "qtr"
  com.astra.hud.model.BillingCadence.YEARLY -> "yr"
}

/** Upcoming bill row with warm date pill, biller name, urgency, and "Mark paid" pill. */
@Composable
internal fun BillRow(item: OrganizedItem, today: LocalDate, onPaid: () -> Unit, onClick: () -> Unit) {
  val due = item.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
  val days = due?.let { ChronoUnit.DAYS.between(today, it) }
  val (when_, tone) = when {
    days == null -> "No due date" to Fin.Ink2
    days < 0 -> "Overdue by ${-days} day${if (days == -1L) "" else "s"}" to Fin.Critical
    days == 0L -> "Due today" to Fin.Critical
    days == 1L -> "Due tomorrow" to Fin.Critical
    days <= 7 -> "Due in $days days" to Fin.Warning
    else -> "Due in $days days" to Fin.Ink2
  }
  val kind = item.title.substringBefore(" · ").trim()
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 4.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      Modifier
        .width(48.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(HudColors.SandSoft)
        .border(1.dp, Fin.Hairline, RoundedCornerShape(14.dp))
        .padding(vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(due?.dayOfMonth?.toString() ?: "—", style = Fin.Figure)
      Text(due?.let { DUE_MONTH.format(it) } ?: "", style = Fin.Tiny)
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      val name = SpendInsights.billerName(item)
      Text(name, style = Fin.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (!kind.equals(name, ignoreCase = true)) Text(kind, style = Fin.Small, maxLines = 1)
      Text(when_, style = Fin.Small.copy(color = tone))
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(item.amount?.let(::rupees) ?: "", style = Fin.BodyStrong)
      Box(
        Modifier
          .clip(CircleShape)
          .background(HudColors.SageSoft)
          .clickable(role = Role.Button, onClick = onPaid)
          .padding(horizontal = 10.dp, vertical = 4.dp),
      ) {
        Text("Mark paid", style = Fin.Tiny.copy(color = HudColors.Sage, fontWeight = FontWeight.SemiBold))
      }
    }
  }
}
