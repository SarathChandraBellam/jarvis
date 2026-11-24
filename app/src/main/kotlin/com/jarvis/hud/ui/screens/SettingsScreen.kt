package com.jarvis.hud.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisSettings
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.OpenRouterModelInfo
import com.jarvis.hud.ui.components.EmptyLine
import com.jarvis.hud.ui.components.JCard
import com.jarvis.hud.ui.components.ListRow
import com.jarvis.hud.ui.components.PermissionList
import com.jarvis.hud.ui.components.Pill
import com.jarvis.hud.ui.components.SectionHeader
import com.jarvis.hud.ui.components.jarvisTextFieldColors
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import kotlinx.coroutines.delay

private const val AUTOSAVE_DELAY_MS = 400L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: JarvisUiState, actions: JarvisActions, snackbar: SnackbarHostState) {
  var draft by remember { mutableStateOf(state.settings) }
  val saved = state.settings
  val dirty = draft != saved
  val currentDraft by rememberUpdatedState(draft)
  val currentSaved by rememberUpdatedState(saved)

  LaunchedEffect(draft) {
    if (draft != currentSaved) {
      delay(AUTOSAVE_DELAY_MS)
      actions.onUpdateSettings(draft)
    }
  }
  DisposableEffect(Unit) {
    onDispose { if (currentDraft != currentSaved) actions.onUpdateSettings(currentDraft) }
  }
  BackHandler(onBack = actions::onCloseSettings)

  fun flush() {
    if (draft != saved) actions.onUpdateSettings(draft)
  }

  Scaffold(
    containerColor = HudColors.Background,
    snackbarHost = { SnackbarHost(snackbar) },
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Settings", style = JarvisType.TitleSerif)
            SaveStatus(dirty = dirty, failed = dirty && state.lastError != null)
          }
        },
        navigationIcon = {
          IconButton(onClick = actions::onCloseSettings) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = HudColors.Background,
          titleContentColor = HudColors.Text,
          navigationIconContentColor = HudColors.Text,
        ),
      )
    },
  ) { inner ->
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
          .widthIn(max = 720.dp)
          .fillMaxSize()
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .testTag("settings"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SectionHeader("Profile")
        JCard {
          OutlinedTextField(
            value = draft.userCallsign,
            onValueChange = { draft = draft.copy(userCallsign = it) },
            label = { Text("What should Jarvis call you?") },
            placeholder = { Text("Sir") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = jarvisTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("callsignField"),
          )
        }

        SectionHeader("Proactive alerts & focus")
        ProactiveCard(
          draft = draft,
          onChange = { draft = it },
          onSendTest = { flush(); actions.onSendTestProactiveAlert() },
        )

        SectionHeader("AI model")
        AiCard(state, draft, onChange = { draft = it }, flush = ::flush, actions = actions)

        SectionHeader("Assistant")
        JCard(padding = 8.dp) {
          SwitchRow(
            "Cycle & period tracker",
            "Show cycle phase context, period calendar & phase-aware insights",
            Icons.Outlined.NotificationsActive,
            draft.cycleTrackingEnabled,
          ) { draft = draft.copy(cycleTrackingEnabled = it) }
          SwitchRow(
            "Voice replies",
            "Read briefings and answers out loud",
            Icons.Outlined.RecordVoiceOver,
            draft.voiceEnabled,
          ) { draft = draft.copy(voiceEnabled = it) }
          SwitchRow(
            "Hide sensitive data",
            "Mask OTPs, card and account numbers before anything is sent to the AI",
            Icons.Outlined.Lock,
            draft.redactSensitiveData,
          ) { draft = draft.copy(redactSensitiveData = it) }
        }

        SectionHeader("Phone access")
        JCard(padding = 12.dp) {
          PermissionList(
            state.permissions,
            actions::onRequestRuntimePermissions,
            actions::onOpenNotificationAccessSettings,
            actions::onOpenUsageAccessSettings,
            includePostNotifications = true,
          )
        }

        SectionHeader("Data sources")
        JCard {
          Text("Read from", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
          Spacer(Modifier.size(8.dp))
          SourceChips(draft.enabledSources, HudColors.SlateBlue) { draft = draft.copy(enabledSources = it) }
          Spacer(Modifier.size(16.dp))
          Text("Send to AI", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
          Spacer(Modifier.size(8.dp))
          SourceChips(draft.aiShareSources, HudColors.Sand) { draft = draft.copy(aiShareSources = it) }
          Spacer(Modifier.size(10.dp))
          Text(
            "Sources not sent to the AI are still organized on your phone with built-in rules.",
            style = MaterialTheme.typography.bodySmall,
            color = HudColors.TextMuted,
          )
        }

        SectionHeader("Storage")
        ClearCacheCard(actions::onClearAiCache)

        Text(
          "Selections & reminders persist across restarts · Jarvis ${appVersion()}",
          style = MaterialTheme.typography.bodySmall,
          color = HudColors.TextMuted,
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProactiveCard(
  draft: JarvisSettings,
  onChange: (JarvisSettings) -> Unit,
  onSendTest: () -> Unit,
) {
  JCard {
    SwitchRow(
      "Proactive notifications",
      "Alert me before subscription renewals, bills, reminders and deliveries",
      Icons.Outlined.NotificationsActive,
      draft.proactiveNotificationsEnabled,
    ) { onChange(draft.copy(proactiveNotificationsEnabled = it)) }
    Spacer(Modifier.size(10.dp))
    Text("Renewal lead time", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
    Spacer(Modifier.size(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(1 to "1 day before", 3 to "3 days before", 7 to "7 days before").forEach { (days, label) ->
        FilterChip(
          selected = draft.renewalLeadDays == days,
          onClick = { onChange(draft.copy(renewalLeadDays = days)) },
          label = { Text(label) },
          shape = CircleShape,
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = HudColors.BlushSoft,
            selectedLabelColor = HudColors.Text,
          ),
        )
      }
    }
    Spacer(Modifier.size(10.dp))
    Text("Daily screen-time focus limit", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
    Spacer(Modifier.size(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(60 to "1h", 120 to "2h", 180 to "3h", 240 to "4h").forEach { (mins, label) ->
        FilterChip(
          selected = draft.dailyFocusGoalMinutes == mins,
          onClick = { onChange(draft.copy(dailyFocusGoalMinutes = mins)) },
          label = { Text(label) },
          shape = CircleShape,
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = HudColors.SageSoft,
            selectedLabelColor = HudColors.Text,
          ),
        )
      }
    }
    Spacer(Modifier.size(12.dp))
    OutlinedButton(onClick = onSendTest, modifier = Modifier.fillMaxWidth()) {
      Text("Send test notification")
    }
  }
}

@Composable
private fun SaveStatus(dirty: Boolean, failed: Boolean) {
  val (icon, text, color) = when {
    failed -> Triple(Icons.Outlined.CloudOff, "Couldn't save", HudColors.Coral)
    dirty -> Triple(Icons.Outlined.CloudSync, "Saving…", HudColors.TextDim)
    else -> Triple(Icons.Outlined.CloudDone, "All changes saved", HudColors.Sage)
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
  }
}

@Composable
private fun AiCard(
  state: JarvisUiState,
  draft: JarvisSettings,
  onChange: (JarvisSettings) -> Unit,
  flush: () -> Unit,
  actions: JarvisActions,
) {
  var showKey by remember { mutableStateOf(false) }
  JCard {
    Text(
      "Jarvis uses OpenRouter for briefings and answers. Without a key it works offline with built-in rules.",
      style = MaterialTheme.typography.bodySmall,
      color = HudColors.TextDim,
    )
    Spacer(Modifier.size(12.dp))
    OutlinedTextField(
      value = draft.apiKey,
      onValueChange = { onChange(draft.copy(apiKey = it.trim())) },
      label = { Text("OpenRouter API key") },
      placeholder = { Text("sk-or-v1-…") },
      singleLine = true,
      visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      trailingIcon = {
        IconButton(onClick = { showKey = !showKey }) {
          Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showKey) "Hide key" else "Show key")
        }
      },
      supportingText = { Text("Stored encrypted on this phone") },
      shape = MaterialTheme.shapes.medium,
      colors = jarvisTextFieldColors(),
      modifier = Modifier.fillMaxWidth().testTag("apiKeyField"),
    )
    Spacer(Modifier.size(4.dp))
    OutlinedTextField(
      value = draft.modelId,
      onValueChange = { onChange(draft.copy(modelId = it.trim())) },
      label = { Text("Model") },
      singleLine = true,
      shape = MaterialTheme.shapes.medium,
      colors = jarvisTextFieldColors(),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = { flush(); actions.onTestOpenRouterConnection() },
        enabled = draft.apiKey.isNotBlank(),
        modifier = Modifier.weight(1f),
      ) { Text("Test") }
      OutlinedButton(
        onClick = { flush(); actions.onFetchOpenRouterModels() },
        enabled = draft.apiKey.isNotBlank() && !state.isLoadingModels,
        modifier = Modifier.weight(1f),
      ) {
        if (state.isLoadingModels) {
          CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
        }
        Text("Browse models")
      }
    }
    state.connectionTestResult?.let { r ->
      Spacer(Modifier.size(10.dp))
      val c = if (r.success) HudColors.Sage else HudColors.Coral
      Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(c.copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(if (r.success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = c, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(r.message, style = MaterialTheme.typography.bodySmall, color = HudColors.Text, modifier = Modifier.weight(1f))
        if (r.latencyMs > 0) Text("${r.latencyMs} ms", style = MaterialTheme.typography.labelMedium, color = c)
      }
    }
    if (state.availableModels.isNotEmpty()) {
      Spacer(Modifier.size(12.dp))
      ModelList(state.availableModels, draft.modelId) { onChange(draft.copy(modelId = it)) }
    }
  }
}

@Composable
private fun ModelList(models: List<OpenRouterModelInfo>, selectedId: String, onSelect: (String) -> Unit) {
  var freeOnly by remember { mutableStateOf(false) }
  val shown = if (freeOnly) models.filter { it.isFree } else models
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("${shown.size} models", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim, modifier = Modifier.weight(1f))
    FilterChip(
      selected = freeOnly,
      onClick = { freeOnly = !freeOnly },
      label = { Text("Free only") },
      shape = CircleShape,
      colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = HudColors.SageSoft,
        selectedLabelColor = HudColors.Text,
        labelColor = HudColors.TextDim,
      ),
    )
  }
  Spacer(Modifier.size(6.dp))
  LazyColumn(
    Modifier
      .fillMaxWidth()
      .heightIn(max = 320.dp)
      .clip(MaterialTheme.shapes.medium)
      .border(1.dp, HudColors.OutlineSoft, MaterialTheme.shapes.medium),
    contentPadding = PaddingValues(4.dp),
  ) {
    items(shown, key = { it.id }) { m ->
      val sel = m.id == selectedId
      Row(
        Modifier
          .fillMaxWidth()
          .clip(MaterialTheme.shapes.small)
          .background(if (sel) HudColors.SandSoft else Color.Transparent)
          .clickable { onSelect(m.id) }
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(m.name, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            m.id + (m.contextLength?.let { " · ${it / 1000}K context" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = HudColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (m.isFree) Pill("Free", HudColors.Sage, Modifier.padding(start = 8.dp))
        if (sel) Icon(Icons.Outlined.CheckCircle, "Selected", tint = HudColors.Cream, modifier = Modifier.padding(start = 8.dp).size(18.dp))
      }
    }
    if (shown.isEmpty()) item { EmptyLine("No models match", Modifier.padding(8.dp)) }
  }
}

@Composable
private fun SwitchRow(
  title: String,
  sub: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  ListRow(
    title = title,
    subtitle = sub,
    icon = icon,
    tint = HudColors.Sand,
    onClick = { onChange(!checked) },
    modifier = Modifier.padding(horizontal = 6.dp),
    trailing = {
      Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
          checkedThumbColor = HudColors.OnCream,
          checkedTrackColor = HudColors.Cream,
          uncheckedThumbColor = HudColors.TextDim,
          uncheckedTrackColor = HudColors.SurfaceHigh,
          uncheckedBorderColor = HudColors.Outline,
        ),
      )
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChips(selected: Set<DataSource>, accent: Color, onChange: (Set<DataSource>) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    DataSource.entries.forEach { src ->
      val on = src in selected
      FilterChip(
        selected = on,
        onClick = { onChange(if (on) selected - src else selected + src) },
        label = { Text(src.label) },
        leadingIcon = if (on) {
          { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp)) }
        } else null,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
          containerColor = HudColors.Surface,
          labelColor = HudColors.TextDim,
          selectedContainerColor = accent.copy(alpha = 0.14f),
          selectedLabelColor = HudColors.Text,
          selectedLeadingIconColor = accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
          enabled = true,
          selected = on,
          borderColor = HudColors.Outline,
          selectedBorderColor = accent.copy(alpha = 0.5f),
        ),
      )
    }
  }
}

@Composable
private fun ClearCacheCard(onClear: () -> Unit) {
  var confirm by remember { mutableStateOf(false) }
  JCard(padding = 8.dp) {
    ListRow(
      title = "Clear organized data",
      subtitle = "Deletes the saved inbox and briefing, then re-scans your phone",
      icon = Icons.Outlined.DeleteOutline,
      tint = HudColors.Coral,
      onClick = { confirm = true },
      modifier = Modifier.padding(horizontal = 8.dp),
    )
  }
  if (confirm) {
    AlertDialog(
      onDismissRequest = { confirm = false },
      title = { Text("Clear organized data?") },
      text = { Text("Your inbox, completed items and today's briefing will be removed. Settings and your API key are kept.") },
      confirmButton = {
        TextButton(
          onClick = { confirm = false; onClear() },
          colors = ButtonDefaults.textButtonColors(contentColor = HudColors.Coral),
        ) { Text("Clear") }
      },
      dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
      containerColor = HudColors.Surface,
    )
  }
}

@Composable
private fun appVersion(): String {
  val context = LocalContext.current
  return remember {
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
  }
}
