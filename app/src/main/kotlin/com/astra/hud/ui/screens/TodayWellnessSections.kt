package com.astra.hud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astra.hud.insights.CycleSummary
import com.astra.hud.model.HabitItem
import com.astra.hud.model.AstraUiState
import com.astra.hud.model.MealLog
import com.astra.hud.model.SleepLog
import com.astra.hud.model.TimerMode
import com.astra.hud.model.TimerSession
import com.astra.hud.model.WorkoutLog
import com.astra.hud.ui.components.HudFormat
import com.astra.hud.ui.components.IconBadge
import com.astra.hud.ui.components.Meter
import com.astra.hud.ui.components.SectionHeader
import com.astra.hud.ui.theme.HudColors
import com.astra.hud.ui.theme.AstraType
import com.astra.hud.ui.viz.BaselineRangeBar
import com.astra.hud.ui.viz.BentoCard
import com.astra.hud.ui.viz.ConsistencyDay
import com.astra.hud.ui.viz.ConsistencyStrip
import com.astra.hud.ui.viz.CyclePhaseRingCard
import com.astra.hud.ui.viz.DonutArcRing
import com.astra.hud.ui.viz.MacroTripleBars
import com.astra.hud.ui.viz.MiniWeekSleepRingsRow
import com.astra.hud.ui.viz.StepsBarChartCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Food Diary sub-view: "Gentle tracking, never judging" + calorie donut + 3 macro bars + Indian meal log.
 */
