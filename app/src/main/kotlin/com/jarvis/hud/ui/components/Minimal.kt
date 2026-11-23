package com.jarvis.hud.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.HudSans

/*
 * Quiet building blocks for data-heavy screens: no frames, no glow, one accent, lots of air.
 * Sections are separated by a single hairline and whitespace; numbers carry the hierarchy.
 */

/** Monochrome ramp for ranked series (largest share = full accent). */
fun rampColor(index: Int, base: Color = HudColors.Cyan): Color =
  base.copy(alpha = listOf(1f, 0.72f, 0.52f, 0.38f, 0.28f, 0.22f, 0.18f, 0.15f, 0.13f, 0.11f)[index.coerceIn(0, 9)])

private val Hairline = Color(0x1FBFEFFF)
private val Track = Color(0x14BFEFFF)

/** Titled block separated from the previous one by a hairline. */
@Composable
fun QuietSection(
  title: String,
  modifier: Modifier = Modifier,
  trailing: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(modifier.fillMaxWidth().padding(top = 20.dp)) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = HudColors.TextMuted,
        modifier = Modifier.weight(1f),
      )
      if (trailing != null) {
        Text(trailing, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
      }
    }
    Spacer(Modifier.height(14.dp))
    content()
  }
}

/** Large figure for the one number that matters on a screen. */
@Composable
fun HeroFigure(label: String, value: String, caption: String? = null, captionColor: Color = HudColors.TextDim) {
  Column {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    Spacer(Modifier.height(6.dp))
    Text(
      value,
      style = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.5).sp),
      color = HudColors.Text,
      maxLines = 1,
    )
    if (caption != null) {
      Spacer(Modifier.height(4.dp))
      Text(caption, style = MaterialTheme.typography.bodySmall, color = captionColor)
    }
  }
}

/** Small label-over-value metric; use several in a Row with weight(1f). */
@Composable
fun QuietMetric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = HudColors.Text) {
  Column(modifier) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Text(
      value,
      style = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.Normal, fontSize = 17.sp),
      color = valueColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** 4dp rounded progress line on a faint track. */
@Composable
fun ThinBar(fraction: Float, modifier: Modifier = Modifier, color: Color = HudColors.Cyan) {
  Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Track)) {
    val f = fraction.coerceIn(0f, 1f)
    if (f > 0f) {
      Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.015f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(color))
    }
  }
}

/** One ranked row: label, secondary text, value, and a thin bar underneath. */
@Composable
fun ShareRow(
  label: String,
  value: String,
  fraction: Float,
  modifier: Modifier = Modifier,
  detail: String? = null,
  color: Color = HudColors.Cyan,
) {
  Column(modifier.fillMaxWidth().padding(vertical = 7.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Text(
          label,
          style = MaterialTheme.typography.bodyMedium,
          color = HudColors.Text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (detail != null) {
          Spacer(Modifier.width(8.dp))
          Text(detail, style = MaterialTheme.typography.bodySmall, color = HudColors.TextMuted, maxLines = 1)
        }
      }
      Spacer(Modifier.width(12.dp))
      Text(value, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text, maxLines = 1)
    }
    Spacer(Modifier.height(7.dp))
    ThinBar(fraction, color = color)
  }
}

/** One stacked bar split into coloured segments, with a dot legend beneath. */
@Composable
fun SegmentBar(segments: List<Triple<String, Float, Color>>, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
      segments.filter { it.second > 0f }.forEach { (_, f, c) ->
        Box(Modifier.weight(f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c))
      }
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      segments.forEach { (label, f, c) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(6.dp).clip(CircleShape).background(c))
          Spacer(Modifier.width(6.dp))
          Text(
            "$label ${(f * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = HudColors.TextDim,
            maxLines = 1,
          )
        }
      }
    }
  }
}

/**
 * Minimal column chart: thin rounded bars, no grid, the last bar in full accent and the others
 * muted. [labels] sit under the bars.
 */
@Composable
fun MiniBars(values: List<Double>, labels: List<String>, modifier: Modifier = Modifier, height: Int = 88) {
  val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
  Column(modifier.fillMaxWidth()) {
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
      val n = values.size.coerceAtLeast(1)
      val slot = size.width / n
      val barW = minOf(slot * 0.34f, 14.dp.toPx())
      val r = CornerRadius(barW / 2, barW / 2)
      values.forEachIndexed { i, v ->
        val x = slot * i + (slot - barW) / 2
        val h = ((v / max).toFloat() * size.height).coerceAtLeast(if (v > 0) barW else 2.dp.toPx())
        val color = when {
          i == values.lastIndex -> HudColors.Cyan
          v > 0 -> HudColors.Cyan.copy(alpha = 0.32f)
          else -> Track
        }
        drawRoundRect(color, topLeft = Offset(x, size.height - h), size = Size(barW, h), cornerRadius = r)
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
      labels.forEachIndexed { i, l ->
        Text(
          l,
          style = MaterialTheme.typography.labelSmall,
          color = if (i == labels.lastIndex) HudColors.Text else HudColors.TextMuted,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

/** Plain two-column line: muted label left, value right. */
@Composable
fun QuietLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = HudColors.Text) {
  Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim, modifier = Modifier.weight(1f))
    Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
  }
}
