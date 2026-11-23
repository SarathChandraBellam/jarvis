package com.jarvis.hud.ui.finance

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.hud.insights.SpendCategory
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.HudSans
import java.util.Locale
import kotlin.math.roundToLong

/*
 * Visual vocabulary for the Finance tab. Kept deliberately small: one sans face, three text
 * sizes, a surface + two hairline tones, and a categorical palette validated (CVD + contrast)
 * against the app background #050A14.
 */
internal object Fin {
  val Surface = HudColors.Background
  val Raised = Color(0xFF0D1621)
  val Hairline = Color(0xFF17222E)
  val Track = Color(0xFF111B26)

  val Ink = Color(0xFFE6EEF2)
  val Ink2 = Color(0xFF9AAAB5)
  val Ink3 = Color(0xFF5F707C)

  val Accent = Color(0xFF3987E5)
  val LastMonth = Color(0xFF4B5A66)
  val Positive = Color(0xFF3DBE8B)
  val Warning = Color(0xFFE0A33A)
  val Critical = Color(0xFFE66767)
  /** "Everything else" in the category bar. */
  val Neutral = Color(0xFF3A4651)
  /** People & transfers / uncategorised: a lighter grey so it never matches "everything else". */
  val NeutralLight = Color(0xFF7C8B96)

  /** Sequential blue for the spend calendar, dim → bright (validated as an ordinal ramp on the surface). */
  val Heat = listOf(Color(0xFF184F95), Color(0xFF256ABF), Color(0xFF3987E5), Color(0xFF6DA7EC), Color(0xFF9EC5F4))

  /** Colour follows the category, never its rank: a filter must not repaint survivors. */
  fun color(c: SpendCategory): Color = when (c) {
    SpendCategory.FOOD -> Color(0xFF3987E5)
    SpendCategory.SHOPPING -> Color(0xFFD95926)
    SpendCategory.GROCERIES -> Color(0xFF199E70)
    SpendCategory.BILLS -> Color(0xFFC98500)
    SpendCategory.TRANSPORT -> Color(0xFFD55181)
    SpendCategory.FUEL -> Color(0xFF0CA30C)
    SpendCategory.ENTERTAINMENT -> Color(0xFF9085E9)
    SpendCategory.HEALTH -> Color(0xFFE66767)
    SpendCategory.TRANSFERS, SpendCategory.OTHER -> NeutralLight
  }

  val Hero = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, letterSpacing = (-1).sp, color = Ink)
  val Title = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink)
  val Body = TextStyle(fontFamily = HudSans, fontSize = 15.sp, color = Ink)
  val BodyStrong = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Ink)
  val Small = TextStyle(fontFamily = HudSans, fontSize = 13.sp, color = Ink2)
  val Tiny = TextStyle(fontFamily = HudSans, fontSize = 11.sp, color = Ink3)
  val Figure = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = Ink)
}

internal fun rupees(v: Double) = HudFormat.money(v.roundToLong().toDouble())

/** ₹980 · ₹12.4K · ₹3.2L — for axis ticks and tight spots. */
internal fun rupeesShort(v: Double): String = when {
  v >= 1_00_000 -> "₹" + trim(v / 1_00_000) + "L"
  v >= 1_000 -> "₹" + trim(v / 1_000) + "K"
  else -> "₹" + v.roundToLong()
}

private fun trim(x: Double) = String.format(Locale.ENGLISH, "%.1f", x).removeSuffix(".0")

/** Three-way switch at the top of the tab. */
@Composable
internal fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(Fin.Track).padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    options.forEachIndexed { i, label ->
      val on = i == selected
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(8.dp))
          .background(if (on) Fin.Raised else Color.Transparent)
          .clickable(role = Role.Tab) { onSelect(i) },
        contentAlignment = Alignment.Center,
      ) {
        Text(label, style = Fin.Small.copy(color = if (on) Fin.Ink else Fin.Ink3, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal))
      }
    }
  }
}

/** Section heading with an optional action on the right. */
@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
  Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
    Text(title, style = Fin.Title, modifier = Modifier.weight(1f))
    if (action != null && onAction != null) {
      Text(action, style = Fin.Small.copy(color = Fin.Accent), modifier = Modifier.clickable(role = Role.Button, onClick = onAction).padding(4.dp))
    }
  }
}

@Composable
internal fun Divider(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().height(1.dp).background(Fin.Hairline))
}
