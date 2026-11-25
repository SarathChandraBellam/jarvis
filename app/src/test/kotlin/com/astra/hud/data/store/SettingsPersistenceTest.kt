package com.astra.hud.data.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.astra.hud.model.DataSource
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.UserGender
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reversible stand-in for the Android Keystore (unavailable on the JVM). */
private class FakeCipher(var failEncrypt: Boolean = false) : SecretCipher {
  var resets = 0
  override fun encrypt(plain: String): Pair<String, String> {
    if (failEncrypt) throw java.security.KeyStoreException("keystore broken")
    return plain.reversed() to "iv"
  }
  override fun decrypt(ctB64: String, ivB64: String) = ctB64.reversed()
  override fun reset() { resets++ }
}

/** Settings must survive an app restart — this is what "save not working" was about. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsPersistenceTest {
  private val ctx: Context = ApplicationProvider.getApplicationContext()
  private val prefs get() = ctx.getSharedPreferences(SharedPrefsSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)

  @Before fun clear() { prefs.edit().clear().commit() }

  @Test fun everyFieldSurvivesRestart() = runTest {
    val cipher = FakeCipher()
    val edited = AstraSettings(
      apiKey = "sk-or-v1-secret",
      modelId = "meta/llama",
      userCallsign = "Boss",
      voiceEnabled = false,
      redactSensitiveData = false,
      enabledSources = setOf(DataSource.SMS, DataSource.CALENDAR),
      aiShareSources = setOf(DataSource.SMS),
      onboardingDone = true,
      monthlyBudget = 42_000.0,
      selectedTabName = "FINANCE",
      todayViewIndex = 2,
      subsViewIndex = 1,
      inboxCategoryName = "BILL",
      inboxShowCompleted = true,
      proactiveNotificationsEnabled = false,
      renewalLeadDays = 7,
      morningBriefingHour = 9,
      dailyFocusGoalMinutes = 180,
      dailyCalorieGoal = 2100,
      dailyStepGoal = 10000,
      sleepGoalMinutes = 450,
      userGender = UserGender.MALE,
      cycleTrackingEnabled = false,
    )
    SharedPrefsSettingsRepository(ctx, cipher).update(edited)
    val reloaded = SharedPrefsSettingsRepository(ctx, cipher).settings.value
    assertEquals(edited, reloaded)
  }

  @Test fun apiKeyIsNotStoredInPlainText() = runTest {
    SharedPrefsSettingsRepository(ctx, FakeCipher()).update(AstraSettings(apiKey = "sk-or-v1-secret"))
    assertFalse(prefs.all.values.any { it.toString().contains("sk-or-v1-secret") })
  }

  @Test fun keyAndModelAreTrimmed() = runTest {
    val repo = SharedPrefsSettingsRepository(ctx, FakeCipher())
    repo.update(AstraSettings(apiKey = "  sk-x  ", modelId = " m/1 "))
    assertEquals("sk-x", repo.settings.value.apiKey)
    assertEquals("m/1", SharedPrefsSettingsRepository(ctx, FakeCipher()).settings.value.modelId)
  }

  @Test fun clearingKeyRemovesIt() = runTest {
    val repo = SharedPrefsSettingsRepository(ctx, FakeCipher())
    repo.update(AstraSettings(apiKey = "sk-x"))
    repo.update(AstraSettings(apiKey = ""))
    assertEquals("", SharedPrefsSettingsRepository(ctx, FakeCipher()).settings.value.apiKey)
  }

  @Test fun brokenKeystoreSurfacesErrorInsteadOfSilentlyDroppingKey() = runTest {
    val cipher = FakeCipher(failEncrypt = true)
    val repo = SharedPrefsSettingsRepository(ctx, cipher)
    try {
      repo.update(AstraSettings(apiKey = "sk-x", userCallsign = "Boss"))
      fail("expected an error")
    } catch (e: IllegalStateException) {
      assertTrue(e.message!!.contains("API key"))
    }
    assertEquals(1, cipher.resets)
    assertNotEquals("sk-x", repo.settings.value.apiKey)
  }

  @Test fun concurrentSavesKeepTheLastOne() = runTest {
    val repo = SharedPrefsSettingsRepository(ctx, FakeCipher())
    val a = async { repo.update(AstraSettings(userCallsign = "First")) }
    val b = async { repo.update(AstraSettings(userCallsign = "Second")) }
    a.await(); b.await()
    assertEquals("Second", repo.settings.value.userCallsign)
    assertEquals("Second", SharedPrefsSettingsRepository(ctx, FakeCipher()).settings.value.userCallsign)
  }

  @Test fun freshInstallNeedsOnboardingButUpgradeDoesNot() {
    assertFalse(SharedPrefsSettingsRepository(ctx, FakeCipher()).settings.value.onboardingDone)
    // 1.0.x stored a callsign and a demo flag but no onboarding flag.
    prefs.edit().putString("callsign", "Alex").putBoolean("demo_mode", true).commit()
    assertTrue(SharedPrefsSettingsRepository(ctx, FakeCipher()).settings.value.onboardingDone)
  }

  @Test fun legacyDemoFlagIsRemovedOnSave() = runTest {
    prefs.edit().putBoolean("demo_mode", true).commit()
    SharedPrefsSettingsRepository(ctx, FakeCipher()).update(AstraSettings())
    assertFalse(prefs.contains("demo_mode"))
  }

  @Test fun mismatchedSinglePreferenceDoesNotWipeOtherSelections() = runTest {
    val cipher = FakeCipher()
    SharedPrefsSettingsRepository(ctx, cipher).update(
      AstraSettings(userCallsign = "Maya", onboardingDone = true, selectedTabName = "INTEL", subsViewIndex = 2),
    )
    // Simulate a legacy/mismatched type on one key across an upgrade.
    prefs.edit().putString("subs_view_index", "bad-type").commit()
    val reloaded = SharedPrefsSettingsRepository(ctx, cipher).settings.value
    assertEquals("Maya", reloaded.userCallsign)
    assertTrue(reloaded.onboardingDone)
    assertEquals("INTEL", reloaded.selectedTabName)
    assertEquals(0, reloaded.subsViewIndex)
  }
}
