package com.jarvis.hud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallMissed
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.hud.insights.CycleSummary
import com.jarvis.hud.insights.SubscriptionInsights
import com.jarvis.hud.insights.WellnessEngine
import com.jarvis.hud.model.HudTab
import com.jarvis.hud.model.IntelCategory
import com.jarvis.hud.model.ItemStatus
import com.jarvis.hud.model.JarvisActions
import com.jarvis.hud.model.JarvisUiState
import com.jarvis.hud.model.Priority
import com.jarvis.hud.model.ProactiveAlert
import com.jarvis.hud.model.ProactiveKind
import com.jarvis.hud.ui.components.EmptyLine
import com.jarvis.hud.ui.components.HudFormat
import com.jarvis.hud.ui.components.IconBadge
import com.jarvis.hud.ui.components.ItemCard
import com.jarvis.hud.ui.components.ListRow
import com.jarvis.hud.ui.components.Meter
import com.jarvis.hud.ui.components.Refreshable
import com.jarvis.hud.ui.components.SectionHeader
import com.jarvis.hud.ui.components.SetupCard
import com.jarvis.hud.ui.components.jarvisTextFieldColors
import com.jarvis.hud.ui.finance.rupees
import com.jarvis.hud.ui.theme.HudColors
import com.jarvis.hud.ui.theme.JarvisType
import com.jarvis.hud.ui.viz.BentoCard
import com.jarvis.hud.ui.viz.DayPillItem
import com.jarvis.hud.ui.viz.DaySelectorStrip
import com.jarvis.hud.ui.viz.DayTimelineBar
import com.jarvis.hud.ui.viz.DonutArcRing
import com.jarvis.hud.ui.viz.HexRadarChart
import com.jarvis.hud.ui.viz.MacroTripleBars
import com.jarvis.hud.ui.viz.RadarAxis
import com.jarvis.hud.ui.viz.ScoreRingSpec
import com.jarvis.hud.ui.viz.ScoreRingsCard
import com.jarvis.hud.ui.viz.StepsBarChartCard
import com.jarvis.hud.ui.viz.TimelineDot
import com.jarvis.hud.ui.viz.TimelineSpan
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private enum class TodayModal { REMINDER, MEAL, SLEEP, WORKOUT, STEPS, HABIT }

/**
 * Calm personal Today hub combining @itspsneha + Ojas features:
 * Overview | Food | Cycle | Sleep | Plan — powered by hardware steps, local/AI food logging,
 * period/cycle phase insights, fasting/focus timer, daily habits, day planner, and proactive alerts.
 */
