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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to PulseColors.divider, 0.4f to PulseColors.overlay, 1f to PulseColors.base))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
                Text("MONITOR DE SALUD", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            // Live pulse with a grid background and the trace on the right.
            Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(210.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val step = 14.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(PulseColors.divider, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                    var y = 0f
                    while (y < size.height) { drawLine(PulseColors.divider, Offset(0f, y), Offset(size.width, y), 1f); y += step }
                    drawRect(Brush.horizontalGradient(0f to Color(0xF21B1F23), 0.45f to Color(0x661B1F23), 1f to Color(0x1A1B1F23)))
                    if (trace.size >= 2) {
                        val left = size.width - 300.dp.toPx() - 20.dp.toPx()
                        val w = 300.dp.toPx()
                        val lo = (trace.min() - 5).toFloat(); val hi = (trace.max() + 5).toFloat()
                        val p = Path()
                        trace.forEachIndexed { i, v ->
                            val px = left + w * i / (trace.size - 1)
                            val py = size.height - (v - lo) / (hi - lo) * size.height * 0.8f - size.height * 0.1f
                            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                        }
                        drawPath(p, PulseColors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        val lastY = size.height - (trace.last() - lo) / (hi - lo) * size.height * 0.8f - size.height * 0.1f
                        drawCircle(PulseColors.accent, 4.dp.toPx(), Offset(left + w, lastY))
                    }
                }
                Column(Modifier.padding(start = 20.dp, top = 10.dp)) {
                    Text("PULSO EN DIRECTO", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
                    Spacer(Modifier.height(10.dp))
                    Icon(Icons.Outlined.Favorite, null, tint = PulseColors.recoveryRed, modifier = Modifier.size(22.dp, 20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(hr?.toString() ?: "--", style = PulseText.bigNumber.copy(fontSize = 34.sp, lineHeight = 34.sp), color = PulseColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text("LPM", style = PulseText.overline, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            !live.connected -> "Pulsera desconectada"
                            zone == null -> "Esperando el pulso…"
                            else -> "Zona $zone · ${zoneWord[zone]}"
                        },
                        style = PulseText.body, color = PulseColors.textQuiet,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(5) { i -> Box(Modifier.width(20.dp).height(2.dp).background(if (zone != null && i < zone) PulseColors.accent else PulseColors.hairlineStrong)) }
                    }
                }
            }
            if (nights < PulseData.calibrationNights) {
                Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.divider).padding(16.dp)) {
                    Text("Duerme con la pulsera ${PulseData.calibrationNights - nights} noches más para calibrar tus rangos.", style = PulseText.body, color = PulseColors.text)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(7) { i -> Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i < nights) PulseColors.accent else PulseColors.textTertiary)) }
                    }
                }
            }
            Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vitals.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { v ->
                            val history = days.takeLast(31).filter { it.day != d?.day }.mapNotNull(v.pick)
                            val value = d?.let(v.pick)
                            val status = VitalRange.status(history, value, nights, v.minHalf)
                            VitalCard(v, value, status, Modifier.weight(1f)) { info = v }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(110.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 18.dp).size(66.dp).shadow(18.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp)).background(Color(0xF0282E34)).border(1.dp, PulseColors.hairline, RoundedCornerShape(24.dp)).pulseClick(onCoach),
            contentAlignment = Alignment.Center,
        ) { CoachOrb(diameter = 40.dp) }
        info?.let { v ->
            val history = days.takeLast(31).filter { it.day != d?.day }.mapNotNull(v.pick)
            val status = VitalRange.status(history, d?.let(v.pick), nights, v.minHalf)
            VitalInfoSheet(v, status, history) { info = null }
        }
    }
}

@Composable
private fun VitalCard(v: Vital, value: Double?, status: VitalStatus, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(132.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.divider).pulseClick(onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(v.icon, null, tint = PulseColors.textSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(v.title, style = PulseText.overline.copy(fontSize = 9.5.sp, lineHeight = 13.sp), color = PulseColors.text)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value?.let(v.format) ?: "--", style = PulseText.bigNumber.copy(fontSize = 20.sp), color = PulseColors.text)
                if (value != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(v.unit, style = PulseText.chartAxis.copy(fontSize = 10.sp), color = PulseColors.textSecondary, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(PulseColors.hairline).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(status.color))
            Spacer(Modifier.width(6.dp))
            Text(status.label, style = PulseText.body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
        }
    }
}

@Composable
private fun VitalInfoSheet(v: Vital, status: VitalStatus, history: List<Double>, onClose: () -> Unit) {
    var how by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0x8C080A0C)).pulseClick(onClose)) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp).fillMaxWidth().shadow(30.dp, RoundedCornerShape(PulseMetrics.cardRadius)).clip(RoundedCornerShape(PulseMetrics.cardRadius))
                .background(Brush.linearGradient(listOf(PulseColors.hairlineStrong, PulseColors.divider, PulseColors.overlay))).pulseClick { }
                .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 28.dp),
        ) {
            Row {
                Icon(v.icon, null, tint = PulseColors.textQuiet, modifier = Modifier.size(30.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).pulseClick(onClose), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(16.dp)) }
            }
            Spacer(Modifier.height(14.dp))
            Text("${v.title.substringBefore(" (")}\n${status.label.uppercase()}", style = PulseText.overline.copy(fontSize = 11.5.sp, lineHeight = 17.sp), color = PulseColors.text)
            Spacer(Modifier.height(16.dp))
            Text(v.info, style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = PulseColors.text)
            Spacer(Modifier.height(14.dp))
            Text(
                if (status == VitalStatus.Calibrating) "Tras 7 noches con la pulsera, Pulse conocerá tu rango habitual y cada mañana te dirá si estás por debajo, dentro o por encima de él."
                else history.takeIf { it.isNotEmpty() }?.let { "Tu media de los últimos 30 días es ${v.format(it.average())} ${v.unit}." } ?: "",
                style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = PulseColors.text,
            )
            if (how) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Pulse calcula la media y la variación de tus últimas 30 noches. Tu rango es la media ± 1,5 veces esa variación; un valor fuera de él se marca por encima o por debajo.",
                    style = PulseText.body, color = PulseColors.textSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Pulse no es un dispositivo médico. Si tienes dudas sobre tu salud, consulta con un profesional sanitario.", style = PulseText.body.copy(fontSize = 11.sp, lineHeight = 16.sp), color = PulseColors.textSecondary)
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, PulseColors.textTertiary, RoundedCornerShape(10.dp)).pulseClick { how = !how },
                contentAlignment = Alignment.Center,
            ) { Text("CÓMO LO CALCULA PULSE", style = PulseText.overline, color = PulseColors.text) }
        }
    }
}
