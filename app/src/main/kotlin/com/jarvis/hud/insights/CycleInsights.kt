package com.jarvis.hud.insights

import com.jarvis.hud.model.CyclePhase
import com.jarvis.hud.model.CycleState
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Pure Kotlin Cycle & Period calculations modeled after @itspsneha's Cycle screen.
 */
data class CycleSummary(
  val today: LocalDate,
  val hasData: Boolean,
  val cycleDay: Int,
  val averageCycleLength: Int,
  val periodDurationDays: Int,
  val phase: CyclePhase,
  val nextPeriodDate: LocalDate?,
  val daysToNextPeriod: Int?,
  val periodDaysInMonth: Set<LocalDate>,
  val predictedPeriodDaysInMonth: Set<LocalDate>,
  val cycleProgressFraction: Float,
) {
  companion object {
    fun from(state: CycleState, today: LocalDate = LocalDate.now()): CycleSummary {
      val parsedStarts = state.periodStartDates
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .distinct()
        .sorted()
      val avgLen = computeAverageLength(parsedStarts, state.cycleLengthDays).coerceIn(21, 45)
      val dur = state.periodDurationDays.coerceIn(2, 9)
      val lastStart = parsedStarts.lastOrNull { !it.isAfter(today) } ?: parsedStarts.lastOrNull()

      if (lastStart == null) {
        return CycleSummary(
          today = today,
          hasData = false,
          cycleDay = 1,
          averageCycleLength = avgLen,
          periodDurationDays = dur,
          phase = CyclePhase.FOLLICULAR,
          nextPeriodDate = null,
          daysToNextPeriod = null,
          periodDaysInMonth = emptySet(),
          predictedPeriodDaysInMonth = emptySet(),
          cycleProgressFraction = 0.05f,
        )
      }

      val rawDiff = ChronoUnit.DAYS.between(lastStart, today).toInt()
      val cycleDay = if (rawDiff >= 0) (rawDiff % avgLen) + 1 else 1
      val cyclesElapsed = if (rawDiff >= 0) rawDiff / avgLen else 0
      val currentCycleStart = lastStart.plusDays((cyclesElapsed * avgLen).toLong())
      val nextStart = currentCycleStart.plusDays(avgLen.toLong())
      val daysToNext = ChronoUnit.DAYS.between(today, nextStart).toInt().coerceAtLeast(0)

      val ovulationDay = (avgLen - 14).coerceIn(dur + 2, avgLen - 4)
      val phase = when {
        cycleDay <= dur -> CyclePhase.MENSTRUAL
        cycleDay < ovulationDay - 2 -> CyclePhase.FOLLICULAR
        cycleDay <= ovulationDay + 2 -> CyclePhase.MID_CYCLE
        else -> CyclePhase.LUTEAL
      }

      val month = YearMonth.from(today)
      val loggedPeriodDays = parsedStarts.flatMap { start ->
        (0 until dur).map { d -> start.plusDays(d.toLong()) }
      }.filter { YearMonth.from(it) == month }.toSet()

      val predictedDays = (0 until dur).map { d -> nextStart.plusDays(d.toLong()) }
        .filter { YearMonth.from(it) == month }
        .toSet()

      return CycleSummary(
        today = today,
        hasData = true,
        cycleDay = cycleDay,
        averageCycleLength = avgLen,
        periodDurationDays = dur,
        phase = phase,
        nextPeriodDate = nextStart,
        daysToNextPeriod = daysToNext,
        periodDaysInMonth = loggedPeriodDays,
        predictedPeriodDaysInMonth = predictedDays,
        cycleProgressFraction = (cycleDay.toFloat() / avgLen.toFloat()).coerceIn(0.03f, 1f),
      )
    }

    private fun computeAverageLength(starts: List<LocalDate>, fallback: Int): Int {
      if (starts.size < 2) return fallback
      val diffs = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        .filter { it in 20..50 }
      return if (diffs.isEmpty()) fallback else diffs.average().toInt()
    }
  }
}
