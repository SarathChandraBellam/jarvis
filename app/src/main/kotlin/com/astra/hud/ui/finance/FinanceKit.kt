package com.astra.hud.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.hud.model.SubscriptionCategory
import com.astra.hud.ui.components.HudFormat
import com.astra.hud.ui.theme.HudColors
import com.astra.hud.ui.theme.AstraType
import java.util.Locale
import kotlin.math.roundToLong

/*
 * Warm paper & pastel visual vocabulary for the Subscriptions & Bills tab.
 */
internal object Fin {
  val Surface = HudColors.Background
  val Raised = HudColors.SurfaceHigh
  val Card = HudColors.Surface
  val Hairline = HudColors.OutlineSoft
  val Track = HudColors.SurfaceHigh

  val Ink = HudColors.Text
  val Ink2 = HudColors.TextDim
  val Ink3 = HudColors.TextMuted

  val Accent = HudColors.Blush
  val Link = HudColors.Sand
  val Positive = HudColors.Sage
  val Warning = HudColors.Sand
  val Critical = HudColors.Coral
  val Neutral = HudColors.SurfaceHighest

  fun subColor(c: SubscriptionCategory): Color = when (c) {
    SubscriptionCategory.STREAMING -> HudColors.Blush
    SubscriptionCategory.SOFTWARE -> HudColors.Lavender
    SubscriptionCategory.TELECOM -> HudColors.SlateBlue
    SubscriptionCategory.FITNESS -> HudColors.Sage
    SubscriptionCategory.UTILITIES -> HudColors.Sand
  }

  val Hero = AstraType.HeroSerif.copy(color = Ink)
  val Title = AstraType.SectionSerif.copy(color = Ink)
  val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, color = Ink, fontFeatureSettings = "tnum")
  val BodyStrong = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink, fontFeatureSettings = "tnum")
  val Small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = Ink2, fontFeatureSettings = "tnum")
  val Tiny = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = Ink3, fontFeatureSettings = "tnum")
  val Figure = AstraType.MetricMedium.copy(color = Ink)
}

internal fun rupees(v: Double) = HudFormat.money(v.roundToLong().toDouble())

/** ₹980 · ₹12.4K · ₹3.2L — for compact chart readouts. */
internal fun rupeesShort(v: Double): String = when {
  v >= 1_00_000 -> "₹" + trim(v / 1_00_000) + "L"
  v >= 1_000 -> "₹" + trim(v / 1_000) + "K"
  else -> "₹" + v.roundToLong()
}

private fun trim(x: Double) = String.format(Locale.ENGLISH, "%.1f", x).removeSuffix(".0")

/** Pastel pill three-way switch at the top of the Subs tab. */
@Composable
internal fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(CircleShape)
      .background(HudColors.SurfaceHigh)
      .border(1.dp, Fin.Hairline, CircleShape)
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    options.forEachIndexed { i, label ->
      val on = i == selected
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(CircleShape)
          .background(if (on) HudColors.Surface else Color.Transparent)
          .then(if (on) Modifier.border(1.dp, HudColors.Outline, CircleShape) else Modifier)
          .clickable(role = Role.Tab) { onSelect(i) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = Fin.Small.copy(
            color = if (on) HudColors.Text else Fin.Ink2,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
          ),
        )
      }
    }
  }
}

/** Editorial section heading with an optional link action on the right. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
    Text(title, style = Fin.Title, modifier = Modifier.weight(1f))
    if (action != null && onAction != null) {
      Text(
        action,
        style = Fin.Small.copy(color = HudColors.Blush, fontWeight = FontWeight.Medium),
        modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(4.dp),
      )
    }
  }
}

@Composable
internal fun Divider(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(1.dp).background(Fin.Hairline))
}

internal fun niceCeil(v: Double): Double {
  if (v <= 0) return 1.0
  val step = v / 3
  val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(step)))
  val n = step / mag
  val nice = when {
    n <= 1 -> 1.0
    n <= 2 -> 2.0
    n <= 2.5 -> 2.5
    n <= 5 -> 5.0
    else -> 10.0
  }
  return 3 * nice * mag
}
