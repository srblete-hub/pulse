package com.noop.pulse

import androidx.compose.foundation.Canvas
import com.noop.data.DailyMetric
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ui.AppViewModel
import com.noop.ui.PulseMark
import com.noop.widget.StressPoint
import com.noop.widget.StressWidgetProducer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class HomeActions(
    val openProfile: () -> Unit,
    val openStrap: () -> Unit,
    val openSleep: () -> Unit,
    val openRecovery: () -> Unit,
    val openEffort: () -> Unit,
    val openStress: () -> Unit,
    val openCalibration: () -> Unit,
    val openGoals: () -> Unit,
    val openCharging: () -> Unit,
    val openSleepSchedule: () -> Unit,
    val openStartActivity: () -> Unit,
    val openHrv: () -> Unit,
    val openSteps: () -> Unit,
    val openCalories: () -> Unit,
    val openReport: () -> Unit = {},
    val openCoach: () -> Unit = {},
    val openMonitor: () -> Unit = {},
    val openAddActivity: () -> Unit = {},
    val openJournal: () -> Unit = {},
    val openAge: () -> Unit = {},
)

@Composable
fun PulseHomeScreen(vm: AppViewModel, showLiveHr: Boolean = false, actions: HomeActions) {
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    val live by vm.live.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableStateOf(0) }
    val metric = PulseData.metricFor(days, today, offset)
    val nights = PulseData.nightsWithSleep(days)
    val needCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(days) { if (days.isNotEmpty()) PulseNeed.save(needCtx, com.noop.pulse.engine.PulseSleepNeed.needMin(needCtx, days)) }

    var stress by remember { mutableStateOf<List<StressPoint>>(emptyList()) }
    LaunchedEffect(days.size, today?.day) {
        stress = runCatching { StressWidgetProducer.todayCurve(vm.repo, vm.activeStrapId)?.points }.getOrNull().orEmpty()
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val view = com.noop.pulse.engine.pulseDayView(vm, offset)
    val sleep = view.sleep
    val rec = view.recovery
    val eff = view.effort21
    val signals by vm.v5Signals.collectAsStateWithLifecycle()
    val workoutsAll by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { vm.loadWorkouts() } }
    val dayKey = PulseData.dayKey(offset)

    // Last night's main sleep, for the activities card.
    var lastSleep by remember { mutableStateOf<SleepItem?>(null) }
    LaunchedEffect(days.size, offset) {
        val zone = ZoneId.systemDefault()
        val d = java.time.LocalDate.now().plusDays(offset.toLong())
        val from = d.minusDays(1).atTime(12, 0).atZone(zone).toEpochSecond(); val to = d.atTime(14, 0).atZone(zone).toEpochSecond()
        lastSleep = runCatching { vm.repo.sleepSessionsUnion(vm.activeStrapId, from, to).filter { it.endTs - it.startTs >= 2 * 3600 }.maxByOrNull { it.endTs - it.startTs } }
            .getOrNull()?.let { SleepItem(it.effectiveStartTs, it.endTs) }
    }
    // Weekly heart-rate zone minutes and VO2 max for the dashboard.
    var zonesWeek by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var vo2 by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(days.size) {
        zonesWeek = runCatching {
            (0..6).map { k -> EffortDiagnostics.forDay(ctx, vm, java.time.LocalDate.now().minusDays(k.toLong())) }.let { l -> l.sumOf { it.zones13 } to l.sumOf { it.zones45 } }
        }.getOrNull()
        vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull()
    }

    Box(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding()) {
        HomeHeader(
            streak = streaks.current,
            dayLabel = PulseData.dayLabel(offset),
            canGoForward = offset < 0,
            battery = live.batteryPct,
            connected = live.connected,
            heartRate = if (showLiveHr) live.heartRate else null,
            onPrev = { offset -= 1 },
            onNext = { if (offset < 0) offset += 1 },
            onProfile = actions.openProfile,
            onBattery = actions.openStrap,
        )
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PulseMark(size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("PULSE", style = PulseText.wordmark.copy(fontSize = 20.sp), color = PulseColors.text)
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            HomeRing(sleep?.let { (it / 100).toFloat() }, PulseColors.sleep, sleep?.roundToInt()?.toString() ?: "--", "%", "SUEÑO", onClick = actions.openSleep)
            HomeRing(rec?.let { (it / 100).toFloat() }, PulseColors.recovery(rec), rec?.roundToInt()?.toString() ?: "--", "%", "RECUPERACIÓN", onClick = actions.openRecovery)
            HomeRing(eff?.let { (it / 21).toFloat() }, PulseColors.strain, PulseData.oneDecimal(eff ?: 0.0), null, "ESFUERZO", onClick = actions.openEffort)
        }

        // Insights: beginner steps, calibration, the day's recovery read with today's Effort target, notable vitals.
        val hasSchedule = SleepSchedules.load(ctx).isNotEmpty()
        val firstSync = PulseProfile.firstSyncAt(ctx)
        LaunchedEffect(live.lastSyncAt) { if (live.lastSyncAt != null && firstSync == null) PulseProfile.markFirstSync(ctx) }
        val beginner = nights == 0 && (firstSync == null || System.currentTimeMillis() - firstSync < 2 * 3600_000L)
        val steps = if (!beginner) emptyList() else listOfNotNull(
            Insight("step_goals", "Define tus objetivos", "Responde unas preguntas y adaptamos los consejos diarios a lo que quieres mejorar.", actions.openGoals),
            Insight("step_charging", "Cómo cargar la pulsera", "Llega con batería suficiente para registrar tu primer día y tu primera noche.", actions.openCharging),
            Insight("step_schedule", "Configura tu horario de sueño", "Te avisamos para desconectar y la pulsera te despierta con vibración suave.", actions.openSleepSchedule).takeIf { !hasSchedule },
        )
        val need = rememberTonightNeed(vm, days, if (offset == 0) view.effort21 else null)
        val insights = buildInsights(days, metric, rec, nights, if (offset == 0) effortTargetFor(days, rec) else null, need?.debtMin?.times(3) ?: 0.0,
            signals?.illness, steps)
        InsightStack(insights, dayKey, Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 32.dp))

        // Health + stress monitors.
        val valid = stress.filter { it.level != null }
        val lastStress = valid.lastOrNull()
        Row(Modifier.fillMaxWidth().padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MonitorTile("MONITOR DE SALUD", Modifier.weight(1f), actions.openMonitor) { HealthMonitorBody(healthMonitorStatus(days, metric, nights)) }
            MonitorTile("MONITOR DE ESTRÉS", Modifier.weight(1f), actions.openStress) {
                StressMonitorBody(lastStress?.level, lastStress?.let { Instant.ofEpochSecond(it.ts).atZone(ZoneId.systemDefault()).toLocalTime().let { t -> "%d:%02d".format(t.hour, t.minute) } })
            }
        }

        // My day.
        Text("Mi día", style = PulseText.headline, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp))
        Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoachOutlookRow(onCoach = actions.openCoach, onOutlook = {
                CoachLaunch.offer("Tu resumen del día", "¿Cómo estoy hoy?"); actions.openCoach()
            })
            TodayActivitiesCard(
                sleep = lastSleep,
                workouts = workoutsAll.filter { PulseActivity.dayOf(it) == dayKey }.sortedBy { it.startTs },
                onSleep = actions.openSleep, onWorkout = actions.openEffort,
                onAdd = actions.openAddActivity, onStart = actions.openStartActivity, onExpand = actions.openEffort,
            )
            if (offset == 0 && need != null) {
                val tonight = SleepSchedules.tonight(ctx, need.totalMin)
                var habitualWake by remember { mutableStateOf<Int?>(null) }
                LaunchedEffect(Unit) {
                    val zone = ZoneId.systemDefault(); val now = System.currentTimeMillis() / 1000
                    habitualWake = runCatching {
                        com.noop.pulse.engine.PulsePlanEngine.habitualWake(vm.repo.sleepSessionsUnion(vm.activeStrapId, now - 14 * 86_400L, now)
                            .filter { it.endTs - it.startTs >= 3 * 3600 }.map { Instant.ofEpochSecond(it.endTs).atZone(zone).let { t -> t.hour * 60 + t.minute } })
                    }.getOrNull()
                }
                when {
                    tonight != null -> TonightSleepCard(Math.floorMod(tonight.bedMin, 1440), tonight.wakeMin, tonight.on && tonight.mode != WakeMode.None, actions.openSleepSchedule)
                    habitualWake != null -> TonightSleepCard(com.noop.pulse.engine.PulsePlanEngine.bedtime(habitualWake!!, need.totalMin), habitualWake!!, false, actions.openSleepSchedule)
                }
                NeedBreakdown(need, Modifier.padding(horizontal = 4.dp))
            }
        }

        // My plan.
        Text("Mi plan", style = PulseText.headline, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp))
        Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MyPlanCard(actions.openGoals)
            if (nights >= 3) PulseCard(onClick = actions.openReport) {
                CardHeader("Tu semana")
                Text(PulseReportData.headline(PulseReportData.build(days, 7)), style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // My dashboard: today's value with the 30-day average under it.
        var customize by remember { mutableStateOf(false) }
        var dashTick by remember { mutableStateOf(0) }
        DashboardHeader { customize = true }
        if (customize) CustomizeDashboardDialog { customize = false; dashTick++ }
        @Suppress("UNUSED_EXPRESSION") dashTick
        val shown = PulseDashboardPrefs.shown(ctx)
        val last30 = days.filter { it.day != metric?.day }.takeLast(30)
        fun avg(f: (DailyMetric) -> Double?) = last30.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
        fun up(v: Double?, b: Double?) = if (v == null || b == null || v == b) null else v > b
        val rows = listOfNotNull(
            DashMetric("hrv", "Variabilidad (VFC)", DashIcons.hrv, metric?.avgHrv?.roundToInt()?.toString() ?: "--", avg { it.avgHrv }?.roundToInt()?.toString(), up(metric?.avgHrv, avg { it.avgHrv }), actions.openHrv),
            DashMetric("rhr", "Pulso en reposo", DashIcons.rhr, metric?.restingHr?.toString() ?: "--", avg { it.restingHr?.toDouble() }?.roundToInt()?.toString(), up(metric?.restingHr?.toDouble(), avg { it.restingHr?.toDouble() }), actions.openRecovery, goodUp = false),
            DashMetric("steps", "Pasos", DashIcons.steps, metric?.steps?.let { PulseData.int(it) } ?: "--", avg { it.steps?.toDouble() }?.let { PulseData.int(it.roundToInt()) }, up(metric?.steps?.toDouble(), avg { it.steps?.toDouble() }), actions.openSteps),
            DashMetric("hours", "Horas de sueño", DashIcons.sleep, metric?.totalSleepMin?.takeIf { it > 0 }?.let { PulseData.hoursMinutes(it) } ?: "--",
                avg { it.totalSleepMin?.takeIf { m -> m > 0 } }?.let { PulseData.hoursMinutes(it) }, up(metric?.totalSleepMin, avg { it.totalSleepMin?.takeIf { m -> m > 0 } }), actions.openSleep),
            DashMetric("restor", "Sueño reparador", DashIcons.sleep, restor(metric)?.let { PulseData.hoursMinutes(it) } ?: "--",
                avg { restor(it) }?.let { PulseData.hoursMinutes(it) }, up(restor(metric), avg { restor(it) }), actions.openSleep),
            DashMetric("eff", "Eficiencia del sueño", DashIcons.sleep, effPct(metric)?.let { "${it.roundToInt()}%" } ?: "--",
                avg { effPct(it) }?.let { "${it.roundToInt()}%" }, up(effPct(metric), avg { effPct(it) }), actions.openSleep),
            DashMetric("z13", "Zonas 1–3 (semana)", DashIcons.zones, zonesWeek?.first?.let { PulseActivity.mmss(it) } ?: "--", null, null, actions.openEffort),
            DashMetric("z45", "Zonas 4–5 (semana)", DashIcons.zones, zonesWeek?.second?.let { PulseActivity.mmss(it) } ?: "--", null, null, actions.openEffort),
            DashMetric("vo2", "VO₂ máx.", DashIcons.vo2, vo2?.roundToInt()?.toString() ?: "--", null, null, actions.openAge),
            DashMetric("kcal", "Calorías", DashIcons.kcal, metric?.activeKcalEst?.let { PulseData.int(it.roundToInt()) } ?: "--", avg { it.activeKcalEst }?.let { PulseData.int(it.roundToInt()) }, up(metric?.activeKcalEst, avg { it.activeKcalEst }), actions.openCalories),
            DashMetric("resp", "Respiración", DashIcons.resp, metric?.respRateBpm?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "--", avg { it.respRateBpm }?.let { String.format(java.util.Locale.US, "%.1f", it) }, up(metric?.respRateBpm, avg { it.respRateBpm }), actions.openRecovery, goodUp = false),
        ).filter { it.key in shown }
        Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { DashRow(it) }
            StressCard(stress, nights, actions.openStress)
        }
        Spacer(Modifier.height(PulseMetrics.barClearance + 40.dp))
    }

    // Sticky compact rings once the big ones have scrolled away.
    val threshold = with(density) { 330.dp.toPx() }
    if (scroll.value > threshold) {
        Column(Modifier.fillMaxWidth().background(PulseColors.base).statusBarsPadding()) {
            HomeHeader(
                streak = streaks.current, dayLabel = PulseData.dayLabel(offset), canGoForward = offset < 0,
                battery = live.batteryPct, connected = live.connected, heartRate = if (showLiveHr) live.heartRate else null,
                onPrev = { offset -= 1 }, onNext = { if (offset < 0) offset += 1 }, onProfile = actions.openProfile, onBattery = actions.openStrap,
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniRing(sleep?.let { (it / 100).toFloat() }, PulseColors.sleep, "SUEÑO", actions.openSleep)
                MiniRing(rec?.let { (it / 100).toFloat() }, PulseColors.recovery(rec), "RECUPERACIÓN", actions.openRecovery)
                MiniRing(eff?.let { (it / 21).toFloat() }, PulseColors.strain, "ESFUERZO", actions.openEffort)
            }
        }
    }

    // Floating "+" (start / add an activity, open the journal).
    var plusMenu by remember { mutableStateOf(false) }
    Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 104.dp)) {
        Box(Modifier.size(56.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(PulseColors.text).pulseClick { plusMenu = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, "Añadir", tint = PulseColors.base, modifier = Modifier.size(28.dp))
        }
        androidx.compose.material3.DropdownMenu(plusMenu, { plusMenu = false }, Modifier.background(PulseColors.overlay)) {
            listOf("Iniciar actividad" to actions.openStartActivity, "Añadir actividad" to actions.openAddActivity, "Escribir en el diario" to actions.openJournal).forEach { (l, go) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(l, style = PulseText.bodyLg, color = PulseColors.text) }, onClick = { plusMenu = false; go() })
            }
        }
    }
    }
}

