package com.jarvis.hud.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.ui.components.CutCornerHudShape
import com.jarvis.hud.ui.components.HudButton
import com.jarvis.hud.ui.components.HudChip
import com.jarvis.hud.ui.components.HudEmptyLine
import com.jarvis.hud.ui.components.HudPanel
import com.jarvis.hud.ui.theme.HudColors

/**
 * Full-screen settings overlay. Edits stay local until SAVE (→ onUpdateSettings).
 * Note: test connection / fetch models act on the *persisted* settings, so we save first.
 */
@Composable
fun SettingsScreen(state: JarvisUiState, actions: JarvisActions) {
  var draft by remember(state.settings) { mutableStateOf(state.settings) }
  var showKey by remember { mutableStateOf(false) }
  val dirty = draft != state.settings
  BackHandler(onBack = actions::onCloseSettings)

  Column(
    Modifier
      .fillMaxSize()
      .background(HudColors.Background)
      // Swallow touches that children don't handle so they never reach the HUD underneath.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) awaitPointerEvent().changes.forEach { it.consume() }
        }
      }
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    // Header.
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = actions::onCloseSettings) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close settings", tint = HudColors.Cyan)
      }
      Column(Modifier.weight(1f)) {
        Text("SYSTEM CONFIG", style = MaterialTheme.typography.titleLarge, color = HudColors.Cyan)
        Text(
          if (dirty) "UNSAVED CHANGES" else "ALL CHANGES SAVED",
          style = MaterialTheme.typography.labelSmall,
          color = if (dirty) HudColors.Amber else HudColors.TextMuted,
        )
      }
      HudButton(
        "Save",
        onClick = { actions.onUpdateSettings(draft) },
        enabled = dirty,
        icon = Icons.Outlined.Save,
        modifier = Modifier.padding(end = 12.dp),
      )
    }

    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      HudPanel(title = "Identity") {
        OutlinedTextField(
          value = draft.userCallsign,
          onValueChange = { draft = draft.copy(userCallsign = it) },
          label = { Text("Callsign") },
          singleLine = true,
          shape = CutCornerHudShape(8.dp),
          colors = hudTextFieldColors(),
          modifier = Modifier.fillMaxWidth(),
        )
      }

      HudPanel(title = "OpenRouter uplink") {
        OutlinedTextField(
          value = draft.apiKey,
          onValueChange = { draft = draft.copy(apiKey = it.trim()) },
          label = { Text("API key") },
          placeholder = { Text("sk-or-v1-…", color = HudColors.TextMuted) },
          singleLine = true,
          visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
              Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showKey) "Hide key" else "Show key")
            }
          },
          shape = CutCornerHudShape(8.dp),
          colors = hudTextFieldColors(),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = draft.modelId,
          onValueChange = { draft = draft.copy(modelId = it.trim()) },
          label = { Text("Model id") },
          singleLine = true,
          shape = CutCornerHudShape(8.dp),
          colors = hudTextFieldColors(),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          HudButton(
            if (state.isLoadingModels) "Fetching…" else "Fetch models",
            onClick = {
              if (dirty) actions.onUpdateSettings(draft)
              actions.onFetchOpenRouterModels()
            },
            enabled = !state.isLoadingModels && draft.apiKey.isNotBlank(),
            icon = Icons.Outlined.Refresh,
            filled = false,
            modifier = Modifier.fillMaxWidth(),
          )
          HudButton(
            "Test connection",
            onClick = {
              if (dirty) actions.onUpdateSettings(draft)
              actions.onTestOpenRouterConnection()
            },
            enabled = draft.apiKey.isNotBlank(),
            icon = Icons.Outlined.NetworkCheck,
            filled = false,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        state.connectionTestResult?.let { r ->
          Spacer(Modifier.height(10.dp))
          val c = if (r.success) HudColors.Green else HudColors.Red
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = c, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(r.message, style = MaterialTheme.typography.bodySmall, color = c, modifier = Modifier.weight(1f))
            if (r.latencyMs > 0) Text("${r.latencyMs} MS", style = MaterialTheme.typography.labelMedium, color = c)
          }
        }
        if (state.isLoadingModels) {
          Spacer(Modifier.height(10.dp))
          CircularProgressIndicator(color = HudColors.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
        if (state.availableModels.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          ModelList(state.availableModels, draft.modelId) { draft = draft.copy(modelId = it) }
        }
      }

      HudPanel(title = "Protocols") {
        ToggleRow("Demo mode", "Use the built-in synthetic dataset", draft.demoModeEnabled) {
          draft = draft.copy(demoModeEnabled = it)
        }
        ToggleRow("Voice", "Spoken briefings and replies", draft.voiceEnabled) { draft = draft.copy(voiceEnabled = it) }
        ToggleRow("Redact sensitive data", "Mask OTPs, card and account numbers before AI", draft.redactSensitiveData) {
          draft = draft.copy(redactSensitiveData = it)
        }
      }

      HudPanel(title = "Data sources") {
        SourceGrid("Collect", draft.enabledSources) { draft = draft.copy(enabledSources = it) }
        Spacer(Modifier.height(12.dp))
        SourceGrid("Share with AI", draft.aiShareSources, HudColors.Amber) { draft = draft.copy(aiShareSources = it) }
        Spacer(Modifier.height(6.dp))
        Text(
          "Sources not shared are organized by the on-device parser only.",
          style = MaterialTheme.typography.bodySmall,
          color = HudColors.TextMuted,
        )
      }

      HudPanel(title = "Maintenance", accent = HudColors.Red) {
        HudButton("Clear AI cache", actions::onClearAiCache, Modifier.fillMaxWidth(), accent = HudColors.Red,
          icon = Icons.Outlined.DeleteSweep, filled = false)
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ModelList(models: List<OpenRouterModelInfo>, selectedId: String, onSelect: (String) -> Unit) {
  var freeOnly by remember { mutableStateOf(false) }
  val shown = if (freeOnly) models.filter { it.isFree } else models
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("${shown.size} MODELS", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim, modifier = Modifier.weight(1f))
    HudChip("Free only", freeOnly, { freeOnly = !freeOnly }, accent = HudColors.Green)
  }
  Spacer(Modifier.height(8.dp))
  LazyColumn(
    Modifier
      .fillMaxWidth()
      .heightIn(max = 300.dp)
      .border(1.dp, HudColors.BorderDim, CutCornerHudShape(8.dp)),
    contentPadding = PaddingValues(4.dp),
  ) {
    items(shown.size, key = { shown[it].id }) { i ->
      val m = shown[i]
      val sel = m.id == selectedId
      Row(
        Modifier
          .fillMaxWidth()
          .background(if (sel) HudColors.CyanFaint else HudColors.Background.copy(alpha = 0f))
          .clickable { onSelect(m.id) }
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(m.name, style = MaterialTheme.typography.bodySmall, color = if (sel) HudColors.Cyan else HudColors.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            m.id + (m.contextLength?.let { " · ${it / 1000}K CTX" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
          )
        }
        if (m.isFree) {
          Box(
            Modifier
              .border(1.dp, HudColors.Green, CutCornerHudShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 1.dp),
          ) { Text("FREE", style = MaterialTheme.typography.labelSmall, color = HudColors.Green) }
        }
        if (sel) {
          Spacer(Modifier.width(6.dp))
          Icon(Icons.Outlined.CheckCircle, null, tint = HudColors.Cyan, modifier = Modifier.size(16.dp))
        }
      }
    }
    if (shown.isEmpty()) item { HudEmptyLine("No models match") }
  }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = HudColors.Text)
      Text(sub, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
    }
    Switch(
      checked = checked,
      onCheckedChange = onChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = HudColors.Background,
        checkedTrackColor = HudColors.Cyan,
        uncheckedThumbColor = HudColors.TextMuted,
        uncheckedTrackColor = HudColors.Background,
        uncheckedBorderColor = HudColors.Border,
      ),
    )
  }
}

@Composable
private fun SourceGrid(
  label: String,
  selected: Set<DataSource>,
  accent: androidx.compose.ui.graphics.Color = HudColors.Cyan,
  onChange: (Set<DataSource>) -> Unit,
) {
  Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
  Spacer(Modifier.height(6.dp))
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    DataSource.entries.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { src ->
          val on = src in selected
          HudChip(src.label, on, { onChange(if (on) selected - src else selected + src) }, accent = accent)
        }
      }
    }
  }
}
