package com.jarvis.hud.ui.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val BentoShape = RoundedCornerShape(22.dp)

/**
 * Calm paper-and-pastel container inspired by @itspsneha's personal software aesthetic.
 * Uses crisp white or soft pastel wash fills with subtle warm hairline borders.
 */
@Composable
fun BentoCard(
  modifier: Modifier = Modifier,
  tint: Color? = null,
  pastelBg: Color? = null,
  onClick: (() -> Unit)? = null,
  padding: Dp = 16.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val bg = when {
    pastelBg != null -> Modifier.background(pastelBg)
    tint != null -> Modifier.background(
      Brush.verticalGradient(listOf(tint.copy(alpha = 0.11f), HudColors.Surface, HudColors.Surface)),
    )
    else -> Modifier.background(HudColors.Surface)
  }
  val border = when {
    pastelBg != null -> HudColors.OutlineSoft
    tint != null -> tint.copy(alpha = 0.20f)
    else -> HudColors.Outline
  }
  Column(
    modifier
      .fillMaxWidth()
      .clip(BentoShape)
      .then(bg)
      .border(1.dp, border, BentoShape)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
      .padding(padding),
    content = content,
  )
}

/**
 * Soft pastel donut arc ring with rounded stroke caps and centered editorial readout.
 */
@Composable
fun DonutArcRing(
  fraction: Float,
  centerText: String,
  color: Color,
  modifier: Modifier = Modifier,
  size: Dp = 84.dp,
  strokeWidth: Dp = 8.dp,
  partialSweep: Boolean = false,
  subText: String? = null,
) {
  val f = fraction.coerceIn(0f, 1f)
  val startAngle = if (partialSweep) 145f else -90f
  val totalSweep = if (partialSweep) 250f else 360f
  Box(modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val sw = strokeWidth.toPx()
      val pad = sw / 2f + 2.dp.toPx()
      val arcSize = Size(this.size.width - pad * 2, this.size.height - pad * 2)
      val topLeft = Offset(pad, pad)
      drawArc(
        color = color.copy(alpha = 0.16f),
        startAngle = startAngle,
        sweepAngle = totalSweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(sw, cap = StrokeCap.Round),
      )
      if (f > 0.005f) {
        val sweep = totalSweep * f
        drawArc(
          color = color,
          startAngle = startAngle,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(sw, cap = StrokeCap.Round),
        )
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        centerText,
        style = if (size >= 80.dp) JarvisType.MetricMedium else MaterialTheme.typography.titleMedium,
        color = HudColors.Text,
        maxLines = 1,
      )
      if (subText != null) {
        Text(subText, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim, maxLines = 1)
      }
    }
  }
}

data class ScoreRingSpec(
  val label: String,
  val scoreText: String,
  val fraction: Float,
  val color: Color,
  val onClick: (() -> Unit)? = null,
)

