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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.CoachOrb
import com.noop.ui.ProfileStore
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ChevronLeft
import kotlin.math.abs
import kotlin.math.sqrt

/** Where tonight's value sits against the user's own range (mean ± 1.5 SD over the last 30 nights). */
enum class VitalStatus(val label: String, val color: Color) {
    Calibrating("Calibrando rango", PulseColors.text),
    Missing("Sin dato", PulseColors.textSecondary),
    Low("Por debajo", PulseColors.stressLow),
    InRange("Dentro de tu rango", PulseColors.recoveryGreen),
    High("Por encima", PulseColors.stressHigh),
}

object VitalRange {
    fun status(history: List<Double>, today: Double?, nights: Int, minHalfWidth: Double): VitalStatus {
        if (nights < PulseData.calibrationNights || history.size < 5) return VitalStatus.Calibrating
        if (today == null) return VitalStatus.Missing
        val mean = history.average()
        val sd = sqrt(history.sumOf { (it - mean) * (it - mean) } / history.size)
        val half = maxOf(1.5 * sd, minHalfWidth)
        return when {
            today < mean - half -> VitalStatus.Low
            today > mean + half -> VitalStatus.High
            else -> VitalStatus.InRange
        }
    }
}

private class Vital(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val unit: String,
    val pick: (DailyMetric) -> Double?,
    val minHalf: Double,
    val format: (Double) -> String,
    val info: String,
)

private val vitals = listOf(
    Vital("resp", "RESPIRACIÓN", Icons.Outlined.Air, "rpm", { it.respRateBpm }, 0.6, { String.format(Locale.US, "%.1f", it) },
        "Son las respiraciones por minuto mientras duermes. Suele ser muy estable de una noche a otra, así que un cambio claro puede ser una señal temprana de que estás incubando algo o de que no te has recuperado bien."),
    Vital("rhr", "PULSO EN REPOSO", Icons.Outlined.FavoriteBorder, "lpm", { it.restingHr?.toDouble() }, 3.0, { "${it.toInt()}" },
        "Es tu pulso más bajo y estable durante el sueño. Baja cuando mejoras tu forma física y sube con el estrés, el alcohol, el calor, la falta de sueño o una infección."),
    Vital("hrv", "VFC", Icons.Outlined.ShowChart, "ms", { it.avgHrv }, 6.0, { "${it.toInt()}" },
        "La variabilidad de la frecuencia cardiaca mide cuánto cambia el tiempo entre latidos. Un valor más alto que tu media indica que tu cuerpo está descansado; uno más bajo, que está gestionando estrés o fatiga."),
    Vital("temp", "TEMPERATURA (VS. TU BASE)", Icons.Outlined.Thermostat, "°C", { it.skinTempDevC?.takeIf { v -> abs(v) < 5 } }, 0.4, { String.format(Locale.US, "%+.1f", it) },
        "Es la diferencia entre la temperatura de tu piel esta noche y tu valor habitual. Sube unas décimas con un resfriado, con el calor o, en mujeres, en la segunda mitad del ciclo."),
)

