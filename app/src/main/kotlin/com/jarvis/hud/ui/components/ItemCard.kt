package com.jarvis.hud.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.ui.theme.HudColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Intel item card. Swipe right = DONE, swipe left = DISMISS; explicit buttons are also shown
 * for active items. [onStatus] receives (itemId, newStatus).
 */
@Composable
fun ItemCard(
  item: OrganizedItem,
  onStatus: (String, ItemStatus) -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  val accent = item.category.accent()
  val pColor = item.priority.color()
  val active = item.status == ItemStatus.ACTIVE
  val shape = CutCornerHudShape(10.dp, 3.dp)
  val scope = rememberCoroutineScope()
  val offsetX = remember(item.id) { Animatable(0f) }
  val threshold = with(LocalDensity.current) { 110.dp.toPx() }

  Box(modifier.fillMaxWidth()) {
    // Swipe reveal background.
    if (active) {
      val dx = offsetX.value
      val revealColor = if (dx >= 0) HudColors.Green else HudColors.Red
      Row(
        Modifier
          .matchParentSize()
          .clip(shape)
          .background(revealColor.copy(alpha = (abs(dx) / threshold).coerceIn(0f, 1f) * 0.25f))
          .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (dx >= 0) Arrangement.Start else Arrangement.End,
      ) {
        if (dx != 0f) {
          Text(if (dx > 0) "DONE ✓" else "DISMISS ✕", style = MaterialTheme.typography.labelLarge, color = revealColor)
        }
      }
    }

    Row(
      Modifier
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .clip(shape)
        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), HudColors.Panel)))
        .border(1.dp, accent.copy(alpha = 0.28f), shape)
        .then(
          if (active) {
            Modifier.pointerInput(item.id) {
              detectHorizontalDragGestures(
                onDragEnd = {
                  scope.launch {
                    val v = offsetX.value
                    when {
                      v > threshold -> onStatus(item.id, ItemStatus.DONE)
                      v < -threshold -> onStatus(item.id, ItemStatus.DISMISSED)
                    }
                    offsetX.snapTo(0f)
                  }
                },
                onDragCancel = { scope.launch { offsetX.snapTo(0f) } },
              ) { change, amount ->
                change.consume()
                scope.launch { offsetX.snapTo(offsetX.value + amount) }
              }
            }
          } else Modifier,
        ),
    ) {
      // Priority rail.
      Box(Modifier.width(3.dp).fillMaxHeight().background(pColor))
      Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(item.category.icon(), null, tint = accent, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            item.category.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
          )
          Spacer(Modifier.width(8.dp))
          Text(HudFormat.relative(item.timestamp), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
          Spacer(Modifier.weight(1f))
          if (item.priority.ordinal <= 1 || !compact) PriorityChip(item.priority)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Top) {
          Column(Modifier.weight(1f)) {
            Text(
              item.title,
              style = MaterialTheme.typography.titleSmall,
              color = if (active) HudColors.Text else HudColors.TextMuted,
              textDecoration = if (item.status == ItemStatus.DONE) TextDecoration.LineThrough else null,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            if (item.summary.isNotBlank()) {
              Text(
                item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = HudColors.TextDim,
                maxLines = if (compact) 1 else 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          item.amount?.let { amt ->
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
              Text(
                (if (item.isCredit) "+" else "−") + HudFormat.money(amt, item.currency),
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isCredit) HudColors.Green else HudColors.Red,
              )
              item.merchant?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1)
              }
            }
          }
        }
        if (item.dueDate != null || (active && !compact) || !active) {
          Spacer(Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            item.dueDate?.let {
              Icon(Icons.Outlined.Schedule, null, tint = HudColors.Amber, modifier = Modifier.size(13.dp))
              Spacer(Modifier.width(4.dp))
              Text("DUE ${it.uppercase()}", style = MaterialTheme.typography.labelSmall, color = HudColors.Amber)
            }
            Spacer(Modifier.weight(1f))
            if (active) {
              if (!compact) {
                CardAction("DONE", Icons.Outlined.Check, HudColors.Green) { onStatus(item.id, ItemStatus.DONE) }
                Spacer(Modifier.width(8.dp))
                CardAction("DISMISS", Icons.Outlined.Close, HudColors.TextDim) { onStatus(item.id, ItemStatus.DISMISSED) }
              }
            } else {
              Text(
                if (item.status == ItemStatus.DONE) "RESOLVED" else "DISMISSED",
                style = MaterialTheme.typography.labelSmall,
                color = HudColors.TextMuted,
              )
              Spacer(Modifier.width(8.dp))
              CardAction("RESTORE", null, HudColors.Cyan) { onStatus(item.id, ItemStatus.ACTIVE) }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CardAction(label: String, icon: ImageVector?, color: Color, onClick: () -> Unit) {
  Row(
    Modifier
      .clip(CutCornerHudShape(5.dp))
      .border(1.dp, color.copy(alpha = 0.5f), CutCornerHudShape(5.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 9.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
      Spacer(Modifier.width(4.dp))
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
  }
}
