package com.jarvis.hud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.EmptyState
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.Refreshable
import com.jarvis.hud.ui.components.accent
import com.jarvis.hud.ui.components.icon
import com.jarvis.hud.ui.components.jarvisTextFieldColors
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType

/** Inbox tab: everything Jarvis organized, with persisted category & completion filters. */
@Composable
fun IntelScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val selectedName = state.settings.inboxCategoryName
  val showArchived = state.settings.inboxShowCompleted
  var query by rememberSaveable { mutableStateOf("") }
  val selected = selectedName?.let { n -> IntelCategory.entries.firstOrNull { it.name == n } }

  val visibleBase = remember(state.items, showArchived) {
    state.items.filter { showArchived || it.status == ItemStatus.ACTIVE }
  }
  val counts = remember(visibleBase) { visibleBase.groupingBy { it.category }.eachCount() }
  val filtered = remember(visibleBase, selected, query) {
    val q = query.trim()
    visibleBase
      .filter { selected == null || it.category == selected }
      .filter {
        q.isEmpty() || it.title.contains(q, true) || it.summary.contains(q, true) ||
          (it.merchant?.contains(q, true) ?: false)
      }
      .sortedWith(compareBy({ it.status.ordinal }, { it.priority.ordinal }, { -it.timestamp }))
  }
  val pad = screenPadding(contentPadding)

  Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = { Text("Search inbox") },
      leadingIcon = { Icon(Icons.Outlined.Search, null) },
      trailingIcon = if (query.isNotEmpty()) {
        { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } }
      } else null,
      singleLine = true,
      shape = RoundedCornerShape(28.dp),
      colors = jarvisTextFieldColors(),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 10.dp).testTag("inboxSearch"),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 18.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "all") {
        Chip("All · ${visibleBase.size}", selected == null, HudColors.Blush) {
          actions.onSelectInboxFilter(null, showArchived)
        }
      }
      items(IntelCategory.entries.filter { (counts[it] ?: 0) > 0 || it == selected }, key = { it.name }) { cat ->
        Chip("${cat.label} · ${counts[cat] ?: 0}", selected == cat, cat.accent(), cat) {
          actions.onSelectInboxFilter(if (selected == cat) null else cat.name, showArchived)
        }
      }
      item(key = "archived") {
        Chip("Show completed", showArchived, HudColors.TextDim) {
          actions.onSelectInboxFilter(selectedName, !showArchived)
        }
      }
    }
    Refreshable(state.isSyncing, actions::onScanAndOrganize, Modifier.fillMaxSize()) {
      LazyColumn(
        Modifier.fillMaxSize().testTag("screen-inbox"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = pad.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (filtered.isEmpty()) {
          item(key = "empty") {
            EmptyState(
              if (query.isNotBlank()) Icons.Outlined.Search else selected?.icon() ?: Icons.Outlined.Inventory2,
              when {
                query.isNotBlank() -> "No results for \"${query.trim()}\""
                selected == null -> "Inbox zero"
                else -> "Nothing in ${selected.label}"
              },
              if (state.isSyncing) "Checking your messages and notifications…"
              else "Subscriptions, bills, deliveries and messages will appear here.",
            ) {
              FilledTonalButton(
                onClick = actions::onScanAndOrganize,
                enabled = !state.isSyncing,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
              ) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                Text("  Refresh")
              }
            }
          }
        } else {
          item(key = "hint") {
            Text(
              "Swipe right to mark done, left to dismiss",
              style = JarvisType.PromptItalic,
              color = HudColors.TextMuted,
              modifier = Modifier.padding(start = 4.dp),
            )
          }
          items(filtered, key = { it.id }) { ItemCard(it, actions::onMarkItemStatus, onClick = { actions.onOpenItem(it.id) }) }
        }
      }
    }
  }
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, category: IntelCategory? = null, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    leadingIcon = category?.let { c -> { Icon(c.icon(), null, Modifier.size(16.dp)) } },
    shape = CircleShape,
    colors = FilterChipDefaults.filterChipColors(
      containerColor = HudColors.Surface,
      labelColor = HudColors.TextDim,
      iconColor = accent,
      selectedContainerColor = accent.copy(alpha = 0.14f),
      selectedLabelColor = HudColors.Text,
      selectedLeadingIconColor = accent,
    ),
    border = FilterChipDefaults.filterChipBorder(
      enabled = true,
      selected = selected,
      borderColor = HudColors.Outline,
      selectedBorderColor = accent.copy(alpha = 0.5f),
    ),
  )
}