@Composable
private fun HomeHeader(
    streak: Int,
    dayLabel: String,
    canGoForward: Boolean,
    battery: Double?,
    connected: Boolean,
    heartRate: Int?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onProfile: () -> Unit,
    onBattery: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(16.dp)).background(PulseColors.overlay).pulseClick(onProfile).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initials = PulseProfile.initials(androidx.compose.ui.platform.LocalContext.current)
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(PulseColors.accentBrush), contentAlignment = Alignment.Center) {
                if (initials.isNotEmpty()) Text(initials, style = PulseText.small.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.onAccent)
                else Icon(Icons.Outlined.Person, "Perfil", tint = PulseColors.onAccent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Whatshot, null, tint = PulseColors.gold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(8.dp))
            Text("$streak", style = PulseText.small, color = PulseColors.text)
        }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(PulseColors.overlay).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp, 26.dp).pulseClick(onPrev), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronLeft, "Día anterior", tint = PulseColors.textTertiary, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.chip).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(dayLabel, style = PulseText.overline.copy(letterSpacing = PulseText.tabTitle.letterSpacing), color = if (canGoForward) PulseColors.text else PulseColors.textTertiary)
            }
            Box(Modifier.size(30.dp, 26.dp).pulseClick(onNext), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronRight, "Día siguiente", tint = if (canGoForward) PulseColors.textTertiary else PulseColors.hairlineStrong, modifier = Modifier.size(16.dp))
            }
        }
        Row(Modifier.pulseClick(onBattery), verticalAlignment = Alignment.CenterVertically) {
            if (heartRate != null) {
                Icon(Icons.Outlined.MonitorHeart, null, tint = PulseColors.recoveryRed, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("$heartRate", style = PulseText.small, color = PulseColors.text)
                Spacer(Modifier.width(8.dp))
            }
            Text(battery?.let { "${it.roundToInt()}%" } ?: "--%", style = PulseText.small, color = PulseColors.text)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(19.dp, 25.dp)) {
                Box(
                    Modifier.align(Alignment.BottomStart).size(15.dp, 21.dp)
                        .border(1.6.dp, PulseColors.text, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val f = ((battery ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.padding(4.dp).fillMaxWidth().height((15 * f).dp).clip(RoundedCornerShape(1.5.dp)).background(PulseColors.text))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (connected) PulseColors.recoveryGreen else PulseColors.textTertiary),
                )
            }
        }
    }
}

