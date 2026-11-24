package com.jarvis.hud.ui.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.DayPart
import com.jarvis.hud.insights.Share
import com.jarvis.hud.insights.SpendCategory
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BaselineRangeBar
import com.jarvis.hud.ui.viz.BentoCard
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the month's money went: a continuous multi-segment phase ring with centered editorial
 * readout + interactive category rows that filter the ledger below.
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
  val totalAmount = shares.sumOf { it.amount }
  val activeShare = shares.firstOrNull { it.key == selected }

  val ringParts = top.map { Triple(it.key as SpendCategory?, it.fraction, Fin.color(it.key)) } +
    if (restFraction > 0.01f) listOf(Triple(null, restFraction, Fin.Neutral)) else emptyList()

  BentoCard(padding = 18.dp) {
    Box(
      Modifier.fillMaxWidth().height(210.dp),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(
        Modifier
          .size(196.dp)
          .semantics {
            contentDescription = activeShare?.let {
              "${it.key.label}: ${rupees(it.amount)}, ${(it.fraction * 100).roundToInt()}%"
            } ?: "Category breakdown total ${rupees(totalAmount)}"
          },
      ) {
        val sw = 18.dp.toPx()
        val pad = sw / 2f + 6.dp.toPx()
        val arcSz = Size(size.width - pad * 2, size.height - pad * 2)
        val topLeft = Offset(pad, pad)
        val gapDeg = if (ringParts.size > 1) 6f else 0f
        val availableDeg = 360f - gapDeg * ringParts.size
        var angle = -90f

        ringParts.forEach { (cat, frac, color) ->
          val sweep = (availableDeg * frac).coerceAtLeast(4f)
          val isSelected = selected == null || cat == selected
          val strokeColor = if (isSelected) color else color.copy(alpha = 0.22f)
          drawArc(
            color = strokeColor,
            startAngle = angle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSz,
            style = Stroke(sw, cap = StrokeCap.Round),
          )
          if (selected != null && cat == selected) {
            val midRad = Math.toRadians((angle + sweep / 2f).toDouble())
            val r = arcSz.width / 2f
            val dot = Offset(center.x + (r * cos(midRad)).toFloat(), center.y + (r * sin(midRad)).toFloat())
            drawCircle(HudColors.Background, radius = 6.dp.toPx(), center = dot)
            drawCircle(HudColors.Cream, radius = 3.8.dp.toPx(), center = dot)
          }
          angle += sweep + gapDeg
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 42.dp)) {
        Text(
          activeShare?.key?.label ?: "Total spent",
          style = Fin.Tiny,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          rupees(activeShare?.amount ?: totalAmount),
          style = JarvisType.TitleSerif.copy(color = Fin.Ink),
          maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          activeShare?.let { "${(it.fraction * 100).roundToInt()}% of month" } ?: "${shares.size} categories",
          style = Fin.Small.copy(color = activeShare?.let { Fin.color(it.key) } ?: Fin.Ink2),
          maxLines = 1,
        )
      }
    }

    Spacer(Modifier.height(12.dp))
    shares.forEach { sh ->
      val on = sh.key == selected
      val c = Fin.color(sh.key)
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(if (on) Fin.Raised else Color.Transparent)
          .clickable(role = Role.Button) { onSelect(if (on) null else sh.key) }
          .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(10.dp))
        Text(sh.key.label, style = Fin.Body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${(sh.fraction * 100).roundToInt()}%", style = Fin.Small, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
        Text(rupees(sh.amount), style = Fin.BodyStrong, modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
      }
    }
  }
}

/**
 * Day-part spending vs an even 25% typical baseline tick (Morning, Afternoon, Evening, Night).
 */
@Composable
internal fun DayPartBreakdown(shares: List<Share<DayPart>>, modifier: Modifier = Modifier) {
  if (shares.isEmpty()) return
  val colors = mapOf(
    DayPart.MORNING to HudColors.Amber,
    DayPart.AFTERNOON to HudColors.Peach,
    DayPart.EVENING to HudColors.Sky,
    DayPart.NIGHT to HudColors.Lavender,
  )
  BentoCard(modifier = modifier, padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("Time of day vs even split", style = Fin.BodyStrong, modifier = Modifier.weight(1f))
      Text("Dashed tick = 25%", style = Fin.Tiny)
    }
    Spacer(Modifier.height(14.dp))
    shares.forEachIndexed { idx, sh ->
      if (idx > 0) Spacer(Modifier.height(12.dp))
      val pct = (sh.fraction * 100).roundToInt()
      val delta = pct - 25
      val deltaStr = when {
        delta > 2 -> "+$delta% vs even"
        delta < -2 -> "$delta% vs even"
        else -> "Right around typical"
      }
      BaselineRangeBar(
        label = "${sh.key.label} · $pct%",
        valueText = rupees(sh.amount),
        fraction = sh.fraction,
        typicalFraction = 0.25f,
        color = colors[sh.key] ?: HudColors.Sky,
        deltaText = deltaStr,
      )
    }
  }
}

/**
 * The month as a calm rounded calendar inside a bento card; tap any past/today cell to inspect
 * its payments. Future days have subtle outlines.
 */
@Composable
internal fun SpendCalendar(s: SpendInsights, selected: LocalDate?, onSelect: (LocalDate?) -> Unit) {
  val first = s.month.atDay(1)
  val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
  val days = s.month.lengthOfMonth()
  val max = s.monthDays.maxOrNull()?.takeIf { it > 0 } ?: 1.0
  val cells: List<Int?> = List(lead) { null } + (1..days).toList()
  val rows = cells.chunked(7)

  BentoCard(padding = 16.dp) {
    Row(Modifier.fillMaxWidth()) {
      listOf("M", "T", "W", "T", "F", "S", "S").forEach {
        Text(it, style = Fin.Tiny, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
      }
    }
    Spacer(Modifier.height(8.dp))
    rows.forEach { week ->
      Row(Modifier.fillMaxWidth()) {
        (0 until 7).forEach { col ->
          val day = week.getOrNull(col)
          Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
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
                  .clip(CircleShape)
                  .background(fill)
                  .then(
                    when {
                      isSel -> Modifier.border(2.dp, HudColors.Cream, CircleShape)
                      date == s.today -> Modifier.border(1.5.dp, HudColors.Amber, CircleShape)
                      future -> Modifier.border(1.dp, Fin.Hairline, CircleShape)
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
                      step >= 3 -> HudColors.OnCyan
                      step >= 0 -> Fin.Ink
                      else -> Fin.Ink2
                    },
                    fontWeight = if (date == s.today || isSel) FontWeight.Bold else FontWeight.Medium,
                  ),
                )
              }
            }
          }
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
      Text("Less", style = Fin.Tiny)
      Spacer(Modifier.width(6.dp))
      (listOf(Fin.Track) + Fin.Heat).forEach {
        Box(Modifier.padding(horizontal = 2.dp).size(10.dp).clip(CircleShape).background(it))
      }
      Spacer(Modifier.width(6.dp))
      Text("More", style = Fin.Tiny)
    }
    val note = weekNote(s)
    if (note.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      Text(note, style = Fin.Small)
    }
  }
}

private fun weekNote(s: SpendInsights): String {
  val wd = s.weekdayAverage
  val we = s.weekendAverage
  if (wd <= 0 && we <= 0) return ""
  return "On average ${rupees(wd)} a day on weekdays, ${rupees(we)} on weekends (last 4 weeks)."
}
