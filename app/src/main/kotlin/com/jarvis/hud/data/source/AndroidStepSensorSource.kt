package com.jarvis.hud.data.source

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.jarvis.hud.contracts.StepSensorSource
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the phone's hardware step counter ([Sensor.TYPE_STEP_COUNTER]) and tracks today's
 * delta locally in SharedPreferences — zero restricted usage-stats access required.
 */
class AndroidStepSensorSource(context: Context) : StepSensorSource {
  private val app = context.applicationContext
  private val prefs = app.getSharedPreferences("jarvis_steps", Context.MODE_PRIVATE)

  override suspend fun readTodaySteps(): Int? = withContext(Dispatchers.Default) {
    val sm = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@withContext null
    val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return@withContext null
    val rawTotal = withTimeoutOrNull(800L) {
      suspendCancellableCoroutine<Int?> { cont ->
        val listener = object : SensorEventListener {
          override fun onSensorChanged(event: SensorEvent) {
            sm.unregisterListener(this)
            if (cont.isActive) cont.resume(event.values.firstOrNull()?.toInt())
          }
          override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (!registered && cont.isActive) cont.resume(null)
        cont.invokeOnCancellation { sm.unregisterListener(listener) }
      }
    } ?: return@withContext null

    val today = LocalDate.now().toString()
    val savedDay = prefs.getString(K_DAY, null)
    var baseline = prefs.getInt(K_BASELINE, -1)
    if (savedDay != today || baseline < 0 || rawTotal < baseline) {
      baseline = rawTotal
      prefs.edit().putString(K_DAY, today).putInt(K_BASELINE, baseline).apply()
    }
    (rawTotal - baseline).coerceAtLeast(0)
  }

  private companion object {
    const val K_DAY = "step_day"
    const val K_BASELINE = "step_baseline"
  }
}
