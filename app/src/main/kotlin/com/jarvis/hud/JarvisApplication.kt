package com.jarvis.hud

import android.app.Application

class JarvisApplication : Application() {
  /** Lazily built so the TTS engine and Keystore aren't touched until the UI needs them. */
  val container: AppContainer by lazy { AppContainer(this) }
}
