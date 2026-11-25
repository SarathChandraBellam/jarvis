package com.astra.hud

import android.content.Context
import com.astra.hud.ai.HybridIntelOrganizer
import com.astra.hud.ai.AstraAskAssistant
import com.astra.hud.ai.AstraBriefingGenerator
import com.astra.hud.ai.OkHttpOpenRouterClient
import com.astra.hud.ai.RegexRedactor
import com.astra.hud.contracts.AstraContainer
import com.astra.hud.contracts.NotificationFeed
import com.astra.hud.contracts.PlannerStore
import com.astra.hud.contracts.ProactiveNotifier
import com.astra.hud.contracts.SettingsRepository
import com.astra.hud.contracts.StepSensorSource
import com.astra.hud.data.AndroidProactiveNotifier
import com.astra.hud.data.source.AndroidPermissionChecker
import com.astra.hud.data.source.AndroidPhoneDataSource
import com.astra.hud.data.source.AndroidStepSensorSource
import com.astra.hud.data.source.NotificationHub
import com.astra.hud.data.store.FileIntelStore
import com.astra.hud.data.store.FilePlannerStore
import com.astra.hud.data.store.SharedPrefsSettingsRepository
import com.astra.hud.ui.voice.AndroidSpeaker

/** Production wiring of every contract implementation. Built once in [AstraApplication]. */
class AppContainer(context: Context) : AstraContainer {
  private val app = context.applicationContext

  override val settingsRepo: SettingsRepository = SharedPrefsSettingsRepository(app)
  override val phoneData = AndroidPhoneDataSource(app)
  override val stepSensor: StepSensorSource = AndroidStepSensorSource(app)
  override val permissions = AndroidPermissionChecker(app)
  override val notifications: NotificationFeed = NotificationHub
  override val openRouter = OkHttpOpenRouterClient()
  override val organizer = HybridIntelOrganizer(openRouter, RegexRedactor())
  override val briefings = AstraBriefingGenerator(openRouter)
  override val assistant = AstraAskAssistant(openRouter)
  override val store = FileIntelStore(app)
  override val plannerStore: PlannerStore = FilePlannerStore(app)
  override val notifier: ProactiveNotifier = AndroidProactiveNotifier(app)
  override val speaker = AndroidSpeaker(app)
}