internal fun LazyListScope.foodDiaryView(
  state: AstraUiState,
  selectedDate: LocalDate,
  dayMeals: List<MealLog>,
  dayWorkouts: List<WorkoutLog>,
  onAddMeal: () -> Unit,
  onDeleteMeal: (String) -> Unit,
) {
  item(key = "foodHero") {
    val eaten = dayMeals.sumOf { it.calories }
    val burned = dayWorkouts.sumOf { it.caloriesBurned }
    val goal = state.settings.dailyCalorieGoal.coerceAtLeast(800)
    val left = (goal - eaten + burned).coerceAtLeast(0)
    val frac = (eaten.toFloat() / goal).coerceIn(0f, 1f)

    BentoCard(pastelBg = HudColors.BlushSoft, padding = 16.dp) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Outlined.Restaurant, HudColors.Blush, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text("Food diary", style = AstraType.SectionSerif, color = HudColors.Text)
          Text("Gentle tracking, never judging", style = AstraType.PromptItalic, color = HudColors.TextDim)
        }
        Box(
          Modifier
            .clip(CircleShape)
            .background(HudColors.Cream)
            .clickable(role = Role.Button, onClick = onAddMeal)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = HudColors.OnCream, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add meal", style = MaterialTheme.typography.labelLarge, color = HudColors.OnCream)
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      Box(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(HudColors.Surface.copy(alpha = 0.8f))
          .padding(horizontal = 12.dp, vertical = 9.dp),
      ) {
        Text("no bad foods here. just logged foods.", style = AstraType.PromptItalic, color = HudColors.TextDim)
      }
    }

    Spacer(Modifier.height(14.dp))
    BentoCard(padding = 18.dp) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("%,d".format(left), style = AstraType.HeroSerif, color = HudColors.Text)
          Text("kcal left today", style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
          Spacer(Modifier.height(8.dp))
          Text(
            "$goal goal · $eaten eaten · $burned burned",
            style = MaterialTheme.typography.labelSmall,
            color = HudColors.TextMuted,
          )
        }
        DonutArcRing(
          fraction = frac,
          centerText = "${(frac * 100).roundToInt()}%",
          color = HudColors.Sand,
          size = 86.dp,
          strokeWidth = 9.dp,
          partialSweep = true,
        )
      }
      Spacer(Modifier.height(16.dp))
      MacroTripleBars(
        proteinG = dayMeals.sumOf { it.proteinG },
        proteinGoal = state.settings.dailyProteinGoalG,
        carbsG = dayMeals.sumOf { it.carbsG },
        carbsGoal = state.settings.dailyCarbsGoalG,
        fatG = dayMeals.sumOf { it.fatG },
        fatGoal = state.settings.dailyFatGoalG,
      )
    }
    Spacer(Modifier.height(14.dp))
  }

  item(key = "mealsList") {
    SectionHeader("Logged on ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}", trailing = "${dayMeals.sumOf { it.calories }} kcal")
    Spacer(Modifier.height(4.dp))
    BentoCard(padding = 14.dp) {
      if (dayMeals.isEmpty()) {
        Text("No meals logged for this day yet. Tap + Add meal and describe what you ate.", style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
      } else {
        dayMeals.forEachIndexed { i, meal ->
          if (i > 0) Spacer(Modifier.height(8.dp))
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(HudColors.SurfaceHigh)
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconBadge(Icons.Outlined.Restaurant, HudColors.Blush, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(meal.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                "${meal.calories} kcal · P ${meal.proteinG}g · C ${meal.carbsG}g · F ${meal.fatG}g",
                style = MaterialTheme.typography.labelSmall,
                color = HudColors.TextDim,
              )
            }
            IconButton(onClick = { onDeleteMeal(meal.id) }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Outlined.DeleteOutline, "Delete meal", tint = HudColors.TextMuted, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}

/**
 * Cycle & Period Tracker sub-view modeled directly after @itspsneha's Cycle screen.
 */
internal fun LazyListScope.cycleView(
  cycle: CycleSummary,
  today: LocalDate,
  onTogglePeriodDate: (String) -> Unit,
) {
  item(key = "cycleTop") {
    val nextLabel = cycle.nextPeriodDate?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) ?: "log a start date"
    Text(
      if (cycle.hasData) "Average ${cycle.averageCycleLength} days · next period ~$nextLabel"
      else "Log your last period start date to begin gentle phase tracking.",
      style = MaterialTheme.typography.bodySmall,
      color = HudColors.TextDim,
    )
    Spacer(Modifier.height(12.dp))
    BentoCard(pastelBg = HudColors.SandSoft, padding = 14.dp) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Outlined.Spa, HudColors.Sand, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column {
          Text(cycle.phase.tagline, style = MaterialTheme.typography.titleSmall, color = HudColors.Text)
          Text("Day ${cycle.cycleDay} · ${cycle.phase.label}", style = AstraType.PromptItalic, color = HudColors.TextDim)
        }
      }
    }
    Spacer(Modifier.height(14.dp))
  }

  item(key = "cycleRing") {
    CyclePhaseRingCard(
      cycleDay = cycle.cycleDay,
      averageLength = cycle.averageCycleLength,
      phaseLabel = cycle.phase.label,
      progressFraction = cycle.cycleProgressFraction,
      onLogPeriodToday = { onTogglePeriodDate(today.toString()) },
      isTodayLogged = today in cycle.periodDaysInMonth,
    )
    Spacer(Modifier.height(14.dp))
  }

  item(key = "cycleSummaryTable") {
    BentoCard(padding = 16.dp) {
      Text("Today", style = AstraType.SectionSerif, color = HudColors.Text)
      Spacer(Modifier.height(10.dp))
      CycleRow("Phase", cycle.phase.label, HudColors.SandSoft, HudColors.Sand)
      Spacer(Modifier.height(8.dp))
      CycleRow("Cycle day", "${cycle.cycleDay} of ~${cycle.averageCycleLength}", HudColors.SurfaceHigh, HudColors.Text)
      Spacer(Modifier.height(8.dp))
      val nextText = cycle.daysToNextPeriod?.let { d ->
        val dateStr = cycle.nextPeriodDate?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) ?: ""
        "in $d days · $dateStr"
      } ?: "Not predicted yet"
      CycleRow("Next period", nextText, HudColors.BlushSoft, HudColors.Blush)
    }
    Spacer(Modifier.height(14.dp))
  }

  item(key = "cycleCalendar") {
    CycleMonthCalendarCard(today = today, cycle = cycle, onToggleDay = { onTogglePeriodDate(it.toString()) })
    Spacer(Modifier.height(14.dp))
  }

  item(key = "cyclePhaseGuidance") {
    SectionHeader("This phase · ${cycle.phase.label}")
    Spacer(Modifier.height(4.dp))
    BentoCard(pastelBg = HudColors.SandSoft, padding = 14.dp) {
      PhaseTipRow("Energy", cycle.phase.energyTip, HudColors.Sand)
      Spacer(Modifier.height(10.dp))
      PhaseTipRow("Movement", cycle.phase.movementTip, HudColors.Sage)
      Spacer(Modifier.height(10.dp))
      PhaseTipRow("Sleep", cycle.phase.sleepTip, HudColors.Lavender)
    }
  }
}

@Composable
private fun CycleRow(label: String, value: String, chipBg: Color, chipFg: Color) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim, modifier = Modifier.weight(1f))
    Box(
      Modifier
        .clip(CircleShape)
        .background(chipBg)
        .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
      Text(value, style = MaterialTheme.typography.labelMedium, color = chipFg)
    }
  }
}

