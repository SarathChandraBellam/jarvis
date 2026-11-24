package com.jarvis.hud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType

val CardShape = RoundedCornerShape(22.dp)

/** Rounded midnight bento surface card. Clickable when [onClick] is set. */
@Composable
fun JCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  color: Color = HudColors.Surface,
  borderColor: Color = HudColors.OutlineSoft,
  padding: Dp = 16.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier
      .fillMaxWidth()
      .clip(CardShape)
      .background(color)
      .border(1.dp, borderColor, CardShape)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
      .padding(padding),
    content = content,
  )
}

/** Editorial serif section heading above a group of bento cards. */
@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  trailing: String? = null,
  onTrailingClick: (() -> Unit)? = null,
) {
  Row(
    modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = JarvisType.SectionSerif, color = HudColors.Text, modifier = Modifier.weight(1f))
    if (trailing != null) {
      if (onTrailingClick != null) {
        TextButton(onClick = onTrailingClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
          Text(trailing, style = MaterialTheme.typography.labelLarge, color = HudColors.Amber)
        }
      } else {
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
      }
    }
  }
}

/** Circular tinted icon badge used in bento summary tiles and rows. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp) {
  Box(
    modifier
      .size(size)
      .clip(CircleShape)
      .background(tint.copy(alpha = 0.14f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.52f))
  }
}

/** Compact bento metric tile: circular icon badge, tabular value, caption. */
@Composable
fun StatCard(
  label: String,
  value: String,
  icon: ImageVector,
  tint: Color,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  JCard(modifier, onClick = onClick, padding = 14.dp) {
    IconBadge(icon, tint, size = 32.dp)
    Spacer(Modifier.height(12.dp))
    Text(
      value,
      style = JarvisType.MetricMedium,
      color = HudColors.Text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(label, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** Small rounded pill label, e.g. priority or "Free". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = color,
    maxLines = 1,
    modifier = modifier
      .clip(CircleShape)
      .background(color.copy(alpha = 0.16f))
      .padding(horizontal = 9.dp, vertical = 3.dp),
  )
}

/** Thin rounded progress track. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 7.dp) {
  val f = fraction.coerceIn(0f, 1f)
  Box(
    modifier
      .fillMaxWidth()
      .height(height)
      .clip(CircleShape)
      .background(HudColors.SurfaceHighest),
  ) {
    if (f > 0f) Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(color))
  }
}

/** Centered empty state with an optional action. */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  Column(
    modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    IconBadge(icon, HudColors.Amber, size = 56.dp)
    Spacer(Modifier.height(14.dp))
    Text(title, style = JarvisType.SectionSerif, color = HudColors.Text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim, textAlign = TextAlign.Center)
    if (action != null) {
      Spacer(Modifier.height(16.dp))
      action()
    }
  }
}

/** Muted one-line placeholder inside a card. */
@Composable
fun EmptyLine(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextMuted, modifier = modifier.padding(vertical = 4.dp))
}

/** Row with a leading circular badge, two lines of text and optional trailing content. */
@Composable
fun ListRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  tint: Color = HudColors.Sky,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start,
  ) {
    if (icon != null) {
      IconBadge(icon, tint)
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
      }
    }
    if (trailing != null) {
      Spacer(Modifier.width(12.dp))
      trailing()
    }
  }
}

@Composable
fun jarvisTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
  focusedTextColor = HudColors.Text,
  unfocusedTextColor = HudColors.Text,
  focusedBorderColor = HudColors.Cream,
  unfocusedBorderColor = HudColors.Outline,
  cursorColor = HudColors.Cream,
  focusedLabelColor = HudColors.Cream,
  unfocusedLabelColor = HudColors.TextDim,
  focusedContainerColor = HudColors.Surface,
  unfocusedContainerColor = HudColors.Surface,
  focusedTrailingIconColor = HudColors.Cream,
  unfocusedTrailingIconColor = HudColors.TextDim,
  focusedPlaceholderColor = HudColors.TextMuted,
  unfocusedPlaceholderColor = HudColors.TextMuted,
)

/** Pull-to-refresh container used by the list screens. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(
  refreshing: Boolean,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
  androidx.compose.material3.pulltorefresh.PullToRefreshBox(
    isRefreshing = refreshing,
    onRefresh = onRefresh,
    state = pullState,
    modifier = modifier,
    indicator = {
      androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
        state = pullState,
        isRefreshing = refreshing,
        modifier = Modifier.align(Alignment.TopCenter),
        containerColor = HudColors.SurfaceHigh,
        color = HudColors.Cream,
      )
    },
  ) { content() }
}
