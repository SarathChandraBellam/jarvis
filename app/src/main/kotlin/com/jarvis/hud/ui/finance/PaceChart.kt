package com.jarvis.hud.ui.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Cumulative spend this month against last month, day by day — answers "am I spending faster
 * than usual?" at a glance. Two series on one axis, a key that doubles as the read-out, and a
 * scrub interaction (tap or drag) that moves the read-out to any day.
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

  Column(modifier.fillMaxWidth()) {
    // Key + read-out: which line is which, and the values at the scrubbed day.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      KeyValue(Fin.Accent, "By ${idx + 1} $thisName", thisVal?.let(::rupees) ?: "—")
      KeyValue(Fin.LastMonth, "By ${idx + 1} $lastName", rupees(lastVal))
    }
    Spacer(Modifier.height(14.dp))

    val measurer = rememberTextMeasurer()
    val maxY = niceCeil(maxOf(thisCum.lastOrNull() ?: 0.0, lastCum.lastOrNull() ?: 0.0, 1.0))
    Canvas(
      Modifier
        .fillMaxWidth()
        .height(170.dp)
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
      val bottomPad = 22.dp.toPx()
      val plotH = size.height - bottomPad
      val w = size.width
      fun x(i: Int) = if (days <= 1) 0f else w * i / (days - 1).toFloat()
      fun y(v: Double) = plotH - (v / maxY).toFloat() * plotH

      // Recessive grid: 3 solid hairlines with compact tick labels on the left.
      for (g in 0..3) {
        val v = maxY * g / 3
        val gy = y(v)
        drawLine(Fin.Hairline, Offset(0f, gy), Offset(w, gy), 1.dp.toPx())
        if (g > 0) {
          drawText(measurer, rupeesShort(v), Offset(0f, gy + 3.dp.toPx()), Fin.Tiny)
        }
      }

      fun path(values: List<Double>) = Path().apply {
        values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
      }
      val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
      drawPath(path(lastCum), Fin.LastMonth, style = stroke)
      if (thisCum.isNotEmpty()) drawPath(path(thisCum), Fin.Accent, style = stroke)

      // Scrub line + markers (8dp, with a surface ring so they sit on top of the lines).
      val sx = x(idx)
      drawLine(Fin.Ink3, Offset(sx, 0f), Offset(sx, plotH), 1.dp.toPx())
      fun marker(v: Double, c: Color) {
        drawCircle(Fin.Surface, 6.dp.toPx(), Offset(sx, y(v)))
        drawCircle(c, 4.dp.toPx(), Offset(sx, y(v)))
      }
      marker(lastVal, Fin.LastMonth)
      thisVal?.let { marker(it, Fin.Accent) }

      // X axis: first, middle, last day.
      listOf(0, days / 2 - 1, days - 1).forEach { i ->
        val label = "${i + 1} $thisName"
        val m = measurer.measure(label, Fin.Tiny)
        val lx = (x(i) - m.size.width / 2f).coerceIn(0f, w - m.size.width)
        drawText(m, topLeft = Offset(lx, plotH + 6.dp.toPx()))
      }
    }
  }
}

@Composable
private fun KeyValue(color: Color, label: String, value: String) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(14.dp).height(2.dp).background(color))
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
