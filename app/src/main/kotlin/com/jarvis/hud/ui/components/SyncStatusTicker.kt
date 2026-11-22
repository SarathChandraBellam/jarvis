package com.jarvis.hud.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors
import kotlinx.coroutines.delay

/** Monospace typewriter readout of the sync status line with a blinking cursor + status LED. */
@Composable
fun SyncStatusTicker(line: String, isSyncing: Boolean, modifier: Modifier = Modifier) {
  val inspection = LocalInspectionMode.current
  var shown by remember { mutableIntStateOf(if (inspection) Int.MAX_VALUE else 0) }
  LaunchedEffect(line) {
    if (inspection) return@LaunchedEffect
    shown = 0
    while (shown < line.length) {
      delay(18)
      shown++
    }
  }
  val blink = rememberInfiniteTransition(label = "blink")
  val cursor by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "cursor")
  val led = if (isSyncing) HudColors.Amber else HudColors.Green

  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .size(6.dp)
        .alpha(if (isSyncing) cursor.coerceAtLeast(0.3f) else 1f)
        .background(led, CircleShape),
    )
    Spacer(Modifier.width(6.dp))
    Text(
      text = line.take(shown),
      style = MaterialTheme.typography.labelSmall,
      color = if (isSyncing) HudColors.Amber else HudColors.TextDim,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Text("_", style = MaterialTheme.typography.labelSmall, color = HudColors.Cyan, modifier = Modifier.alpha(cursor))
  }
}