@Composable
private fun RingTile(
    label: String,
    fraction: Float?,
    color: androidx.compose.ui.graphics.Color,
    value: String,
    modifier: Modifier,
    onClick: () -> Unit,
    large: Boolean = false,
    confidence: com.noop.analytics.ScoreConfidence? = null,
) {
    Column(modifier.pulseClick(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreRing(fraction, color, value, large = large)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = PulseText.overline, color = PulseColors.text)
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.text, modifier = Modifier.size(12.dp))
        }
        confidence?.let { ConfidenceTag(it, Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    cta: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PulseText.cardTitle, color = PulseColors.text)
                Spacer(Modifier.height(8.dp))
                Text(body, style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(8.dp))
                CtaLink(cta, onClick = onClick)
            }
            Spacer(Modifier.width(8.dp))
            if (highlighted) {
                Box(
                    Modifier.size(70.dp).clip(RoundedCornerShape(18.dp))
                        .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = PulseColors.accent, modifier = Modifier.size(36.dp)) }
            } else {
                Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = PulseColors.textTertiary, modifier = Modifier.size(50.dp))
                }
            }
        }
    }
    if (highlighted) {
        AccentCard(padding = 20.dp, onClick = onClick) { content() }
    } else {
        PulseCard(color = PulseColors.raised, padding = 20.dp, onClick = onClick) { content() }
    }
}

