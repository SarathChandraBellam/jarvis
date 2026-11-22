package com.jarvis.hud.data.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jarvis.hud.contracts.SettingsRepository
import com.jarvis.hud.model.DataSource
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
import kotlinx.coroutines.withContext

/**
 * Persists [JarvisSettings] in SharedPreferences. The OpenRouter API key is encrypted with
 * AES/GCM/NoPadding using a non-exportable Android Keystore key (alias [KEY_ALIAS]); the
 * ciphertext and IV are stored base64-encoded. Settings load synchronously at construction.
 */
class SharedPrefsSettingsRepository(context: Context) : SettingsRepository {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val state = MutableStateFlow(load())
  override val settings: StateFlow<JarvisSettings> = state.asStateFlow()

  override suspend fun update(settings: JarvisSettings) {
    withContext(Dispatchers.IO) {
      val editor = prefs.edit()
        .putString(K_MODEL, settings.modelId)
        .putString(K_CALLSIGN, settings.userCallsign)
        .putBoolean(K_DEMO, settings.demoModeEnabled)
        .putBoolean(K_VOICE, settings.voiceEnabled)
        .putBoolean(K_REDACT, settings.redactSensitiveData)
        .putStringSet(K_ENABLED, settings.enabledSources.map { it.name }.toSet())
        .putStringSet(K_AI_SHARE, settings.aiShareSources.map { it.name }.toSet())
      val key = settings.apiKey.trim()
      if (key.isEmpty()) {
        editor.remove(K_KEY_CT).remove(K_KEY_IV)
      } else if (key != state.value.apiKey || !prefs.contains(K_KEY_CT)) {
        val enc = runCatching { encrypt(key) }.getOrNull()
        if (enc != null) {
          editor.putString(K_KEY_CT, enc.first).putString(K_KEY_IV, enc.second)
        } else {
          editor.remove(K_KEY_CT).remove(K_KEY_IV)
        }
      }
      editor.apply()
    }
    state.value = settings.copy(apiKey = settings.apiKey.trim())
  }

  private fun load(): JarvisSettings {
    val defaults = JarvisSettings()
    return try {
      JarvisSettings(
        apiKey = loadApiKey(),
        modelId = prefs.getString(K_MODEL, null)?.takeIf { it.isNotBlank() } ?: defaults.modelId,
        userCallsign = prefs.getString(K_CALLSIGN, null)?.takeIf { it.isNotBlank() } ?: defaults.userCallsign,
        demoModeEnabled = prefs.getBoolean(K_DEMO, defaults.demoModeEnabled),
        voiceEnabled = prefs.getBoolean(K_VOICE, defaults.voiceEnabled),
        redactSensitiveData = prefs.getBoolean(K_REDACT, defaults.redactSensitiveData),
        enabledSources = prefs.getStringSet(K_ENABLED, null)?.let(::toSources) ?: defaults.enabledSources,
        aiShareSources = prefs.getStringSet(K_AI_SHARE, null)?.let(::toSources) ?: defaults.aiShareSources,
      )
    } catch (_: Exception) {
      defaults
    }
  }

  private fun toSources(names: Set<String>): Set<DataSource> =
    names.mapNotNull { n -> DataSource.entries.firstOrNull { it.name == n } }.toSet()

  private fun loadApiKey(): String {
    val ct = prefs.getString(K_KEY_CT, null) ?: return ""
    val iv = prefs.getString(K_KEY_IV, null) ?: return ""
    return runCatching { decrypt(ct, iv) }.getOrDefault("")
  }

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

  /** @return (ciphertext base64, iv base64) */
  private fun encrypt(plain: String): Pair<String, String> {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    val b64 = Base64.getEncoder()
    return b64.encodeToString(ct) to b64.encodeToString(cipher.iv)
  }

  private fun decrypt(ctB64: String, ivB64: String): String {
    val dec = Base64.getDecoder()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, dec.decode(ivB64)))
    return String(cipher.doFinal(dec.decode(ctB64)), Charsets.UTF_8)
  }

  companion object {
    const val PREFS_NAME = "jarvis_settings"
    const val KEY_ALIAS = "jarvis_api_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private const val K_KEY_CT = "api_key_ct"
    private const val K_KEY_IV = "api_key_iv"
    private const val K_MODEL = "model_id"
    private const val K_CALLSIGN = "callsign"
    private const val K_DEMO = "demo_mode"
    private const val K_VOICE = "voice"
    private const val K_REDACT = "redact"
    private const val K_ENABLED = "enabled_sources"
    private const val K_AI_SHARE = "ai_share_sources"
  }
}
