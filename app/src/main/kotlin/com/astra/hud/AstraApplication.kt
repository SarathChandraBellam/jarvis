package com.astra.hud

import android.app.Application

class AstraApplication : Application() {
  /** Lazily built so the TTS engine and Keystore aren't touched until the UI needs them. */
  val container: AppContainer by lazy { AppContainer(this) }
}
