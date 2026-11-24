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
  primaryContainer = HudColors.CyanSoft,
  onPrimaryContainer = HudColors.Sky,
  secondary = HudColors.Amber,
  onSecondary = HudColors.Background,
  secondaryContainer = HudColors.SurfaceHighest,
  onSecondaryContainer = HudColors.Text,
  tertiary = HudColors.Mint,
  onTertiary = HudColors.Background,
  error = HudColors.Coral,
  onError = HudColors.Background,
  background = HudColors.Background,
  onBackground = HudColors.Text,
  surface = HudColors.Background,
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
  inverseSurface = HudColors.Cream,
  inverseOnSurface = HudColors.OnCream,
  inversePrimary = HudColors.OnCream,
)

private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif

/** Editorial display + tabular numeric roles used across bento cards and charts. */
object JarvisType {
  val HeroSerif = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.Medium,
    fontSize = 36.sp,
    lineHeight = 42.sp,
    letterSpacing = (-0.6).sp,
    fontFeatureSettings = "tnum",
  )
  val TitleSerif = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.3).sp,
  )
  val SectionSerif = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.Medium,
    fontSize = 19.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp,
  )
  val MetricLarge = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum",
  )
  val MetricMedium = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    fontFeatureSettings = "tnum",
  )
  val PromptItalic = TextStyle(
    fontFamily = Serif,
    fontStyle = FontStyle.Italic,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  )
}

private val JarvisTypography = Typography(
  displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
  headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
  headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
  titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 26.sp),
  titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
  titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
  bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp),
  bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
  labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
  labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
  labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp, fontFeatureSettings = "tnum"),
)

private val JarvisShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(30.dp),
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
