package com.jarvis.hud.insights

import com.jarvis.hud.contracts.OpenRouterClient
import com.jarvis.hud.model.MealLog
import com.jarvis.hud.model.SleepLog
import com.jarvis.hud.model.WorkoutLog
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Local-first nutrition, workout, and calm daily insight engine inspired by @itspsneha and Ojas.
 * Uses OpenRouter when an API key is configured; otherwise falls back to deterministic Indian/everyday food rules.
 */
object WellnessEngine {

  data class MacroEstimate(
    val title: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val note: String,
  )

  suspend fun estimateMeal(
    description: String,
    apiKey: String,
    modelId: String,
    client: OpenRouterClient?,
  ): MacroEstimate {
    val clean = description.trim()
    if (apiKey.isNotBlank() && client != null) {
      aiMealEstimate(clean, apiKey, modelId, client)?.let { return it }
    }
    return localMealEstimate(clean)
  }

  private suspend fun aiMealEstimate(
    description: String,
    apiKey: String,
    modelId: String,
    client: OpenRouterClient,
  ): MacroEstimate? = try {
    val system = """
      Estimate nutrition for the meal described. Reply with ONLY valid JSON, no markdown:
      {"title": string (short clean dish name, max 42 chars), "calories": int, "proteinG": int, "carbsG": int, "fatG": int}
    """.trimIndent()
    val raw = client.chat(apiKey, modelId, system, listOf("user" to description)).getOrNull() ?: return null
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val obj = JSONObject(raw.substring(start, end + 1))
    val cal = obj.optInt("calories", 0).coerceIn(20, 3000)
    MacroEstimate(
      title = obj.optString("title", description).ifBlank { description }.take(48),
      calories = cal,
      proteinG = obj.optInt("proteinG", 10).coerceIn(0, 250),
      carbsG = obj.optInt("carbsG", 25).coerceIn(0, 400),
      fatG = obj.optInt("fatG", 8).coerceIn(0, 200),
      note = "AI macro estimate",
    )
  } catch (e: CancellationException) {
    throw e
  } catch (_: Exception) {
    null
  }

  /** Pure-Kotlin Indian & everyday meal nutrition estimator. */
  fun localMealEstimate(description: String): MacroEstimate {
    val lower = description.lowercase(Locale.ENGLISH)
    var cal = 0
    var p = 0
    var c = 0
    var f = 0
    var hits = 0

    DISH_MACROS.forEach { (re, macros) ->
      if (re.containsMatchIn(lower)) {
        val qty = quantityNear(lower, re)
        cal += (macros[0] * qty).toInt()
        p += (macros[1] * qty).toInt()
        c += (macros[2] * qty).toInt()
        f += (macros[3] * qty).toInt()
        hits++
      }
    }

    if (hits == 0) {
      cal = 380; p = 14; c = 46; f = 14
    }

    val title = description.trim().replaceFirstChar { it.titlecase(Locale.ENGLISH) }.take(48)
    return MacroEstimate(
      title = title,
      calories = cal.coerceIn(40, 2500),
      proteinG = p.coerceIn(1, 200),
      carbsG = c.coerceIn(1, 350),
      fatG = f.coerceIn(1, 150),
      note = if (hits > 0) "On-device dish estimate" else "Gentle baseline estimate",
    )
  }

  private fun quantityNear(lower: String, dishRe: Regex): Double {
    val m = dishRe.find(lower) ?: return 1.0
    val prefix = lower.substring(maxOf(0, m.range.first - 6), m.range.first)
    val num = Regex("""(\d+(?:\.\d+)?)\s*$""").find(prefix)?.groupValues?.get(1)?.toDoubleOrNull()
    return (num ?: 1.0).coerceIn(0.5, 6.0)
  }

