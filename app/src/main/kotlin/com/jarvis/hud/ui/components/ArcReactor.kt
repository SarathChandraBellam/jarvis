package com.jarvis.hud.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated arc reactor: glowing core, segmented counter-rotating rings and tick dial.
 * Pulses and spins faster while [active] (syncing or speaking).
 */
@Composable
fun ArcReactor(
  modifier: Modifier = Modifier,
  size: Dp = 168.dp,
  active: Boolean = false,
  color: Color = HudColors.Cyan,
) {
  val transition = rememberInfiniteTransition(label = "arc")
  val speed = if (active) 0.35f else 1f
  val spin by transition.animateFloat(
    0f, 360f,
    infiniteRepeatable(tween((14000 * speed).toInt(), easing = LinearEasing)),
    label = "spin",
  )
  val counter by transition.animateFloat(
    360f, 0f,
    infiniteRepeatable(tween((9000 * speed).toInt(), easing = LinearEasing)),
    label = "counter",
  )
  val pulse by transition.animateFloat(
    0.55f, 1f,
    infiniteRepeatable(tween(if (active) 420 else 1800), RepeatMode.Reverse),
    label = "pulse",
  )

  Canvas(modifier.size(size)) {
    val c = center
    val r = this.size.minDimension / 2f

    // Outer glow halo.
    drawCircle(
      Brush.radialGradient(
        listOf(color.copy(alpha = 0.28f * pulse), Color.Transparent),
        center = c, radius = r,
      ),
      radius = r,
    )

    // Tick dial.
    val ticks = 60
    for (i in 0 until ticks) {
      val a = Math.toRadians((i * 360.0 / ticks) + spin / 6)
      val long = i % 5 == 0
      val r1 = r * 0.93f
      val r2 = r * (if (long) 0.84f else 0.88f)
      drawLine(
        color.copy(alpha = if (long) 0.8f else 0.35f),
        Offset(c.x + (r1 * cos(a)).toFloat(), c.y + (r1 * sin(a)).toFloat()),
        Offset(c.x + (r2 * cos(a)).toFloat(), c.y + (r2 * sin(a)).toFloat()),
        strokeWidth = if (long) 2.dp.toPx() else 1.dp.toPx(),
      )
    }

    // Segmented ring (rotating).
    rotate(spin, c) {
      segmentedRing(color.copy(alpha = 0.9f), r * 0.76f, 6.dp.toPx(), segments = 3, gapDeg = 28f)
    }
    // Thin counter-rotating dashed ring.
    rotate(counter, c) {
      segmentedRing(color.copy(alpha = 0.55f), r * 0.66f, 2.dp.toPx(), segments = 12, gapDeg = 12f)
    }
    // Inner trapezoid segments (the classic reactor coil look).
    rotate(spin * 0.5f, c) {
      segmentedRing(color.copy(alpha = 0.35f + 0.4f * pulse), r * 0.52f, 10.dp.toPx(), segments = 10, gapDeg = 9f)
    }
    drawCircle(color.copy(alpha = 0.6f), radius = r * 0.43f, style = Stroke(1.5.dp.toPx()))

    // Core.
    drawCircle(
      Brush.radialGradient(
        listOf(Color.White, color.copy(alpha = 0.95f), color.copy(alpha = 0.15f), Color.Transparent),
        center = c, radius = r * 0.38f * (0.85f + 0.15f * pulse),
      ),
      radius = r * 0.38f,
    )
    drawCircle(Color.White.copy(alpha = 0.85f * pulse), radius = r * 0.09f)
  }
}

private fun DrawScope.segmentedRing(color: Color, radius: Float, stroke: Float, segments: Int, gapDeg: Float) {
  val sweep = 360f / segments - gapDeg
  val topLeft = Offset(center.x - radius, center.y - radius)
  val sz = Size(radius * 2, radius * 2)
  for (i in 0 until segments) {
    drawArc(
      color = color,
      startAngle = i * (360f / segments),
      sweepAngle = sweep,
      useCenter = false,
      topLeft = topLeft,
      size = sz,
      style = Stroke(stroke, cap = StrokeCap.Butt),
    )
  }
}
