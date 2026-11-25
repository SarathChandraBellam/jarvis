package com.astra.hud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astra.hud.model.ChatMessage
import com.astra.hud.model.AstraActions
import com.astra.hud.model.AstraUiState
import com.astra.hud.ui.components.HaloEmblem
import com.astra.hud.ui.components.HudFormat
import com.astra.hud.ui.components.astraTextFieldColors
import com.astra.hud.ui.theme.HudColors
import com.astra.hud.ui.theme.AstraType
import com.astra.hud.ui.viz.BentoCard

private val SuggestedPrompts = listOf(
  "What subscriptions renew soon?",
  "Any bills due?",
  "Where are my deliveries?",
  "Brief me",
)

@Composable
fun AskScreen(state: AstraUiState, actions: AstraActions, contentPadding: PaddingValues) {
  var input by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()
  val send: (String) -> Unit = { p ->
    val t = p.trim()
    if (t.isNotEmpty() && !state.isAskingAi) {
      actions.onSendChatMessage(t)
      input = ""
    }
  }
  val extra = if (state.settings.apiKey.isBlank()) 1 else 0
  val itemCount = extra + state.chatMessages.size + if (state.isAskingAi) 1 else 0
  LaunchedEffect(itemCount) {
    if (itemCount > 0) listState.scrollToItem(itemCount - 1)
  }
  val canSend = input.isNotBlank() && !state.isAskingAi

  Column(
    Modifier
      .fillMaxSize()
      .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
      .consumeWindowInsets(contentPadding)
      .imePadding(),
  ) {
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      state = listState,
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (state.settings.apiKey.isBlank()) {
        item(key = "nokey") {
          BentoCard(pastelBg = HudColors.SandSoft, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Outlined.Info, null, tint = HudColors.Sand, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Text(
                "Offline mode: answers come from on-device rules. Add an OpenRouter key for smarter replies.",
                style = MaterialTheme.typography.bodySmall,
                color = HudColors.TextDim,
                modifier = Modifier.weight(1f),
              )
            }
            TextButton(onClick = actions::onOpenSettings, modifier = Modifier.align(Alignment.End)) {
              Text("Add key", color = HudColors.Text)
            }
          }
        }
      }
      if (state.chatMessages.isEmpty()) {
        item(key = "intro") { AskIntro(state.settings.userCallsign) }
      }
      items(state.chatMessages, key = { it.id }) { m ->
        ChatBubble(m, onSpeak = { actions.onSpeakText(m.content) })
      }
      if (state.isAskingAi) {
        item(key = "typing") { TypingIndicator() }
      }
    }
    LazyRow(
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(SuggestedPrompts) { p ->
        SuggestionChip(
          onClick = { send(p) },
          label = { Text(p) },
          enabled = !state.isAskingAi,
          shape = CircleShape,
          colors = SuggestionChipDefaults.suggestionChipColors(containerColor = HudColors.Surface, labelColor = HudColors.Text),
          border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = HudColors.Outline),
        )
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Ask ASTRA anything…") },
        shape = RoundedCornerShape(26.dp),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send(input) }),
        colors = astraTextFieldColors(),
      )
      Spacer(Modifier.width(8.dp))
      FilledIconButton(
        onClick = { send(input) },
        enabled = canSend,
        modifier = Modifier.size(52.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = HudColors.Cream,
          contentColor = HudColors.OnCream,
          disabledContainerColor = HudColors.SurfaceHigh,
          disabledContentColor = HudColors.TextMuted,
        ),
      ) {
        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
      }
    }
  }
}

@Composable
private fun AskIntro(callsign: String) {
  Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    HaloEmblem(size = 92.dp)
    Spacer(Modifier.height(16.dp))
    Text("How can I help, ${callsign.ifBlank { "Sir" }}?", style = AstraType.TitleSerif, color = HudColors.Text)
    Spacer(Modifier.height(6.dp))
    Text(
      "Ask about your subscriptions, bills, deliveries, day plan or messages.",
      style = MaterialTheme.typography.bodyMedium,
      color = HudColors.TextDim,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun ChatBubble(m: ChatMessage, onSpeak: () -> Unit) {
  val user = m.isFromUser
  val shape = if (user) {
    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
  } else {
    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
    Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 320.dp)) {
      Box(
        Modifier
          .background(if (user) HudColors.Cream else HudColors.Surface, shape)
          .then(if (!user) Modifier.border(1.dp, HudColors.Outline, shape) else Modifier)
          .padding(horizontal = 15.dp, vertical = 11.dp),
      ) {
        Text(m.content, style = MaterialTheme.typography.bodyMedium, color = if (user) HudColors.OnCream else HudColors.Text)
      }
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
          HudFormat.time(m.timestamp) + (m.modelUsed?.takeIf { !user }?.let { " · $it" } ?: ""),
          style = MaterialTheme.typography.labelSmall,
          color = HudColors.TextMuted,
          maxLines = 1,
        )
        if (!user) {
          IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Read aloud", tint = HudColors.TextDim, modifier = Modifier.size(16.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun TypingIndicator() {
  Row(
    Modifier
      .background(HudColors.Surface, RoundedCornerShape(22.dp))
      .border(1.dp, HudColors.Outline, RoundedCornerShape(22.dp))
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(3) { i ->
      Box(Modifier.padding(horizontal = 3.dp).size(7.dp).background(HudColors.Blush.copy(alpha = 0.45f + i * 0.2f), CircleShape))
    }
  }
}
