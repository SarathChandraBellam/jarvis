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
import com.jarvis.hud.insights.Share
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.SubscriptionCategory
import com.jarvis.hud.model.TrackedSubscription
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BentoCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Continuous pastel multi-segment phase ring for Subscriptions by category,
 * modeled after @itspsneha's Cycle Ring with centered serif monthly burn readout.
 */
@Composable
internal fun SubscriptionCategoryRing(
  shares: List<Share<SubscriptionCategory>>,
  selected: SubscriptionCategory?,
  onSelect: (SubscriptionCategory?) -> Unit,
) {
  if (shares.isEmpty()) return
  val totalMonthly = shares.sumOf { it.amount }
  val activeShare = shares.firstOrNull { it.key == selected }

  BentoCard(padding = 18.dp) {
    Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
      Canvas(
        Modifier
          .size(196.dp)
          .semantics {
            contentDescription = activeShare?.let {
              "${it.key.label}: ${rupees(it.amount)} per month"
            } ?: "Monthly subscriptions ${rupees(totalMonthly)}"
          },
      ) {
        val sw = 16.dp.toPx()
        val pad = sw / 2f + 6.dp.toPx()
        val arcSz = Size(size.width - pad * 2, size.height - pad * 2)
        val topLeft = Offset(pad, pad)
        val gapDeg = if (shares.size > 1) 7f else 0f
        val availableDeg = 360f - gapDeg * shares.size
        var angle = -90f

        shares.forEach { sh ->
          val sweep = (availableDeg * sh.fraction).coerceAtLeast(6f)
          val color = Fin.subColor(sh.key)
          val isSelected = selected == null || sh.key == selected
          drawArc(
            color = if (isSelected) color else color.copy(alpha = 0.22f),
            startAngle = angle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSz,
            style = Stroke(sw, cap = StrokeCap.Round),
          )
          if (selected == sh.key) {
            val midRad = Math.toRadians((angle + sweep / 2f).toDouble())
            val r = arcSz.width / 2f
            val dot = Offset(center.x + (r * cos(midRad)).toFloat(), center.y + (r * sin(midRad)).toFloat())
            drawCircle(HudColors.Surface, radius = 6.dp.toPx(), center = dot)
            drawCircle(HudColors.Cream, radius = 3.6.dp.toPx(), center = dot)
          }
          angle += sweep + gapDeg
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
        Text(
          activeShare?.key?.label ?: "Monthly burn",
          style = Fin.Tiny,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          rupees(activeShare?.amount ?: totalMonthly),
          style = JarvisType.TitleSerif.copy(color = Fin.Ink),
          maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          activeShare?.let { "${(it.fraction * 100).roundToInt()}% of plans" } ?: "${shares.size} categories",
          style = Fin.Small.copy(color = activeShare?.let { Fin.subColor(it.key) } ?: Fin.Ink2),
          maxLines = 1,
        )
      }
    }

    Spacer(Modifier.height(10.dp))
    shares.forEach { sh ->
      val on = sh.key == selected
      val c = Fin.subColor(sh.key)
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(if (on) HudColors.SurfaceHigh else Color.Transparent)
          .clickable(role = Role.Button) { onSelect(if (on) null else sh.key) }
          .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(10.dp))
        Text(sh.key.label, style = Fin.Body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${(sh.fraction * 100).roundToInt()}%", style = Fin.Small, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
        Text(rupees(sh.amount) + "/mo", style = Fin.BodyStrong, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
      }
    }
  }
}

/**
 * Pastel Renewal & Bill Calendar inspired by @itspsneha's Cycle Calendar:
 * soft blush circles mark subscription renewals, lavender circles mark open bills,
 * and today sits inside a warm espresso circle.
 */
@Composable
internal fun RenewalCalendar(
  today: LocalDate,
  subscriptions: List<TrackedSubscription>,
  bills: List<OrganizedItem>,
  selected: LocalDate?,
  onSelect: (LocalDate?) -> Unit,
) {
  val month = YearMonth.from(today)
  val first = month.atDay(1)
  val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
  val days = month.lengthOfMonth()
  val subDates = subscriptions
    .filterNot { it.isPaused }
    .mapNotNull { it.nextRenewalDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
    .filter { YearMonth.from(it) == month }
    .toSet()
  val billDates = bills
    .mapNotNull { it.dueDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() } }
    .filter { YearMonth.from(it) == month }
    .toSet()

  val cells: List<Int?> = List(lead) { null } + (1..days).toList()
  val rows = cells.chunked(7)
  val monthTitle = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}"

  BentoCard(padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(monthTitle, style = JarvisType.SectionSerif, color = Fin.Ink, modifier = Modifier.weight(1f))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendDot("Renewal", HudColors.BlushSoft, HudColors.Blush)
        LegendDot("Bill", HudColors.LavenderSoft, HudColors.Lavender)
      }
    }
    Spacer(Modifier.height(12.dp))
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
              val date = month.atDay(day)
              val hasSub = date in subDates
              val hasBill = date in billDates
              val isSel = date == selected
              val isToday = date == today
              val fill = when {
                isSel -> HudColors.Cream
                hasSub -> HudColors.BlushSoft
                hasBill -> HudColors.LavenderSoft
                else -> Color.Transparent
              }
              Box(
                Modifier
                  .fillMaxWidth()
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(fill)
                  .then(
                    when {
                      isToday && !isSel -> Modifier.border(1.5.dp, HudColors.Cream, CircleShape)
                      hasSub && !isSel -> Modifier.border(1.dp, HudColors.Blush.copy(alpha = 0.45f), CircleShape)
                      hasBill && !isSel -> Modifier.border(1.dp, HudColors.Lavender.copy(alpha = 0.45f), CircleShape)
                      else -> Modifier
                    },
                  )
                  .clickable(role = Role.Button) { onSelect(if (isSel) null else date) }
                  .semantics { contentDescription = "$day ${month.month.name}" },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  "$day",
                  style = Fin.Tiny.copy(
                    color = when {
                      isSel -> HudColors.OnCream
                      hasSub -> HudColors.Blush
                      hasBill -> HudColors.Lavender
                      else -> Fin.Ink
                    },
                    fontWeight = if (isToday || isSel || hasSub || hasBill) FontWeight.Bold else FontWeight.Normal,
                  ),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LegendDot(label: String, fill: Color, stroke: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .size(9.dp)
        .clip(CircleShape)
        .background(fill)
        .border(1.dp, stroke, CircleShape),
    )
    Spacer(Modifier.width(4.dp))
    Text(label, style = Fin.Tiny)
  }
}
