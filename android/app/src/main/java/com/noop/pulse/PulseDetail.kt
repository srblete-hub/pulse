package com.noop.pulse

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.PulseAgeEngine
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One "today vs your 30-day average" row of a metric detail page. */
data class DetailRow(val label: String, val value: String, val base: String)

/** One weekly mini bar chart on a detail page. */
data class WeekChart(val title: String, val color: Color, val max: Double, val values: List<Double?>, val format: (Double) -> String)

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, info: Pair<String, String>?, content: @Composable () -> Unit) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        PulseTopBar(title, onBack, trailing = info?.let {
            {
                Box(
                    Modifier.size(22.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(11.dp)).pulseClick { showInfo = true },
                    contentAlignment = Alignment.Center,
                ) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
            }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
    if (showInfo && info != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = PulseColors.overlay,
            title = { Text(info.first, style = PulseText.headline.copy(fontSize = 20.sp), color = PulseColors.text) },
            text = { Text(info.second, style = PulseText.bodyLg, color = PulseColors.textSecondary) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("ENTENDIDO", style = PulseText.overline, color = PulseColors.accent) } },
        )
    }
}

@Composable
private fun MetricDetail(
    title: String,
    onBack: () -> Unit,
    info: Pair<String, String>,
    ringFraction: Float?,
    ringColor: Color,
    ringValue: String,
    ringLabel: List<String>,
    caption: String,
    rows: List<DetailRow>,
    charts: List<WeekChart>,
    extra: (@Composable () -> Unit)? = null,
) {
    DetailScaffold(title, onBack, info) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ScoreRing(ringFraction, ringColor, ringValue, size = 170.dp, stroke = 9.dp, textStyle = PulseText.heroNumber)
            Spacer(Modifier.height(14.dp))
            ringLabel.forEach { Text(it, style = PulseText.overlineLg, color = PulseColors.text) }
            Spacer(Modifier.height(12.dp))
            Text(caption, style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
        }
        SectionTitle("Tu última noche".takeIf { title == "Sueño" } ?: "Hoy")
        Text("frente a tus últimos 30 días", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
        Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.label.uppercase(), style = PulseText.overlineLg, color = PulseColors.text, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(r.value, style = PulseText.bigNumber, color = PulseColors.text)
                        Text(r.base, style = PulseText.chartAxis, color = PulseColors.textSecondary)
                    }
                }
            }
        }
        extra?.invoke()
        SectionTitle("Tendencias de la semana")
        Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            charts.forEach { WeekBars(it) }
        }
    }
}

