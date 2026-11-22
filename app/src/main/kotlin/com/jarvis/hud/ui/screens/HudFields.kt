package com.jarvis.hud.ui.screens

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import com.jarvis.hud.ui.theme.HudColors

@Composable
internal fun hudTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
  focusedTextColor = HudColors.Text,
  unfocusedTextColor = HudColors.Text,
  focusedBorderColor = HudColors.Cyan,
  unfocusedBorderColor = HudColors.Border,
  cursorColor = HudColors.Cyan,
  focusedLabelColor = HudColors.Cyan,
  unfocusedLabelColor = HudColors.TextDim,
  focusedContainerColor = HudColors.Panel,
  unfocusedContainerColor = HudColors.Panel,
  focusedTrailingIconColor = HudColors.Cyan,
  unfocusedTrailingIconColor = HudColors.TextDim,
)