/** Row of 3 calm pastel score rings + reassuring guidance line underneath. */
@Composable
fun ScoreRingsCard(
  rings: List<ScoreRingSpec>,
  guidance: String,
  modifier: Modifier = Modifier,
) {
  BentoCard(modifier = modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      rings.forEach { ring ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .then(if (ring.onClick != null) Modifier.clickable(role = Role.Button, onClick = ring.onClick) else Modifier)
            .padding(vertical = 4.dp),
        ) {
          DonutArcRing(
            fraction = ring.fraction,
            centerText = ring.scoreText,
            color = ring.color,
            size = 76.dp,
            strokeWidth = 7.dp,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            ring.label,
            style = MaterialTheme.typography.labelMedium,
            color = HudColors.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    if (guidance.isNotBlank()) {
      Spacer(Modifier.height(12.dp))
      Text(
        guidance,
        style = MaterialTheme.typography.bodySmall,
        color = HudColors.TextDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

data class DayPillItem(
  val letter: String,
  val dayNumber: String,
  val isSelected: Boolean,
  val hasMarker: Boolean,
  val onClick: () -> Unit,
)

/**
 * Warm sand 7-day pill selector card inspired by @itspsneha's Day context card.
 */
@Composable
fun DaySelectorStrip(
  eyebrow: String,
  headline: String,
  rightHeadline: String,
  rightSub: String,
  days: List<DayPillItem>,
  modifier: Modifier = Modifier,
) {
  BentoCard(modifier = modifier, pastelBg = HudColors.SandSoft, padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = HudColors.Sand)
        Spacer(Modifier.height(2.dp))
        Text(headline, style = JarvisType.TitleSerif, color = HudColors.Text)
      }
      Column(horizontalAlignment = Alignment.End) {
        Text(rightHeadline, style = JarvisType.MetricMedium, color = HudColors.Text)
        Text(rightSub, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
      }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      days.forEach { d ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = d.onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
          Text(d.letter, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
          Spacer(Modifier.height(6.dp))
          Box(
            Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(if (d.isSelected) HudColors.Cream else HudColors.Surface.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              d.dayNumber,
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (d.isSelected) FontWeight.Bold else FontWeight.Medium,
              ),
              color = if (d.isSelected) HudColors.OnCream else HudColors.Text,
            )
          }
          Spacer(Modifier.height(4.dp))
          Box(
            Modifier
              .size(5.dp)
              .clip(CircleShape)
              .background(if (d.hasMarker) HudColors.Blush else Color.Transparent),
          )
        }
      }
    }
  }
}

data class TimelineSpan(val startFrac: Float, val endFrac: Float, val color: Color)

data class TimelineDot(val frac: Float, val color: Color, val radiusDp: Float = 5f)

/**
 * 24-hour unified day timeline bar (`12 am` to `12 am`) on a calm paper capsule.
 */
@Composable
fun DayTimelineBar(
  spans: List<TimelineSpan>,
  dots: List<TimelineDot>,
  nowFrac: Float,
  summaryLine: String,
  legend: List<Pair<String, Color>>,
  modifier: Modifier = Modifier,
) {
  BentoCard(modifier = modifier, padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("Today's 24h timeline", style = MaterialTheme.typography.titleMedium, color = HudColors.Text, modifier = Modifier.weight(1f))
      Text("12 am – 12 am", style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    }
    Spacer(Modifier.height(14.dp))
    Canvas(
      Modifier
        .fillMaxWidth()
        .height(32.dp)
        .semantics { contentDescription = "24 hour timeline: $summaryLine" },
    ) {
      val h = size.height
      val w = size.width
      val r = h / 2f

      drawRoundRect(
        color = HudColors.SurfaceHigh,
        cornerRadius = CornerRadius(r, r),
      )
      val dayStartX = w * 0.25f
      val dayEndX = w * (22f / 24f)
      drawRect(
        color = HudColors.SandSoft.copy(alpha = 0.55f),
        topLeft = Offset(dayStartX, 2.dp.toPx()),
        size = Size((dayEndX - dayStartX).coerceAtLeast(0f), h - 4.dp.toPx()),
      )

      spans.forEach { sp ->
        val x1 = w * sp.startFrac.coerceIn(0f, 1f)
        val x2 = w * sp.endFrac.coerceIn(sp.startFrac, 1f)
        val segW = (x2 - x1).coerceAtLeast(6.dp.toPx())
        drawRoundRect(
          color = sp.color.copy(alpha = 0.8f),
          topLeft = Offset(x1, 6.dp.toPx()),
          size = Size(segW, h - 12.dp.toPx()),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
      }

      val nx = w * nowFrac.coerceIn(0f, 1f)
      drawLine(
        color = HudColors.Cream.copy(alpha = 0.8f),
        start = Offset(nx, 2.dp.toPx()),
        end = Offset(nx, h - 2.dp.toPx()),
        strokeWidth = 1.5.dp.toPx(),
      )

      dots.forEach { d ->
        val cx = w * d.frac.coerceIn(0.02f, 0.98f)
        val cy = h / 2f
        val rad = d.radiusDp.dp.toPx()
        drawCircle(HudColors.Surface, radius = rad + 2.dp.toPx(), center = Offset(cx, cy))
        drawCircle(d.color, radius = rad, center = Offset(cx, cy))
      }

      drawRoundRect(
        color = HudColors.Outline,
        cornerRadius = CornerRadius(r, r),
        style = Stroke(1.dp.toPx()),
      )
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      listOf("12 am", "6 am", "noon", "6 pm", "12 am").forEach {
        Text(it, style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
      }
    }
    if (summaryLine.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Text(summaryLine, style = MaterialTheme.typography.bodySmall, color = HudColors.Text)
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      legend.forEach { (label, color) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(7.dp).clip(CircleShape).background(color))
          Spacer(Modifier.width(5.dp))
          Text(label, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
        }
      }
    }
  }
}

/**
 * Pastel gradient card with smooth cubic-Bezier area sparkline and endpoint dot.
 */
@Composable
fun SparklineAreaCard(
  title: String,
  value: String,
  subtitle: String,
  series: List<Float>,
  tint: Color,
  pastelBg: Color = HudColors.Surface,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  BentoCard(modifier = modifier, pastelBg = pastelBg, onClick = onClick, padding = 14.dp) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(6.dp))
    Text(value, style = JarvisType.MetricMedium, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(12.dp))
    SmoothSparkline(series = series, color = tint, modifier = Modifier.fillMaxWidth().height(40.dp))
  }
}

@Composable
fun SmoothSparkline(series: List<Float>, color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    if (series.isEmpty()) return@Canvas
    val pts = if (series.size == 1) listOf(series[0], series[0]) else series
    val minV = pts.minOrNull() ?: 0f
    val maxV = (pts.maxOrNull() ?: 1f).let { if (it - minV < 1e-4f) minV + 1f else it }
    val w = size.width
    val h = size.height
    val topPad = 6.dp.toPx()
    val botPad = 4.dp.toPx()
    val plotH = (h - topPad - botPad).coerceAtLeast(1f)

    val coords = pts.mapIndexed { i, v ->
      val x = w * i / (pts.size - 1).toFloat()
      val norm = (v - minV) / (maxV - minV)
      val y = topPad + (1f - norm) * plotH
      Offset(x, y)
    }

    val strokePath = smoothCubicPath(coords)
    val fillPath = Path().apply {
      addPath(strokePath)
      lineTo(coords.last().x, h)
      lineTo(coords.first().x, h)
      close()
    }
    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.0f))),
    )
    drawPath(
      path = strokePath,
      color = color,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    val last = coords.last()
    drawCircle(HudColors.Surface, radius = 4.dp.toPx(), center = last)
    drawCircle(color, radius = 2.6.dp.toPx(), center = last)
  }
}

