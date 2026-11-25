package com.noop.pulse

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.noop.data.DailyMetric
import com.noop.data.WorkoutRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ------------------------------------------------------------------ rings

/** Large Home ring: thick arc, big number with a small "%" suffix. */
@Composable
fun HomeRing(fraction: Float?, color: Color, value: String, suffix: String?, label: String, size: Dp = 100.dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val anim by androidx.compose.animation.core.animateFloatAsState(fraction ?: 0f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "ring")
    Column(modifier.pulseClick(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val sw = 8.dp.toPx()
                val inset = sw / 2
                val arc = Size(this.size.width - sw, this.size.height - sw)
                drawArc(PulseColors.track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                if (fraction != null && anim > 0f) drawArc(color, -90f, 360f * anim.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(buildAnnotatedString {
                append(value)
                if (suffix != null && value != "--") withStyle(SpanStyle(fontSize = 16.sp)) { append(suffix) }
            }, style = PulseText.metric.copy(fontSize = 30.sp, lineHeight = 30.sp), color = PulseColors.text)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = PulseText.overline.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text)
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.text, modifier = Modifier.size(14.dp))
        }
    }
}

/** Compact ring + label for the sticky bar that appears once the big rings scroll away. */
@Composable
fun MiniRing(fraction: Float?, color: Color, label: String, onClick: () -> Unit) {
    Row(Modifier.pulseClick(onClick), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(22.dp)) {
            val sw = 3.dp.toPx(); val arc = Size(size.width - sw, size.height - sw)
            drawArc(PulseColors.track, 0f, 360f, false, Offset(sw / 2, sw / 2), arc, style = Stroke(sw))
            if (fraction != null && fraction > 0f) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(sw / 2, sw / 2), arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = PulseText.overline, color = PulseColors.text)
    }
}

// ------------------------------------------------------------------ insights

data class Insight(val key: String, val title: String, val body: String, val action: (() -> Unit)? = null)

object PulseInsightsRead {
    private fun prefs(c: Context) = c.getSharedPreferences("pulse_insights", Context.MODE_PRIVATE)
    fun isRead(c: Context, day: String, key: String) = prefs(c).getBoolean("$day/$key", false)
    fun markRead(c: Context, day: String, key: String) = prefs(c).edit().putBoolean("$day/$key", true).apply()
}

