package com.jarvis.hud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.ItemDetailSheet
import com.jarvis.hud.ui.screens.AskScreen
import com.jarvis.hud.ui.screens.FinanceScreen
import com.jarvis.hud.ui.screens.HudScreen
import com.jarvis.hud.ui.screens.IntelScreen
import com.jarvis.hud.ui.screens.OnboardingScreen
import com.jarvis.hud.ui.screens.PreviewFixtures
import com.jarvis.hud.ui.screens.SettingsScreen
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisTheme
import com.jarvis.hud.ui.theme.JarvisType

/**
 * Root composable. Stateless: everything comes from [state], every event goes to [actions].
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

/** Editorial header + floating pill dock with detached Ask orb (or side rail on wide screens). */
@Composable
private fun MainShell(state: JarvisUiState, actions: JarvisActions, snackbar: SnackbarHostState) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
      if (wide) MainNavRail(state, actions::onSelectTab)
      Scaffold(
        containerColor = HudColors.Background,
        topBar = { EditorialHeader(state, actions) },
        bottomBar = { if (!wide) FloatingPillDock(state, actions::onSelectTab) },
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
private fun EditorialHeader(state: JarvisUiState, actions: JarvisActions) {
  val now = System.currentTimeMillis()
  val initial = state.settings.userCallsign.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "J"
  val title = when (state.selectedTab) {
    HudTab.HUD -> "${HudFormat.greeting(now)}, ${state.settings.userCallsign.ifBlank { "Sir" }}"
    else -> state.selectedTab.label
  }
  val subtitle = if (state.selectedTab == HudTab.HUD) {
    "${HudFormat.longDate(now)} · ${state.syncStatusLine}"
  } else {
    state.syncStatusLine
  }

  Row(
    Modifier
      .fillMaxWidth()
      .background(HudColors.Background)
      .statusBarsPadding()
      .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        subtitle,
        style = MaterialTheme.typography.labelMedium,
        color = if (state.isSyncing) HudColors.Amber else HudColors.TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        title,
        style = JarvisType.TitleSerif,
        color = HudColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (state.selectedTab == HudTab.ASK && state.chatMessages.isNotEmpty()) {
      IconButton(onClick = actions::onClearChat) {
        Icon(Icons.Outlined.DeleteSweep, "Clear chat", tint = HudColors.TextDim)
      }
    }

    IconButton(
      onClick = actions::onScanAndOrganize,
      enabled = !state.isSyncing,
      modifier = Modifier
        .size(38.dp)
        .clip(CircleShape)
        .background(HudColors.Surface)
        .border(1.dp, HudColors.OutlineSoft, CircleShape),
    ) {
      if (state.isSyncing) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = HudColors.Amber)
      } else {
        Icon(Icons.Outlined.Refresh, "Refresh", tint = HudColors.TextDim, modifier = Modifier.size(18.dp))
      }
    }

    Spacer(Modifier.width(10.dp))

    // Personal monogram avatar pill (opens Settings), echoing Ojas ("Ko") and calm wellness ("S").
    Box(
      Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(HudColors.SurfaceHigh)
        .border(1.dp, HudColors.Outline, CircleShape)
        .clickable(role = Role.Button, onClick = actions::onOpenSettings)
        .testTag("openSettings")
        .semantics { contentDescription = "Settings" },
      contentAlignment = Alignment.Center,
    ) {
      Text(initial, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = HudColors.Cream)
    }
  }
}

/**
 * Floating frosted-pill bottom navigation bar (`Home`, `Money`, `Inbox`) paired with
 * a detached circular Ask sparkle orb (`✦`) on the right.
 */