@Composable
fun DashedProgressRing(done: Int, total: Int, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = 3.5.dp.toPx()
            val r = this.size.minDimension / 2 - sw - 2.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val gap = 22f
            val seg = 360f / total
            for (i in 0 until total) {
                drawArc(
                    if (i < done) PulseColors.accent else PulseColors.hairlineStrong,
                    -90f + i * seg + gap / 2, seg - gap, false, tl, Size(r * 2, r * 2),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        Text("$done/$total", style = PulseText.ringNumber.copy(fontSize = 14.sp), color = PulseColors.text)
    }
}

@Composable
fun MiniSparkline(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val step = size.width / (values.size - 1)
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - v / max * size.height * 0.9f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, PulseColors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun StressCard(points: List<StressPoint>, nights: Int, onClick: () -> Unit) {
    val valid = points.filter { it.level != null }
    val last = valid.lastOrNull()
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    fun hhmm(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)
    PulseCard(onClick = onClick) {
        CardHeader("Nivel de estrés")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                last?.let { "Actualizado a las ${hhmm(it.ts)}" } ?: "Sin lecturas todavía hoy",
                style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.weight(1f),
            )
            Text(PulseData.stressWord(last?.level), style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.stressLowLabel)
            Spacer(Modifier.width(8.dp))
            Text(PulseData.oneDecimal(last?.level), style = PulseText.bigNumber, color = PulseColors.text)
        }
        Row(Modifier.fillMaxWidth().height(130.dp).padding(top = 16.dp)) {
            Column(Modifier.width(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("3.0", "2.0", "1.0", "0.0").forEach { Text(it, style = PulseText.chartAxis, color = PulseColors.textSecondary) }
            }
            Canvas(Modifier.weight(1f).fillMaxSize()) {
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(PulseColors.hairline, Offset(0f, y), Offset(size.width, y), 1f)
                }
                if (valid.size >= 2) {
                    val t0 = valid.first().ts.toFloat()
                    val span = (valid.last().ts - valid.first().ts).coerceAtLeast(1).toFloat()
                    val p = Path()
                    valid.forEachIndexed { i, pt ->
                        val x = (pt.ts - t0) / span * size.width
                        val y = size.height - (pt.level!!.toFloat() / 3f).coerceIn(0f, 1f) * size.height
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, Brush.verticalGradient(listOf(PulseColors.stressMid, PulseColors.stressLow)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                if (last != null) {
                    val y = size.height - (last.level!!.toFloat() / 3f).coerceIn(0f, 1f) * size.height
                    drawCircle(PulseColors.text, 3.5.dp.toPx(), Offset(size.width - 3.dp.toPx(), y.coerceAtMost(size.height - 3.dp.toPx())))
                }
            }
        }
        if (valid.size >= 2) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val first = valid.first().ts
                val lastTs = valid.last().ts
                listOf(0.0, 0.33, 0.66).forEach { f -> Text(hhmm(first + ((lastTs - first) * f).toLong()), style = PulseText.chartAxis, color = PulseColors.textSecondary) }
                Text(hhmm(lastTs), style = PulseText.chartAxis.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text)
            }
        }
        if (nights < PulseData.recoverySeedNights) {
            Spacer(Modifier.height(16.dp))
            UnlockPanel(
                "Duerme con la pulsera ${PulseData.recoverySeedNights - nights} noches para calibrar",
                nights, PulseData.recoverySeedNights, fixedSegments = true,
            )
        }
    }
}

@Composable
fun EffortRecoveryChart(days: List<com.noop.data.DailyMetric>, modifier: Modifier = Modifier) {
    val keys = remember { (-6..0).map { PulseData.dayKey(it) } }
    val byDay = remember(days) { days.associateBy { it.day } }
    PulseCard(modifier) {
        CardHeader("Esfuerzo y recuperación", trailing = {
            Box(
                Modifier.size(20.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
        })
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(PulseColors.strain))
                Spacer(Modifier.width(4.dp))
                Text("ESFUERZO", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textQuiet)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).border(2.dp, PulseColors.recoveryGreen, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(4.dp))
                Text("RECUPERACIÓN", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textQuiet)
            }
        }
        Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 16.dp)) {
            Column(Modifier.width(22.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("21", "14", "7", "0").forEach { Text(it, style = PulseText.chartAxis.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.strain) }
            }
            Canvas(Modifier.weight(1f).fillMaxSize()) {
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(PulseColors.hairline, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                }
                val n = 7
                val slot = size.width / n
                val barW = slot * 0.42f
                val pts = mutableListOf<Offset>()
                for (i in 0 until n) {
                    val d = byDay[keys[i]]
                    val cx = slot * i + slot / 2
                    val e = PulseData.effort21(d)
                    if (e != null && e > 0) {
                        val h = (e / 21.0).toFloat().coerceIn(0f, 1f) * size.height
                        drawRoundRect(PulseColors.strain, Offset(cx - barW / 2, size.height - h), Size(barW, h), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    }
                    PulseData.rec(d)?.let { pts += Offset(cx, size.height - (it / 100.0).toFloat().coerceIn(0f, 1f) * size.height) }
                }
                if (pts.size >= 2) {
                    val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(p, PulseColors.recoveryGreen, style = Stroke(2.dp.toPx()))
                }
                pts.forEach {
                    drawCircle(PulseColors.overlay, 4.dp.toPx(), it)
                    drawCircle(PulseColors.recoveryGreen, 4.dp.toPx(), it, style = Stroke(2.dp.toPx()))
                }
            }
            Column(Modifier.width(34.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                listOf("100%", "66%", "33%", "0%").forEach { Text(it, style = PulseText.chartAxis.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.recoveryGreen) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 32.dp, top = 8.dp)) {
            keys.forEachIndexed { i, k ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val isToday = i == keys.lastIndex
                    Text(if (isToday) "Hoy" else PulseData.shortWeekday(k), style = PulseText.chartAxis, color = if (isToday) PulseColors.text else PulseColors.textSecondary, textAlign = TextAlign.Center)
                    if (!isToday) Text(PulseData.dayOfMonth(k), style = PulseText.chartAxis, color = PulseColors.textSecondary)
                }
            }
        }
    }
}

/** "Sueño de esta noche": recommended bedtime and tomorrow's wake alarm from the sleep planner. */
@Composable
private fun TonightCard(t: SleepSchedules.Tonight, modifier: Modifier, onEdit: () -> Unit) {
    PulseCard(modifier, onClick = onEdit) {
        CardHeader("Sueño de esta noche")
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bedtime, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(SleepSchedules.hm(t.bedMin), style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text)
                }
                Spacer(Modifier.height(8.dp))
                Text("HORA DE\nACOSTARTE", style = PulseText.overline.copy(lineHeight = 14.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center)
            }
            Text("- - - - -", style = PulseText.small, color = PulseColors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(SleepSchedules.hm(t.wakeMin), style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text)
                }
                Spacer(Modifier.height(8.dp))
                val alarmOn = t.on && t.mode != WakeMode.None
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(if (alarmOn) PulseColors.recoveryGreen else PulseColors.textTertiary))
                    Spacer(Modifier.width(8.dp))
                    Text(if (alarmOn) "ALARMA ACTIVADA" else "ALARMA APAGADA", style = PulseText.overline, color = if (alarmOn) PulseColors.recoveryGreen else PulseColors.textSecondary)
                }
                Text(if (alarmOn) t.mode.label else "SIN ALARMA", style = PulseText.overline, color = PulseColors.textSecondary)
            }
        }
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(10.dp)).background(PulseColors.hairline).pulseClick(onEdit),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Edit, null, tint = PulseColors.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("EDITAR ALARMA", style = PulseText.overlineLg, color = PulseColors.text)
        }
    }
}

private fun restor(d: DailyMetric?): Double? = d?.let { ((it.deepMin ?: 0.0) + (it.remMin ?: 0.0)).takeIf { v -> v > 0 } }
private fun effPct(d: DailyMetric?): Double? = d?.efficiency?.let { if (it <= 1.0) it * 100 else it }