/** Build today's insights from Pulse's own numbers (never a fabricated value). */
fun buildInsights(
    days: List<DailyMetric>, metric: DailyMetric?, rec: Double?, nights: Int,
    target: com.noop.pulse.engine.PulsePlanEngine.Target?, debtMin: Double, illness: com.noop.analytics.IllnessSignalEngine.Result?,
    beginnerSteps: List<Insight>,
): List<Insight> {
    val out = mutableListOf<Insight>()
    out += beginnerSteps
    if (illness != null && (illness.level == com.noop.analytics.IllnessSignalEngine.Level.MILD || illness.level == com.noop.analytics.IllnessSignalEngine.Level.RAISED))
        out += Insight("illness", "Tu cuerpo está más estresado de lo normal", "Varias señales de anoche se salen de tu rango a la vez. Puede deberse a muchas cosas; prioriza el descanso y mira el aviso en Salud.")
    if (nights < PulseData.recoverySeedNights) {
        val left = PulseData.recoverySeedNights - nights
        out += Insight("calib", if (left == 1) "¡Ya casi está!" else "Calibrando tu recuperación",
            if (left == 1) "Solo te queda una noche para tener tu primera recuperación. Vuelve mañana al despertar."
            else "Duerme con la pulsera $left noches más y tendrás tu primera recuperación personal.")
    }
    if (rec != null && target != null) {
        val t = targetText(target)
        out += when (target.zone) {
            "green" -> Insight("rec", "Día para rendir", "Tu recuperación está en verde: aprovéchala buscando un esfuerzo de $t. Tu cuerpo está preparado para una carga alta hoy.")
            "yellow" -> Insight("rec", "Mantén el ritmo", "Recuperación en amarillo: un esfuerzo de $t mantiene tu forma sin sumar fatiga de más.")
            else -> Insight("rec", "Día para recuperar", "Tu recuperación está en rojo. Quédate en un esfuerzo de $t: paseo, movilidad o técnica, y prioriza dormir bien esta noche.")
        }
    }
    val last30 = days.filter { it.day != metric?.day }.takeLast(30)
    fun mean(f: (DailyMetric) -> Double?) = last30.mapNotNull(f).takeIf { it.size >= 7 }?.average()
    val rhr = metric?.restingHr?.toDouble(); val rhrMean = mean { it.restingHr?.toDouble() }
    if (rhr != null && rhrMean != null && rhrMean > 0) {
        val ch = (rhr - rhrMean) / rhrMean
        if (ch <= -0.05) out += Insight("rhr", "Pulso en reposo más bajo", "Tu pulso en reposo está un ${(abs(ch) * 100).roundToInt()} % por debajo de lo habitual, señal de buena forma y de un corazón más eficiente. Sigue así.")
        if (ch >= 0.05) out += Insight("rhr", "Pulso en reposo más alto", "Tu pulso en reposo está un ${(ch * 100).roundToInt()} % por encima de lo habitual. Suele pasar con poco sueño, alcohol, calor, estrés o al empezar a ponerse malo.")
    }
    val hrv = metric?.avgHrv; val hrvMean = mean { it.avgHrv }
    if (hrv != null && hrvMean != null && hrvMean > 0) {
        val ch = (hrv - hrvMean) / hrvMean
        if (ch >= 0.10) out += Insight("hrv", "VFC por encima de tu media", "Tu VFC está un ${(ch * 100).roundToInt()} % por encima de lo habitual: tu sistema nervioso está descansado.")
        if (ch <= -0.10) out += Insight("hrv", "VFC por debajo de tu media", "Tu VFC está un ${(abs(ch) * 100).roundToInt()} % por debajo de lo habitual: tu cuerpo está gestionando estrés o fatiga.")
    }
    if (debtMin >= 60) out += Insight("debt", "Deuda de sueño", "Llevas ${(debtMin / 60).toInt()} h ${"%02d".format((debtMin % 60).roundToInt())} min de deuda de sueño. Adelantar un poco la hora de acostarte unos días te ayuda a recuperarla.")
    return out
}

/** Stacked insight card: one insight, a ✓ + count badge to mark it read, a peek of the next one. */
@Composable
fun InsightStack(items: List<Insight>, day: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") tick
    val pending = items.filter { !PulseInsightsRead.isRead(ctx, day, it.key) }
    val cur = pending.firstOrNull() ?: return
    var expanded by remember(cur.key) { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        if (pending.size > 1) Box(Modifier.align(Alignment.BottomCenter).offset(y = 8.dp).padding(horizontal = 16.dp).fillMaxWidth().height(24.dp)
            .clip(RoundedCornerShape(bottomStart = PulseMetrics.cardRadius, bottomEnd = PulseMetrics.cardRadius)).background(PulseColors.raised))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay)
            .pulseClick { cur.action?.invoke() ?: run { expanded = !expanded } }.padding(start = 20.dp, top = 20.dp, bottom = 16.dp, end = 12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(cur.title, style = PulseText.cardTitleLg, color = PulseColors.text)
                    Spacer(Modifier.height(4.dp))
                    Text(cur.body, style = PulseText.bodyLg, color = PulseColors.textSecondary, maxLines = if (expanded) 20 else 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.clip(RoundedCornerShape(12.dp)).background(PulseColors.divider).pulseClick { PulseInsightsRead.markRead(ctx, day, cur.key); tick++ }
                    .padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Check, "Leído", tint = PulseColors.text, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("${pending.size}", style = PulseText.cardTitle, color = PulseColors.textSecondary)
                }
            }
            if (cur.action == null && cur.body.length > 140) Icon(Icons.Outlined.ExpandMore, "Ver más", tint = PulseColors.text,
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp).size(24.dp))
        }
    }
}

