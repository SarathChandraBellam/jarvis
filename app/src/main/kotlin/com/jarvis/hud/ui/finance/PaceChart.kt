package com.jarvis.hud.ui.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.SpendInsights
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.viz.BentoCard
import com.jarvis.hud.ui.viz.smoothCubicPath
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Smooth cubic-Bezier cumulative spend area chart comparing this month against last month,
 * inside a calm bento card with interactive day scrubbing.
 */
@Composable
internal fun PaceChart(s: SpendInsights, modifier: Modifier = Modifier) {
  val days = s.month.lengthOfMonth()
  val thisCum = remember(s) { s.monthDays.runningReduce { a, b -> a + b } }
  val lastCum = remember(s) {
    val l = s.lastMonthDays.runningReduce { a, b -> a + b }
    (0 until days).map { i -> l.getOrElse(i) { l.lastOrNull() ?: 0.0 } }
  }
  val todayIdx = s.today.dayOfMonth - 1
  var sel by remember(s) { mutableStateOf<Int?>(null) }
  val idx = (sel ?: todayIdx).coerceIn(0, days - 1)

  val thisName = s.month.month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH)
  val lastName = s.month.minusMonths(1).month.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH)
  val thisVal = thisCum.getOrNull(idx)
  val lastVal = lastCum.getOrNull(idx) ?: 0.0

  BentoCard(modifier = modifier, padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("Cumulative spend pace", style = Fin.BodyStrong, modifier = Modifier.weight(1f))
      Text("Tap or drag to scrub", style = Fin.Tiny)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      KeyValue(Fin.Accent, "By ${idx + 1} $thisName", thisVal?.let(::rupees) ?: "—")
      KeyValue(Fin.LastMonth, "By ${idx + 1} $lastName", rupees(lastVal))
    }
    Spacer(Modifier.height(16.dp))

    val measurer = rememberTextMeasurer()
    val maxY = niceCeil(maxOf(thisCum.lastOrNull() ?: 0.0, lastCum.lastOrNull() ?: 0.0, 1.0))
    Canvas(
      Modifier
        .fillMaxWidth()
        .height(176.dp)
        .semantics {
          contentDescription = "Cumulative spend: ${thisVal?.let(::rupees) ?: "nothing"} by day ${idx + 1} " +
            "this month versus ${rupees(lastVal)} last month"
        }
        .pointerInput(days) {
          detectTapGestures { o -> sel = dayAt(o.x, size.width.toFloat(), days) }
        }
        .pointerInput(days) {
          detectHorizontalDragGestures(onDragEnd = {}) { change, _ ->
            sel = dayAt(change.position.x, size.width.toFloat(), days)
          }
        },
    ) {
      val topPad = 8.dp.toPx()
      val bottomPad = 24.dp.toPx()
      val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
      val w = size.width
      fun x(i: Int) = if (days <= 1) 0f else w * i / (days - 1).toFloat()
      fun y(v: Double) = topPad + plotH - (v / maxY).toFloat() * plotH

      // Subtle dashed baseline grid.
      val gridDash = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
      for (g in 0..3) {
        val v = maxY * g / 3
        val gy = y(v)
        drawLine(
          color = Fin.Hairline,
          start = Offset(0f, gy),
          end = Offset(w, gy),
          strokeWidth = 1.dp.toPx(),
          pathEffect = if (g == 0) null else gridDash,
        )
        if (g > 0) {
          drawText(measurer, rupeesShort(v), Offset(0f, gy + 3.dp.toPx()), Fin.Tiny)
        }
      }

      val lastPts = lastCum.mapIndexed { i, v -> Offset(x(i), y(v)) }
      val lastPath = smoothCubicPath(lastPts)
      drawPath(
        path = lastPath,
        color = Fin.LastMonth,
        style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
      )

      if (thisCum.isNotEmpty()) {
        val thisPts = thisCum.mapIndexed { i, v -> Offset(x(i), y(v)) }
        val thisPath = smoothCubicPath(thisPts)
        val areaPath = Path().apply {
          addPath(thisPath)
          lineTo(thisPts.last().x, topPad + plotH)
          lineTo(thisPts.first().x, topPad + plotH)
          close()
        }
        drawPath(
          path = areaPath,
          brush = Brush.verticalGradient(
            listOf(Fin.Accent.copy(alpha = 0.28f), Fin.Accent.copy(alpha = 0.0f)),
            startY = topPad,
            endY = topPad + plotH,
          ),
        )
        drawPath(
          path = thisPath,
          color = Fin.Accent,
          style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }

      // Scrub indicator + glowing ring markers.
      val sx = x(idx)
      drawLine(
        color = HudColors.Cream.copy(alpha = 0.45f),
        start = Offset(sx, topPad),
        end = Offset(sx, topPad + plotH),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
      )
      fun marker(v: Double, c: Color) {
        val pt = Offset(sx, y(v))
        drawCircle(Fin.Card, 6.5.dp.toPx(), pt)
        drawCircle(c, 4.dp.toPx(), pt)
      }
      marker(lastVal, Fin.LastMonth)
      thisVal?.let { marker(it, Fin.Accent) }

      // X axis: start, middle, end of month.
      listOf(0, days / 2 - 1, days - 1).forEach { i ->
        val label = "${i + 1} $thisName"
        val m = measurer.measure(label, Fin.Tiny)
        val lx = (x(i) - m.size.width / 2f).coerceIn(0f, w - m.size.width)
        drawText(m, topLeft = Offset(lx, topPad + plotH + 7.dp.toPx()))
      }
    }
  }
}

@Composable
private fun KeyValue(color: Color, label: String, value: String) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(6.dp))
      Text(label, style = Fin.Small)
    }
    Spacer(Modifier.height(2.dp))
    Text(value, style = Fin.Figure)
  }
}

private fun dayAt(px: Float, width: Float, days: Int): Int =
  ((px / width.coerceAtLeast(1f)) * (days - 1) + 0.5f).toInt().coerceIn(0, days - 1)

/** Top of a 3-step axis whose step is 1, 2, 2.5 or 5 × 10ⁿ, so every gridline is a round value. */
internal fun niceCeil(v: Double): Double {
  if (v <= 0) return 1.0
  val step = v / 3
  val mag = 10.0.pow(floor(log10(step)))
  val n = step / mag
  val nice = when {
    n <= 1 -> 1.0
    n <= 2 -> 2.0
    n <= 2.5 -> 2.5
    n <= 5 -> 5.0
    else -> 10.0
  }
  return 3 * nice * mag
}