@Composable
fun WeekBars(chart: WeekChart) {
    val keys = remember { (-6..0).map { PulseData.dayKey(it) } }
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(chart.title, Modifier.weight(1f), large = true)
            val last = chart.values.lastOrNull { it != null }
            Text(last?.let(chart.format) ?: "SIN DATOS", style = PulseText.overline, color = PulseColors.textSecondary)
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 14.dp)) {
            val slot = size.width / 7
            val w = slot * 0.46f
            chart.values.forEachIndexed { i, v ->
                val cx = slot * i + slot / 2
                drawRoundRect(PulseColors.inset, Offset(cx - w / 2, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
                if (v != null && v > 0) {
                    val h = (v / chart.max).toFloat().coerceIn(0.02f, 1f) * size.height
                    drawRoundRect(chart.color, Offset(cx - w / 2, size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            keys.forEachIndexed { i, k ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i == 6) "Hoy" else PulseData.shortWeekday(k), style = PulseText.chartAxis, color = if (i == 6) PulseColors.text else PulseColors.textSecondary)
                    Text(PulseData.dayOfMonth(k), style = PulseText.chartAxis, color = PulseColors.textSecondary)
                }
            }
        }
    }
}

private fun weekValues(days: List<DailyMetric>, f: (DailyMetric) -> Double?): List<Double?> {
    val byDay = days.associateBy { it.day }
    return (-6..0).map { byDay[PulseData.dayKey(it)]?.let(f) }
}

private fun avg30(days: List<DailyMetric>, f: (DailyMetric) -> Double?): Double? =
    days.takeLast(30).mapNotNull(f).takeIf { it.isNotEmpty() }?.average()

@Composable
fun PulseSleepScreen(vm: AppViewModel, onBack: () -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val d = PulseData.metricFor(days, today, 0)
    val perf = PulseData.sleepPct(d)
    val nights = PulseData.nightsWithSleep(days)
    fun h(v: Double?) = if (v == null) "-:--" else PulseData.hoursMinutes(v)
    MetricDetail(
        title = "Sueño", onBack = onBack,
        info = "Rendimiento del sueño" to "Una sola nota que resume tu noche: cuánto has dormido frente a lo que necesitabas, lo regulares que son tus horarios y lo bien aprovechado que ha estado el tiempo en la cama.",
        ringFraction = perf?.let { (it / 100).toFloat() }, ringColor = PulseColors.sleep, ringValue = PulseData.pct(perf),
        ringLabel = listOf("RENDIMIENTO", "DEL SUEÑO"),
        caption = if (perf == null) "Duerme esta noche con la pulsera y mañana verás qué tal has descansado." else "Has dormido ${h(d?.totalSleepMin)} h con una eficiencia del ${PulseData.pct(d?.efficiency)}.",
        rows = listOf(
            DetailRow("Horas dormidas", h(d?.totalSleepMin), "media ${h(avg30(days) { it.totalSleepMin })}"),
            DetailRow("Eficiencia", PulseData.pct(d?.efficiency), "media ${PulseData.pct(avg30(days) { it.efficiency })}"),
            DetailRow("Sueño profundo", h(d?.deepMin), "media ${h(avg30(days) { it.deepMin })}"),
            DetailRow("Sueño REM", h(d?.remMin), "media ${h(avg30(days) { it.remMin })}"),
            DetailRow("Despertares", d?.disturbances?.toString() ?: "--", "media ${avg30(days) { it.disturbances?.toDouble() }?.roundToInt() ?: "--"}"),
        ),
        charts = listOf(
            WeekChart("Rendimiento del sueño", PulseColors.sleep, 100.0, weekValues(days) { PulseData.sleepPct(it) }) { "${it.roundToInt()}%" },
            WeekChart("Horas dormidas", PulseColors.sleep, 600.0, weekValues(days) { it.totalSleepMin }) { PulseData.hoursMinutes(it) },
            WeekChart("Sueño reparador (horas)", PulseColors.accent, 300.0, weekValues(days) { ((it.deepMin ?: 0.0) + (it.remMin ?: 0.0)).takeIf { v -> v > 0 } }) { PulseData.hoursMinutes(it) },
            WeekChart("Eficiencia", PulseColors.sleep, 100.0, weekValues(days) { it.efficiency }) { "${it.roundToInt()}%" },
        ),
        extra = if (nights < PulseData.recoverySeedNights) {
            { Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 12.dp)) { UnlockPanel("Duerme con la pulsera ${PulseData.recoverySeedNights - nights} noches para desbloquear la regularidad.", nights, PulseData.recoverySeedNights) } }
        } else null,
    )
}

