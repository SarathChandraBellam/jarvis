package com.jarvis.hud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors

/** Rectangle with the top-left and bottom-right corners cut at 45 degrees. */
class CutCornerHudShape(private val cut: Dp = 12.dp, private val small: Dp = 0.dp) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
    val s = with(density) { small.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
    val path = Path().apply {
      moveTo(c, 0f)
      lineTo(size.width - s, 0f)
      lineTo(size.width, s)
      lineTo(size.width, size.height - c)
      lineTo(size.width - c, size.height)
      lineTo(s, size.height)
      lineTo(0f, size.height - s)
      lineTo(0f, c)
      close()
    }
    return Outline.Generic(path)
  }
}

/**
 * Bordered HUD panel with angled corners, corner tick marks and a small uppercase header label.
 */
@Composable
fun HudPanel(
  modifier: Modifier = Modifier,
  title: String? = null,
  accent: Color = HudColors.Cyan,
  trailing: (@Composable () -> Unit)? = null,
  contentPadding: Dp = 14.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = CutCornerHudShape(14.dp, 4.dp)
  Box(
    modifier = modifier
      .background(
        Brush.verticalGradient(listOf(HudColors.Panel, Color(0xE6060E19))),
        shape,
      )
      .border(1.dp, accent.copy(alpha = 0.38f), shape)
      .drawBehind {
        // Bright accent ticks on the cut corners.
        val t = 18.dp.toPx()
        val sw = 2.dp.toPx()
        val c = 14.dp.toPx()
        drawLine(accent, Offset(c, 0f), Offset(c + t, 0f), sw)
        drawLine(accent, Offset(0f, c), Offset(0f, c + t), sw)
        drawLine(accent, Offset(size.width - c - t, size.height), Offset(size.width - c, size.height), sw)
        drawLine(accent, Offset(size.width, size.height - c - t), Offset(size.width, size.height - c), sw)
      },
  ) {
    Column(Modifier.fillMaxWidth().padding(contentPadding)) {
      if (title != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          Box(Modifier.size(6.dp).background(accent))
          Spacer(Modifier.width(8.dp))
          Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
          )
          Spacer(Modifier.width(10.dp))
          Box(
            Modifier
              .weight(1f)
              .height(1.dp)
              .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent))),
          )
          if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
          }
        }
        Spacer(Modifier.height(12.dp))
      }
      content()
    }
  }
}

/** Small mono caption + value row used inside panels. */
@Composable
fun HudKeyValue(label: String, value: String, valueColor: Color = HudColors.Text, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    Text(value, style = MaterialTheme.typography.labelLarge, color = valueColor)
  }
}

/** Shown inside panels when a list is empty. */
@Composable
fun HudEmptyLine(text: String, modifier: Modifier = Modifier) {
  Text(
    text = "// " + text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = HudColors.TextMuted,
    modifier = modifier.padding(vertical = 6.dp),
  )
}