// ------------------------------------------------------------------ monitors

data class MonitorStatus(val calibrating: Boolean, val inRange: Int, val total: Int)

fun healthMonitorStatus(days: List<DailyMetric>, metric: DailyMetric?, nights: Int): MonitorStatus {
    data class V(val pick: (DailyMetric) -> Double?, val minHalf: Double)
    val vs = listOf(V({ it.respRateBpm }, 0.6), V({ it.restingHr?.toDouble() }, 3.0), V({ it.avgHrv }, 6.0), V({ it.skinTempDevC?.takeIf { v -> abs(v) < 5 } }, 0.4))
    if (nights < PulseData.calibrationNights || metric == null) return MonitorStatus(true, 0, vs.size)
    val hist = days.filter { it.day != metric.day }.takeLast(30)
    var ok = 0; var n = 0
    vs.forEach { v ->
        val today = v.pick(metric) ?: return@forEach
        val h = hist.mapNotNull(v.pick); if (h.size < 5) return@forEach
        n++
        val m = h.average(); val sd = sqrt(h.sumOf { (it - m) * (it - m) } / h.size); val half = maxOf(1.5 * sd, v.minHalf)
        if (today in (m - half)..(m + half)) ok++
    }
    return if (n == 0) MonitorStatus(true, 0, vs.size) else MonitorStatus(false, ok, n)
}

@Composable
fun MonitorTile(title: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).pulseClick(onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun HealthMonitorBody(s: MonitorStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when { s.calibrating -> PulseColors.textSecondary; s.inRange == s.total -> PulseColors.statusHigh; else -> PulseColors.statusMid }
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            when {
                s.calibrating -> Box(Modifier.size(10.dp).clip(CircleShape).background(PulseColors.textSecondary))
                s.inRange == s.total -> Icon(Icons.Outlined.Check, null, tint = color, modifier = Modifier.size(20.dp))
                else -> Icon(Icons.Outlined.WarningAmber, null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            if (s.calibrating) Text("Calibrando", style = PulseText.bodyLg, color = PulseColors.text)
            else {
                Text(if (s.inRange == s.total) "EN RANGO" else "FUERA DE RANGO", style = PulseText.overline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
                Text("${s.inRange}/${s.total} métricas", style = PulseText.body, color = PulseColors.textSecondary)
            }
        }
    }
}

@Composable
fun StressMonitorBody(level: Double?, time: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color = when { level == null -> PulseColors.textSecondary; level < 1 -> PulseColors.stressLow; level < 2 -> PulseColors.stressMid; else -> PulseColors.stressHigh }
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(PulseData.oneDecimal(level), style = PulseText.bigNumber, color = color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(PulseData.stressWord(level), style = PulseText.overline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
            Text(time ?: "Sin lecturas", style = PulseText.body, color = PulseColors.textSecondary)
        }
    }
}

// ------------------------------------------------------------------ my day

@Composable
fun CoachOutlookRow(onCoach: () -> Unit, onOutlook: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).pulseClick(onCoach), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp).border(2.dp, PulseColors.accent, CircleShape), contentAlignment = Alignment.Center) {
                com.noop.ui.PulseMark(size = 16.dp)
            }
        }
        Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).pulseClick(onOutlook).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WbSunny, null, tint = PulseColors.textSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Tu resumen del día", style = PulseText.cardTitleLg, color = PulseColors.text, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(24.dp))
        }
    }
}

data class SleepItem(val startTs: Long, val endTs: Long)

private fun clock(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalTime().let { "%d:%02d".format(it.hour, it.minute) }
private fun hmm(sec: Long) = "%d:%02d".format(sec / 3600, (sec % 3600) / 60)

@Composable
private fun ActivityRow(color: Color, icon: ImageVector, value: String, name: String, start: String, end: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(PulseColors.divider).pulseClick(onClick), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(color), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PulseColors.text, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(value, style = PulseText.bigNumber.copy(fontSize = 22.sp), color = PulseColors.text)
        }
        Spacer(Modifier.width(12.dp))
        Text(name.uppercase(Locale("es")), style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text, maxLines = 2, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
            Text(start, style = PulseText.body, color = PulseColors.textSecondary)
            Text(end, style = PulseText.body, color = PulseColors.textSecondary)
        }
    }
}

