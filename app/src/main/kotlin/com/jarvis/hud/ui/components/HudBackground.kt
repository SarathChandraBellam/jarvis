package com.jarvis.hud.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors

/**
 * Full-bleed HUD backdrop: deep navy gradient, faint grid, and horizontal scanlines. Static (drawn once, no animation).
 */
@Composable
fun HudBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(
    modifier
      .fillMaxSize()
      .background(
        Brush.radialGradient(
          listOf(Color(0xFF0B1E33), HudColors.Background),
          radius = 1800f,
        ),
      ),
  ) {
    ScanlineOverlay(Modifier.fillMaxSize())
    content()
  }
}

/** Grid + scanlines. Purely decorative, draws behind content. */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val grid = 32.dp.toPx()
    var x = 0f
    while (x < size.width) {
      drawLine(HudColors.Grid, Offset(x, 0f), Offset(x, size.height), 1f)
      x += grid
    }
    var y = 0f
    while (y < size.height) {
      drawLine(HudColors.Grid, Offset(0f, y), Offset(size.width, y), 1f)
      y += grid
    }
    val line = 3.dp.toPx()
    var s = 0f
    while (s < size.height) {
      drawLine(Color(0x08000000), Offset(0f, s), Offset(size.width, s), 1.dp.toPx())
      s += line
    }
  }
}
