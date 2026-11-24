package com.noop.pulse

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.zoneSummary
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Per-day activity figures derived from noop's workout rows (zones from imported/derived zone splits). */
object PulseActivity {
    fun dayOf(w: WorkoutRow): String = Instant.ofEpochSecond(w.startTs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    fun isStrength(w: WorkoutRow): Boolean = w.sport.lowercase().let {
        "strength" in it || "weight" in it || "functional" in it || "fuerza" in it || "crossfit" in it || "gym" in it
    }

    private fun durMin(w: WorkoutRow) = (w.durationS ?: (w.endTs - w.startTs).toDouble()) / 60.0

    /** Minutes in zones 1–3 and 4–5 for the workouts of [day]; null when none carries zone data. */
    fun zones(workouts: List<WorkoutRow>, day: String): Pair<Double, Double>? {
        val z = zoneSummary(workouts.filter { dayOf(it) == day }) ?: return null
        return (z.minutes[0] + z.minutes[1] + z.minutes[2]) to (z.minutes[3] + z.minutes[4])
    }

    fun strengthMin(workouts: List<WorkoutRow>, day: String): Double =
        workouts.filter { dayOf(it) == day && isStrength(it) }.sumOf { durMin(it) }

    /** Weekly averages over the last 28 days, for the physiological-age inputs. */
    fun weekly(workouts: List<WorkoutRow>): Triple<Double?, Double?, Double?> {
        val from = java.time.LocalDate.now().minusDays(28).toString()
        val recent = workouts.filter { dayOf(it) >= from }
        if (recent.isEmpty()) return Triple(null, null, 0.0)
        val z = zoneSummary(recent)
        val z13 = z?.let { (it.minutes[0] + it.minutes[1] + it.minutes[2]) / 4.0 }
        val z45 = z?.let { (it.minutes[3] + it.minutes[4]) / 4.0 }
        return Triple(z13, z45, recent.filter(::isStrength).sumOf { durMin(it) } / 4.0)
    }

    fun mmss(min: Double?): String {
        if (min == null) return "--"
        val m = min.roundToInt()
        return "%d:%02d".format(m / 60, m % 60)
    }
}

private fun week(days: List<DailyMetric>, f: (DailyMetric) -> Double?): List<Double?> {
    val by = days.associateBy { it.day }
    return (-6..0).map { by[PulseData.dayKey(it)]?.let(f) }
}

private fun avg30(days: List<DailyMetric>, f: (DailyMetric) -> Double?): Double? =
    days.takeLast(30).mapNotNull(f).takeIf { it.isNotEmpty() }?.average()

private fun calibrationNotice(nights: Int, text: (Int) -> String): Pair<String, Int>? =
    if (nights >= PulseData.recoverySeedNights) null else text(PulseData.recoverySeedNights - nights) to PulseData.recoverySeedNights

@Composable
fun PulseEffortScreen(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit) {
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { vm.loadWorkouts() } }
    val view = com.noop.pulse.engine.pulseDayView(vm, 0)
    val d = view.metric
    val eff = view.effort21
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val nights = PulseData.nightsWithSleep(days)
    val todayKey = PulseData.dayKey(0)
    val keys = (-6..0).map { PulseData.dayKey(it) }
    // Zones from the whole day's heart rate, scored exactly as noop scores Effort (EffortDiagnostics).
    val context = androidx.compose.ui.platform.LocalContext.current
    var diags by remember { mutableStateOf<Map<String, EffortDiag>>(emptyMap()) }
    LaunchedEffect(days.size, today?.strain) {
        val out = HashMap<String, EffortDiag>()
        for (k in -6..0) {
            val day = java.time.LocalDate.now().plusDays(k.toLong())
            runCatching { EffortDiagnostics.forDay(context, vm, day) }.getOrNull()?.let { out[day.toString()] = it }
        }
        diags = out
    }
    var diagOffset by remember { mutableStateOf(0) }
    val z = diags[todayKey]?.let { it.zones13 to it.zones45 }
    val zAvg = keys.mapNotNull { diags[it]?.let { d -> d.zones13 to d.zones45 } }
    val str = PulseActivity.strengthMin(workouts, todayKey)
    val strAvg = keys.map { PulseActivity.strengthMin(workouts, it) }.average()
    val coach = when {
        eff == null -> "Empiezas el día con margen de sobra para moverte."
        eff >= 14 -> "Llevas un día exigente (${PulseData.oneDecimal(eff)}). Te ayudo a recuperarte."
        eff >= 8 -> "Llevas un esfuerzo de ${PulseData.oneDecimal(eff)}. ¿Te digo cuánto más te conviene?"
        else -> "Empiezas la semana con margen de sobra para moverte."
    }
    MetricPage(
        tipKey = "effort",
        tipTitle = "Esfuerzo del día",
        tipBody = "Mide la carga que ha soportado tu corazón a lo largo del día, entrenando y en tu vida normal. Cuanto más tiempo pasas con el pulso alto, más sube. Abajo, en «Cómo se ha calculado», ves el pulso y los minutos por zona que lo explican.",
        heroIcon = Icons.Outlined.ShowChart, heroColor = PulseColors.strain,
        heroFraction = eff?.let { (it / 21).toFloat() }, heroValue = PulseData.oneDecimal(eff ?: 0.0), heroValueSize = 52, heroLabel = "ESFUERZO",
        notice = null,
        middle = {
            EffortDiagCard(diags[PulseData.dayKey(diagOffset)], if (diagOffset == 0) "HOY ›" else "‹ AYER", onToggleDay = { diagOffset = if (diagOffset == 0) -1 else 0 })
            val dayKey = PulseData.dayKey(diagOffset)
            ActivitiesCard(
                workouts.filter { PulseActivity.dayOf(it) == dayKey }.sortedBy { it.startTs },
                isAuto = { PulseAutoActivity.isAuto(context, it) },
                onDelete = { PulseAutoActivity.delete(context, vm, it) },
                onLabel = { w, label -> scope.launch { PulseSports.confirm(context, vm, w, label); vm.loadWorkouts() } },
            )
            val (named, right) = remember(workouts) { PulseSports.accuracy(context) }
            if (named > 0) Text("Pulse acertó el deporte en $right de $named actividades que revisaste.",
                style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        },
        rows = listOf(
            MetricRow(Icons.Outlined.FavoriteBorder, "ZONAS DE PULSO 1–3", PulseActivity.mmss(z?.first ?: 0.0), "media ${PulseActivity.mmss(zAvg.map { it.first }.takeIf { it.isNotEmpty() }?.average() ?: 0.0)}"),
            MetricRow(Icons.Outlined.MonitorHeart, "ZONAS DE PULSO 4–5", PulseActivity.mmss(z?.second ?: 0.0), "media ${PulseActivity.mmss(zAvg.map { it.second }.takeIf { it.isNotEmpty() }?.average() ?: 0.0)}"),
            MetricRow(Icons.Outlined.FitnessCenter, "TIEMPO DE FUERZA", PulseActivity.mmss(str), "media ${PulseActivity.mmss(strAvg)}"),
            MetricRow(Icons.AutoMirrored.Outlined.DirectionsWalk, "PASOS", d?.steps?.let { PulseData.int(it) } ?: "--", "media ${avg30(days) { it.steps?.toDouble() }?.let { PulseData.int(it.roundToInt()) } ?: "--"}"),
        ),
        coachLine = coach, onBack = onBack, onCoach = onCoach,
        charts = listOf(
            TrendChart("Esfuerzo", PulseColors.strain, 21.0, week(days) { PulseData.effort21(it) }.dropLast(1) + eff, { String.format(Locale.US, "%.1f", it) }),
            TrendChart("Zonas 1–3", PulseColors.stressLow, 120.0, keys.map { diags[it]?.zones13 }, { PulseActivity.mmss(it) },
                legend = listOf(LegendItem("ZONA 1", PulseColors.textSecondary), LegendItem("ZONA 2", PulseColors.stressLow), LegendItem("ZONA 3", PulseColors.recoveryGreen)), valueColor = PulseColors.text),
            TrendChart("Zonas 4–5", PulseColors.stressHigh, 45.0, keys.map { diags[it]?.zones45 }, { PulseActivity.mmss(it) },
                legend = listOf(LegendItem("ZONA 4", PulseColors.stressHigh), LegendItem("ZONA 5", PulseColors.recoveryRed)), valueColor = PulseColors.text),
            TrendChart("Pasos", PulseColors.accent, 15000.0, week(days) { it.steps?.toDouble() }, { PulseData.int(it.roundToInt()) }),
            TrendChart("Calorías", PulseColors.accent, 4000.0, week(days) { it.activeKcalEst }, { PulseData.int(it.roundToInt()) }),
            TrendChart("Tiempo de fuerza", PulseColors.accent, 90.0, keys.map { PulseActivity.strengthMin(workouts, it).takeIf { m -> m > 0 } }, { PulseActivity.mmss(it) }, valueColor = PulseColors.text),
        ),
    )
}

@Composable
fun PulseRecoveryScreen(vm: AppViewModel, onBack: () -> Unit, onHabits: () -> Unit, onCoach: () -> Unit) {
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    val view = com.noop.pulse.engine.pulseDayView(vm, 0)
    // With tonight not scored yet, noop carries over the last scored night: its vitals come with it.
    val d = view.metric?.takeIf { it.recovery != null } ?: days.lastOrNull { it.day == view.recoveryFromDay } ?: view.metric
    val rec = view.recovery
    val nights = PulseData.nightsWithSleep(days)
    fun avgS(v: Double?, unit: String, dec: Boolean = false) = v?.let { if (dec) String.format(Locale.US, "%.1f%s", it, unit) else "${it.roundToInt()}$unit" } ?: "--"
    MetricPage(
        tipKey = "recovery",
        tipTitle = "Recuperación",
        tipBody = "Te dice cada mañana cómo de preparado está tu cuerpo. Se calcula como en NOOP: tu VFC (lo que más pesa), tu pulso en reposo, tu sueño, tu respiración y tu temperatura, comparados con tus propias medias.",
        heroIcon = Icons.Outlined.FavoriteBorder, heroColor = PulseColors.recovery(rec).takeIf { rec != null } ?: PulseColors.recoveryGreen,
        heroFraction = rec?.let { (it / 100).toFloat() }, heroValue = PulseData.pct(rec), heroValueSize = 46, heroLabel = "RECUPERACIÓN",
        notice = if (rec != null) null else calibrationNotice(nights) { "Duerme con la pulsera $it noches para calibrar del todo tu recuperación." },
        heroCaption = view.recoveryCaption,
        rows = listOf(
            MetricRow(Icons.Outlined.ShowChart, "VFC", avgS(d?.avgHrv, " ms"), "media ${avgS(avg30(days) { it.avgHrv }, " ms")}"),
            MetricRow(Icons.Outlined.FavoriteBorder, "PULSO EN REPOSO", avgS(d?.restingHr?.toDouble(), " lpm"), "media ${avgS(avg30(days) { it.restingHr?.toDouble() }, " lpm")}"),
            MetricRow(Icons.Outlined.Air, "RESPIRACIÓN", avgS(d?.respRateBpm, " rpm", true), "media ${avgS(avg30(days) { it.respRateBpm }, " rpm", true)}"),
            MetricRow(Icons.Outlined.Bedtime, "RENDIMIENTO DEL SUEÑO", PulseData.pct(PulseData.sleepPct(d)).replace("--%", "--"), "media ${PulseData.pct(avg30(days) { PulseData.sleepPct(it) }).replace("--%", "--")}"),
        ),
        coachLine = if (rec == null) "Cuando tengas tu primera recuperación, te ayudo a interpretarla." else "Recuperación del ${rec.roundToInt()} %. Te explico qué la ha movido.",
        onBack = onBack, onCoach = onCoach,
        middle = {
            HabitsCard(Icons.Outlined.CalendarMonth, onHabits)
        },
        charts = listOf(
            TrendChart("Recuperación", PulseColors.recoveryGreen, 100.0, week(days) { it.recovery }, { "${it.roundToInt()}%" }),
            TrendChart("VFC", PulseColors.accent, (avg30(days) { it.avgHrv } ?: 60.0) * 1.6, week(days) { it.avgHrv }, { "${it.roundToInt()}" }),
            TrendChart("Pulso en reposo", PulseColors.accent, 100.0, week(days) { it.restingHr?.toDouble() }, { "${it.roundToInt()}" }),
            TrendChart("Respiración", PulseColors.accent, 25.0, week(days) { it.respRateBpm }, { String.format(Locale.US, "%.1f", it) }),
            TrendChart("Rendimiento del sueño", PulseColors.sleep, 100.0, week(days) { PulseData.sleepPct(it) }, { "${it.roundToInt()}%" }),
        ),
    )
}

@Composable
fun PulseSleepScreen(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit) {
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    val context = androidx.compose.ui.platform.LocalContext.current
    val d = PulseData.metricFor(days, today, 0)
    val perf = com.noop.pulse.engine.pulseDayView(vm, 0).sleep
    val nights = PulseData.nightsWithSleep(days)
    val needMin = (PulseNeed.hours(context)) * 60
    fun h(v: Double?) = if (v == null || v <= 0) "-:--" else PulseData.hoursMinutes(v)
    fun vsNeed(v: Double?) = v?.takeIf { it > 0 }?.let { "${(it / needMin * 100).roundToInt()}%" } ?: "--%"
    fun inBed(m: DailyMetric) = m.totalSleepMin?.let { t -> m.efficiency?.takeIf { it > 0 }?.let { t / (it / 100) } }
    // noop: regularity = 1 − coefficient of variation of nightly hours over the last 28 nights (≥ 3 nights).
    fun regularityAt(dayKey: String) = com.noop.analytics.VitalityEngine.sleepConsistency(
        days.filter { it.day <= dayKey }.takeLast(28).mapNotNull { it.totalSleepMin?.div(60.0) })?.times(100)
    val regularity = regularityAt(PulseData.dayKey(0))
    val regularityAvg = (0 until 30).mapNotNull { regularityAt(PulseData.dayKey(-it)) }.takeIf { it.isNotEmpty() }?.average()
    val regularityLocked = regularity == null
    // noop's sleep-debt ledger (55 % carried each night, 14 nights).
    val debtByDay = com.noop.analytics.SleepDebt.debtSeries(days.map { it.day to it.totalSleepMin }, needHours = needMin / 60.0).toMap()
    MetricPage(
        tipKey = "sleep",
        tipTitle = "Rendimiento del sueño",
        tipBody = "Una sola nota que resume tu noche, calculada como en NOOP: 50 % horas dormidas frente a las que necesitas, 20 % eficiencia, 20 % sueño reparador (profundo + REM) y 10 % regularidad.",
        heroIcon = Icons.Outlined.Bedtime, heroColor = PulseColors.sleep,
        heroFraction = perf?.let { (it / 100).toFloat() }, heroValue = PulseData.pct(perf), heroValueSize = 40, heroLabel = "RENDIMIENTO\nDEL SUEÑO",
        notice = if (perf == null) "Duerme esta noche con la pulsera y mañana verás qué tal has descansado." to 1 else null,
        rows = listOf(
            MetricRow(Icons.Outlined.Timer, "HORAS VS. NECESARIAS", vsNeed(d?.totalSleepMin), "necesitas ${h(needMin)} h"),
            MetricRow(Icons.Outlined.CalendarMonth, "REGULARIDAD", regularity?.let { "${it.roundToInt()}%" } ?: "--", if (regularityLocked) "en calibración" else "media ${regularityAvg?.let { "${it.roundToInt()}%" } ?: "--"}"),
            MetricRow(Icons.Outlined.Speed, "EFICIENCIA", PulseData.pct(d?.efficiency).replace("--%", "--"), "media ${PulseData.pct(avg30(days) { it.efficiency }).replace("--%", "--")}"),
            MetricRow(Icons.Outlined.Psychology, "DEUDA DE SUEÑO", debtByDay[PulseData.dayKey(0)]?.let { h(it.takeIf { v -> v > 0 } ?: 0.0).replace("-:--", "0:00") } ?: "--", "acumulada"),
        ),
        rowHeight = 48.dp,
        coachLine = if (perf == null) "Mañana te cuento qué tal has dormido y cómo mejorar." else "Has dormido ${h(d?.totalSleepMin)} h. ¿Te doy consejos para esta noche?",
        onBack = onBack, onCoach = onCoach,
        middle = {
            Text("Tu última noche", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 36.dp))
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = PulseColors.text, fontWeight = FontWeight.SemiBold)); append("Hoy"); pop()
                    append(" frente a tus últimos 30 días")
                },
                style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 16.dp),
            )
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NightCard("Horas dormidas", h(d?.totalSleepMin), avg30(days) { it.totalSleepMin }?.let { h(it) }, "Tiempo total dormido, sin contar los ratos despierto en la cama.")
                NightCard("Horas vs. necesarias", vsNeed(d?.totalSleepMin), avg30(days) { it.totalSleepMin }?.let { vsNeed(it) }, "Lo que has dormido frente a lo que necesitas (${h(needMin)} h). Puedes ajustarlo diciéndoselo al coach.")
                NightCard(
                    "Regularidad", regularity?.let { "${it.roundToInt()}%" } ?: "--%", regularityAvg?.let { "${it.roundToInt()}%" },
                    "Lo parecidas que son tus horas de sueño de una noche a otra en las últimas 4 semanas (100 % = siempre lo mismo).",
                    locked = if (regularityLocked) "Duerme con la pulsera ${(3 - nights).coerceAtLeast(1)} noches más para desbloquearla." to nights else null,
                )
                NightCard("Eficiencia", PulseData.pct(d?.efficiency), avg30(days) { it.efficiency }?.let { PulseData.pct(it) }, "Porcentaje del tiempo en la cama que pasaste dormido. Por encima del 85 % es buena.")
            }
        },
        charts = listOf(
            TrendChart("Rendimiento del sueño", PulseColors.sleep, 100.0, week(days) { PulseData.sleepPct(it) }, { "${it.roundToInt()}%" }),
            TrendChart("Horas vs. necesarias", PulseColors.sleep, needMin * 1.25, week(days) { it.totalSleepMin?.takeIf { m -> m > 0 } }, { PulseData.hoursMinutes(it) },
                legend = listOf(LegendItem("DORMIDAS", PulseColors.sleep, round = true), LegendItem("NECESARIAS", PulseColors.textSecondary, round = true))),
            TrendChart("Sueño reparador (horas)", PulseColors.sleepDeep, 300.0, week(days) { ((it.deepMin ?: 0.0) + (it.remMin ?: 0.0)).takeIf { v -> v > 0 } }, { PulseData.hoursMinutes(it) },
                legend = listOf(LegendItem("PROFUNDO", PulseColors.sleepDeep), LegendItem("REM", PulseColors.accent))),
            TrendChart("Regularidad", PulseColors.sleep, 100.0, (-6..0).map { regularityAt(PulseData.dayKey(it)) }, { "${it.roundToInt()}%" },
                locked = if (regularityLocked) "Duerme con la pulsera ${(3 - nights).coerceAtLeast(1)} noches más para desbloquearla." else null),
            TrendChart("Tiempo en la cama", PulseColors.sleep, 660.0, week(days) { inBed(it) }, { PulseData.hoursMinutes(it) }),
            TrendChart("Eficiencia", PulseColors.sleep, 100.0, week(days) { it.efficiency }, { "${it.roundToInt()}%" }),
            TrendChart("Deuda de sueño", PulseColors.stressHigh, 180.0, (-6..0).map { debtByDay[PulseData.dayKey(it)] }, { PulseData.hoursMinutes(it) }),
        ),
    )
}