@Composable
fun TodayActivitiesCard(sleep: SleepItem?, workouts: List<WorkoutRow>, onSleep: () -> Unit, onWorkout: () -> Unit, onAdd: () -> Unit, onStart: () -> Unit, onExpand: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIVIDADES DE HOY", style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.OpenInFull, "Ver todas", tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp).pulseClick(onExpand))
        }
        sleep?.let { s ->
            ActivityRow(PulseColors.sleep.copy(alpha = 0.55f), Icons.Outlined.Bedtime, hmm(s.endTs - s.startTs), "Sueño", clock(s.startTs), clock(s.endTs), onSleep)
        }
        workouts.forEach { w ->
            val name = if (w.sport == PulseAutoActivity.SPORT || w.sport == "Workout") "Actividad" else w.sport
            val icon = when { name.contains("Fuerza", true) || name.contains("Strength", true) -> Icons.Outlined.FitnessCenter; name.contains("Camin", true) -> Icons.Outlined.DirectionsWalk; else -> Icons.AutoMirrored.Outlined.DirectionsRun }
            ActivityRow(PulseColors.load, icon, PulseData.effort21(DailyMetricLike(w))?.let { String.format(Locale.US, "%.1f", it) } ?: "--", name, clock(w.startTs), clock(w.endTs), onWorkout)
        }
        if (sleep == null && workouts.isEmpty()) Text("Aquí aparecerán tu sueño y tus actividades de hoy, también las que Pulse detecta solo.", style = PulseText.body, color = PulseColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple(Icons.Outlined.Add, "AÑADIR\nACTIVIDAD", onAdd), Triple(Icons.Outlined.Timer, "INICIAR\nACTIVIDAD", onStart)).forEach { (ic, label, go) ->
                Row(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(PulseColors.divider).pulseClick(go).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, null, tint = PulseColors.text, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = PulseText.overline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, lineHeight = 15.sp), color = PulseColors.text)
                }
            }
        }
    }
}

/** A workout's stored Effort read through the same display path as a day (calibration aware). */
private fun DailyMetricLike(w: WorkoutRow) = DailyMetric(deviceId = w.deviceId, day = PulseActivity.dayOf(w), strain = w.strain)

@Composable
fun TonightSleepCard(bedMin: Int, wakeMin: Int, alarmOn: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).pulseClick(onClick).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SUEÑO DE ESTA NOCHE", style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bedtime, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text("%d:%02d".format(bedMin / 60, bedMin % 60), style = PulseText.metric.copy(fontSize = 30.sp, lineHeight = 30.sp), color = PulseColors.text)
                }
                Text("HORA RECOMENDADA\nPARA ACOSTARTE", style = PulseText.overline.copy(lineHeight = 15.sp), color = PulseColors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
            Text("- - -", style = PulseText.small, color = PulseColors.textTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text("%d:%02d".format(wakeMin / 60, wakeMin % 60), style = PulseText.metric.copy(fontSize = 30.sp, lineHeight = 30.sp), color = PulseColors.text)
                }
                Text(if (alarmOn) "DESPERTAR\n(ALARMA ACTIVADA)" else "DESPERTAR\n(ALARMA APAGADA)", style = PulseText.overline.copy(lineHeight = 15.sp), color = PulseColors.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun MyPlanCard(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).pulseClick(onClick).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Tu mejor versión", style = PulseText.cardTitleLg, color = PulseColors.text)
            Text("Marca objetivos, sigue tu progreso y convierte pequeños hábitos en resultados a largo plazo.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            Text("VER PLANES →", style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.accent, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(64.dp).border(2.dp, PulseColors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.TrackChanges, null, tint = PulseColors.accent, modifier = Modifier.size(30.dp))
        }
    }
}

