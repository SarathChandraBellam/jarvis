package com.jarvis.hud.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Monospace for labels, numbers and telemetry. */
val HudMono = FontFamily.Monospace

/** Sans for prose (summaries, chat). */
val HudSans = FontFamily.SansSerif

private val JarvisColorScheme = darkColorScheme(
  primary = HudColors.Cyan,
  onPrimary = HudColors.Background,
  primaryContainer = HudColors.CyanFaint,
  onPrimaryContainer = HudColors.Cyan,
  secondary = HudColors.Amber,
  onSecondary = HudColors.Background,
  tertiary = HudColors.Green,
  onTertiary = HudColors.Background,
  error = HudColors.Red,
  onError = HudColors.Background,
  background = HudColors.Background,
  onBackground = HudColors.Text,
  surface = HudColors.Background,
  onSurface = HudColors.Text,
  surfaceVariant = HudColors.PanelSolid,
  onSurfaceVariant = HudColors.TextDim,
  surfaceContainer = HudColors.PanelSolid,
  surfaceContainerHigh = HudColors.BackgroundElevated,
  surfaceContainerHighest = HudColors.BackgroundElevated,
  surfaceContainerLow = HudColors.Background,
  outline = HudColors.Border,
  outlineVariant = HudColors.BorderDim,
  inverseSurface = HudColors.Cyan,
  inverseOnSurface = HudColors.Background,
)

private val JarvisTypography = Typography(
  displaySmall = TextStyle(fontFamily = HudMono, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 2.sp),
  headlineSmall = TextStyle(fontFamily = HudMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.5.sp),
  titleLarge = TextStyle(fontFamily = HudMono, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 1.sp),
  titleMedium = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.2.sp),
  titleSmall = TextStyle(fontFamily = HudSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
  bodyLarge = TextStyle(fontFamily = HudSans, fontSize = 15.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontFamily = HudSans, fontSize = 14.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = HudSans, fontSize = 12.sp, lineHeight = 16.sp),
  labelLarge = TextStyle(fontFamily = HudMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp),
  labelMedium = TextStyle(fontFamily = HudMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp),
  labelSmall = TextStyle(fontFamily = HudMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = JarvisColorScheme,
    typography = JarvisTypography,
    content = content,
  )
}
