package com.jarvis.hud.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.Share
import com.jarvis.hud.insights.SpendCategory
import com.jarvis.hud.insights.SpendInsights
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Where the month's money went: one tall segmented bar (top 5 categories + "everything else")
 * and a tappable legend that filters the ledger below. Identity is never colour alone: every
 * part is named in the legend with its amount and share.
 */
@Composable
internal fun CategoryBreakdown(
  shares: List<Share<SpendCategory>>,
  selected: SpendCategory?,
  onSelect: (SpendCategory?) -> Unit,
) {
  if (shares.isEmpty()) return
  val top = shares.take(5)
  val rest = shares.drop(5)
  val restFraction = rest.sumOf { it.fraction.toDouble() }.toFloat()

  // Tall segmented bar: 28dp, 2dp gaps, share printed inside wide-enough parts.
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val total = maxWidth
    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      val parts = top.map { Triple(it.key.label, it.fraction, Fin.color(it.key)) } +
        if (restFraction > 0f) listOf(Triple("Everything else", restFraction, Fin.Neutral)) else emptyList()
      parts.forEachIndexed { i, (_, f, c) ->
        val dim = selected != null && top.getOrNull(i)?.key != selected
        Box(
          Modifier.weight(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (dim) c.copy(alpha = 0.25f) else c),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (total * f >= 40.dp) {
            Text(
              "${(f * 100).roundToInt()}%",
              style = Fin.Tiny.copy(color = Color(0xFF0B0F14), fontWeight = FontWeight.SemiBold),
              modifier = Modifier.padding(start = 6.dp),
            )
          }
        }
      }
    }
  }
  Spacer(Modifier.height(12.dp))
  shares.forEach { sh ->
    val on = sh.key == selected
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(if (on) Fin.Raised else Color.Transparent)
        .clickable(role = Role.Button) { onSelect(if (on) null else sh.key) }
        .padding(horizontal = 8.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Fin.color(sh.key)))
      Spacer(Modifier.width(10.dp))
      Text(sh.key.label, style = Fin.Body, modifier = Modifier.weight(1f))
      Text("${(sh.fraction * 100).roundToInt()}%", style = Fin.Small, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
      Text(rupees(sh.amount), style = Fin.Body, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
    }
  }
}

/**
 * The month as a calendar, each day shaded by how much was spent (one blue hue, dim → bright).
 * Tap a day to see its payments. Future days are outlined, not filled.
 */
@Composable
internal fun SpendCalendar(s: SpendInsights, selected: LocalDate?, onSelect: (LocalDate?) -> Unit) {
  val first = s.month.atDay(1)
  val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
  val days = s.month.lengthOfMonth()
  val max = s.monthDays.maxOrNull()?.takeIf { it > 0 } ?: 1.0
  val cells: List<Int?> = List(lead) { null } + (1..days).toList()
  val rows = cells.chunked(7)

  Row(Modifier.fillMaxWidth()) {
    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
      Text(it, style = Fin.Tiny, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
  }
  Spacer(Modifier.height(6.dp))
  rows.forEach { week ->
    Row(Modifier.fillMaxWidth()) {
      (0 until 7).forEach { col ->
        val day = week.getOrNull(col)
        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
          if (day != null) {
            val date = s.month.atDay(day)
            val future = date.isAfter(s.today)
            val v = s.monthDays.getOrNull(day - 1) ?: 0.0
            val step = if (v <= 0) -1 else (ceil(v / max * Fin.Heat.size).toInt() - 1).coerceIn(0, Fin.Heat.lastIndex)
            val fill = when {
              future -> Color.Transparent
              step < 0 -> Fin.Track
              else -> Fin.Heat[step]
            }
            val isSel = date == selected
            Box(
              Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(fill)
                .then(
                  when {
                    isSel -> Modifier.border(2.dp, Fin.Ink, RoundedCornerShape(6.dp))
                    future -> Modifier.border(1.dp, Fin.Hairline, RoundedCornerShape(6.dp))
                    else -> Modifier
                  },
                )
                .clickable(enabled = !future, role = Role.Button) { onSelect(if (isSel) null else date) }
                .semantics { contentDescription = "$day: ${if (future) "upcoming" else rupees(v)}" },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                "$day",
                style = Fin.Tiny.copy(
                  color = when {
                    future -> Fin.Ink3
                    step >= 3 -> Color(0xFF0B0F14)
                    step >= 0 -> Fin.Ink
                    else -> Fin.Ink2
                  },
                  fontWeight = if (date == s.today) FontWeight.Bold else FontWeight.Normal,
                ),
              )
            }
          }
        }
      }
    }
  }
  Spacer(Modifier.height(10.dp))
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
    Text("Less", style = Fin.Tiny)
    Spacer(Modifier.width(6.dp))
    (listOf(Fin.Track) + Fin.Heat).forEach {
      Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(it))
    }
    Spacer(Modifier.width(6.dp))
    Text("More", style = Fin.Tiny)
  }
  val note = weekNote(s)
  if (note.isNotEmpty()) {
    Spacer(Modifier.height(10.dp))
    Text(note, style = Fin.Small)
  }
}

private fun weekNote(s: SpendInsights): String {
  val wd = s.weekdayAverage
  val we = s.weekendAverage
  if (wd <= 0 && we <= 0) return ""
  return "On average ${rupees(wd)} a day on weekdays, ${rupees(we)} on weekends (last 4 weeks)."
}