fun smoothCubicPath(points: List<Offset>): Path {
  val path = Path()
  if (points.isEmpty()) return path
  path.moveTo(points.first().x, points.first().y)
  if (points.size == 1) return path
  for (i in 0 until points.lastIndex) {
    val p0 = points[i]
    val p1 = points[i + 1]
    val midX = (p0.x + p1.x) / 2f
    path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
  }
  return path
}

data class RadarAxis(val label: String, val fraction: Float, val detail: String)

/**
 * 6-axis hexagonal radar chart on warm paper with concentric rings and contributor bars.
 */
@Composable
fun HexRadarChart(
  axes: List<RadarAxis>,
  score: Int,
  headline: String,
  accent: Color = HudColors.Sand,
  modifier: Modifier = Modifier,
) {
  val six = if (axes.size >= 6) axes.take(6) else axes + List(6 - axes.size) { RadarAxis("", 0f, "") }
  BentoCard(modifier = modifier, padding = 18.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f)) {
        Text("Daily balance", style = MaterialTheme.typography.titleMedium, color = HudColors.Text)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text("$score", style = JarvisType.HeroSerif, color = HudColors.Text)
          Spacer(Modifier.width(6.dp))
          Text("out of 100", style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, modifier = Modifier.padding(bottom = 6.dp))
        }
      }
      Text(headline, style = MaterialTheme.typography.labelMedium, color = accent)
    }
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(
      Modifier.fillMaxWidth().height(232.dp),
      contentAlignment = Alignment.Center,
    ) {
      val chartSize = min(maxWidth.value, maxHeight.value).dp
      Canvas(
        Modifier
          .size(chartSize)
          .semantics { contentDescription = "Pulse radar score $score out of 100" },
      ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.minDimension * 0.34f
        fun vertex(idx: Int, frac: Float): Offset {
          val angle = -PI / 2.0 + idx * (2.0 * PI / 6.0)
          return Offset(cx + (maxR * frac * cos(angle)).toFloat(), cy + (maxR * frac * sin(angle)).toFloat())
        }

        listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { ring ->
          val hex = Path().apply {
            for (i in 0 until 6) {
              val p = vertex(i, ring)
              if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
          }
          drawPath(hex, HudColors.Outline, style = Stroke(1.dp.toPx()))
        }

        for (i in 0 until 6) {
          drawLine(HudColors.OutlineSoft, Offset(cx, cy), vertex(i, 1f), strokeWidth = 1.dp.toPx())
        }

        val dataPts = six.mapIndexed { i, ax -> vertex(i, ax.fraction.coerceIn(0.08f, 1f)) }
        val dataPath = Path().apply {
          dataPts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
          close()
        }
        drawPath(
          dataPath,
          Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.14f)), center = Offset(cx, cy), radius = maxR),
        )
        drawPath(dataPath, accent, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

        dataPts.forEach { p ->
          drawCircle(HudColors.Surface, radius = 4.5.dp.toPx(), center = p)
          drawCircle(HudColors.Cream, radius = 2.8.dp.toPx(), center = p)
        }
      }

      val positions = listOf(
        Alignment.TopCenter to Pair(0.dp, 2.dp),
        Alignment.TopEnd to Pair((-10).dp, 46.dp),
        Alignment.BottomEnd to Pair((-10).dp, (-46).dp),
        Alignment.BottomCenter to Pair(0.dp, (-2).dp),
        Alignment.BottomStart to Pair(10.dp, (-46).dp),
        Alignment.TopStart to Pair(10.dp, 46.dp),
      )
      six.forEachIndexed { i, ax ->
        if (ax.label.isNotBlank()) {
          val (align, off) = positions[i]
          Text(
            ax.label,
            style = MaterialTheme.typography.labelSmall,
            color = HudColors.TextDim,
            modifier = Modifier.align(align).offset(x = off.first, y = off.second),
          )
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    six.filter { it.label.isNotBlank() }.forEachIndexed { idx, ax ->
      if (idx > 0) Spacer(Modifier.height(10.dp))
      BaselineRangeBar(
        label = ax.label,
        valueText = ax.detail,
        fraction = ax.fraction,
        typicalFraction = 0.70f,
        color = when {
          ax.fraction >= 0.7f -> HudColors.Sage
          ax.fraction >= 0.45f -> accent
          else -> HudColors.Blush
        },
      )
    }
  }
}