@Composable
fun HudScreen(state: JarvisUiState, actions: JarvisActions, contentPadding: PaddingValues) {
  val now = System.currentTimeMillis()
  val zone = remember { ZoneId.systemDefault() }
  val todayDate = remember(now) { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
  var selectedDayOffset by remember { mutableIntStateOf(0) }
  var activeModal by remember { mutableStateOf<TodayModal?>(null) }

  val selectedDate = todayDate.plusDays(selectedDayOffset.toLong())
  val selectedDateKey = selectedDate.toString()
  val selStart = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
  val selEnd = selectedDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val dayStart = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
  val dayEnd = todayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val dayMillis = 24f * 3_600_000f

  val active = remember(state.items) { state.items.filter { it.status == ItemStatus.ACTIVE } }
  val urgent = remember(active) {
    active
      .filter { it.priority == Priority.CRITICAL || it.priority == Priority.HIGH }
      .sortedWith(compareBy({ it.priority.ordinal }, { -it.timestamp }))
      .take(4)
  }
  val activeSubs = remember(state.subscriptions) { state.subscriptions.filterNot { it.isPaused } }
  val billsDue = active.count { it.category == IntelCategory.BILL }
  val upcomingSubs = remember(activeSubs, todayDate) {
    activeSubs.filter {
      val d = SubscriptionInsights.daysUntil(it.nextRenewalDate, todayDate)
      d != null && d in 0..7
    }
  }
  val dayEvents = remember(state.calendarEntries, selStart, selEnd) {
    state.calendarEntries.filter { it.startTime < selEnd && it.endTime >= selStart }.sortedBy { it.startTime }
  }
  val dayReminders = remember(state.reminders, selectedDateKey) {
    state.reminders.filter { it.dateKey == selectedDateKey }.sortedBy { it.minutesOfDay }
  }
  val dayMeals = remember(state.meals, selectedDateKey) { state.meals.filter { it.dateKey == selectedDateKey } }
  val dayWorkouts = remember(state.workouts, selectedDateKey) { state.workouts.filter { it.dateKey == selectedDateKey } }
  val daySleep = remember(state.sleepLogs, selectedDateKey) { state.sleepLogs.firstOrNull { it.dateKey == selectedDateKey } }
  val daySteps = state.stepsByDate[selectedDateKey] ?: 0
  val showCycle = state.settings.isPeriodTrackerActive
  val cycleSummary = remember(state.cycleState, todayDate) { CycleSummary.from(state.cycleState, todayDate) }

  val subTabs = remember(showCycle) {
    if (showCycle) listOf("Overview", "Food", "Cycle", "Sleep", "Plan")
    else listOf("Overview", "Food", "Sleep", "Plan")
  }
  val rawIdx = state.settings.todayViewIndex.coerceIn(0, subTabs.lastIndex)
  val currentSubTab = subTabs[rawIdx]

  Refreshable(state.isSyncing, actions::onScanAndOrganize, Modifier.fillMaxSize()) {
    LazyColumn(
      Modifier.fillMaxSize().testTag("screen-home"),
      contentPadding = screenPadding(contentPadding),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item(key = "todaySubTabs") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(subTabs.size) { idx ->
            val selected = idx == rawIdx
            Box(
              Modifier
                .clip(CircleShape)
                .background(if (selected) HudColors.Cream else HudColors.Surface)
                .border(1.dp, if (selected) HudColors.Cream else HudColors.Outline, CircleShape)
                .clickable(role = Role.Tab) { actions.onSelectTodayView(idx) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
              Text(
                subTabs[idx],
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) HudColors.OnCream else HudColors.TextDim,
              )
            }
          }
        }
      }

      when (currentSubTab) {
        "Food" -> foodDiaryView(
          state = state,
          selectedDate = selectedDate,
          dayMeals = dayMeals,
          dayWorkouts = dayWorkouts,
          onAddMeal = { activeModal = TodayModal.MEAL },
          onDeleteMeal = actions::onDeleteMeal,
        )
        "Cycle" -> cycleView(
          cycle = cycleSummary,
          today = todayDate,
          onTogglePeriodDate = { d -> actions.onTogglePeriodDay(d) },
        )
        "Sleep" -> sleepAndBodyView(
          state = state,
          todayDate = todayDate,
          todaySleep = daySleep,
          dayWorkouts = dayWorkouts,
          onLogSleepClick = { activeModal = TodayModal.SLEEP },
          onLogWorkoutClick = { activeModal = TodayModal.WORKOUT },
          onLogStepsClick = { activeModal = TodayModal.STEPS },
          onStartTimer = actions::onStartTimer,
          onStopTimer = actions::onStopTimer,
          onDeleteWorkout = actions::onDeleteWorkout,
        )
        "Plan" -> {
          item(key = "habits") {
            HabitCheckStripCard(
              habits = state.habits,
              dateKey = selectedDateKey,
              todayDate = todayDate,
              onToggleHabit = { hid -> actions.onToggleHabitToday(hid, selectedDateKey) },
              onAddHabitClick = { activeModal = TodayModal.HABIT },
            )
          }
          item(key = "planner") {
            SmartDayPlannerCard(
              dateLabel = if (selectedDayOffset == 0) "Today's plan" else selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
              events = dayEvents,
              reminders = dayReminders,
              onToggleReminder = actions::onToggleReminder,
              onDeleteReminder = actions::onDeleteReminder,
              onAddReminderClick = { activeModal = TodayModal.REMINDER },
            )
          }
          if (state.proactiveAlerts.isNotEmpty()) {
            item(key = "proactivePlan") {
              SectionHeader("Proactive nudges", trailing = "${state.proactiveAlerts.size} active")
              Spacer(Modifier.height(4.dp))
              ProactiveAlertsCard(state.proactiveAlerts, { it?.let(actions::onSelectTab) }, actions::onDismissProactiveAlert)
            }
          }
          item(key = "calls") { RecentCallsCard(state) }
          item(key = "device") { DeviceCard(state) }
        }
        else -> {
          item(key = "daySelector") {
            val weekStart = todayDate.with(DayOfWeek.MONDAY)
            val renewalDates = activeSubs.mapNotNull { it.nextRenewalDate }.toSet()
            val reminderDates = state.reminders.filterNot { it.isDone }.map { it.dateKey }.toSet()
            val pills = (0..6).map { offset ->
              val d = weekStart.plusDays(offset.toLong())
              val key = d.toString()
              val hasMark = key in renewalDates || key in reminderDates || (showCycle && d in cycleSummary.periodDaysInMonth)
              DayPillItem(
                letter = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                dayNumber = d.dayOfMonth.toString(),
                isSelected = d == selectedDate,
                hasMarker = hasMark,
                onClick = { selectedDayOffset = java.time.temporal.ChronoUnit.DAYS.between(todayDate, d).toInt() },
              )
            }
            val eyebrow = if (showCycle && cycleSummary.hasData) {
              "${cycleSummary.phase.label} · Day ${cycleSummary.cycleDay}"
            } else if (upcomingSubs.isNotEmpty()) {
              "${upcomingSubs.size} renewal${if (upcomingSubs.size == 1) "" else "s"} this week"
            } else {
              "Personal companion"
            }
            val rightHeadline = if (showCycle && cycleSummary.daysToNextPeriod != null) {
              "${cycleSummary.daysToNextPeriod} days"
            } else {
              rupees(activeSubs.sumOf { it.monthlyEquivalent })
            }
            val rightSub = if (showCycle && cycleSummary.daysToNextPeriod != null) {
              "to period"
            } else {
              "monthly subs"
            }
            DaySelectorStrip(
              eyebrow = eyebrow,
              headline = when (selectedDayOffset) {
                0 -> "Today"
                1 -> "Tomorrow"
                -1 -> "Yesterday"
                else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
              },
              rightHeadline = rightHeadline,
              rightSub = rightSub,
              days = pills,
            )
          }

          item(key = "briefing") {
            BriefingCard(
              state = state,
              actions = actions,
              calmQuotes = WellnessEngine.calmInsights(
                sleep = daySleep,
                steps = daySteps,
                stepGoal = state.settings.dailyStepGoal,
                meals = dayMeals,
                workouts = dayWorkouts,
              ),
            )
          }

          item(key = "scoreRings") {
            val sleepScore = daySleep?.qualityScore ?: state.sleepLogs.firstOrNull()?.qualityScore ?: 78
            val stepPct = ((daySteps.toFloat() / state.settings.dailyStepGoal.coerceAtLeast(1000)) * 100f).roundToInt().coerceIn(10, 100)
            val renewalScore = (100 - upcomingSubs.size * 15 - billsDue * 12).coerceIn(25, 100)
            val guidance = if (showCycle && cycleSummary.hasData) {
              "${cycleSummary.phase.label}: ${cycleSummary.phase.energyTip}"
            } else if (urgent.isNotEmpty()) {
              "Take it steady today. ${urgent.size} priority item${if (urgent.size == 1) "" else "s"} below."
            } else {
              "Take it slow today. Consistency is built gently."
            }
            ScoreRingsCard(
              rings = listOf(
                ScoreRingSpec("Sleep", "$sleepScore", sleepScore / 100f, HudColors.SlateBlue) {
                  actions.onSelectTodayView(subTabs.indexOf("Sleep").coerceAtLeast(0))
                },
                ScoreRingSpec("Movement", "$stepPct", stepPct / 100f, HudColors.Blush) {
                  actions.onSelectTodayView(subTabs.indexOf("Sleep").coerceAtLeast(0))
                },
                ScoreRingSpec("Renewals", "$renewalScore", renewalScore / 100f, HudColors.Lavender) {
                  actions.onSelectTab(HudTab.FINANCE)
                },
              ),
              guidance = guidance,
            )
          }

          item(key = "quickActions") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              QuickTile("Add meal", Icons.Outlined.Restaurant, HudColors.Blush, HudColors.BlushSoft, Modifier.weight(1f)) {
                activeModal = TodayModal.MEAL
              }
              QuickTile("Add activity", Icons.Outlined.DirectionsRun, HudColors.Sand, HudColors.SandSoft, Modifier.weight(1f)) {
                activeModal = TodayModal.WORKOUT
              }
              if (showCycle) {
                QuickTile("Log period", Icons.Outlined.Spa, HudColors.SlateBlue, HudColors.SlateBlueSoft, Modifier.weight(1f)) {
                  actions.onTogglePeriodDay(selectedDateKey)
                }
              } else {
                QuickTile("Log sleep", Icons.Outlined.Bedtime, HudColors.Lavender, HudColors.LavenderSoft, Modifier.weight(1f)) {
                  activeModal = TodayModal.SLEEP
                }
              }
            }
          }

          item(key = "nutritionOverview") {
            val eaten = dayMeals.sumOf { it.calories }
            val burned = dayWorkouts.sumOf { it.caloriesBurned }
            val goal = state.settings.dailyCalorieGoal.coerceAtLeast(800)
            val left = (goal - eaten + burned).coerceAtLeast(0)
            val frac = (eaten.toFloat() / goal).coerceIn(0f, 1f)
            BentoCard(
              onClick = { actions.onSelectTodayView(subTabs.indexOf("Food").coerceAtLeast(0)) },
              padding = 16.dp,
            ) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text("Nutrition · ${dayMeals.size} meal${if (dayMeals.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium, color = HudColors.Blush)
                  Spacer(Modifier.height(2.dp))
                  Text("%,d kcal left".format(left), style = JarvisType.TitleSerif, color = HudColors.Text)
                  Text("$eaten eaten · $burned burned · $goal goal", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
                }
                DonutArcRing(
                  fraction = frac,
                  centerText = "${(frac * 100).roundToInt()}%",
                  color = HudColors.Sand,
                  size = 72.dp,
                  strokeWidth = 7.dp,
                  partialSweep = true,
                )
              }
              Spacer(Modifier.height(12.dp))
              MacroTripleBars(
                proteinG = dayMeals.sumOf { it.proteinG },
                proteinGoal = state.settings.dailyProteinGoalG,
                carbsG = dayMeals.sumOf { it.carbsG },
                carbsGoal = state.settings.dailyCarbsGoalG,
                fatG = dayMeals.sumOf { it.fatG },
                fatGoal = state.settings.dailyFatGoalG,
              )
            }
          }

          if (state.proactiveAlerts.isNotEmpty()) {
            item(key = "proactive") {
              SectionHeader("Proactive nudges", trailing = "${state.proactiveAlerts.size} active")
              Spacer(Modifier.height(4.dp))
              ProactiveAlertsCard(state.proactiveAlerts.take(3), { it?.let(actions::onSelectTab) }, actions::onDismissProactiveAlert)
            }
          }

          item(key = "urgentHeader") {
            SectionHeader("Needs attention", trailing = if (active.isNotEmpty()) "See all" else null, onTrailingClick = { actions.onSelectTab(HudTab.INTEL) })
          }
          if (urgent.isEmpty()) {
            item(key = "urgentEmpty") {
              BentoCard { EmptyLine("Nothing urgent. You're all caught up.") }
            }
          } else {
            items(urgent, key = { "u-" + it.id }) { ItemCard(it, actions::onMarkItemStatus, onClick = { actions.onOpenItem(it.id) }) }
          }

          item(key = "dayTimeline") {
            val spans = buildList {
              // Sleep span (e.g. 12:30 AM - 7:15 AM if logged)
              daySleep?.let { sl ->
                add(TimelineSpan(0.02f, (sl.totalMinutes / 1440f).coerceIn(0.15f, 0.42f), HudColors.SlateBlue.copy(alpha = 0.45f)))
              }
              state.calendarEntries.filter { it.startTime < dayEnd && it.endTime >= dayStart }.forEach { e ->
                val s = ((e.startTime - dayStart) / dayMillis).coerceIn(0f, 1f)
                val en = ((e.endTime - dayStart) / dayMillis).coerceIn(s, 1f)
                add(TimelineSpan(s, en, HudColors.Lavender))
              }
              dayWorkouts.forEach { w ->
                val s = ((w.timestamp - dayStart) / dayMillis).coerceIn(0f, 1f)
                val en = (s + w.durationMinutes / 1440f).coerceAtMost(1f)
                add(TimelineSpan(s, en, HudColors.Peach))
              }
            }
            val dots = buildList {
              dayMeals.forEach { m ->
                add(TimelineDot(((m.timestamp - dayStart) / dayMillis).coerceIn(0.03f, 0.97f), HudColors.Sand, 5.5f))
              }
              state.reminders.filter { it.dateKey == todayDate.toString() }.forEach { r ->
                add(TimelineDot((r.minutesOfDay / 1440f).coerceIn(0.03f, 0.97f), HudColors.Sage, 4.5f))
              }
            }
            val nowFrac = ((now - dayStart) / dayMillis).coerceIn(0f, 1f)
            DayTimelineBar(
              spans = spans,
              dots = dots,
              nowFrac = nowFrac,
              summaryLine = "${dayMeals.size} meals · ${dayWorkouts.size} workouts · ${dayEvents.size} events",
              legend = listOf(
                "Sleep" to HudColors.SlateBlue,
                "Meals" to HudColors.Sand,
                "Activity" to HudColors.Peach,
                "Events" to HudColors.Lavender,
              ),
            )
          }

          item(key = "stepsCard") {
            val weekStart = todayDate.with(DayOfWeek.MONDAY)
            val last7 = (0..6).map { off ->
              val d = weekStart.plusDays(off.toLong())
              d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) to (state.stepsByDate[d.toString()] ?: 0)
            }
            val nonZero = last7.map { it.second }.filter { it > 0 }
            val avgSteps = if (nonZero.isEmpty()) 0 else nonZero.average().toInt()
            StepsBarChartCard(todaySteps = daySteps, sevenDayAvg = avgSteps, days = last7, onAddStepsClick = { activeModal = TodayModal.STEPS })
          }

          item(key = "pulseRadar") {
            val sleepFrac = ((daySleep?.totalMinutes ?: state.sleepLogs.firstOrNull()?.totalMinutes ?: 420).toFloat() / state.settings.sleepGoalMinutes.coerceAtLeast(300)).coerceIn(0.2f, 1f)
            val moveFrac = (daySteps.toFloat() / state.settings.dailyStepGoal.coerceAtLeast(1000)).coerceIn(0.2f, 1f)
            val nutrFrac = if (dayMeals.isEmpty()) 0.55f else (dayMeals.sumOf { it.proteinG }.toFloat() / state.settings.dailyProteinGoalG.coerceAtLeast(40)).coerceIn(0.25f, 1f)
            val focusFrac = (0.5f + state.timerSession.completedFocusMinutesToday / 120f).coerceIn(0.25f, 1f)
            val doneH = state.habits.count { selectedDateKey in it.completedDateKeys }
            val habitFrac = if (state.habits.isEmpty()) 0.75f else (doneH.toFloat() / state.habits.size).coerceIn(0.25f, 1f)
            val subsFrac = (1f - upcomingSubs.size * 0.14f).coerceIn(0.25f, 1f)
            val axes = listOf(
              RadarAxis("Sleep", sleepFrac, daySleep?.let { HudFormat.minutes(it.totalMinutes.toLong()) } ?: "Log sleep"),
              RadarAxis("Move", moveFrac, "%,d steps".format(daySteps)),
              RadarAxis("Food", nutrFrac, "${dayMeals.sumOf { it.proteinG }}g protein"),
              RadarAxis("Focus", focusFrac, "${state.timerSession.completedFocusMinutesToday}m today"),
              RadarAxis("Habits", habitFrac, "$doneH/${state.habits.size} done"),
              RadarAxis("Subs", subsFrac, "${upcomingSubs.size} due 7d"),
            )
            val overall = (axes.map { it.fraction }.average() * 100).roundToInt()
            HexRadarChart(axes = axes, score = overall, headline = if (overall >= 75) "Steady glow" else "Gentle pace")
          }

          item(key = "setup") {
            SetupCard(
              state.permissions,
              actions::onRequestRuntimePermissions,
              actions::onOpenNotificationAccessSettings,
              actions::onOpenUsageAccessSettings,
            )
          }
        }
      }
    }
  }

  when (activeModal) {
    TodayModal.REMINDER -> AddReminderSheet(
      dateKey = selectedDateKey,
      onAdd = { title, mins -> actions.onAddReminder(title, mins, selectedDateKey); activeModal = null },
      onDismiss = { activeModal = null },
    )
    TodayModal.MEAL -> QuickTextLogSheet(
      title = "Log a meal",
      subtitle = "Describe what you ate (e.g. \"2 idlis, poha and 2 boiled eggs\") — Jarvis estimates calories & macros.",
      label = "Meal description",
      placeholder = "Poha, idli & 2 eggs",
      cta = "Save meal",
      presets = listOf("Poha & 2 eggs", "Chicken biryani", "Dal, 2 rotis & paneer", "Oats & whey shake"),
      onSubmit = { text -> actions.onLogMeal(text, selectedDateKey); activeModal = null },
      onDismiss = { activeModal = null },
    )
    TodayModal.WORKOUT -> QuickWorkoutSheet(
      onSubmit = { title, mins -> actions.onLogWorkout(title, mins, selectedDateKey); activeModal = null },
      onDismiss = { activeModal = null },
    )
    TodayModal.SLEEP -> QuickSleepSheet(
      onSubmit = { hrs, bed, wake, qual -> actions.onLogSleep(hrs, bed, wake, qual, selectedDateKey); activeModal = null },
      onDismiss = { activeModal = null },
    )
    TodayModal.STEPS -> QuickNumberSheet(
      title = "Update today's steps",
      subtitle = "Auto-reads your phone's step sensor on refresh, or adjust manually.",
      label = "Steps today",
      initial = daySteps.takeIf { it > 0 }?.toString() ?: "",
      onSubmit = { v -> actions.onLogManualSteps(v, selectedDateKey); activeModal = null },
      onDismiss = { activeModal = null },
    )
    TodayModal.HABIT -> QuickTextLogSheet(
      title = "Add daily habit or supplement",
      subtitle = "Shows up as a quick check-off chip on your Plan view.",
      label = "Habit or supplement",
      placeholder = "Magnesium 400mg, 10m stretch…",
      cta = "Add habit",
      presets = listOf("Vitamin D3", "Magnesium 400mg", "20m Evening Walk", "Read 15 pages"),
      onSubmit = { text -> actions.onAddHabit(text); activeModal = null },
      onDismiss = { activeModal = null },
    )
    null -> Unit
  }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, tint: Color, pastelBg: Color, modifier: Modifier, onClick: () -> Unit) {
  BentoCard(modifier = modifier, onClick = onClick, padding = 12.dp) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(38.dp).clip(CircleShape).background(pastelBg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
      }
      Spacer(Modifier.height(8.dp))
      Text(label, style = MaterialTheme.typography.labelMedium, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun ProactiveAlertsCard(
  alerts: List<ProactiveAlert>,
  onOpenTab: (HudTab?) -> Unit,
  onDismiss: (String) -> Unit,
) {
  BentoCard(pastelBg = HudColors.BlushSoft, padding = 14.dp) {
    alerts.forEachIndexed { idx, alert ->
      if (idx > 0) Spacer(Modifier.height(10.dp))
      val iconColor = when (alert.kind) {
        ProactiveKind.RENEWAL, ProactiveKind.BILL, ProactiveKind.CYCLE -> HudColors.Blush
        ProactiveKind.DELIVERY -> HudColors.SlateBlue
        ProactiveKind.FOCUS -> HudColors.Peach
        else -> HudColors.Sage
      }
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(HudColors.Surface)
          .clickable { onOpenTab(alert.targetTab) }
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconBadge(Icons.Outlined.NotificationsActive, iconColor, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(alert.kind.label, style = MaterialTheme.typography.labelSmall, color = iconColor)
          Text(alert.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(alert.body, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onDismiss(alert.dedupKey) }, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Outlined.Close, "Dismiss alert", tint = HudColors.TextMuted, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun SmartDayPlannerCard(
  dateLabel: String,
  events: List<com.jarvis.hud.model.CalendarEntry>,
  reminders: List<com.jarvis.hud.model.PlannerReminder>,
  onToggleReminder: (String) -> Unit,
  onDeleteReminder: (String) -> Unit,
  onAddReminderClick: () -> Unit,
) {
  SectionHeader(dateLabel, trailing = "+ Reminder", onTrailingClick = onAddReminderClick)
  Spacer(Modifier.height(4.dp))
  BentoCard(padding = 16.dp) {
    if (events.isEmpty() && reminders.isEmpty()) {
      Text("Clear schedule — add a gentle reminder or focus block.", style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
      Spacer(Modifier.height(10.dp))
      Box(
        Modifier
          .clip(CircleShape)
          .background(HudColors.SandSoft)
          .clickable(role = Role.Button, onClick = onAddReminderClick)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Add, null, tint = HudColors.Text, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Add reminder", style = MaterialTheme.typography.labelLarge, color = HudColors.Text)
        }
      }
    }
    events.forEachIndexed { i, e ->
      if (i > 0) Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
          Text(HudFormat.time(e.startTime), style = MaterialTheme.typography.labelLarge, color = HudColors.Lavender)
          Text(HudFormat.time(e.endTime), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted)
        }
        Box(Modifier.width(3.dp).height(34.dp).background(HudColors.Lavender, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(e.title, style = MaterialTheme.typography.titleSmall, color = HudColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          e.location?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
    }
    if (events.isNotEmpty() && reminders.isNotEmpty()) {
      Spacer(Modifier.height(6.dp))
      Box(Modifier.fillMaxWidth().height(1.dp).background(HudColors.OutlineSoft))
      Spacer(Modifier.height(6.dp))
    }
    reminders.forEach { rem ->
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { onToggleReminder(rem.id) }
          .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          if (rem.isDone) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (rem.isDone) HudColors.Sage else HudColors.TextDim,
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(
            rem.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (rem.isDone) HudColors.TextMuted else HudColors.Text,
            textDecoration = if (rem.isDone) TextDecoration.LineThrough else null,
          )
          Text(formatMinutesOfDay(rem.minutesOfDay), style = MaterialTheme.typography.labelSmall, color = HudColors.TextMuted)
        }
        IconButton(onClick = { onDeleteReminder(rem.id) }, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Outlined.DeleteOutline, "Remove reminder", tint = HudColors.TextMuted, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun RecentCallsCard(state: JarvisUiState) {
  SectionHeader("Recent calls")
  Spacer(Modifier.height(4.dp))
  BentoCard(padding = 14.dp) {
    if (state.recentCalls.isEmpty()) EmptyLine("No recent calls.", Modifier.padding(4.dp))
    state.recentCalls.take(5).forEach { c ->
      val type = c.callType.uppercase()
      val (icon, color, label) = when {
        "MISS" in type || "REJECT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMissed, HudColors.Blush, "Missed")
        "OUT" in type -> Triple(Icons.AutoMirrored.Outlined.CallMade, HudColors.SlateBlue, "Outgoing")
        else -> Triple(Icons.AutoMirrored.Outlined.CallReceived, HudColors.Sage, "Incoming")
      }
      ListRow(
        title = c.nameOrNumber,
        subtitle = "$label · ${HudFormat.relative(c.timestamp)}",
        icon = icon,
        tint = color,
        trailing = { Text(HudFormat.duration(c.durationSec), style = MaterialTheme.typography.labelMedium, color = HudColors.TextMuted) },
      )
    }
  }
}

@Composable
private fun BriefingCard(state: JarvisUiState, actions: JarvisActions, calmQuotes: List<String>) {
  val b = state.briefing
  val combinedHighlights = remember(b?.highlights, calmQuotes) {
    ((b?.highlights ?: emptyList()) + calmQuotes).distinct()
  }
  var highlightIdx by remember(b?.dateKey) { mutableIntStateOf(0) }
  val canCycle = combinedHighlights.size > 1

  BentoCard(
    pastelBg = HudColors.BlushSoft,
    onClick = if (canCycle) {
      { highlightIdx = (highlightIdx + 1) % combinedHighlights.size }
    } else null,
    padding = 18.dp,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconBadge(Icons.Outlined.AutoAwesome, HudColors.Blush, size = 30.dp)
      Spacer(Modifier.width(10.dp))
      Text("Daily briefing", style = MaterialTheme.typography.labelLarge, color = HudColors.Blush, modifier = Modifier.weight(1f))
      if (b != null) {
        Text("Updated ${HudFormat.time(b.createdAt)}", style = MaterialTheme.typography.labelSmall, color = HudColors.TextDim)
      }
    }
    Spacer(Modifier.height(12.dp))
    if (b == null) {
      Text(
        if (state.isSyncing) "Putting your briefing together…" else "No briefing yet. Refresh and I'll put one together.",
        style = MaterialTheme.typography.bodyLarge,
        color = HudColors.TextDim,
      )
      if (!state.isSyncing) {
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
          onClick = actions::onScanAndOrganize,
          colors = ButtonDefaults.filledTonalButtonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream),
        ) {
          Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Refresh")
        }
      }
      return@BentoCard
    }
    Text(b.headline, style = JarvisType.TitleSerif, color = HudColors.Text)
    Spacer(Modifier.height(6.dp))
    Text(b.summary, style = MaterialTheme.typography.bodyMedium, color = HudColors.TextDim)
    if (combinedHighlights.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      val activeHighlight = combinedHighlights[highlightIdx.coerceIn(0, combinedHighlights.lastIndex)]
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(HudColors.Surface.copy(alpha = 0.85f))
          .border(1.dp, HudColors.OutlineSoft, RoundedCornerShape(14.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(7.dp).background(HudColors.Blush, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(activeHighlight, style = MaterialTheme.typography.bodyMedium, color = HudColors.Text)
          if (canCycle) {
            Spacer(Modifier.height(2.dp))
            Text("tap for another (${highlightIdx + 1}/${combinedHighlights.size})", style = JarvisType.PromptItalic, color = HudColors.TextMuted)
          }
        }
      }
    }
    Spacer(Modifier.height(14.dp))
    Box(
      Modifier
        .clip(CircleShape)
        .background(HudColors.Surface)
        .border(1.dp, HudColors.Outline, CircleShape)
        .clickable(role = Role.Button, onClick = actions::onToggleSpeakBriefing)
        .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (state.isSpeaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp, null, tint = HudColors.Text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (state.isSpeaking) "Stop" else "Listen", style = MaterialTheme.typography.labelLarge, color = HudColors.Text)
      }
    }
  }
}

@Composable
private fun DeviceCard(state: JarvisUiState) {
  val t = state.telemetry
  val batteryColor = when {
    t.isCharging -> HudColors.Sage
    t.batteryPercent <= 15 -> HudColors.Coral
    t.batteryPercent <= 30 -> HudColors.Sand
    else -> HudColors.SlateBlue
  }
  val used = (t.totalStorageGb - t.freeStorageGb).coerceAtLeast(0f)
  val frac = if (t.totalStorageGb > 0) used / t.totalStorageGb else 0f
  val storageColor = if (frac > 0.9f) HudColors.Coral else if (frac > 0.75f) HudColors.Sand else HudColors.SlateBlue
  SectionHeader("This phone")
  Spacer(Modifier.height(4.dp))
  BentoCard {
    ListRow(
      title = "Battery ${t.batteryPercent}%",
      subtitle = if (t.isCharging) "Charging" else "On battery",
      icon = if (t.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryStd,
      tint = batteryColor,
    )
    Meter(t.batteryPercent / 100f, batteryColor)
    Spacer(Modifier.height(12.dp))
    ListRow(
      title = String.format(Locale.US, "%.1f GB free", t.freeStorageGb),
      subtitle = String.format(Locale.US, "%d%% of %.0f GB used", (frac * 100).toInt(), t.totalStorageGb),
      icon = Icons.Outlined.SdStorage,
      tint = storageColor,
    )
    Meter(frac, storageColor)
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReminderSheet(dateKey: String, onAdd: (String, Int) -> Unit, onDismiss: () -> Unit) {
  var title by remember { mutableStateOf("") }
  var selectedMinutes by remember { mutableIntStateOf(10 * 60) }
  val slots = listOf("9:00 AM" to 9 * 60, "11:00 AM" to 11 * 60, "2:00 PM" to 14 * 60, "5:00 PM" to 17 * 60, "8:00 PM" to 20 * 60)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HudColors.Surface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Quick reminder", style = JarvisType.TitleSerif, color = HudColors.Text)
      Text("For $dateKey · Jarvis will nudge you gently.", style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
      OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("What would you like to remember?") }, singleLine = true, shape = MaterialTheme.shapes.medium, colors = jarvisTextFieldColors(), modifier = Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { (label, mins) ->
          FilterChip(selected = selectedMinutes == mins, onClick = { selectedMinutes = mins }, label = { Text(label) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HudColors.SandSoft, selectedLabelColor = HudColors.Text))
        }
      }
      Button(onClick = { onAdd(title, selectedMinutes) }, enabled = title.isNotBlank(), shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream)) {
        Text("Save reminder")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickTextLogSheet(
  title: String,
  subtitle: String,
  label: String,
  placeholder: String,
  cta: String,
  presets: List<String>,
  onSubmit: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf("") }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HudColors.Surface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, style = JarvisType.TitleSerif, color = HudColors.Text)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
      OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, placeholder = { Text(placeholder) }, shape = MaterialTheme.shapes.medium, colors = jarvisTextFieldColors(), modifier = Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { p ->
          FilterChip(selected = text == p, onClick = { text = p }, label = { Text(p) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HudColors.BlushSoft, selectedLabelColor = HudColors.Text))
        }
      }
      Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank(), shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream)) {
        Text(cta)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickWorkoutSheet(onSubmit: (String, Int) -> Unit, onDismiss: () -> Unit) {
  var kind by remember { mutableStateOf("Brisk walk") }
  var mins by remember { mutableIntStateOf(30) }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HudColors.Surface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Log activity", style = JarvisType.TitleSerif, color = HudColors.Text)
      OutlinedTextField(value = kind, onValueChange = { kind = it }, label = { Text("Activity") }, singleLine = true, shape = MaterialTheme.shapes.medium, colors = jarvisTextFieldColors(), modifier = Modifier.fillMaxWidth())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Brisk walk", "Cycling", "Strength", "Yoga", "Run").forEach { a ->
          FilterChip(selected = kind == a, onClick = { kind = a }, label = { Text(a) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HudColors.SageSoft, selectedLabelColor = HudColors.Text))
        }
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60).forEach { m ->
          FilterChip(selected = mins == m, onClick = { mins = m }, label = { Text("${m}m") }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HudColors.SandSoft, selectedLabelColor = HudColors.Text))
        }
      }
      Button(onClick = { onSubmit(kind, mins) }, enabled = kind.isNotBlank(), shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream)) {
        Text("Save activity")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickSleepSheet(onSubmit: (Float, String, String, Int) -> Unit, onDismiss: () -> Unit) {
  var hours by remember { mutableFloatStateOf(7.5f) }
  var quality by remember { mutableIntStateOf(84) }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HudColors.Surface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Log last night's sleep", style = JarvisType.TitleSerif, color = HudColors.Text)
      Text("Duration", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5.5f, 6.5f, 7.0f, 7.5f, 8.0f, 8.5f).forEach { h ->
          FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("${h}h") }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HudColors.SlateBlueSoft, selectedLabelColor = HudColors.Text))
        }
      }
      Text("How rested do you feel?", style = MaterialTheme.typography.labelLarge, color = HudColors.TextDim)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Tired" to 58, "Okay" to 74, "Rested" to 86, "Great" to 94).forEach { (label, score) ->
          FilterChip(selected = quality == score, onClick = { quality = score }, label = { Text(label) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HudColors.LavenderSoft, selectedLabelColor = HudColors.Text))
        }
      }
      Button(onClick = { onSubmit(hours, "11:30 PM", "7:00 AM", quality) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream)) {
        Text("Save sleep")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNumberSheet(title: String, subtitle: String, label: String, initial: String, onSubmit: (Int) -> Unit, onDismiss: () -> Unit) {
  var text by remember { mutableStateOf(initial) }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = HudColors.Surface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(title, style = JarvisType.TitleSerif, color = HudColors.Text)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HudColors.TextDim)
      OutlinedTextField(value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(6) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.medium, colors = jarvisTextFieldColors(), modifier = Modifier.fillMaxWidth())
      Button(onClick = { onSubmit(text.toIntOrNull() ?: 0) }, enabled = (text.toIntOrNull() ?: 0) > 0, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = HudColors.Cream, contentColor = HudColors.OnCream)) {
        Text("Save")
      }
    }
  }
}

private fun formatMinutesOfDay(mins: Int): String {
  val hh = (mins / 60).coerceIn(0, 23)
  val mm = (mins % 60).coerceIn(0, 59)
  val ampm = if (hh < 12) "AM" else "PM"
  val h12 = when {
    hh == 0 -> 12
    hh > 12 -> hh - 12
    else -> hh
  }
  return String.format(Locale.ENGLISH, "%d:%02d %s", h12, mm, ampm)
}

internal fun screenPadding(inner: PaddingValues) = PaddingValues(
  start = 18.dp,
  end = 18.dp,
  top = inner.calculateTopPadding() + 6.dp,
  bottom = inner.calculateBottomPadding() + 18.dp,
)
