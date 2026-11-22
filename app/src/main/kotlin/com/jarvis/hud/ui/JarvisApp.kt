package com.jarvis.hud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.platform.testTag
import com.jarvis.hud.ui.components.ItemDetailSheet
import com.jarvis.hud.ui.screens.OnboardingScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.ArcReactor
import com.jarvis.hud.ui.screens.AskScreen
import com.jarvis.hud.ui.screens.FinanceScreen
import com.jarvis.hud.ui.screens.HudScreen
import com.jarvis.hud.ui.screens.IntelScreen
import com.jarvis.hud.ui.screens.PreviewFixtures
import com.jarvis.hud.ui.screens.SettingsScreen
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisTheme

/**
 * Root composable. Stateless: everything comes from [state], every event goes to [actions].
 * Settings is a real full screen (not an overlay), so nothing underneath can steal its taps.
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
      val r = snackbar.showSnackbar(err, actionLabel = "OK", duration = SnackbarDuration.Long)
      if (r == SnackbarResult.ActionPerformed || r == SnackbarResult.Dismissed) actions.onDismissError()
    }

    Box(Modifier.fillMaxSize().background(HudColors.Background)) {
      when {
        !state.settings.onboardingDone -> OnboardingScreen(state, actions)
        state.showSettings -> SettingsScreen(state, actions, snackbar)
        else -> MainShell(state, actions, snackbar)
      }
    }
    state.openItem?.let { item ->
      ItemDetailSheet(item, actions::onMarkItemStatus, onDismiss = { actions.onOpenItem(null) })
    }
  }
}

/** Tabs + top bar. Phones get a bottom bar; tablets and landscape get a side rail and centered content. */
@Composable
private fun MainShell(state: JarvisUiState, actions: JarvisActions, snackbar: SnackbarHostState) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
      if (wide) MainNavRail(state, actions::onSelectTab)
      Scaffold(
        containerColor = HudColors.Background,
        topBar = { MainTopBar(state, actions) },
        bottomBar = { if (!wide) MainNavBar(state, actions::onSelectTab) },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.weight(1f),
      ) { inner ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
          Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            when (state.selectedTab) {
              HudTab.HUD -> HudScreen(state, actions, inner)
              HudTab.FINANCE -> FinanceScreen(state, actions, inner)
              HudTab.INTEL -> IntelScreen(state, actions, inner)
              HudTab.ASK -> AskScreen(state, actions, inner)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MainNavRail(state: JarvisUiState, onSelect: (HudTab) -> Unit) {
  NavigationRail(
    containerColor = HudColors.Surface,
    modifier = Modifier.fillMaxHeight(),
  ) {
    Spacer(Modifier.weight(1f))
    HudTab.entries.forEach { tab ->
      NavigationRailItem(
        selected = tab == state.selectedTab,
        onClick = { onSelect(tab) },
        icon = { TabIcon(tab, state) },
        label = { Text(tab.label) },
        modifier = Modifier.testTag("tab-${tab.name}"),
        colors = NavigationRailItemDefaults.colors(
          selectedIconColor = HudColors.OnCyan,
          selectedTextColor = HudColors.Text,
          indicatorColor = HudColors.Cyan,
          unselectedIconColor = HudColors.TextMuted,
          unselectedTextColor = HudColors.TextMuted,
        ),
      )
    }
    Spacer(Modifier.weight(1f))
  }
}

@Composable
private fun TabIcon(tab: HudTab, state: JarvisUiState) {
  val inboxCount = state.items.count { it.status == ItemStatus.ACTIVE }
  if (tab == HudTab.INTEL && inboxCount > 0) {
    BadgedBox(badge = { Badge(containerColor = HudColors.Cyan, contentColor = HudColors.OnCyan) { Text(if (inboxCount > 99) "99+" else "$inboxCount") } }) {
      Icon(tab.icon(), contentDescription = tab.label)
    }
  } else {
    Icon(tab.icon(), contentDescription = tab.label)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(state: JarvisUiState, actions: JarvisActions) {
  TopAppBar(
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ArcReactor(size = 30.dp, active = state.isSyncing || state.isSpeaking)
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            if (state.selectedTab == HudTab.HUD) "Jarvis" else state.selectedTab.label,
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            state.syncStatusLine,
            style = MaterialTheme.typography.labelMedium,
            color = if (state.isSyncing) HudColors.Cyan else HudColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    actions = {
      if (state.selectedTab == HudTab.ASK && state.chatMessages.isNotEmpty()) {
        IconButton(onClick = actions::onClearChat) {
          Icon(Icons.Outlined.DeleteSweep, "Clear chat")
        }
      }
      IconButton(onClick = actions::onScanAndOrganize, enabled = !state.isSyncing) {
        if (state.isSyncing) {
          CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = HudColors.Cyan)
        } else {
          Icon(Icons.Outlined.Refresh, "Refresh")
        }
      }
      IconButton(onClick = actions::onOpenSettings, modifier = Modifier.testTag("openSettings")) {
        Icon(Icons.Outlined.Settings, "Settings")
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = HudColors.Background,
      titleContentColor = HudColors.Text,
      actionIconContentColor = HudColors.TextDim,
    ),
  )
}

private fun HudTab.icon(): ImageVector = when (this) {
  HudTab.HUD -> Icons.Outlined.Home
  HudTab.FINANCE -> Icons.Outlined.AccountBalanceWallet
  HudTab.INTEL -> Icons.Outlined.Inbox
  HudTab.ASK -> Icons.Outlined.AutoAwesome
}

@Composable
private fun MainNavBar(state: JarvisUiState, onSelect: (HudTab) -> Unit) {
  NavigationBar(containerColor = HudColors.Surface, tonalElevation = 0.dp) {
    HudTab.entries.forEach { tab ->
      NavigationBarItem(
        selected = tab == state.selectedTab,
        onClick = { onSelect(tab) },
        icon = { TabIcon(tab, state) },
        label = { Text(tab.label) },
        modifier = Modifier.testTag("tab-${tab.name}"),
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = HudColors.OnCyan,
          selectedTextColor = HudColors.Text,
          indicatorColor = HudColors.Cyan,
          unselectedIconColor = HudColors.TextMuted,
          unselectedTextColor = HudColors.TextMuted,
        ),
      )
    }
  }
}

// ---------------------------------------------------------------- Previews

@Preview(name = "Home", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0A0F1A)
@Composable
private fun PreviewHome() = JarvisApp(PreviewFixtures.state(HudTab.HUD), PreviewFixtures.noOpActions)

@Preview(name = "Money", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0A0F1A)
@Composable
private fun PreviewMoney() = JarvisApp(PreviewFixtures.state(HudTab.FINANCE), PreviewFixtures.noOpActions)

@Preview(name = "Inbox", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0A0F1A)
@Composable
private fun PreviewInbox() = JarvisApp(PreviewFixtures.state(HudTab.INTEL), PreviewFixtures.noOpActions)

@Preview(name = "Ask", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0A0F1A)
@Composable
private fun PreviewAsk() = JarvisApp(PreviewFixtures.state(HudTab.ASK).copy(isAskingAi = true), PreviewFixtures.noOpActions)

@Preview(name = "Tablet", widthDp = 900, heightDp = 600, showBackground = true, backgroundColor = 0xFF0A0F1A)
@Composable
private fun PreviewTablet() = JarvisApp(PreviewFixtures.state(HudTab.HUD), PreviewFixtures.noOpActions)

@Preview(name = "Settings", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0A0F1A)
@Composable
private fun PreviewSettings() = JarvisApp(PreviewFixtures.state().copy(showSettings = true), PreviewFixtures.noOpActions)