/**
 * Horizontal baseline bar with a vertical dashed typical marker tick.
 */
@Composable
fun BaselineRangeBar(
  label: String,
  valueText: String,
  fraction: Float,
  typicalFraction: Float?,
  color: Color,
  modifier: Modifier = Modifier,
  deltaText: String? = null,
) {
  val f = fraction.coerceIn(0f, 1f)
  Column(modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = HudColors.Text, modifier = Modifier.weight(1f))
      Text(valueText, style = MaterialTheme.typography.labelMedium, color = HudColors.TextDim)
    }
    Spacer(Modifier.height(6.dp))
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
      val barH = 6.dp.toPx()
      val yTop = (size.height - barH) / 2f
      val r = barH / 2f
      drawRoundRect(
        color = HudColors.SurfaceHigh,
        topLeft = Offset(0f, yTop),
        size = Size(size.width, barH),
        cornerRadius = CornerRadius(r, r),
      )
      if (f > 0f) {
        drawRoundRect(
          color = color,
          topLeft = Offset(0f, yTop),
          size = Size((size.width * f).coerceAtLeast(barH), barH),
          cornerRadius = CornerRadius(r, r),
        )
      }
      if (typicalFraction != null) {
        val tx = size.width * typicalFraction.coerceIn(0.02f, 0.98f)
        drawLine(
          color = HudColors.Text.copy(alpha = 0.55f),
          start = Offset(tx, 0f),
          end = Offset(tx, size.height),
          strokeWidth = 1.4.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
        )
      }
    }
    if (!deltaText.isNullOrBlank()) {
      Spacer(Modifier.height(2.dp))
      Text(deltaText, style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    }
  }
}

data class ConsistencyDay(
  val dayLabel: String,
  val fillColor: Color,
  val active: Boolean,
  val tooltip: String,
)

/** 7-day rounded squircle consistency strip in calm sage/sand/blush. */
@Composable
fun ConsistencyStrip(
  title: String,
  days: List<ConsistencyDay>,
  caption: String,
  modifier: Modifier = Modifier,
) {
  BentoCard(modifier = modifier, padding = 16.dp) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = HudColors.Text)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      days.forEach { d ->
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            Modifier
              .fillMaxWidth()
              .height(22.dp)
              .clip(RoundedCornerShape(7.dp))
              .background(if (d.active) d.fillColor else HudColors.SurfaceHigh)
              .then(
                if (!d.active) Modifier.border(1.dp, HudColors.Outline, RoundedCornerShape(7.dp)) else Modifier,
              )
              .semantics { contentDescription = d.tooltip },
          )
          Spacer(Modifier.height(5.dp))
          Text(d.dayLabel, style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
        }
      }
    }
    if (caption.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Text(caption, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
    }
  }
}
