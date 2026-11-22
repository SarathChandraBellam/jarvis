package com.jarvis.hud.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors

/** Sync status line with a status LED (amber while syncing, green when idle). Static, no animation. */
@Composable
fun SyncStatusTicker(line: String, isSyncing: Boolean, modifier: Modifier = Modifier) {
  val led = if (isSyncing) HudColors.Amber else HudColors.Green
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(6.dp).background(led, CircleShape))
    Spacer(Modifier.width(6.dp))
    Text(
      text = line,
      style = MaterialTheme.typography.labelSmall,
      color = if (isSyncing) HudColors.Amber else HudColors.TextDim,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
