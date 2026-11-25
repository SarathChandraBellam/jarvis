package com.astra.hud

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.astra.hud.model.AstraActions
import com.astra.hud.ui.AstraApp
import com.astra.hud.ui.AstraViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: AstraViewModel by viewModels {
    AstraViewModel.factory((application as AstraApplication).container)
  }

  private val runtimePermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      viewModel.refreshPermissions()
    }

  /** ViewModel handles everything except the three actions that need an Activity. */
  private val actions: AstraActions by lazy {
    object : AstraActions by viewModel {
      override fun onRequestRuntimePermissions() {
        runtimePermissionLauncher.launch(RUNTIME_PERMISSIONS)
      }

      override fun onOpenNotificationAccessSettings() {
        openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
      }

      override fun onOpenUsageAccessSettings() {
        openSettings(
          Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:$packageName")),
          fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      AstraApp(state = state, actions = actions) // applies AstraTheme itself
    }
  }

  override fun onResume() {
    super.onResume()
    // User may have just granted notification/usage access in system settings.
    viewModel.refreshPermissions()
    viewModel.refreshTelemetry()
  }

  private fun openSettings(intent: Intent, fallback: Intent? = null) {
    try {
      startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      fallback?.let { runCatching { startActivity(it) } }
    }
  }

  private companion object {
    val RUNTIME_PERMISSIONS = buildList {
      add(Manifest.permission.READ_SMS)
      add(Manifest.permission.READ_CALENDAR)
      add(Manifest.permission.READ_CALL_LOG)
      add(Manifest.permission.READ_CONTACTS)
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        add(Manifest.permission.ACTIVITY_RECOGNITION)
      }
    }.toTypedArray()
  }
}