@Composable
private fun PhaseTipRow(title: String, body: String, tint: Color) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(HudColors.Surface)
      .padding(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(tint))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text)
      Spacer(Modifier.height(2.dp))
      Text(body, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
    }
  }
}

@Composable
private fun CycleMonthCalendarCard(today: LocalDate, cycle: CycleSummary, onToggleDay: (LocalDate) -> Unit) {
  val month = YearMonth.from(today)
  val first = month.atDay(1)
  val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
  val days = month.lengthOfMonth()
  val cells: List<Int?> = List(lead) { null } + (1..days).toList()
  val rows = cells.chunked(7)

  BentoCard(padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
        style = AstraType.SectionSerif,
        color = HudColors.Text,
        modifier = Modifier.weight(1f),
      )
      Text("Tap day to log", style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
      listOf("M", "T", "W", "T", "F", "S", "S").forEach {
        Text(it, style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
      }
    }
    Spacer(Modifier.height(6.dp))
    rows.forEach { week ->
      Row(Modifier.fillMaxWidth()) {
        (0 until 7).forEach { col ->
          val day = week.getOrNull(col)
          Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
            if (day != null) {
              val date = month.atDay(day)
              val isLogged = date in cycle.periodDaysInMonth
              val isPredicted = date in cycle.predictedPeriodDaysInMonth
              val isToday = date == today
              val future = date.isAfter(today)
              Box(
                Modifier
                  .fillMaxWidth()
                  .fillMaxHeight()
                  .clip(CircleShape)
                  .background(
                    when {
                      isToday -> HudColors.Cream
                      isLogged -> HudColors.BlushSoft
                      else -> Color.Transparent
                    },
                  )
                  .then(
                    if (isPredicted && !isLogged && !isToday) Modifier.border(1.dp, HudColors.Blush.copy(alpha = 0.55f), CircleShape) else Modifier,
                  )
                  .clickable(enabled = !future, role = Role.Button) { onToggleDay(date) },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  "$day",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = when {
                      isToday -> HudColors.OnCream
                      isLogged -> HudColors.Blush
                      future -> HudColors.TextMuted
                      else -> HudColors.Text
                    },
                    fontWeight = if (isToday || isLogged) FontWeight.Bold else FontWeight.Normal,
                  ),
                )
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Sleep, Body, Fasting/Focus Timer & Steps sub-view (@itspsneha Sleep + Ojas Fasting/Steps).
 */
internal fun LazyListScope.sleepAndBodyView(
  state: AstraUiState,
  todayDate: LocalDate,
  todaySleep: SleepLog?,
  dayWorkouts: List<WorkoutLog>,
  onLogSleepClick: () -> Unit,
  onLogWorkoutClick: () -> Unit,
  onLogStepsClick: () -> Unit,
  onStartTimer: (TimerMode, Int) -> Unit,
  onStopTimer: () -> Unit,
  onDeleteWorkout: (String) -> Unit,
) {
  item(key = "sleepHero") {
    val weekStart = todayDate.with(DayOfWeek.MONDAY)
    val goalMin = state.settings.sleepGoalMinutes.coerceAtLeast(300)
    val sleepMap = state.sleepLogs.associateBy { it.dateKey }
    val weekRings = (0..6).map { off ->
      val d = weekStart.plusDays(off.toLong())
      val log = sleepMap[d.toString()]
      val frac = log?.let { it.totalMinutes.toFloat() / goalMin } ?: 0f
      d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3) to frac
    }
    val shown = todaySleep ?: state.sleepLogs.firstOrNull()
    val asleepHrs = (shown?.totalMinutes ?: 0) / 60
    val asleepMins = (shown?.totalMinutes ?: 0) % 60

    BentoCard(padding = 18.dp) {
      MiniWeekSleepRingsRow(weekRings)
      Spacer(Modifier.height(16.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Asleep", style = MaterialTheme.typography.labelMedium, color = HudColors.SlateBlue)
          Text(
            if (shown != null) "${asleepHrs}h ${asleepMins}m" else "Not logged",
            style = AstraType.HeroSerif,
            color = HudColors.Text,
          )
          Text(
            shown?.let { "${it.bedtimeLabel} – ${it.wakeLabel}" } ?: "Tap below to log last night's sleep",
            style = MaterialTheme.typography.labelSmall,
            color = HudColors.TextDim,
          )
        }
        DonutArcRing(
          fraction = ((shown?.qualityScore ?: 0) / 100f),
          centerText = shown?.qualityScore?.toString() ?: "—",
          color = HudColors.SlateBlue,
          size = 78.dp,
          strokeWidth = 7.dp,
        )
      }
      Spacer(Modifier.height(14.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Sleep need", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
        Text("${HudFormat.minutes((shown?.totalMinutes ?: 0).toLong())} / ${HudFormat.minutes(goalMin.toLong())}", style = MaterialTheme.typography.labelSmall, color = HudColors.Text)
      }
      Spacer(Modifier.height(6.dp))
      Meter(((shown?.totalMinutes ?: 0).toFloat() / goalMin).coerceIn(0f, 1f), HudColors.SlateBlue, height = 7.dp)
      Spacer(Modifier.height(14.dp))
      Box(
        Modifier
          .fillMaxWidth()
          .clip(CircleShape)
          .background(HudColors.SlateBlueSoft)
          .clickable(role = Role.Button, onClick = onLogSleepClick)
          .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(if (shown == null) "Log sleep" else "Update sleep log", style = MaterialTheme.typography.labelLarge, color = HudColors.SlateBlue)
      }
    }
    Spacer(Modifier.height(12.dp))
    BentoCard(pastelBg = HudColors.LavenderSoft, padding = 14.dp) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Outlined.LocalCafe, HudColors.Lavender, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column {
          Text("chamomile at 10, phone away at 10:30.", style = MaterialTheme.typography.titleSmall, color = HudColors.Text)
          Text("that's the plan.", style = AstraType.PromptItalic, color = HudColors.TextDim)
        }
      }
    }
    Spacer(Modifier.height(14.dp))
  }

  val latestSleep = todaySleep ?: state.sleepLogs.firstOrNull()
  if (latestSleep != null) {
    item(key = "sleepStages") {
      BentoCard(padding = 16.dp) {
        Text("Stages vs typical", style = AstraType.SectionSerif, color = HudColors.Text)
        Spacer(Modifier.height(12.dp))
        val total = latestSleep.totalMinutes.coerceAtLeast(1).toFloat()
        BaselineRangeBar("Deep", HudFormat.minutes(latestSleep.deepMinutes.toLong()), latestSleep.deepMinutes / total, 0.18f, HudColors.Lavender)
        Spacer(Modifier.height(10.dp))
        BaselineRangeBar("REM", HudFormat.minutes(latestSleep.remMinutes.toLong()), latestSleep.remMinutes / total, 0.25f, HudColors.SlateBlue)
        Spacer(Modifier.height(10.dp))
        BaselineRangeBar("Light", HudFormat.minutes(latestSleep.lightMinutes.toLong()), latestSleep.lightMinutes / total, 0.52f, HudColors.Sage)
      }
      Spacer(Modifier.height(14.dp))
    }
  }

  item(key = "timerCard") {
    TimerControlCard(state.timerSession, onStartTimer, onStopTimer)
    Spacer(Modifier.height(14.dp))
  }

  item(key = "stepsAndWorkouts") {
    val weekStart = todayDate.with(DayOfWeek.MONDAY)
    val last7 = (0..6).map { off ->
      val d = weekStart.plusDays(off.toLong())
      val l = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
      l to (state.stepsByDate[d.toString()] ?: 0)
    }
    val todaySteps = state.stepsByDate[todayDate.toString()] ?: 0
    val nonZero = last7.map { it.second }.filter { it > 0 }
    val avgSteps = if (nonZero.isEmpty()) 0 else nonZero.average().toInt()
    StepsBarChartCard(todaySteps = todaySteps, sevenDayAvg = avgSteps, days = last7, onAddStepsClick = onLogStepsClick)
    Spacer(Modifier.height(14.dp))

    SectionHeader("Movement today", trailing = "+ Log workout", onTrailingClick = onLogWorkoutClick)
    Spacer(Modifier.height(4.dp))
    BentoCard(padding = 14.dp) {
      if (dayWorkouts.isEmpty()) {
        Text("No workouts logged today — even a 20 minute walk counts.", style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
      } else {
        dayWorkouts.forEachIndexed { i, w ->
          if (i > 0) Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.DirectionsRun, HudColors.Sage, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
              Text(w.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text)
              Text("${w.durationMinutes} min · ${w.caloriesBurned} kcal burned", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
            }
            IconButton(onClick = { onDeleteWorkout(w.id) }, modifier = Modifier.size(28.dp)) {
              Icon(Icons.Outlined.DeleteOutline, "Remove workout", tint = HudColors.TextMuted, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun TimerControlCard(
  timer: TimerSession,
  onStart: (TimerMode, Int) -> Unit,
  onStop: () -> Unit,
) {
  val now = System.currentTimeMillis()
  val elapsedMin = if (timer.isRunning) ((now - timer.startedAtMillis).coerceAtLeast(0L) / 60_000L).toInt() else 0
  val hrs = elapsedMin / 60
  val mins = elapsedMin % 60
  val frac = if (timer.isRunning && timer.targetMinutes > 0) (elapsedMin.toFloat() / timer.targetMinutes).coerceIn(0.02f, 1f) else 0f

  BentoCard(pastelBg = if (timer.mode == TimerMode.FASTING) HudColors.LavenderSoft else HudColors.SageSoft, padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      IconBadge(Icons.Outlined.Timer, if (timer.mode == TimerMode.FASTING) HudColors.Lavender else HudColors.Sage, size = 34.dp)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          if (timer.isRunning) "${timer.mode.label} in progress" else "Fasting & Focus Timer",
          style = MaterialTheme.typography.titleSmall,
          color = HudColors.Text,
        )
        Text(
          if (timer.isRunning) "${hrs}h ${mins}m elapsed · goal ${HudFormat.minutes(timer.targetMinutes.toLong())}"
          else "${timer.completedFocusMinutesToday}m focus today · last fast ${HudFormat.minutes(timer.lastFastedMinutes.toLong())}",
          style = MaterialTheme.typography.labelSmall,
          color = HudColors.TextDim,
        )
      }
      if (timer.isRunning) {
        Box(
          Modifier
            .clip(CircleShape)
            .background(HudColors.Cream)
            .clickable(role = Role.Button, onClick = onStop)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Stop, null, tint = HudColors.OnCream, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("End", style = MaterialTheme.typography.labelMedium, color = HudColors.OnCream)
          }
        }
      }
    }
    if (timer.isRunning) {
      Spacer(Modifier.height(12.dp))
      Meter(frac, if (timer.mode == TimerMode.FASTING) HudColors.Lavender else HudColors.Sage, height = 7.dp)
    } else {
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimerPresetPill("16h Fast", Modifier.weight(1f)) { onStart(TimerMode.FASTING, 16 * 60) }
        TimerPresetPill("25m Focus", Modifier.weight(1f)) { onStart(TimerMode.FOCUS, 25) }
        TimerPresetPill("45m Focus", Modifier.weight(1f)) { onStart(TimerMode.FOCUS, 45) }
      }
    }
  }
}

@Composable
private fun TimerPresetPill(label: String, modifier: Modifier, onClick: () -> Unit) {
  Box(
    modifier
      .clip(CircleShape)
      .background(HudColors.Surface)
      .border(1.dp, HudColors.Outline, CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.PlayArrow, null, tint = HudColors.Text, modifier = Modifier.size(14.dp))
      Spacer(Modifier.width(4.dp))
      Text(label, style = MaterialTheme.typography.labelMedium, color = HudColors.Text)
    }
  }
}

/**
 * Daily Habits & Supplements check-off strip (Ojas style) + 7-day consistency tiles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HabitCheckStripCard(
  habits: List<HabitItem>,
  dateKey: String,
  todayDate: LocalDate,
  onToggleHabit: (String) -> Unit,
  onAddHabitClick: () -> Unit,
) {
  val doneCount = habits.count { dateKey in it.completedDateKeys }
  BentoCard(padding = 16.dp) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("Daily habits & supplements", style = AstraType.SectionSerif, color = HudColors.Text, modifier = Modifier.weight(1f))
      Text("$doneCount of ${habits.size} done", style = MaterialTheme.typography.labelSmall, color = HudColors.Sage)
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      habits.forEach { h ->
        val done = dateKey in h.completedDateKeys
        Row(
          Modifier
            .clip(CircleShape)
            .background(if (done) HudColors.SageSoft else HudColors.SurfaceHigh)
            .border(1.dp, if (done) HudColors.Sage.copy(alpha = 0.45f) else HudColors.Outline, CircleShape)
            .clickable(role = Role.Button) { onToggleHabit(h.id) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (done) {
            Icon(Icons.Outlined.CheckCircle, null, tint = HudColors.Sage, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
          }
          Text(h.title, style = MaterialTheme.typography.labelMedium, color = if (done) HudColors.Sage else HudColors.Text)
        }
      }
      Box(
        Modifier
          .clip(CircleShape)
          .background(HudColors.SandSoft)
          .clickable(role = Role.Button, onClick = onAddHabitClick)
          .padding(horizontal = 12.dp, vertical = 7.dp),
      ) {
        Text("+ Add", style = MaterialTheme.typography.labelMedium, color = HudColors.Sand)
      }
    }
  }
  Spacer(Modifier.height(12.dp))
  val weekStart = todayDate.with(DayOfWeek.MONDAY)
  val totalH = habits.size.coerceAtLeast(1)
  val stripDays = (0..6).map { off ->
    val d = weekStart.plusDays(off.toLong())
    val k = d.toString()
    val c = habits.count { k in it.completedDateKeys }
    val fill = when {
      c == 0 -> HudColors.SurfaceHigh
      c >= totalH -> HudColors.Sage
      c * 2 >= totalH -> HudColors.Sage.copy(alpha = 0.55f)
      else -> HudColors.Lavender.copy(alpha = 0.55f)
    }
    ConsistencyDay(
      dayLabel = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
      fillColor = fill,
      active = c > 0,
      tooltip = "$k: $c of $totalH habits",
    )
  }
  ConsistencyStrip(
    title = "Weekly consistency",
    days = stripDays,
    caption = "Small daily anchors build calm momentum without perfection.",
  )
}