@Composable
fun PulseRecoveryScreen(vm: AppViewModel, onBack: () -> Unit, onHabits: () -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val d = PulseData.metricFor(days, today, 0)
    val rec = d?.recovery
    val nights = PulseData.nightsWithSleep(days)
    MetricDetail(
        title = "Recuperación", onBack = onBack,
        info = "Recuperación" to "Te dice cada mañana cómo de preparado está tu cuerpo, a partir de tu VFC, tu pulso en reposo, tu respiración y cómo has dormido. Se calcula en el teléfono con tu propia línea base.",
        ringFraction = rec?.let { (it / 100).toFloat() }, ringColor = PulseColors.recovery(rec), ringValue = PulseData.pct(rec),
        ringLabel = listOf("RECUPERACIÓN"),
        caption = if (rec == null) "Duerme con la pulsera ${(PulseData.recoverySeedNights - nights).coerceAtLeast(1)} noches para calibrar del todo tu recuperación." else "Recuperación ${PulseData.recoveryWord(rec).lowercase()} hoy.",
        rows = listOf(
            DetailRow("VFC", d?.avgHrv?.let { "${it.roundToInt()} ms" } ?: "--", "media ${avg30(days) { it.avgHrv }?.roundToInt()?.let { "$it ms" } ?: "--"}"),
            DetailRow("Pulso en reposo", d?.restingHr?.let { "$it lpm" } ?: "--", "media ${avg30(days) { it.restingHr?.toDouble() }?.roundToInt()?.let { "$it lpm" } ?: "--"}"),
            DetailRow("Frecuencia respiratoria", d?.respRateBpm?.let { String.format(Locale.US, "%.1f rpm", it) } ?: "--", "media ${avg30(days) { it.respRateBpm }?.let { String.format(Locale.US, "%.1f", it) } ?: "--"}"),
            DetailRow("Rendimiento del sueño", PulseData.pct(PulseData.sleepPct(d)), "media ${PulseData.pct(avg30(days) { PulseData.sleepPct(it) })}"),
        ),
        charts = listOf(
            WeekChart("Recuperación", PulseColors.recoveryGreen, 100.0, weekValues(days) { it.recovery }) { "${it.roundToInt()}%" },
            WeekChart("VFC", PulseColors.accent, (avg30(days) { it.avgHrv } ?: 60.0) * 1.8, weekValues(days) { it.avgHrv }) { "${it.roundToInt()} ms" },
            WeekChart("Pulso en reposo", PulseColors.recoveryRed, 100.0, weekValues(days) { it.restingHr?.toDouble() }) { "${it.roundToInt()} lpm" },
        ),
        extra = {
            Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
                AccentCard(onClick = onHabits) {
                    Text("Descubre qué te ayuda a recuperarte", style = PulseText.cardTitle, color = PulseColors.text)
                    Spacer(Modifier.height(6.dp))
                    Text("Apunta tus hábitos en el diario y verás cuáles suben o bajan tu recuperación.", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    CtaLink("Ver hábitos que influyen", onClick = onHabits)
                }
            }
        },
    )
}

@Composable
fun PulseEffortScreen(vm: AppViewModel, onBack: () -> Unit, onStartActivity: () -> Unit) {
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val d = PulseData.metricFor(days, today, 0)
    val eff = PulseData.effort21(d)
    MetricDetail(
        title = "Esfuerzo", onBack = onBack,
        info = "Esfuerzo del día" to "Mide la carga que ha soportado tu corazón a lo largo del día, entrenando y en tu vida normal. Cuanto más tiempo pasas con el pulso alto, más sube. Escala de 0 a 21.",
        ringFraction = eff?.let { (it / 21).toFloat() }, ringColor = PulseColors.strain, ringValue = PulseData.oneDecimal(eff ?: 0.0),
        ringLabel = listOf("ESFUERZO"),
        caption = "Mueve el cuerpo: cada minuto con el pulso alto suma a tu esfuerzo del día.",
        rows = listOf(
            DetailRow("Esfuerzo", PulseData.oneDecimal(eff), "media ${PulseData.oneDecimal(avg30(days) { PulseData.effort21(it) })}"),
            DetailRow("Actividades", d?.exerciseCount?.toString() ?: "0", "media ${avg30(days) { it.exerciseCount?.toDouble() }?.let { String.format(Locale.US, "%.1f", it) } ?: "--"}"),
            DetailRow("Pasos", PulseData.int(d?.steps), "media ${PulseData.int(avg30(days) { it.steps?.toDouble() }?.roundToInt())}"),
            DetailRow("Calorías", d?.activeKcalEst?.let { PulseData.int(it.roundToInt()) } ?: "--", "media ${PulseData.int(avg30(days) { it.activeKcalEst }?.roundToInt())}"),
        ),
        charts = listOf(
            WeekChart("Esfuerzo", PulseColors.strain, 21.0, weekValues(days) { PulseData.effort21(it) }) { String.format(Locale.US, "%.1f", it) },
            WeekChart("Pasos", PulseColors.strain, 15000.0, weekValues(days) { it.steps?.toDouble() }) { PulseData.int(it.roundToInt()) },
            WeekChart("Calorías", PulseColors.stressHigh, 3500.0, weekValues(days) { it.activeKcalEst }) { PulseData.int(it.roundToInt()) },
        ),
        extra = {
            Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
                AccentCard(onClick = onStartActivity) {
                    Text("¿Vas a entrenar?", style = PulseText.cardTitle, color = PulseColors.text)
                    Spacer(Modifier.height(6.dp))
                    Text("Inicia una actividad para ver tu pulso en directo y registrar el esfuerzo.", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    CtaLink("Iniciar actividad", onClick = onStartActivity)
                }
            }
        },
    )
}

