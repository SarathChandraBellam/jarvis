package com.astra.hud.ui.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.astra.hud.contracts.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * [Speaker] backed by Android TextToSpeech. British voice (falls back to en-IN, then default),
 * slightly lowered pitch for a calm assistant feel. Text spoken before init completes is queued.
 */
class AndroidSpeaker(context: Context) : Speaker {
  private val _isSpeaking = MutableStateFlow(false)
  override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val lock = Any()
  private var ready = false
  private var failed = false
  private var pending: String? = null
  private val activeUtterances = AtomicInteger(0)
  private var counter = 0L

  private fun onInit(status: Int) {
    val queued: String?
    synchronized(lock) {
      if (status != TextToSpeech.SUCCESS) {
        failed = true
        pending = null
        _isSpeaking.value = false
        return
      }
      configureVoice()
      tts.setOnUtteranceProgressListener(listener)
      ready = true
      queued = pending
      pending = null
    }
    queued?.let { speak(it) }
  }

  private fun configureVoice() {
    val candidates = listOf(Locale.UK, Locale.forLanguageTag("en-IN"), Locale.US, Locale.ENGLISH)
    for (loc in candidates) {
      val r = runCatching { tts.setLanguage(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
      if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) break
    }
    tts.setPitch(0.9f)
    tts.setSpeechRate(1.0f)
  }

  private val listener = object : UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) {
      _isSpeaking.value = true
    }

    override fun onDone(utteranceId: String?) = finished()

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) = finished()

    override fun onError(utteranceId: String?, errorCode: Int) = finished()

    override fun onStop(utteranceId: String?, interrupted: Boolean) {
      activeUtterances.set(0)
      _isSpeaking.value = false
    }
  }

  // Declared after [listener] so it is initialized before any init callback can touch it.
  private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> onInit(status) }

  private fun finished() {
    if (activeUtterances.decrementAndGet() <= 0) {
      activeUtterances.set(0)
      _isSpeaking.value = false
    }
  }

  /** Replaces anything currently speaking. Long text is chunked to the engine's input limit. */
  override fun speak(text: String) {
    val clean = text.replace(Regex("[*_#`>]"), "").replace("ASTRA", "Astra").trim()
    if (clean.isEmpty()) return
    synchronized(lock) {
      if (failed) return
      if (!ready) {
        pending = clean
        _isSpeaking.value = true // reflect intent so UI toggles immediately
        return
      }
    }
    val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000).coerceAtLeast(200) - 1
    val chunks = chunk(clean, max)
    activeUtterances.set(chunks.size)
    _isSpeaking.value = true
    chunks.forEachIndexed { i, c ->
      val id = "astra-${counter++}"
      val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
      if (tts.speak(c, mode, null, id) == TextToSpeech.ERROR) finished()
    }
  }

  override fun stop() {
    synchronized(lock) { pending = null }
    if (ready) runCatching { tts.stop() }
    activeUtterances.set(0)
    _isSpeaking.value = false
  }

  override fun shutdown() {
    stop()
    synchronized(lock) {
      ready = false
      failed = true
    }
    runCatching { tts.shutdown() }
  }

  private fun chunk(text: String, max: Int): List<String> {
    if (text.length <= max) return listOf(text)
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    text.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
      var s = sentence
      while (s.length > max) {
        if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
        out += s.take(max)
        s = s.drop(max)
      }
      if (sb.length + s.length + 1 > max) { out += sb.toString(); sb.clear() }
      if (sb.isNotEmpty()) sb.append(' ')
      sb.append(s)
    }
    if (sb.isNotEmpty()) out += sb.toString()
    return out.filter { it.isNotBlank() }
  }
}
