package com.jarvis.hud.ui.finance

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
import com.jarvis.hud.insights.SpendCategory
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import java.util.Locale
import kotlin.math.roundToLong

/*
 * Calm midnight-bento visual vocabulary for the Money tab.
 * Editorial serif hero figures paired with tabular sans numerals and soft, low-glare accents.
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

  val Accent = HudColors.Sky
  val Link = HudColors.Cream
  val LastMonth = Color(0xFF5D6E8C)
  val Positive = HudColors.Mint
  val Warning = HudColors.Amber
  val Critical = HudColors.Coral
  val Neutral = Color(0xFF3E4B63)
  val NeutralLight = Color(0xFF8393AA)

  /** Sequential amber-gold ramp for the calm spend calendar. */
  val Heat = listOf(
    Color(0xFF21375B),
    Color(0xFF2F5386),
    Color(0xFF4579B8),
    Color(0xFF6BA3E5),
    Color(0xFF9AC5F6),
  )

  /** Calm categorical palette: colour stays bound to the category regardless of rank. */
  fun color(c: SpendCategory): Color = when (c) {
    SpendCategory.FOOD -> HudColors.Amber
    SpendCategory.SHOPPING -> HudColors.Coral
    SpendCategory.GROCERIES -> HudColors.Mint
    SpendCategory.BILLS -> HudColors.Peach
    SpendCategory.TRANSPORT -> HudColors.Sky
    SpendCategory.FUEL -> Color(0xFF7FD18C)
    SpendCategory.ENTERTAINMENT -> HudColors.Lavender
    SpendCategory.HEALTH -> Color(0xFFE589B4)
    SpendCategory.TRANSFERS, SpendCategory.OTHER -> NeutralLight
  }

  val Hero = JarvisType.HeroSerif.copy(color = Ink)
  val Title = JarvisType.SectionSerif.copy(color = Ink)
  val Body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, color = Ink, fontFeatureSettings = "tnum")
  val BodyStrong = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink, fontFeatureSettings = "tnum")
  val Small = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, color = Ink2, fontFeatureSettings = "tnum")
  val Tiny = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, color = Ink3, fontFeatureSettings = "tnum")
  val Figure = JarvisType.MetricMedium.copy(color = Ink)
}

internal fun rupees(v: Double) = HudFormat.money(v.roundToLong().toDouble())

/** ₹980 · ₹12.4K · ₹3.2L — for axis ticks and tight spots. */
internal fun rupeesShort(v: Double): String = when {
  v >= 1_00_000 -> "₹" + trim(v / 1_00_000) + "L"
  v >= 1_000 -> "₹" + trim(v / 1_000) + "K"
  else -> "₹" + v.roundToLong()
}

private fun trim(x: Double) = String.format(Locale.ENGLISH, "%.1f", x).removeSuffix(".0")

/** Pill-style three-way switch at the top of the Money tab. */
@Composable
internal fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(RoundedCornerShape(22.dp))
      .background(Fin.Card)
      .border(1.dp, Fin.Hairline, RoundedCornerShape(22.dp))
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    options.forEachIndexed { i, label ->
      val on = i == selected
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(18.dp))
          .background(if (on) HudColors.Cream else Color.Transparent)
          .clickable(role = Role.Tab) { onSelect(i) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = Fin.Small.copy(
            color = if (on) HudColors.OnCream else Fin.Ink2,
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
        style = Fin.Small.copy(color = HudColors.Amber, fontWeight = FontWeight.Medium),
        modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(4.dp),
      )
    }
  }
}

@Composable
internal fun Divider(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(1.dp).background(Fin.Hairline))
}
