package com.jarvis.hud.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val JarvisColorScheme = darkColorScheme(
  primary = HudColors.Cream,
  onPrimary = HudColors.OnCream,
  primaryContainer = HudColors.BlushSoft,
  onPrimaryContainer = HudColors.Cream,
  secondary = HudColors.Sand,
  onSecondary = HudColors.OnCream,
  secondaryContainer = HudColors.SandSoft,
  onSecondaryContainer = HudColors.Text,
  tertiary = HudColors.Sage,
  onTertiary = HudColors.OnCream,
  error = HudColors.Coral,
  onError = HudColors.OnCream,
  background = HudColors.Background,
  onBackground = HudColors.Text,
  surface = HudColors.Surface,
  onSurface = HudColors.Text,
  surfaceVariant = HudColors.SurfaceHigh,
  onSurfaceVariant = HudColors.TextDim,
  surfaceContainerLowest = HudColors.Background,
  surfaceContainerLow = HudColors.Surface,
  surfaceContainer = HudColors.Surface,
  surfaceContainerHigh = HudColors.SurfaceHigh,
  surfaceContainerHighest = HudColors.SurfaceHighest,
  outline = HudColors.Outline,
  outlineVariant = HudColors.OutlineSoft,
  inverseSurface = HudColors.Text,
  inverseOnSurface = HudColors.Background,
  inversePrimary = HudColors.Cream,
)

private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

/** Tactical Stark HUD display + tabular numeric roles. */
object JarvisType {
  val HeroSerif = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 32.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
  )
  val TitleSerif = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
  )
  val SectionSerif = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.2.sp,
  )
  val MetricLarge = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.4).sp,
    fontFeatureSettings = "tnum",
  )
  val MetricMedium = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = "tnum",
  )
  val PromptItalic = TextStyle(
    fontFamily = Mono,
    fontStyle = FontStyle.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp,
  )
}

private val JarvisTypography = Typography(
  displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
  headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
  headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
  titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
  titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
  titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
  bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.5.sp, lineHeight = 18.sp),
  labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum"),
  labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
  labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp, fontFeatureSettings = "tnum"),
)

private val JarvisShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = JarvisColorScheme,
    typography = JarvisTypography,
    shapes = JarvisShapes,
    content = content,
  )
}
