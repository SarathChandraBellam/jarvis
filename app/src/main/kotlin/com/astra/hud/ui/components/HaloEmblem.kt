package com.astra.hud.ui.components

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
import com.astra.hud.ui.theme.HudColors

/**
 * Calm pastel halo emblem for warm paper UI: soft blush, sage, lavender, and slate-blue
 * phase arcs around a warm cream-gold center. Static to keep CPU/GPU idle.
 */
@Composable
fun HaloEmblem(
  modifier: Modifier = Modifier,
  size: Dp = 168.dp,
  active: Boolean = false,
  color: Color = HudColors.Blush,
) {
  val alphaBoost = if (active) 1f else 0.78f
  Canvas(modifier.size(size)) {
    val c = center
    val r = this.size.minDimension / 2f

    drawCircle(
      Brush.radialGradient(
        listOf(color.copy(alpha = 0.22f * alphaBoost), HudColors.SandSoft, Color.Transparent),
        center = c,
        radius = r,
      ),
      radius = r,
    )

    val outerR = r * 0.78f
    val outerTopLeft = Offset(c.x - outerR, c.y - outerR)
    val outerSz = Size(outerR * 2, outerR * 2)
    val phaseColors = listOf(HudColors.Blush, HudColors.Sand, HudColors.Sage, HudColors.Lavender)
    phaseColors.forEachIndexed { idx, cPhase ->
      drawArc(
        color = cPhase.copy(alpha = 0.85f * alphaBoost),
        startAngle = -90f + idx * 90f + 7f,
        sweepAngle = 76f,
        useCenter = false,
        topLeft = outerTopLeft,
        size = outerSz,
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
      )
    }

    drawCircle(
      color = HudColors.Outline,
      radius = r * 0.56f,
      style = Stroke(width = 1.2.dp.toPx()),
    )

    drawCircle(
      Brush.radialGradient(
        listOf(HudColors.Surface, HudColors.BlushSoft, color.copy(alpha = 0.15f), Color.Transparent),
        center = c,
        radius = r * 0.44f,
      ),
      radius = r * 0.44f,
    )
    drawCircle(color.copy(alpha = 0.88f), radius = r * 0.14f, center = c)
  }
}
