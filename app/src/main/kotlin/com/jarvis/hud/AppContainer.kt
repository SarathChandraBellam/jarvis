package com.jarvis.hud

import android.content.Context
import com.jarvis.hud.ai.HybridIntelOrganizer
import com.jarvis.hud.ai.JarvisAskAssistant
import com.jarvis.hud.ai.JarvisBriefingGenerator
import com.jarvis.hud.ai.OkHttpOpenRouterClient
import com.jarvis.hud.ai.RegexRedactor
import com.jarvis.hud.contracts.JarvisContainer
import com.jarvis.hud.contracts.NotificationFeed
import com.jarvis.hud.contracts.PlannerStore
import com.jarvis.hud.contracts.ProactiveNotifier
import com.jarvis.hud.contracts.SettingsRepository
import com.jarvis.hud.contracts.StepSensorSource
import com.jarvis.hud.data.AndroidProactiveNotifier
import com.jarvis.hud.data.source.AndroidPermissionChecker
import com.jarvis.hud.data.source.AndroidPhoneDataSource
import com.jarvis.hud.data.source.AndroidStepSensorSource
import com.jarvis.hud.data.source.NotificationHub
import com.jarvis.hud.data.store.FileIntelStore
import com.jarvis.hud.data.store.FilePlannerStore
import com.jarvis.hud.data.store.SharedPrefsSettingsRepository
import com.jarvis.hud.ui.voice.AndroidSpeaker

/** Production wiring of every contract implementation. Built once in [JarvisApplication]. */
class AppContainer(context: Context) : JarvisContainer {
  private val app = context.applicationContext

  override val settingsRepo: SettingsRepository = SharedPrefsSettingsRepository(app)
  override val phoneData = AndroidPhoneDataSource(app)
  override val stepSensor: StepSensorSource = AndroidStepSensorSource(app)
  override val permissions = AndroidPermissionChecker(app)
  override val notifications: NotificationFeed = NotificationHub
  override val openRouter = OkHttpOpenRouterClient()
  override val organizer = HybridIntelOrganizer(openRouter, RegexRedactor())
  override val briefings = JarvisBriefingGenerator(openRouter)
  override val assistant = JarvisAskAssistant(openRouter)
  override val store = FileIntelStore(app)
  override val plannerStore: PlannerStore = FilePlannerStore(app)
  override val notifier: ProactiveNotifier = AndroidProactiveNotifier(app)
  override val speaker = AndroidSpeaker(app)
}
