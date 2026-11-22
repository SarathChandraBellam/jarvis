package com.jarvis.hud

import android.content.Context
import com.jarvis.hud.ai.HybridIntelOrganizer
import com.jarvis.hud.ai.JarvisAskAssistant
import com.jarvis.hud.ai.JarvisBriefingGenerator
import com.jarvis.hud.ai.OkHttpOpenRouterClient
import com.jarvis.hud.ai.RegexRedactor
import com.jarvis.hud.contracts.DemoDataProvider
import com.jarvis.hud.contracts.JarvisContainer
import com.jarvis.hud.contracts.NotificationFeed
import com.jarvis.hud.contracts.SettingsRepository
import com.jarvis.hud.data.source.AndroidPermissionChecker
import com.jarvis.hud.data.source.AndroidPhoneDataSource
import com.jarvis.hud.data.source.IndianDemoDataProvider
import com.jarvis.hud.data.source.NotificationHub
import com.jarvis.hud.data.store.FileIntelStore
import com.jarvis.hud.data.store.SharedPrefsSettingsRepository
import com.jarvis.hud.model.CalendarEntry
import com.jarvis.hud.model.RawEvent
import com.jarvis.hud.ui.voice.AndroidSpeaker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

/** Production wiring of every contract implementation. Built once in [JarvisApplication]. */
class AppContainer(context: Context) : JarvisContainer {
  private val app = context.applicationContext

  override val settingsRepo: SettingsRepository = SharedPrefsSettingsRepository(app)
  override val phoneData = AndroidPhoneDataSource(app)
  override val permissions = AndroidPermissionChecker(app)
  override val demoData: DemoDataProvider = SessionPinnedDemoData(IndianDemoDataProvider())
  override val notifications: NotificationFeed = DemoAwareNotificationFeed(NotificationHub, settingsRepo)
  override val openRouter = OkHttpOpenRouterClient()
  override val organizer = HybridIntelOrganizer(openRouter, RegexRedactor())
  override val briefings = JarvisBriefingGenerator(openRouter)
  override val assistant = JarvisAskAssistant(openRouter)
  override val store = FileIntelStore(app)
  override val speaker = AndroidSpeaker(app)
}

/**
 * Demo raw events are hashed from timestamps relative to `now`, so re-seeding at a new time would
 * look like brand-new events. Pin them to one anchor per process so repeated scans dedupe.
 */
private class SessionPinnedDemoData(private val delegate: DemoDataProvider) : DemoDataProvider by delegate {
  private val anchor = System.currentTimeMillis()
  override fun rawEvents(nowMillis: Long): List<RawEvent> = delegate.rawEvents(anchor)
  override fun calendar(nowMillis: Long): List<CalendarEntry> = delegate.calendar(nowMillis)
}

/** Suppresses real live notifications while demo mode is on, so demo and real data never mix. */
private class DemoAwareNotificationFeed(
  private val delegate: NotificationFeed,
  private val settings: SettingsRepository,
) : NotificationFeed {
  override val events: Flow<RawEvent> = delegate.events.filter { !settings.settings.value.demoModeEnabled }
  override fun snapshot(): List<RawEvent> = delegate.snapshot()
}
