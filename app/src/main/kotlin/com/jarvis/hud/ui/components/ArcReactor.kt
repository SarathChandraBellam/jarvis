package com.jarvis.hud.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors

/**
 * Calm concentric orbital aura: replaces the old harsh reactor with soft multi-phase rings
 * and a warm glowing core. Static to keep CPU/GPU idle.
 */
@Composable
fun ArcReactor(
  modifier: Modifier = Modifier,
  size: Dp = 168.dp,
  active: Boolean = false,
  color: Color = HudColors.Amber,
) {
  val alphaBoost = if (active) 1f else 0.72f
  Canvas(modifier.size(size)) {
    val c = center
    val r = this.size.minDimension / 2f

    // Warm outer halo.
    drawCircle(
      Brush.radialGradient(
        listOf(color.copy(alpha = 0.24f * alphaBoost), HudColors.Lavender.copy(alpha = 0.08f), Color.Transparent),
        center = c,
        radius = r,
      ),
      radius = r,
    )

    // Multi-phase outer ring (calm pastel arcs).
    val outerR = r * 0.80f
    val outerTopLeft = Offset(c.x - outerR, c.y - outerR)
    val outerSz = Size(outerR * 2, outerR * 2)
    val phaseColors = listOf(color, HudColors.Sky, HudColors.Lavender, HudColors.Mint)
    phaseColors.forEachIndexed { idx, cPhase ->
      drawArc(
        color = cPhase.copy(alpha = 0.8f * alphaBoost),
        startAngle = -90f + idx * 90f + 5f,
        sweepAngle = 78f,
        useCenter = false,
        topLeft = outerTopLeft,
        size = outerSz,
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
      )
    }

    // Middle subtle hairline ring.
    drawCircle(
      color = HudColors.Cream.copy(alpha = 0.28f),
      radius = r * 0.58f,
      style = Stroke(width = 1.5.dp.toPx()),
    )

    // Warm glowing center orb.
    drawCircle(
      Brush.radialGradient(
        listOf(HudColors.Cream, color.copy(alpha = 0.85f), color.copy(alpha = 0.1f), Color.Transparent),
        center = c,
        radius = r * 0.42f,
      ),
      radius = r * 0.42f,
    )
    drawCircle(HudColors.Cream.copy(alpha = 0.95f), radius = r * 0.14f, center = c)
  }
}
