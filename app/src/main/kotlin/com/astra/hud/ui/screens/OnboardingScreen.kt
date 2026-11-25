package com.astra.hud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astra.hud.model.AstraActions
import com.astra.hud.model.AstraUiState
import com.astra.hud.model.UserGender
import com.astra.hud.ui.components.HaloEmblem
import com.astra.hud.ui.components.PermissionList
import com.astra.hud.ui.components.astraTextFieldColors
import com.astra.hud.ui.theme.HudColors
import com.astra.hud.ui.theme.AstraType
import com.astra.hud.ui.viz.BentoCard

@Composable
fun OnboardingScreen(state: AstraUiState, actions: AstraActions) {
  var name by rememberSaveable { mutableStateOf(state.settings.userCallsign.takeIf { it != "Sir" } ?: "") }
  var selectedGenderName by rememberSaveable { mutableStateOf(state.settings.userGender.name) }
  val selectedGender = UserGender.entries.firstOrNull { it.name == selectedGenderName } ?: UserGender.FEMALE
  fun finish() {
    actions.onUpdateSettings(
      state.settings.copy(
        userCallsign = name.trim().ifBlank { "Sir" },
        userGender = selectedGender,
        cycleTrackingEnabled = selectedGender == UserGender.FEMALE,
        onboardingDone = true,
      ),
    )
  }

  Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier
        .widthIn(max = 560.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp)
        .testTag("onboarding"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      HaloEmblem(size = 112.dp, active = true)
      Spacer(Modifier.height(4.dp))
      Text("Meet ASTRA", style = AstraType.HeroSerif, color = HudColors.Text)
      Text(
        "Your subscriptions, bills, deliveries, day plan and proactive reminders in one calm place. Everything stays on this phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = HudColors.TextDim,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("What should I call you?") },
        placeholder = { Text("Sir") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium,
        colors = astraTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("onboardingName"),
      )
      BentoCard(padding = 14.dp) {
        Text(
          "Profile",
          style = AstraType.SectionSerif,
          color = HudColors.Text,
          modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        Text(
          "Tailors your Today dashboard (shows Cycle & period tracker for Female).",
          style = MaterialTheme.typography.bodySmall,
          color = HudColors.TextDim,
          modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          UserGender.entries.forEach { gender ->
            val isSelected = selectedGender == gender
            FilterChip(
              selected = isSelected,
              onClick = { selectedGenderName = gender.name },
              label = { Text(gender.label) },
              shape = CircleShape,
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = if (gender == UserGender.FEMALE) HudColors.BlushSoft else HudColors.SlateBlueSoft,
                selectedLabelColor = HudColors.Text,
              ),
              modifier = Modifier.testTag("onboardingGender-${gender.name}"),
            )
          }
        }
      }
      BentoCard(padding = 14.dp) {
        Text(
          "Allow access",
          style = AstraType.SectionSerif,
          color = HudColors.Text,
          modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
        Text(
          "Pick what ASTRA can read and notify you about. You can change this any time in Settings.",
          style = MaterialTheme.typography.bodySmall,
          color = HudColors.TextDim,
          modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        PermissionList(
          state.permissions,
          actions::onRequestRuntimePermissions,
          actions::onOpenNotificationAccessSettings,
          actions::onOpenUsageAccessSettings,
          includePostNotifications = true,
        )
      }
      Spacer(Modifier.height(4.dp))
      Button(
        onClick = ::finish,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("onboardingStart"),
        colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
      ) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
      if (state.permissions.grantedCount == 0) {
        TextButton(onClick = ::finish) { Text("Skip for now", color = HudColors.TextDim) }
      }
    }
  }
}