@Composable
fun PulseAgeScreen(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    var vo2 by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull() }
    val have = days.size
    val result = remember(days, vo2) { PulseData.pulseAge(context, days, vo2) }
    val previous = remember(days, vo2) { if (days.size > 14) PulseData.pulseAge(context, days.dropLast(7), vo2) else null }
    DetailScaffold(
        "Edad fisiológica", onBack,
        "Edad fisiológica" to "Partimos de tu edad real y sumamos o restamos años según nueve hábitos: horas y regularidad del sueño, pasos, minutos en zonas de pulso, fuerza, VO₂ máx., pulso en reposo y masa magra. Usa guías públicas (OMS 2020) y estudios publicados; no es un dispositivo médico.",
    ) {
        if (have < PulseData.pulseAgeDays || result == null) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 30.dp, end = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DashedProgressRing(have.coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 120.dp)
                Spacer(Modifier.height(20.dp))
                Text("Faltan ${PulseData.pulseAgeDays - have} días para calcular tu edad fisiológica.", style = PulseText.bodyLg, color = PulseColors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Lleva la pulsera día y noche. Necesitamos 3 semanas de datos para que el cálculo sea fiable.", style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            AgeResult(result, previous, onCoach)
        }
    }
}

@Composable
private fun AgeResult(result: PulseAgeEngine.Result, previous: PulseAgeEngine.Result?, onCoach: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.US, "%.1f", result.pulseAge), style = PulseText.heroNumber.copy(fontSize = 56.sp), color = PulseColors.text)
            Overline("Edad fisiológica", large = true)
            Spacer(Modifier.height(8.dp))
            val y = abs(result.yearsYounger)
            val word = if (result.yearsYounger >= 0) "menos" else "más"
            Text(String.format(Locale("es"), "%.1f años %s que tu edad", y, word), style = PulseText.body, color = if (result.yearsYounger >= 0) PulseColors.recoveryGreen else PulseColors.stressHigh)
        }
        val pace = previous?.let { PulseAgeEngine.paceOfAgeing(it, result, 7) }
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 20.dp)) {
            CardHeader("Ritmo de envejecimiento", showChevron = false)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pace?.let { String.format(Locale("es"), "%.1f×", it.coerceIn(-1.0, 3.0)) } ?: "--", style = PulseText.ringNumberLg, color = PulseColors.text)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        pace == null -> "Necesitamos dos semanas para comparar."
                        pace < 1.0 -> "Envejeces más lento que el calendario."
                        else -> "Envejeces al ritmo del calendario o más rápido."
                    },
                    style = PulseText.body, color = PulseColors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                drawRoundRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(PulseColors.recoveryGreen, PulseColors.recoveryYellow, PulseColors.recoveryRed)), cornerRadius = CornerRadius(4.dp.toPx()))
                pace?.let {
                    val x = ((it.coerceIn(-1.0, 3.0) + 1.0) / 4.0).toFloat() * size.width
                    drawCircle(PulseColors.text, 6.dp.toPx(), Offset(x, size.height / 2))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("−1,0×", "1,0×", "3,0×").forEach { Text(it, style = PulseText.chartAxis, color = PulseColors.textSecondary) }
            }
        }
        listOf("SUEÑO", "ACTIVIDAD", "FORMA FÍSICA").forEach { group ->
            PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 12.dp)) {
                CardHeader(group, showChevron = false)
                result.factors.filter { PulseData.factorGroup(it.key) == group }.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(PulseData.factorLabel(f.key), style = PulseText.cardTitle, color = PulseColors.text)
                            Text(PulseData.factorValue(f), style = PulseText.body, color = PulseColors.textSecondary)
                        }
                        val yrs = f.years
                        Text(
                            yrs?.let { String.format(Locale("es"), "%+.1f años", it) } ?: "—",
                            style = PulseText.small,
                            color = when {
                                yrs == null -> PulseColors.textTertiary
                                yrs <= 0 -> PulseColors.recoveryGreen
                                else -> PulseColors.stressHigh
                            },
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
            AccentCard(onClick = onCoach) {
                Text("Pide un plan al coach", style = PulseText.cardTitle, color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text("Te propone pasos concretos para mejorar las métricas que más te restan.", style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                CtaLink("Crear plan", onClick = onCoach)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Pulse calcula tu edad fisiológica con tus propios datos y guías públicas; no es un dispositivo médico.",
                style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
}

/** Calibration progress: one row per night of the first week. */
@Composable
fun PulseCalibrationScreen(vm: AppViewModel, onBack: () -> Unit) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val nights = PulseData.nightsWithSleep(days)
    DetailScaffold("Calibración", onBack, "Calibración" to "Pulse aprende cómo eres tú: tu VFC, tu pulso en reposo y tu respiración normales. Con 4 noches ya calcula tu recuperación; con 7 tu línea base queda completa.") {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DashedProgressRing(nights.coerceAtMost(7), 7, 140.dp)
            Spacer(Modifier.height(16.dp))
            Text("Duerme con la pulsera cada noche y vuelve aquí para ver tu línea base personal.", style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
        }
        Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                1 to "Primer sueño y primer esfuerzo",
                4 to "Primera recuperación",
                7 to "Línea base completa y monitor de salud",
                21 to "Edad fisiológica",
            ).forEach { (n, label) ->
                val done = nights >= n
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (done) PulseColors.accent else PulseColors.hairlineStrong),
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", style = PulseText.overline, color = if (done) PulseColors.onAccent else PulseColors.text) }
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = PulseText.cardTitle, color = PulseColors.text, modifier = Modifier.weight(1f))
                    Text(if (done) "LISTO" else "NOCHE $n", style = PulseText.overline, color = if (done) PulseColors.accent else PulseColors.textTertiary)
                }
            }
        }
    }
}

/** Simple informational page (charging tips, goals, help). */
@Composable
fun PulseInfoScreen(title: String, heading: String, paragraphs: List<String>, onBack: () -> Unit, cta: Pair<String, () -> Unit>? = null) {
    DetailScaffold(title, onBack, null) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(heading, style = PulseText.headline, color = PulseColors.text)
            paragraphs.forEach {
                Spacer(Modifier.height(14.dp))
                Text(it, style = PulseText.bodyLg, color = PulseColors.textSecondary)
            }
            if (cta != null) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(PulseColors.accent).pulseClick(cta.second),
                    contentAlignment = Alignment.Center,
                ) { Text(cta.first.uppercase(), style = PulseText.overlineLg, color = PulseColors.onAccent) }
            }
        }
    }
}