@Composable
private fun FloatingPillDock(state: JarvisUiState, onSelect: (HudTab) -> Unit) {
  val primaryTabs = listOf(HudTab.HUD, HudTab.FINANCE, HudTab.INTEL)
  val inboxCount = state.items.count { it.status == ItemStatus.ACTIVE }
  val askSelected = state.selectedTab == HudTab.ASK

  Box(
    Modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          listOf(Color.Transparent, HudColors.Background.copy(alpha = 0.85f), HudColors.Background),
        ),
      )
      .navigationBarsPadding()
      .padding(horizontal = 18.dp, vertical = 10.dp),
  ) {
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        Modifier
          .weight(1f)
          .height(60.dp)
          .clip(RoundedCornerShape(30.dp))
          .background(HudColors.DockGlass)
          .border(1.dp, HudColors.Outline, RoundedCornerShape(30.dp))
          .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        primaryTabs.forEach { tab ->
          val selected = tab == state.selectedTab
          Column(
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .clip(RoundedCornerShape(25.dp))
              .background(if (selected) HudColors.SurfaceHighest else Color.Transparent)
              .clickable(role = Role.Tab) { onSelect(tab) }
              .testTag("tab-${tab.name}")
              .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            TabIcon(tab, inboxCount, selectedTint = if (selected) HudColors.Cream else HudColors.TextMuted)
            Spacer(Modifier.height(2.dp))
            Text(
              tab.label,
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              ),
              color = if (selected) HudColors.Cream else HudColors.TextMuted,
              maxLines = 1,
            )
          }
        }
      }

      // Detached circular Ask orb (warm cream when active).
      Box(
        Modifier
          .size(60.dp)
          .clip(CircleShape)
          .background(if (askSelected) HudColors.Cream else HudColors.SurfaceHigh)
          .border(1.dp, if (askSelected) HudColors.Cream else HudColors.Outline, CircleShape)
          .clickable(role = Role.Tab) { onSelect(HudTab.ASK) }
          .testTag("tab-${HudTab.ASK.name}")
          .semantics { contentDescription = HudTab.ASK.label },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Outlined.AutoAwesome,
          contentDescription = HudTab.ASK.label,
          tint = if (askSelected) HudColors.OnCream else HudColors.Cream,
          modifier = Modifier.size(24.dp),
        )
      }
    }
  }
}

@Composable
private fun MainNavRail(state: JarvisUiState, onSelect: (HudTab) -> Unit) {
  val inboxCount = state.items.count { it.status == ItemStatus.ACTIVE }
  NavigationRail(
    containerColor = HudColors.Surface,
    modifier = Modifier.fillMaxHeight(),
  ) {
    Spacer(Modifier.weight(1f))
    HudTab.entries.forEach { tab ->
      val selected = tab == state.selectedTab
      NavigationRailItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = { TabIcon(tab, inboxCount, selectedTint = if (selected) HudColors.OnCream else HudColors.TextMuted) },
        label = { Text(tab.label) },
        modifier = Modifier.testTag("tab-${tab.name}"),
        colors = NavigationRailItemDefaults.colors(
          selectedIconColor = HudColors.OnCream,
          selectedTextColor = HudColors.Cream,
          indicatorColor = HudColors.Cream,
          unselectedIconColor = HudColors.TextMuted,
          unselectedTextColor = HudColors.TextMuted,
        ),
      )
    }
    Spacer(Modifier.weight(1f))
  }
}

@Composable
private fun TabIcon(tab: HudTab, inboxCount: Int, selectedTint: Color) {
  if (tab == HudTab.INTEL && inboxCount > 0) {
    BadgedBox(
      badge = {
        Badge(containerColor = HudColors.Amber, contentColor = HudColors.OnCream) {
          Text(if (inboxCount > 99) "99+" else "$inboxCount")
        }
      },
    ) {
      Icon(tab.icon(), contentDescription = tab.label, tint = selectedTint, modifier = Modifier.size(20.dp))
    }
  } else {
    Icon(tab.icon(), contentDescription = tab.label, tint = selectedTint, modifier = Modifier.size(20.dp))
  }
}

private fun HudTab.icon(): ImageVector = when (this) {
  HudTab.HUD -> Icons.Outlined.Home
  HudTab.FINANCE -> Icons.Outlined.AccountBalanceWallet
  HudTab.INTEL -> Icons.Outlined.Inbox
  HudTab.ASK -> Icons.Outlined.AutoAwesome
}

// ---------------------------------------------------------------- Previews

@Preview(name = "Home", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0B1120)
@Composable
private fun PreviewHome() = JarvisApp(PreviewFixtures.state(HudTab.HUD), PreviewFixtures.noOpActions)

@Preview(name = "Money", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0B1120)
@Composable
private fun PreviewMoney() = JarvisApp(PreviewFixtures.state(HudTab.FINANCE), PreviewFixtures.noOpActions)

@Preview(name = "Inbox", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0B1120)
@Composable
private fun PreviewInbox() = JarvisApp(PreviewFixtures.state(HudTab.INTEL), PreviewFixtures.noOpActions)

@Preview(name = "Ask", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0B1120)
@Composable
private fun PreviewAsk() = JarvisApp(PreviewFixtures.state(HudTab.ASK).copy(isAskingAi = true), PreviewFixtures.noOpActions)

@Preview(name = "Tablet", widthDp = 900, heightDp = 600, showBackground = true, backgroundColor = 0xFF0B1120)
@Composable
private fun PreviewTablet() = JarvisApp(PreviewFixtures.state(HudTab.HUD), PreviewFixtures.noOpActions)

@Preview(name = "Settings", widthDp = 400, heightDp = 880, showBackground = true, backgroundColor = 0xFF0B1120)
@Composable
private fun PreviewSettings() = JarvisApp(PreviewFixtures.state().copy(showSettings = true), PreviewFixtures.noOpActions)
