package com.astra.hud.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Original ambient obsidian palette: deep matte charcoal canvas, graphite cards,
 * electric indigo primary accent, live mint health indicators, warm amber alerts,
 * and soft rose highlights.
 */
object HudColors {
  val Background = Color(0xFF0B0D12)
  val Surface = Color(0xFF131722)
  val SurfaceHigh = Color(0xFF1B2130)
  val SurfaceHighest = Color(0xFF252D40)
  val DockGlass = Color(0xF2121620)

  val Outline = Color(0xFF283146)
  val OutlineSoft = Color(0xFF1C2333)

  // Primary electric indigo accent and crisp dark ink on primary controls.
  val Cream = Color(0xFF7C9BFF)
  val OnCream = Color(0xFF090D16)

  // Soft rose accent for cycle/urgent signals.
  val Blush = Color(0xFFFB7185)
  val BlushSoft = Color(0xFF291620)

  // Warm amber for renewals, bills, and attention signals.
  val Sand = Color(0xFFF5B14C)
  val Amber = Sand
  val Gold = Sand
  val SandSoft = Color(0xFF261E12)

  // Live mint for health telemetry, steps, and completed actions.
  val Sage = Color(0xFF34D399)
  val Mint = Sage
  val Green = Sage
  val SageSoft = Color(0xFF0F2620)

  // Soft violet for AI classification and sleep telemetry.
  val Lavender = Color(0xFFA78BFA)
  val Violet = Lavender
  val LavenderSoft = Color(0xFF1E1A32)

  // Cool azure accent for voice presence and active status.
  val SlateBlue = Color(0xFF60A5FA)
  val SlateBlueSoft = Color(0xFF121E32)
  val Sky = SlateBlue
  val Cyan = SlateBlue
  val CyanSoft = SlateBlueSoft
  val OnCyan = Color(0xFF090D16)

  val Peach = Color(0xFFFB923C)
  val PeachSoft = Color(0xFF281A12)

  val Coral = Color(0xFFF87171)
  val Red = Coral

  val Text = Color(0xFFF3F5FA)
  val TextDim = Color(0xFF98A3B8)
  val TextMuted = Color(0xFF636E85)
}
