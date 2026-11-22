package com.jarvis.hud.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val JarvisColorScheme = darkColorScheme(
  primary = HudColors.Cyan,
  onPrimary = HudColors.OnCyan,
  primaryContainer = HudColors.CyanSoft,
  onPrimaryContainer = HudColors.Cyan,
  secondary = HudColors.Amber,
  onSecondary = HudColors.Background,
  secondaryContainer = HudColors.SurfaceHighest,
  onSecondaryContainer = HudColors.Text,
  tertiary = HudColors.Green,
  onTertiary = HudColors.Background,
  error = HudColors.Red,
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
  inverseSurface = HudColors.Text,
  inverseOnSurface = HudColors.Background,
  inversePrimary = HudColors.OnCyan,
)

private val Sans = FontFamily.SansSerif

private val JarvisTypography = Typography(
  displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
  headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
  headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
  titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
  titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
  titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
  bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp),
  bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
  labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
  labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
  labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)

private val JarvisShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
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
