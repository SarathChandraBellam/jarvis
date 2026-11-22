package com.jarvis.hud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.HudButton
import com.jarvis.hud.ui.components.HudChip
import com.jarvis.hud.ui.components.HudPanel
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.accent
import com.jarvis.hud.ui.components.icon
import com.jarvis.hud.ui.theme.HudColors

@Composable
fun IntelScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  // null = ALL. Stored by name so it survives config changes.
  var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
  var showArchived by rememberSaveable { mutableStateOf(false) }
  val selected = selectedName?.let { n -> IntelCategory.entries.firstOrNull { it.name == n } }

  val visibleBase = remember(state.items, showArchived) {
    state.items.filter { showArchived || it.status == ItemStatus.ACTIVE }
  }
  val counts = remember(visibleBase) { visibleBase.groupingBy { it.category }.eachCount() }
  val filtered = remember(visibleBase, selected) {
    visibleBase
      .filter { selected == null || it.category == selected }
      .sortedWith(compareBy({ it.status.ordinal }, { it.priority.ordinal }, { -it.timestamp }))
  }
  val pad = screenPadding(contentPadding)

  Column(Modifier.fillMaxSize()) {
    LazyRow(
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "all") {
        HudChip("All", selected == null, { selectedName = null }, icon = Icons.Outlined.Apps, count = visibleBase.size)
      }
      items(IntelCategory.entries, key = { it.name }) { cat ->
        HudChip(
          cat.label,
          selected == cat,
          { selectedName = if (selected == cat) null else cat.name },
          accent = cat.accent(),
          icon = cat.icon(),
          count = counts[cat] ?: 0,
        )
      }
      item(key = "archived") {
        HudChip(if (showArchived) "Archive on" else "Archive", showArchived, { showArchived = !showArchived }, accent = HudColors.TextDim)
      }
    }
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = pad.calculateBottomPadding()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (filtered.isEmpty()) {
        item(key = "empty") { EmptyIntel(selected, state.isSyncing, actions::onScanAndOrganize) }
      } else {
        item(key = "hint") {
          Text(
            "${filtered.size} RECORDS // SWIPE → DONE · ← DISMISS",
            style = MaterialTheme.typography.labelSmall,
            color = HudColors.TextMuted,
          )
        }
        items(filtered, key = { it.id }) { ItemCard(it, actions::onMarkItemStatus, Modifier.animateItem()) }
      }
    }
  }
}

@Composable
private fun EmptyIntel(category: IntelCategory?, syncing: Boolean, onScan: () -> Unit) {
  HudPanel(Modifier.fillMaxWidth(), title = "No signal") {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(category?.icon() ?: Icons.Outlined.Inventory2, null, tint = HudColors.TextMuted, modifier = Modifier.padding(4.dp))
      Spacer(Modifier.height(8.dp))
      Text(
        if (category == null) "Nothing on the board, sir. All quiet." else "No ${category.label.lowercase()} intel at the moment.",
        style = MaterialTheme.typography.bodyMedium,
        color = HudColors.TextDim,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(14.dp))
      HudButton(if (syncing) "Scanning…" else "Run scan", onScan, enabled = !syncing)
    }
  }
}
