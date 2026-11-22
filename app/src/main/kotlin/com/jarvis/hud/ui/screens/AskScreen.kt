package com.jarvis.hud.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.ChatMessage
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.ArcReactor
import com.jarvis.hud.ui.components.CutCornerHudShape
import com.jarvis.hud.ui.components.HudChip
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.theme.HudColors

private val SuggestedPrompts = listOf(
  "What did I spend today?",
  "Any bills due?",
  "Where are my deliveries?",
  "Brief me",
)

@Composable
fun AskScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  var input by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()
  val send: (String) -> Unit = { p ->
    val t = p.trim()
    if (t.isNotEmpty() && !state.isAskingAi) {
      actions.onSendChatMessage(t)
      input = ""
    }
  }
  val itemCount = state.chatMessages.size + if (state.isAskingAi) 1 else 0
  LaunchedEffect(itemCount) {
    if (itemCount > 0) listState.scrollToItem(itemCount - 1)
  }
  val pad = screenPadding(contentPadding)

  Column(
    Modifier
      .fillMaxSize()
      .padding(top = pad.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding())
      .consumeWindowInsets(contentPadding)
      .imePadding(),
  ) {
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      state = listState,
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(SuggestedPrompts) { p -> HudChip(p, false, { send(p) }) }
    }
    Row(
      Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.weight(1f),
        placeholder = { Text("Ask J.A.R.V.I.S.…", color = HudColors.TextMuted) },
        shape = CutCornerHudShape(10.dp),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send(input) }),
        colors = hudTextFieldColors(),
      )
      IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !state.isAskingAi) {
        Icon(
          Icons.AutoMirrored.Outlined.Send,
          contentDescription = "Send",
          tint = if (input.isNotBlank() && !state.isAskingAi) HudColors.Cyan else HudColors.TextMuted,
        )
      }
    }
  }
}

@Composable
private fun AskIntro(callsign: String) {
  Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    ArcReactor(size = 96.dp)
    Spacer(Modifier.size(12.dp))
    Text("AT YOUR SERVICE, ${callsign.uppercase()}.", style = MaterialTheme.typography.labelLarge, color = HudColors.Cyan)
    Spacer(Modifier.size(4.dp))
    Text(
      "Ask about spending, bills, deliveries, schedule or messages.",
      style = MaterialTheme.typography.bodySmall,
      color = HudColors.TextDim,
    )
  }
}

@Composable
private fun ChatBubble(m: ChatMessage, onSpeak: () -> Unit) {
  val user = m.isFromUser
  val accent = if (user) HudColors.Amber else HudColors.Cyan
  val shape = if (user) CutCornerHudShape(10.dp, 0.dp) else CutCornerHudShape(0.dp, 10.dp)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
    Column(horizontalAlignment = if (user) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 320.dp)) {
      Text(
        if (user) "YOU · ${HudFormat.time(m.timestamp)}" else "J.A.R.V.I.S. · ${HudFormat.time(m.timestamp)}",
        style = MaterialTheme.typography.labelSmall,
        color = accent.copy(alpha = 0.8f),
      )
      Spacer(Modifier.size(3.dp))
      Box(
        Modifier
          .background(accent.copy(alpha = if (user) 0.10f else 0.07f), shape)
          .border(1.dp, accent.copy(alpha = 0.35f), shape)
          .padding(horizontal = 12.dp, vertical = 10.dp),
      ) {
        Text(m.content, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text)
      }
      if (!user) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          m.modelUsed?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, maxLines = 1)
          }
          IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, "Speak", tint = HudColors.Cyan, modifier = Modifier.size(16.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun TypingIndicator() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("J.A.R.V.I.S. ANALYZING", style = MaterialTheme.typography.labelSmall, color = HudColors.Cyan)
    Spacer(Modifier.width(8.dp))
    repeat(3) {
      Box(Modifier.padding(horizontal = 2.dp).size(6.dp).background(HudColors.Cyan, CircleShape))
    }
  }
}