// ------------------------------------------------------------------ dashboard

data class DashMetric(val key: String, val label: String, val icon: ImageVector, val value: String, val baseline: String?, val trendUp: Boolean?, val onClick: () -> Unit, val goodUp: Boolean = true)

object PulseDashboardPrefs {
    val all = listOf("hrv" to "Variabilidad (VFC)", "rhr" to "Pulso en reposo", "steps" to "Pasos", "kcal" to "Calorías", "hours" to "Horas de sueño", "restor" to "Sueño reparador",
        "eff" to "Eficiencia del sueño", "z13" to "Zonas 1–3 (semana)", "z45" to "Zonas 4–5 (semana)", "vo2" to "VO₂ máx.", "resp" to "Respiración")
    private fun prefs(c: Context) = c.getSharedPreferences("pulse_dashboard", Context.MODE_PRIVATE)
    fun shown(c: Context): Set<String> = prefs(c).getStringSet("shown", null) ?: all.map { it.first }.toSet()
    fun set(c: Context, keys: Set<String>) = prefs(c).edit().putStringSet("shown", keys).apply()
}

@Composable
fun DashboardHeader(onCustomize: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Mi panel", style = PulseText.headline, color = PulseColors.text, modifier = Modifier.weight(1f))
        Row(Modifier.pulseClick(onCustomize).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PERSONALIZAR", style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.textSecondary)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Edit, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DashRow(m: DashMetric) {
    Row(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).pulseClick(m.onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(m.icon, null, tint = PulseColors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(m.label.uppercase(Locale("es")), style = PulseText.overlineLg.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text, maxLines = 2, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Top) {
                Text(m.value, style = PulseText.metric.copy(fontSize = 30.sp, lineHeight = 32.sp), color = PulseColors.text)
                m.trendUp?.let { Text(if (it) "▴" else "▾", style = PulseText.small.copy(fontSize = 16.sp), color = if (it == m.goodUp) PulseColors.statusHigh else PulseColors.stressHigh, modifier = Modifier.padding(start = 4.dp)) }
            }
            m.baseline?.let { Text(it, style = PulseText.body, color = PulseColors.textSecondary) }
        }
    }
}

@Composable
fun CustomizeDashboardDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(PulseDashboardPrefs.shown(ctx)) }
    Dialog(onDismissRequest = { PulseDashboardPrefs.set(ctx, shown); onDismiss() }) {
        Column(Modifier.clip(RoundedCornerShape(PulseMetrics.sheetRadius)).background(PulseColors.overlay).padding(20.dp)) {
            Text("Personalizar panel", style = PulseText.cardTitleLg, color = PulseColors.text)
            PulseDashboardPrefs.all.forEach { (k, label) ->
                Row(Modifier.fillMaxWidth().pulseClick { shown = if (k in shown) shown - k else shown + k }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(k in shown, { shown = if (it) shown + k else shown - k }, colors = CheckboxDefaults.colors(checkedColor = PulseColors.accent, checkmarkColor = PulseColors.onAccent, uncheckedColor = PulseColors.textSecondary))
                    Text(label, style = PulseText.bodyLg, color = PulseColors.text)
                }
            }
            Text("LISTO", style = PulseText.overlineLg, color = PulseColors.accent, modifier = Modifier.align(Alignment.End).padding(top = 12.dp).pulseClick { PulseDashboardPrefs.set(ctx, shown); onDismiss() })
        }
    }
}

/** Icons used by the dashboard rows. */
object DashIcons {
    val hrv = Icons.Outlined.ShowChart
    val rhr = Icons.Outlined.FavoriteBorder
    val steps = Icons.Outlined.DirectionsWalk
    val zones = Icons.Outlined.MonitorHeart
    val vo2 = Icons.Outlined.Speed
    val kcal = Icons.Outlined.LocalFireDepartment
    val sleep = Icons.Outlined.Bedtime
    val resp = Icons.Outlined.Air
}

@Suppress("unused") private val unusedDate = LocalDate.MIN
