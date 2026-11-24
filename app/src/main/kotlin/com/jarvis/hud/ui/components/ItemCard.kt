package com.jarvis.hud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.OrganizedItem
import com.jarvis.hud.model.Priority
import com.jarvis.hud.ui.theme.HudColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Calm bento card for one organized item. Swipe right = done, swipe left = dismiss.
 */
@Composable
fun ItemCard(
  item: OrganizedItem,
  onStatus: (String, ItemStatus) -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val accent = item.category.accent()
  val active = item.status == ItemStatus.ACTIVE
  var dragX by remember(item.id) { mutableFloatStateOf(0f) }
  val threshold = with(LocalDensity.current) { 96.dp.toPx() }

  Box(modifier.fillMaxWidth()) {
    if (active && dragX != 0f) {
      val revealColor = if (dragX > 0) HudColors.Mint else HudColors.Coral
      Row(
        Modifier
          .matchParentSize()
          .clip(CardShape)
          .background(revealColor.copy(alpha = (abs(dragX) / threshold).coerceIn(0f, 1f) * 0.22f))
          .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (dragX > 0) Arrangement.Start else Arrangement.End,
      ) {
        Text(if (dragX > 0) "Done" else "Dismiss", style = MaterialTheme.typography.labelLarge, color = revealColor)
      }
    }

    Column(
      Modifier
        .offset { IntOffset(dragX.roundToInt(), 0) }
        .fillMaxWidth()
        .clip(CardShape)
        .background(HudColors.Surface)
        .border(1.dp, HudColors.OutlineSoft, CardShape)
        .then(
          if (active) {
            Modifier.pointerInput(item.id) {
              detectHorizontalDragGestures(
                onDragEnd = {
                  when {
                    dragX > threshold -> onStatus(item.id, ItemStatus.DONE)
                    dragX < -threshold -> onStatus(item.id, ItemStatus.DISMISSED)
                  }
                  dragX = 0f
                },
                onDragCancel = { dragX = 0f },
              ) { change, amount ->
                change.consume()
                dragX += amount
              }
            }
          } else Modifier,
        )
        .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open details", onClick = onClick) else Modifier)
        .testTag("item-${item.id}")
        .padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = if (compact) 15.dp else 7.dp),
    ) {
      Row(verticalAlignment = Alignment.Top) {
        IconBadge(item.category.icon(), if (active) accent else HudColors.TextMuted)
        Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.size(3.dp))
            Text(
              item.summary,
              style = MaterialTheme.typography.bodySmall,
              color = HudColors.TextDim,
              maxLines = if (compact) 1 else 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.size(7.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.priority == Priority.CRITICAL || item.priority == Priority.HIGH) {
              Pill(item.priority.displayLabel(), item.priority.color())
              Spacer(Modifier.width(8.dp))
            }
            val meta = buildList {
              add(HudFormat.relative(item.timestamp))
              item.dueDate?.let { add("Due $it") }
            }.joinToString(" · ")
            Text(
              meta,
              style = MaterialTheme.typography.labelMedium,
              color = if (item.dueDate != null && active) HudColors.Amber else HudColors.TextMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        item.amount?.let { amt ->
          Spacer(Modifier.width(10.dp))
          Column(horizontalAlignment = Alignment.End) {
            Text(
              (if (item.isCredit) "+" else "−") + HudFormat.money(amt, item.currency),
              style = MaterialTheme.typography.titleSmall,
              color = if (item.isCredit) HudColors.Mint else HudColors.Text,
              maxLines = 1,
            )
            item.merchant?.let {
              Text(it, style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted, maxLines = 1)
            }
          }
        }
      }
      if (!compact) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
          if (active) {
            CardAction("Dismiss", Icons.Outlined.Close, HudColors.TextDim) { onStatus(item.id, ItemStatus.DISMISSED) }
            CardAction("Done", Icons.Outlined.Check, HudColors.Mint) { onStatus(item.id, ItemStatus.DONE) }
          } else {
            Text(
              if (item.status == ItemStatus.DONE) "Completed" else "Dismissed",
              style = MaterialTheme.typography.labelMedium,
              color = HudColors.TextMuted,
              modifier = Modifier.weight(1f).padding(start = 48.dp),
            )
            CardAction("Restore", Icons.AutoMirrored.Outlined.Undo, HudColors.Cream) { onStatus(item.id, ItemStatus.ACTIVE) }
          }
        }
      }
    }
  }
}

@Composable
private fun CardAction(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    colors = ButtonDefaults.textButtonColors(contentColor = color),
  ) {
    Icon(icon, null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelLarge)
  }
}
