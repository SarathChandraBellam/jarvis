package com.jarvis.hud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.Priority
import com.jarvis.hud.ui.theme.HudColors

/** Compact uppercase priority tag. */
@Composable
fun PriorityChip(priority: Priority, modifier: Modifier = Modifier) {
  val c = priority.color()
  Box(
    modifier
      .clip(CutCornerHudShape(5.dp))
      .background(c.copy(alpha = 0.14f))
      .border(1.dp, c.copy(alpha = 0.7f), CutCornerHudShape(5.dp))
      .padding(horizontal = 7.dp, vertical = 2.dp),
  ) {
    Text(priority.label, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
  }
}

/** Selectable HUD filter chip (used for category filters, prompt suggestions, toggles). */
@Composable
fun HudChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  accent: Color = HudColors.Cyan,
  icon: ImageVector? = null,
  count: Int? = null,
) {
  val bg = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent
  val shape = CutCornerHudShape(7.dp)
  Row(
    modifier
      .clip(shape)
      .background(bg)
      .border(1.dp, accent.copy(alpha = if (selected) 0.9f else 0.35f), shape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, null, tint = if (selected) accent else HudColors.TextDim, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(6.dp))
    }
    Text(
      text.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) accent else HudColors.TextDim,
      maxLines = 1,
    )
    if (count != null) {
      Spacer(Modifier.width(6.dp))
      Text(
        count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) HudColors.Background else accent,
        modifier = Modifier
          .background(if (selected) accent else accent.copy(alpha = 0.15f))
          .padding(horizontal = 5.dp, vertical = 1.dp),
      )
    }
  }
}

/** Large angular action button. */
@Composable
fun HudButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  accent: Color = HudColors.Cyan,
  icon: ImageVector? = null,
  filled: Boolean = true,
) {
  val shape = CutCornerHudShape(10.dp)
  val c = if (enabled) accent else HudColors.TextMuted
  Row(
    modifier
      .heightIn(min = 46.dp)
      .clip(shape)
      .background(if (filled) c.copy(alpha = if (enabled) 0.16f else 0.06f) else Color.Transparent)
      .border(1.dp, c.copy(alpha = 0.8f), shape)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (icon != null) {
      Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
  }
}

/** Metric tile: caption, big mono value, optional sub-line. */
@Composable
fun StatTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  accent: Color = HudColors.Cyan,
  sub: String? = null,
  icon: ImageVector? = null,
) {
  val shape = CutCornerHudShape(8.dp)
  Column(
    modifier
      .clip(shape)
      .background(accent.copy(alpha = 0.07f))
      .border(1.dp, accent.copy(alpha = 0.3f), shape)
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(icon, null, tint = accent.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
      }
      Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1)
    }
    Spacer(Modifier.height(4.dp))
    Text(
      value,
      style = MaterialTheme.typography.titleLarge,
      color = accent,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (sub != null) {
      Text(sub, style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim, maxLines = 1)
    }
  }
}

/** Thin segmented progress bar (storage, battery). */
@Composable
fun HudBar(fraction: Float, modifier: Modifier = Modifier, color: Color = HudColors.Cyan, segments: Int = 24) {
  val filled = (fraction.coerceIn(0f, 1f) * segments).toInt()
  Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    repeat(segments) { i ->
      Box(
        Modifier
          .weight(1f)
          .height(8.dp)
          .background(if (i < filled) color else color.copy(alpha = 0.12f)),
      )
    }
  }
}
