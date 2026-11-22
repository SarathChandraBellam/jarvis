package com.jarvis.hud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.ArcReactor
import com.jarvis.hud.ui.components.JCard
import com.jarvis.hud.ui.components.PermissionList
import com.jarvis.hud.ui.components.jarvisTextFieldColors
import com.jarvis.hud.ui.theme.HudColors

/** First-run welcome: name + data access, then into the app. */
@Composable
fun OnboardingScreen(state: JarvisUiState, actions: JarvisActions) {
  var name by rememberSaveable { mutableStateOf(state.settings.userCallsign.takeIf { it != "Sir" } ?: "") }
  fun finish() {
    actions.onUpdateSettings(
      state.settings.copy(userCallsign = name.trim().ifBlank { "Sir" }, onboardingDone = true),
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ArcReactor(size = 120.dp, active = true)
      Spacer(Modifier.height(4.dp))
      Text("Meet Jarvis", style = MaterialTheme.typography.displaySmall, color = HudColors.Text)
      Text(
        "Your bank alerts, bills, deliveries, calendar and calls, sorted into one calm daily briefing. Everything is read on this phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = HudColors.TextDim,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("What should I call you?") },
        placeholder = { Text("Sir") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium,
        colors = jarvisTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("onboardingName"),
      )
      JCard(padding = 12.dp) {
        Text(
          "Allow access",
          style = MaterialTheme.typography.titleMedium,
          color = HudColors.Text,
          modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Text(
          "Pick what Jarvis can read. You can change this any time in Settings.",
          style = MaterialTheme.typography.bodySmall,
          color = HudColors.TextDim,
          modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        PermissionList(
          state.permissions,
          actions::onRequestRuntimePermissions,
          actions::onOpenNotificationAccessSettings,
          actions::onOpenUsageAccessSettings,
        )
      }
      Spacer(Modifier.height(4.dp))
      Button(
        onClick = ::finish,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboardingStart"),
        colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cyan, contentColor = HudColors.OnCyan),
      ) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
      if (state.permissions.grantedCount == 0) {
        TextButton(onClick = ::finish) { Text("Skip for now") }
      }
    }
  }
}
