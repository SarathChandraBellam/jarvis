package com.jarvis.hud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.ArcReactor
import com.jarvis.hud.ui.components.CutCornerHudShape
import com.jarvis.hud.ui.components.HudBackground
import com.jarvis.hud.ui.components.SyncStatusTicker
import com.jarvis.hud.ui.screens.AskScreen
import com.jarvis.hud.ui.screens.FinanceScreen
import com.jarvis.hud.ui.screens.HudScreen
import com.jarvis.hud.ui.screens.IntelScreen
import com.jarvis.hud.ui.screens.PreviewFixtures
import com.jarvis.hud.ui.screens.SettingsScreen
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.HudMono
import com.jarvis.hud.ui.theme.JarvisTheme

/**
 * Root J.A.R.V.I.S. HUD. Stateless: everything comes from [state], every event goes to [actions].
 * Wraps itself in [JarvisTheme]; draws edge-to-edge and handles system bar insets.
 */
@Composable
fun JarvisApp(state: JarvisUiState, actions: JarvisActions) {
  JarvisTheme {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.lastError) {
      val err = state.lastError
      if (err == null) {
        snackbar.currentSnackbarData?.dismiss()
        return@LaunchedEffect
      }
      val r = snackbar.showSnackbar(err, actionLabel = "ACK", withDismissAction = false, duration = SnackbarDuration.Long)
      if (r == SnackbarResult.ActionPerformed || r == SnackbarResult.Dismissed) actions.onDismissError()
    }

    HudBackground {
      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { HudTopBar(state, actions) },
        bottomBar = { HudDock(state.selectedTab, actions::onSelectTab) },
        snackbarHost = {
          SnackbarHost(snackbar) { data ->
            Snackbar(
              data,
              shape = CutCornerHudShape(10.dp),
              containerColor = HudColors.PanelSolid,
              contentColor = HudColors.Red,
              actionColor = HudColors.Cyan,
            )
          }
        },
      ) { inner ->
        when (state.selectedTab) {
          HudTab.HUD -> HudScreen(state, actions, inner)
          HudTab.FINANCE -> FinanceScreen(state, actions, inner)
          HudTab.INTEL -> IntelScreen(state, actions, inner)
          HudTab.ASK -> AskScreen(state, actions, inner)
        }
      }

      if (state.showSettings) {
        SettingsScreen(state, actions)
      }
    }
  }
}

@Composable
private fun HudTopBar(state: JarvisUiState, actions: JarvisActions) {
  Column(
    Modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(HudColors.Background, HudColors.Background.copy(alpha = 0.85f), Color.Transparent)))
      .windowInsetsPadding(WindowInsets.statusBars),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArcReactor(size = 26.dp, active = state.isSyncing || state.isSpeaking)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          "J.A.R.V.I.S.",
          fontFamily = HudMono,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          letterSpacing = 4.sp,
          color = HudColors.Cyan,
        )
        SyncStatusTicker(state.syncStatusLine, state.isSyncing)
      }
      IconButton(onClick = actions::onOpenSettings) {
        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = HudColors.Cyan)
      }
    }
    Spacer(Modifier.height(6.dp))
    Box(
      Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(Brush.horizontalGradient(listOf(Color.Transparent, HudColors.Border, Color.Transparent))),
    )
  }
}

private fun HudTab.icon(): ImageVector = when (this) {
  HudTab.HUD -> Icons.Outlined.Radar
  HudTab.FINANCE -> Icons.Outlined.AccountBalanceWallet
  HudTab.INTEL -> Icons.Outlined.Hub
  HudTab.ASK -> Icons.Outlined.RecordVoiceOver
}

@Composable
private fun HudDock(selected: HudTab, onSelect: (HudTab) -> Unit) {
  Box(
    Modifier
      .fillMaxWidth()
      .background(Brush.verticalGradient(listOf(Color.Transparent, HudColors.Background.copy(alpha = 0.92f), HudColors.Background)))
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    val shape = CutCornerHudShape(14.dp, 4.dp)
    Row(
      Modifier
        .fillMaxWidth()
        .background(HudColors.PanelSolid.copy(alpha = 0.92f), shape)
        .padding(6.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      HudTab.entries.forEach { tab ->
        val sel = tab == selected
        val bg = if (sel) HudColors.Cyan.copy(alpha = 0.16f) else Color.Transparent
        val fg = if (sel) HudColors.Cyan else HudColors.TextMuted
        Column(
          Modifier
            .weight(1f)
            .background(bg, CutCornerHudShape(8.dp))
            .clickable(role = Role.Tab) { onSelect(tab) }
            .padding(vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(tab.icon(), contentDescription = tab.label, tint = fg, modifier = Modifier.size(20.dp))
          Spacer(Modifier.height(3.dp))
          Text(tab.label, style = MaterialTheme.typography.labelSmall, color = fg)
          Spacer(Modifier.height(3.dp))
          Box(Modifier.width(if (sel) 18.dp else 0.dp).height(2.dp).background(HudColors.Cyan))
        }
      }
    }
  }
}

// ---------------------------------------------------------------- Previews

@Preview(name = "HUD", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF050A14)
@Composable
private fun PreviewHud() = JarvisApp(PreviewFixtures.state(HudTab.HUD), PreviewFixtures.noOpActions)

@Preview(name = "Finance", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF050A14)
@Composable
private fun PreviewFinance() = JarvisApp(PreviewFixtures.state(HudTab.FINANCE), PreviewFixtures.noOpActions)

@Preview(name = "Intel", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF050A14)
@Composable
private fun PreviewIntel() = JarvisApp(PreviewFixtures.state(HudTab.INTEL), PreviewFixtures.noOpActions)

@Preview(name = "Ask", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF050A14)
@Composable
private fun PreviewAsk() = JarvisApp(PreviewFixtures.state(HudTab.ASK).copy(isAskingAi = true), PreviewFixtures.noOpActions)

@Preview(name = "Settings", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF050A14)
@Composable
private fun PreviewSettings() = JarvisApp(PreviewFixtures.state().copy(showSettings = true), PreviewFixtures.noOpActions)