  fun estimateWorkoutCalories(description: String, durationMinutes: Int): Int {
    val lower = description.lowercase(Locale.ENGLISH)
    val kcalPerMin = when {
      Regex("""\b(run|jog|sprint|hiit)\b""").containsMatchIn(lower) -> 10.5
      Regex("""\b(cycle|cycling|bike|swim)\b""").containsMatchIn(lower) -> 8.5
      Regex("""\b(gym|strength|lift|weight)\b""").containsMatchIn(lower) -> 6.5
      Regex("""\b(yoga|stretch|mobility)\b""").containsMatchIn(lower) -> 3.5
      else -> 5.0 // walk / general movement
    }
    return (durationMinutes.coerceIn(5, 360) * kcalPerMin).toInt()
  }

  /** Calm, personal context notes inspired by @itspsneha's quote banners. */
  fun calmInsights(
    sleep: SleepLog?,
    steps: Int,
    stepGoal: Int,
    meals: List<MealLog>,
    workouts: List<WorkoutLog>,
  ): List<String> = buildList {
    if (sleep != null) {
      val hrs = sleep.totalMinutes / 60
      val mins = sleep.totalMinutes % 60
      if (sleep.totalMinutes < 390) {
        add("body: ${hrs}h ${mins}m sleep. take it gentle today — rest counts too.")
      } else {
        add("rested ${hrs}h ${mins}m. steady energy for what matters today.")
      }
    }
    if (meals.isEmpty()) {
      add("no bad foods here. just gentle logging when you feel like it.")
    } else {
      val protein = meals.sumOf { it.proteinG }
      add("${protein}g protein logged so far today — nourished and on your side.")
    }
    if (steps >= stepGoal) {
      add("$steps steps in the bank — your body thanked you today.")
    } else if (workouts.isNotEmpty()) {
      add("${workouts.sumOf { it.durationMinutes }}m movement logged. consistency beats intensity.")
    }
    add("chamomile at 10, phone away at 10:30. that's the plan.")
  }

  private fun kw(vararg words: String) =
    Regex("""(?<![a-z0-9])(?:${words.joinToString("|") { Regex.escape(it) }})(?![a-z0-9])""")

  // [calories, proteinG, carbsG, fatG]
  private val DISH_MACROS: List<Pair<Regex, IntArray>> = listOf(
    kw("idli", "idlis") to intArrayOf(65, 2, 13, 1),
    kw("dosa", "masala dosa") to intArrayOf(280, 6, 44, 9),
    kw("poha") to intArrayOf(250, 5, 45, 6),
    kw("upma") to intArrayOf(240, 6, 40, 7),
    kw("paratha", "aloo paratha") to intArrayOf(290, 6, 40, 12),
    kw("roti", "chapati", "phulka") to intArrayOf(105, 3, 20, 1),
    kw("rice", "jeera rice", "steamed rice") to intArrayOf(210, 4, 45, 1),
    kw("biryani") to intArrayOf(460, 22, 54, 16),
    kw("dal", "sambar", "rasam") to intArrayOf(160, 9, 24, 4),
    kw("paneer") to intArrayOf(290, 16, 10, 20),
    kw("chicken") to intArrayOf(260, 32, 4, 12),
    kw("egg", "eggs", "omelette", "bhurji") to intArrayOf(85, 7, 1, 6),
    kw("fish", "salmon") to intArrayOf(220, 26, 2, 11),
    kw("oats", "oatmeal") to intArrayOf(220, 8, 36, 4),
    kw("whey", "protein shake", "shake") to intArrayOf(150, 25, 6, 2),
    kw("curd", "yogurt", "dahi") to intArrayOf(110, 6, 8, 5),
    kw("salad") to intArrayOf(140, 5, 14, 7),
    kw("sandwich", "toast") to intArrayOf(260, 9, 34, 9),
    kw("chai", "tea", "coffee", "latte") to intArrayOf(80, 2, 10, 3),
    kw("banana", "apple", "fruit", "mango", "papaya") to intArrayOf(95, 1, 24, 0),
    kw("samosa", "pakora", "vada") to intArrayOf(230, 4, 26, 12),
  )
}
