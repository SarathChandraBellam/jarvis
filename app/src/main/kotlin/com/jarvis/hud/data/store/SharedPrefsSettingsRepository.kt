package com.jarvis.hud.data.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jarvis.hud.contracts.SettingsRepository
import com.jarvis.hud.model.DataSource
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.JarvisSettings
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Persists [JarvisSettings] and UI selections in SharedPreferences.
 * Every preference key is read independently with type-safe fallbacks so an app version
 * upgrade or a single schema mismatch never wipes out other user selections.
 */
class SharedPrefsSettingsRepository(
  context: Context,
  private val cipher: SecretCipher = KeystoreCipher(),
) : SettingsRepository {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val state = MutableStateFlow(load())
  override val settings: StateFlow<JarvisSettings> = state.asStateFlow()

  private val writeLock = Mutex()

  override suspend fun update(settings: JarvisSettings) = writeLock.withLock {
    val clean = settings.copy(apiKey = settings.apiKey.trim(), modelId = settings.modelId.trim())
    withContext(Dispatchers.IO) {
      val editor = prefs.edit()
        .putString(K_MODEL, clean.modelId)
        .putString(K_CALLSIGN, clean.userCallsign)
        .remove(K_LEGACY_DEMO)
        .putBoolean(K_VOICE, clean.voiceEnabled)
        .putBoolean(K_REDACT, clean.redactSensitiveData)
        .putStringSet(K_ENABLED, clean.enabledSources.map { it.name }.toSet())
        .putStringSet(K_AI_SHARE, clean.aiShareSources.map { it.name }.toSet())
        .putBoolean(K_ONBOARDED, clean.onboardingDone)
        .putFloat(K_BUDGET, clean.monthlyBudget.toFloat())
        .putString(K_TAB, clean.selectedTabName)
        .putInt(K_SUBS_VIEW, clean.subsViewIndex)
        .putBoolean(K_INBOX_COMPLETED, clean.inboxShowCompleted)
        .putBoolean(K_PROACTIVE, clean.proactiveNotificationsEnabled)
        .putInt(K_RENEWAL_LEAD, clean.renewalLeadDays)
        .putInt(K_MORNING_HOUR, clean.morningBriefingHour)
        .putInt(K_FOCUS_GOAL, clean.dailyFocusGoalMinutes)
      if (clean.inboxCategoryName == null) editor.remove(K_INBOX_CAT) else editor.putString(K_INBOX_CAT, clean.inboxCategoryName)

      val key = clean.apiKey
      if (key.isEmpty()) {
        editor.remove(K_KEY_CT).remove(K_KEY_IV)
      } else if (key != state.value.apiKey || !prefs.contains(K_KEY_CT)) {
        val (ct, iv) = try {
          cipher.encrypt(key)
        } catch (e: Exception) {
          cipher.reset()
          runCatching { cipher.encrypt(key) }.getOrElse {
            throw IllegalStateException("Couldn't securely store the API key on this device (${e.javaClass.simpleName}).", it)
          }
        }
        editor.putString(K_KEY_CT, ct).putString(K_KEY_IV, iv)
      }
      check(editor.commit()) { "Couldn't write settings to storage." }
    }
    state.value = clean
  }

  private fun load(): JarvisSettings {
    val d = JarvisSettings()
    return JarvisSettings(
      apiKey = loadApiKey(),
      modelId = safeString(K_MODEL)?.takeIf { it.isNotBlank() } ?: d.modelId,
      userCallsign = safeString(K_CALLSIGN)?.takeIf { it.isNotBlank() } ?: d.userCallsign,
      voiceEnabled = safeBool(K_VOICE, d.voiceEnabled),
      redactSensitiveData = safeBool(K_REDACT, d.redactSensitiveData),
      enabledSources = safeStringSet(K_ENABLED)?.let(::toSources) ?: d.enabledSources,
      aiShareSources = safeStringSet(K_AI_SHARE)?.let(::toSources) ?: d.aiShareSources,
      onboardingDone = safeBool(K_ONBOARDED, prefs.contains(K_CALLSIGN)),
      monthlyBudget = safeFloat(K_BUDGET, d.monthlyBudget.toFloat()).toDouble().coerceAtLeast(0.0),
      selectedTabName = safeString(K_TAB)?.takeIf { n -> HudTab.entries.any { it.name == n } } ?: d.selectedTabName,
      subsViewIndex = safeInt(K_SUBS_VIEW, d.subsViewIndex).coerceIn(0, 2),
      inboxCategoryName = safeString(K_INBOX_CAT),
      inboxShowCompleted = safeBool(K_INBOX_COMPLETED, d.inboxShowCompleted),
      proactiveNotificationsEnabled = safeBool(K_PROACTIVE, d.proactiveNotificationsEnabled),
      renewalLeadDays = safeInt(K_RENEWAL_LEAD, d.renewalLeadDays).coerceIn(1, 14),
      morningBriefingHour = safeInt(K_MORNING_HOUR, d.morningBriefingHour).coerceIn(5, 12),
      dailyFocusGoalMinutes = safeInt(K_FOCUS_GOAL, d.dailyFocusGoalMinutes).coerceIn(30, 600),
    )
  }

  private fun safeString(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()
  private fun safeBool(key: String, def: Boolean): Boolean = runCatching { prefs.getBoolean(key, def) }.getOrDefault(def)
  private fun safeInt(key: String, def: Int): Int = runCatching { prefs.getInt(key, def) }.getOrDefault(def)
  private fun safeFloat(key: String, def: Float): Float = runCatching { prefs.getFloat(key, def) }.getOrDefault(def)
  private fun safeStringSet(key: String): Set<String>? = runCatching { prefs.getStringSet(key, null) }.getOrNull()

  private fun toSources(names: Set<String>): Set<DataSource> =
    names.mapNotNull { n -> DataSource.entries.firstOrNull { it.name == n } }.toSet()

  private fun loadApiKey(): String {
    val ct = safeString(K_KEY_CT) ?: return ""
    val iv = safeString(K_KEY_IV) ?: return ""
    return runCatching { cipher.decrypt(ct, iv) }.getOrDefault("")
  }

  companion object {
    const val PREFS_NAME = "jarvis_settings"

    private const val K_KEY_CT = "api_key_ct"
    private const val K_KEY_IV = "api_key_iv"
    private const val K_MODEL = "model_id"
    private const val K_CALLSIGN = "callsign"
    private const val K_LEGACY_DEMO = "demo_mode"
    private const val K_VOICE = "voice"
    private const val K_REDACT = "redact"
    private const val K_ENABLED = "enabled_sources"
    private const val K_AI_SHARE = "ai_share_sources"
    private const val K_ONBOARDED = "onboarding_done"
    private const val K_BUDGET = "monthly_budget"
    private const val K_TAB = "selected_tab"
    private const val K_SUBS_VIEW = "subs_view_index"
    private const val K_INBOX_CAT = "inbox_category"
    private const val K_INBOX_COMPLETED = "inbox_show_completed"
    private const val K_PROACTIVE = "proactive_enabled"
    private const val K_RENEWAL_LEAD = "renewal_lead_days"
    private const val K_MORNING_HOUR = "morning_briefing_hour"
    private const val K_FOCUS_GOAL = "daily_focus_goal_min"
  }
}

interface SecretCipher {
  fun encrypt(plain: String): Pair<String, String>
  fun decrypt(ctB64: String, ivB64: String): String
  fun reset()
}

class KeystoreCipher : SecretCipher {
  private fun secretKey(): SecretKey {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  override fun encrypt(plain: String): Pair<String, String> {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    val b64 = Base64.getEncoder()
    return b64.encodeToString(ct) to b64.encodeToString(cipher.iv)
  }

  override fun decrypt(ctB64: String, ivB64: String): String {
    val dec = Base64.getDecoder()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, dec.decode(ivB64)))
    return String(cipher.doFinal(dec.decode(ctB64)), Charsets.UTF_8)
  }

  override fun reset() {
    runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
  }

  companion object {
    const val KEY_ALIAS = "jarvis_api_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
  }
}
