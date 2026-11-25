package com.astra.hud.insights

import com.astra.hud.data.store.FilePlannerStore
import com.astra.hud.model.CyclePhase
import com.astra.hud.model.CycleState
import com.astra.hud.model.HabitItem
import com.astra.hud.model.AstraSettings
import com.astra.hud.model.MealLog
import com.astra.hud.model.ProactiveKind
import com.astra.hud.model.SleepLog
import com.astra.hud.model.TimerMode
import com.astra.hud.model.UserGender
import com.astra.hud.model.WorkoutLog
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CycleAndWellnessTest {

  @get:Rule val tempDir = TemporaryFolder()

  @Test fun emptyCycleStateReturnsDefaults() {
    val today = LocalDate.of(2026, 11, 24)
    val summary = CycleSummary.from(CycleState(), today)
    assertFalse(summary.hasData)
    assertEquals(1, summary.cycleDay)
    assertEquals(30, summary.averageCycleLength)
    assertEquals(null, summary.nextPeriodDate)
    assertTrue(summary.periodDaysInMonth.isEmpty())
  }

  @Test fun cyclePhasesProgressAccuratelyAcross30DayCycle() {
    val start = LocalDate.of(2026, 11, 1)
    val state = CycleState(periodStartDates = listOf(start.toString()), cycleLengthDays = 30, periodDurationDays = 5)

    // Day 2 -> Menstrual
    val d2 = CycleSummary.from(state, LocalDate.of(2026, 11, 2))
    assertTrue(d2.hasData)
    assertEquals(2, d2.cycleDay)
    assertEquals(CyclePhase.MENSTRUAL, d2.phase)

    // Day 9 -> Follicular (ovulationDay = 16)
    val d9 = CycleSummary.from(state, LocalDate.of(2026, 11, 9))
    assertEquals(9, d9.cycleDay)
    assertEquals(CyclePhase.FOLLICULAR, d9.phase)

    // Day 16 -> Mid-cycle
    val d16 = CycleSummary.from(state, LocalDate.of(2026, 11, 16))
    assertEquals(16, d16.cycleDay)
    assertEquals(CyclePhase.MID_CYCLE, d16.phase)

    // Day 25 -> Luteal, 6 days to next period (Dec 1)
    val d25 = CycleSummary.from(state, LocalDate.of(2026, 11, 25))
    assertEquals(25, d25.cycleDay)
    assertEquals(CyclePhase.LUTEAL, d25.phase)
    assertEquals(6, d25.daysToNextPeriod)
    assertEquals(LocalDate.of(2026, 12, 1), d25.nextPeriodDate)
  }

  @Test fun multiStartDatesComputeObservedAverageLength() {
    val state = CycleState(
      periodStartDates = listOf("2026-09-12", "2026-10-10", "2026-11-08"),
      cycleLengthDays = 30,
      periodDurationDays = 5,
    )
    val summary = CycleSummary.from(state, LocalDate.of(2026, 11, 24))
    // Intervals: 28 and 29 -> integer average 28
    assertEquals(28, summary.averageCycleLength)
    assertEquals(17, summary.cycleDay)
    assertEquals(LocalDate.of(2026, 12, 6), summary.nextPeriodDate)
    assertEquals(12, summary.daysToNextPeriod)
  }

  @Test fun proactivePlannerEmitsCycleNudgeOnlyWhenEnabledAndDueSoon() {
    val zone = ZoneId.of("UTC")
    val today = LocalDate.of(2026, 11, 29)
    val now = ZonedDateTime.of(2026, 11, 29, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    val state = CycleState(periodStartDates = listOf("2026-11-01"), cycleLengthDays = 30, periodDurationDays = 5)

    // Next period Dec 1 -> 2 days away -> nudge shown when enabled
    val alertsEnabled = ProactivePlanner.buildAlerts(
      subscriptions = emptyList(),
      items = emptyList(),
      reminders = emptyList(),
      recentCalls = emptyList(),
      appUsage = emptyList(),
      briefing = null,
      settings = AstraSettings(cycleTrackingEnabled = true, morningBriefingHour = 12),
      dismissedKeys = emptySet(),
      cycleState = state,
      now = now,
      zone = zone,
    )
    assertEquals(1, alertsEnabled.count { it.kind == ProactiveKind.CYCLE })
    assertTrue(alertsEnabled.first { it.kind == ProactiveKind.CYCLE }.title.contains("2 days"))

    // Suppressed when cycleTrackingEnabled = false
    val alertsDisabled = ProactivePlanner.buildAlerts(
      subscriptions = emptyList(),
      items = emptyList(),
      reminders = emptyList(),
      recentCalls = emptyList(),
      appUsage = emptyList(),
      briefing = null,
      settings = AstraSettings(cycleTrackingEnabled = false, morningBriefingHour = 12),
      dismissedKeys = emptySet(),
      cycleState = state,
      now = now,
      zone = zone,
    )
    assertTrue(alertsDisabled.none { it.kind == ProactiveKind.CYCLE })

    // Also suppressed when userGender == MALE even if cycleTrackingEnabled = true
    val alertsMale = ProactivePlanner.buildAlerts(
      subscriptions = emptyList(),
      items = emptyList(),
      reminders = emptyList(),
      recentCalls = emptyList(),
      appUsage = emptyList(),
      briefing = null,
      settings = AstraSettings(userGender = UserGender.MALE, cycleTrackingEnabled = true, morningBriefingHour = 12),
      dismissedKeys = emptySet(),
      cycleState = state,
      now = now,
      zone = zone,
    )
    assertTrue(alertsMale.none { it.kind == ProactiveKind.CYCLE })

    // Also suppressed when > 2 days away
    val earlyNow = ZonedDateTime.of(2026, 11, 15, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    val alertsEarly = ProactivePlanner.buildAlerts(
      subscriptions = emptyList(),
      items = emptyList(),
      reminders = emptyList(),
      recentCalls = emptyList(),
      appUsage = emptyList(),
      briefing = null,
      settings = AstraSettings(cycleTrackingEnabled = true, morningBriefingHour = 12),
      dismissedKeys = emptySet(),
      cycleState = state,
      now = earlyNow,
      zone = zone,
    )
    assertTrue(alertsEarly.none { it.kind == ProactiveKind.CYCLE })
    assertEquals(LocalDate.of(2026, 11, 29), today)
  }

  @Test fun wellnessEngineEstimatesIndianMealsWithQuantitiesAndWorkouts() {
    val breakfast = WellnessEngine.localMealEstimate("poha and 2 eggs")
    // poha (250 cal, 5p, 45c, 6f) + 2 * eggs (170 cal, 14p, 2c, 12f)
    assertEquals(420, breakfast.calories)
    assertEquals(19, breakfast.proteinG)
    assertEquals(47, breakfast.carbsG)
    assertEquals(18, breakfast.fatG)

    val unknown = WellnessEngine.localMealEstimate("mystery bowl")
    assertEquals(380, unknown.calories)

    assertEquals(420, WellnessEngine.estimateWorkoutCalories("Morning run", 40))
    assertEquals(105, WellnessEngine.estimateWorkoutCalories("Evening yoga", 30))
  }

  @Test fun filePlannerStorePersistsMealsSleepWorkoutsHabitsStepsTimerAndCycle() = runTest {
    val file = File(tempDir.root, "planner.json")
    val store = FilePlannerStore(file)

    val meal = MealLog("m1", "Masala dosa", 280, 6, 44, 9, "2026-11-24", 1_000L)
    val sleep = SleepLog("s1", "2026-11-24", 440, "11:30 PM", "6:50 AM", qualityScore = 86)
    val workout = WorkoutLog("w1", "Evening walk", 35, 175, "2026-11-24", 2_000L)

    store.addMeal(meal)
    store.saveSleep(sleep)
    store.addWorkout(workout)
    store.addHabit(HabitItem("h-custom", "Evening stretch"))
    store.toggleHabit("h-custom", "2026-11-24")
    store.recordSteps("2026-11-24", 9_120)
    store.startTimer(TimerMode.FASTING, targetMinutes = 960, nowMillis = 100_000L, dateKey = "2026-11-24")
    store.togglePeriodDay("2026-11-09")
    store.setCycleLength(29)

    val reloaded = FilePlannerStore(file)
    assertEquals(listOf(meal), reloaded.meals.value)
    assertEquals(listOf(sleep), reloaded.sleepLogs.value)
    assertEquals(listOf(workout), reloaded.workouts.value)
    assertTrue(reloaded.habits.value.any { it.id == "h-custom" && "2026-11-24" in it.completedDateKeys })
    assertEquals(9_120, reloaded.stepsByDate.value["2026-11-24"])
    assertTrue(reloaded.timerSession.value.isRunning)
    assertEquals(TimerMode.FASTING, reloaded.timerSession.value.mode)
    assertEquals(listOf("2026-11-09"), reloaded.cycleState.value.periodStartDates)
    assertEquals(29, reloaded.cycleState.value.cycleLengthDays)

    // Stopping timer records fasted minutes across reload
    reloaded.stopTimer(nowMillis = 100_000L + 60 * 60_000L, dateKey = "2026-11-24")
    val reloadedStopped = FilePlannerStore(file)
    assertFalse(reloadedStopped.timerSession.value.isRunning)
    assertEquals(60, reloadedStopped.timerSession.value.lastFastedMinutes)
  }
}