@Composable
fun PulseMonitorScreen(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit) {
    val context = LocalContext.current
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    val live by vm.live.collectAsStateWithLifecycle()
    val nights = PulseData.nightsWithSleep(days)
    DisposableEffect(Unit) {
        vm.requestRealtimeHr()
        onDispose { vm.releaseRealtimeHr() }
    }
    val trace = remember { mutableStateListOf<Int>() }
    LaunchedEffect(live.heartRate) {
        live.heartRate?.let { trace += it; if (trace.size > 90) trace.removeAt(0) }
    }
    val age = remember { ProfileStore.from(context).age }
    val maxHr = 208 - 0.7 * age
    val hr = live.heartRate
    val zone = hr?.let { h ->
        val f = h / maxHr
        when { f < 0.5 -> 0; f < 0.6 -> 1; f < 0.7 -> 2; f < 0.8 -> 3; f < 0.9 -> 4; else -> 5 }
    }
    val zoneWord = listOf("en reposo", "muy suave", "suave", "moderada", "intensa", "máxima")
    val d = today ?: days.lastOrNull { it.day == PulseData.dayKey(0) }
    var info by remember { mutableStateOf<Vital?>(null) }

    val shown = vitalsFor(days)
    val ctxShare = LocalContext.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF15181C), Color(0xFF1E2327), Color(0xFF2A3036))))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ChevronLeft, "Volver", tint = PulseColors.text, modifier = Modifier.size(32.dp)) }
                Text("MONITOR DE SALUD", style = PulseText.tabTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            // Live heart rate: label, heart, number and zone on the left; grid and blue trace on the right.
            Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(230.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 16.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(Color(0x14FFFFFF), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(Color(0x14FFFFFF), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                    val endX = size.width - 24.dp.toPx()
                    drawLine(PulseColors.textSecondary, Offset(endX, 0f), Offset(endX, size.height), 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    if (trace.size >= 2) {
                        val left = size.width * 0.32f
                        val w = endX - left
                        val lo = (trace.min() - 15).toFloat(); val hi = (trace.max() + 15).toFloat()
                        fun py(v: Int) = size.height * 0.15f + (1f - (v - lo) / (hi - lo)) * size.height * 0.6f
                        val p = Path(); val fill = Path()
                        trace.forEachIndexed { i, v ->
                            val px = left + w * i / (trace.size - 1)
                            if (i == 0) { p.moveTo(px, py(v)); fill.moveTo(px, size.height); fill.lineTo(px, py(v)) } else { p.lineTo(px, py(v)); fill.lineTo(px, py(v)) }
                        }
                        fill.lineTo(endX, size.height); fill.close()
                        drawPath(fill, Brush.verticalGradient(listOf(PulseColors.strain.copy(alpha = 0.35f), Color.Transparent)))
                        drawPath(p, PulseColors.strain, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        drawCircle(Color.White, 5.dp.toPx(), Offset(endX, py(trace.last())))
                    }
                }
                Column(Modifier.padding(start = 24.dp, top = 8.dp)) {
                    Text("PULSO", style = PulseText.overlineLg.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = PulseColors.text)
                    Spacer(Modifier.height(12.dp))
                    Icon(Icons.Outlined.Favorite, null, tint = PulseColors.strain, modifier = Modifier.size(28.dp))
                    Text(hr?.toString() ?: "--", style = PulseText.metric.copy(fontSize = 48.sp, lineHeight = 52.sp), color = PulseColors.text)
                    Text("LPM", style = PulseText.overlineLg.copy(fontWeight = FontWeight.Bold), color = PulseColors.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    Text(when { !live.connected -> "Desconectada"; zone == null -> "Esperando…"; else -> "Zona $zone" }, style = PulseText.bodyLg, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(5) { i -> Box(Modifier.width(24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (zone != null && i < zone) PulseColors.strain else PulseColors.hairlineStrong)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("LECTURAS DE ANOCHE", style = PulseText.overlineLg.copy(fontWeight = FontWeight.Bold), color = PulseColors.text)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(24.dp).clip(CircleShape).border(1.5.dp, PulseColors.textSecondary, CircleShape).pulseClick { info = shown.firstOrNull() }, contentAlignment = Alignment.Center) {
                    Text("?", style = PulseText.small, color = PulseColors.textSecondary)
                }
            }
            if (nights < PulseData.calibrationNights) {
                Text("Duerme con la pulsera ${PulseData.calibrationNights - nights} noches más para calibrar tus rangos.", style = PulseText.body, color = PulseColors.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp))
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { v ->
                            val history = days.takeLast(31).filter { it.day != d?.day }.mapNotNull(v.pick)
                            val value = d?.let(v.pick)
                            val status = VitalRange.status(history, value, nights, v.minHalf)
                            VitalCard(v, value, status, rangeOf(history, v.minHalf), Modifier.weight(1f)) { info = v }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                // Share a plain-text health report (no server: it goes through Android's share sheet).
                Row(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(Color(0xFF2E3338)).pulseClick {
                    val lines = shown.map { v ->
                        val history = days.takeLast(31).filter { it.day != d?.day }.mapNotNull(v.pick)
                        val r = rangeOf(history, v.minHalf)
                        "${v.title.lowercase().replaceFirstChar { it.uppercase() }}: ${d?.let(v.pick)?.let(v.format) ?: "--"} ${v.unit}" + (r?.let { " (tu rango ${v.format(it.first)}–${v.format(it.second)})" } ?: "")
                    }
                    val text = "Informe de salud (Pulse) · ${PulseData.dayKey(0)}\n" + lines.joinToString("\n")
                    ctxShare.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Compartir informe"))
                }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.IosShare, null, tint = PulseColors.textSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("COMPARTIR TU INFORME DE SALUD", style = PulseText.overlineLg.copy(fontWeight = FontWeight.Bold), color = PulseColors.text, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.text, modifier = Modifier.size(24.dp))
                }
                Text("Un informe con tus lecturas y tus rangos para compartir con tu médico, tu entrenador o quien tú quieras.", style = PulseText.body, color = PulseColors.textSecondary)
            }
            Spacer(Modifier.height(112.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 16.dp).size(66.dp).shadow(8.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)).background(PulseColors.overlay.copy(alpha = 0.94f)).border(1.dp, PulseColors.hairline, RoundedCornerShape(24.dp)).pulseClick(onCoach),
            contentAlignment = Alignment.Center,
        ) { CoachOrb(diameter = 40.dp) }
        info?.let { v ->
            val history = days.takeLast(31).filter { it.day != d?.day }.mapNotNull(v.pick)
            val status = VitalRange.status(history, d?.let(v.pick), nights, v.minHalf)
            VitalInfoSheet(v, status, history) { info = null }
        }
    }
}

/** The user's own range for a vital: mean ± 1.5 SD of recent nights (same rule as [VitalRange]). */
private fun rangeOf(history: List<Double>, minHalf: Double): Pair<Double, Double>? {
    if (history.size < 5) return null
    val m = history.average(); val sd = sqrt(history.sumOf { (it - m) * (it - m) } / history.size)
    val half = maxOf(1.5 * sd, minHalf)
    return (m - half) to (m + half)
}

/** Blood oxygen only appears when the strap has actually produced it. */
private fun vitalsFor(days: List<DailyMetric>): List<Vital> {
    val hasSpo2 = days.takeLast(14).any { (it.spo2Pct ?: 0.0) > 80 }
    return if (!hasSpo2) vitals else vitals.take(1) + spo2Vital + vitals.drop(1)
}

private val spo2Vital = Vital("spo2", "OXÍGENO EN SANGRE", Icons.Outlined.WaterDrop, "%", { it.spo2Pct?.takeIf { v -> v > 80 } }, 1.0, { "${it.roundToInt()}" },
    "Es el porcentaje de oxígeno en tu sangre mientras duermes. En personas sanas suele estar entre 95 % y 100 %. Es una estimación de la pulsera, no una medida médica.")

@Composable
private fun VitalCard(v: Vital, value: Double?, status: VitalStatus, range: Pair<Double, Double>?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(150.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(Color(0xFF2E3338)).pulseClick(onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(v.icon, null, tint = PulseColors.textSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(v.title, style = PulseText.overlineLg.copy(fontWeight = FontWeight.Bold, lineHeight = 15.sp), color = PulseColors.textSecondary)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value?.let(v.format) ?: "--", style = PulseText.metric.copy(fontSize = 36.sp, lineHeight = 40.sp), color = PulseColors.text)
                if (value != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(v.unit, style = PulseText.cardTitle, color = PulseColors.text, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        }
        val good = status == VitalStatus.InRange
        val warn = status == VitalStatus.High || status == VitalStatus.Low
        val fg = when { good -> PulseColors.statusHigh; warn -> PulseColors.stressHigh; else -> PulseColors.textSecondary }
        val text = when {
            status == VitalStatus.Calibrating || range == null -> "calibrando"
            status == VitalStatus.Missing -> "sin dato"
            good -> "dentro de ${v.format(range.first)} – ${v.format(range.second)}"
            status == VitalStatus.High -> "elevado > ${v.format(range.second)}"
            else -> "bajo < ${v.format(range.first)}"
        }
        Row(Modifier.clip(RoundedCornerShape(4.dp)).background(fg.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (good) Icon(Icons.Outlined.Check, null, tint = fg, modifier = Modifier.size(14.dp)) else if (warn) Text("!", style = PulseText.small, color = fg)
            Spacer(Modifier.width(4.dp))
            Text(text, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = fg)
        }
    }
}

@Composable
private fun VitalInfoSheet(v: Vital, status: VitalStatus, history: List<Double>, onClose: () -> Unit) {
    var how by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(PulseColors.base.copy(alpha = 0.55f)).pulseClick(onClose)) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(PulseMetrics.cardRadius)).clip(RoundedCornerShape(PulseMetrics.cardRadius))
                .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).pulseClick { }
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp),
        ) {
            Row {
                Icon(v.icon, null, tint = PulseColors.textQuiet, modifier = Modifier.size(30.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).pulseClick(onClose), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            Text("${v.title.substringBefore(" (")}\n${status.label.uppercase()}", style = PulseText.overline.copy(fontSize = 11.5.sp, lineHeight = 17.sp), color = PulseColors.text)
            Spacer(Modifier.height(16.dp))
            Text(v.info, style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = PulseColors.text)
            Spacer(Modifier.height(16.dp))
            Text(
                if (status == VitalStatus.Calibrating) "Tras 7 noches con la pulsera, Pulse conocerá tu rango habitual y cada mañana te dirá si estás por debajo, dentro o por encima de él."
                else history.takeIf { it.isNotEmpty() }?.let { "Tu media de los últimos 30 días es ${v.format(it.average())} ${v.unit}." } ?: "",
                style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = PulseColors.text,
            )
            if (how) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Pulse calcula la media y la variación de tus últimas 30 noches. Tu rango es la media ± 1,5 veces esa variación; un valor fuera de él se marca por encima o por debajo.",
                    style = PulseText.body, color = PulseColors.textSecondary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Pulse no es un dispositivo médico. Si tienes dudas sobre tu salud, consulta con un profesional sanitario.", style = PulseText.body.copy(fontSize = 11.sp, lineHeight = 16.sp), color = PulseColors.textSecondary)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, PulseColors.textTertiary, RoundedCornerShape(10.dp)).pulseClick { how = !how },
                contentAlignment = Alignment.Center,
            ) { Text("CÓMO LO CALCULA PULSE", style = PulseText.overline, color = PulseColors.text) }
        }
    }
}
