package com.noop.pulse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.CircadianEngine
import com.noop.analytics.IllnessSignalEngine
import com.noop.analytics.TrainingLoadEngine
import com.noop.data.DailyMetric
import com.noop.pulse.engine.PulseRhythmEngine
import com.noop.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Salud insight cards built from community / published methods:
 *  - Aviso de salud: noop's IllnessSignalEngine (multi-signal, personal-baseline z-scores) — reports the
 *    state, never a cause.
 *  - Carga de entrenamiento: noop's TrainingLoadEngine (Banister fitness–fatigue, 42 d / 7 d EWMA) plus the
 *    acute:chronic ratio bands of Gabbett, Br J Sports Med 2016.
 *  - Ritmo diario: noop's body-clock phase (CircadianEngine) plus Pulse's own PulseRhythmEngine (SRI,
 *    social jetlag / chronotype, IS / IV / RA).
 */

private val cardPad = Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)

@Composable
private fun StatLine(label: String, value: String, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Overline(label, color = PulseColors.textSecondary)
            note?.let { Text(it, style = PulseText.body, color = PulseColors.textSecondary) }
        }
        Spacer(Modifier.width(12.dp))
        Text(value, style = PulseText.ringNumberLg, color = PulseColors.text)
    }
}

// ---------------- Aviso de salud ----------------

@Composable
fun HealthAlertCard(vm: AppViewModel) {
    val signals by vm.v5Signals.collectAsStateWithLifecycle()
    val s = signals ?: return
    val ill = s.illness
    PulseCard(cardPad) {
        CardHeader("Aviso de salud", showChevron = false)
        Spacer(Modifier.height(8.dp))
        val what = ill.firedSignals.map {
            when {
                it.contains("RHR", true) -> "pulso en reposo más alto"
                it.contains("temp", true) -> "temperatura de la piel más alta"
                it.contains("HRV", true) -> "VFC más baja"
                it.contains("resp", true) -> "respiración más rápida"
                else -> it
            }
        }
        val text = when {
            !s.baselineTrusted -> "Aún estoy aprendiendo tu línea base. Cuando la tenga, te avisaré si varias señales se salen a la vez de lo normal para ti."
            ill.level == IllnessSignalEngine.Level.QUIET -> "Todo dentro de tu rango habitual."
            ill.level == IllnessSignalEngine.Level.SUPPRESSED -> "Algún dato se sale de lo normal, pero lo que registraste en el diario (alcohol, entreno duro, viaje…) lo explica."
            ill.level == IllnessSignalEngine.Level.ALREADY_UNWELL -> "Anotaste que no te encuentras bien. Descansa: tus datos lo acompañan."
            else -> "Tu cuerpo está más estresado de lo normal para ti: ${what.joinToString(", ").ifEmpty { "varias señales a la vez" }}. " +
                "Puede deberse a muchas cosas (una infección que empieza, alcohol, una cena tarde, calor, estrés…); los datos no dicen cuál. Vigila cómo te encuentras y prioriza el descanso."
        }
        Text(text, style = PulseText.body, color = PulseColors.text)
        if (ill.level == IllnessSignalEngine.Level.MILD || ill.level == IllnessSignalEngine.Level.RAISED) {
            Text("Estimación en el móvil, no es un diagnóstico.", style = PulseText.body, color = PulseColors.textSecondary,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------- Carga de entrenamiento ----------------

@Composable
fun TrainingLoadCardPulse(days: List<DailyMetric>) {
    val result = remember(days) { TrainingLoadEngine.evaluate(days.map { TrainingLoadEngine.DailyLoad(it.day, it.strain) }) }
    PulseCard(cardPad) {
        CardHeader("Carga de entrenamiento", showChevron = false)
        if (!result.isAvailable) {
            Spacer(Modifier.height(8.dp))
            Text("Necesito ${TrainingLoadEngine.standard.minimumDays} días seguidos con esfuerzo registrado (llevo ${result.contiguousDays}).",
                style = PulseText.body, color = PulseColors.textSecondary)
            return@PulseCard
        }
        fun e21(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it * 21 / 100) } ?: "--"
        StatLine("FORMA (42 DÍAS)", e21(result.ctl), "Tu carga media de las últimas semanas")
        StatLine("FATIGA (7 DÍAS)", e21(result.atl), "Tu carga de esta semana")
        val ratio = if ((result.ctl ?: 0.0) > 0.5) result.atl!! / result.ctl!! else null
        val (ratioText, advice) = when {
            ratio == null -> "--" to "Aún no hay carga suficiente para comparar."
            ratio < 0.8 -> String.format(Locale.US, "%.2f", ratio) to "Por debajo de tu carga habitual: margen para apretar un poco."
            ratio <= 1.3 -> String.format(Locale.US, "%.2f", ratio) to "En tu zona habitual (0,8–1,3): progresión sostenible."
            ratio <= 1.5 -> String.format(Locale.US, "%.2f", ratio) to "Pico de carga: vigila el descanso unos días."
            else -> String.format(Locale.US, "%.2f", ratio) to "Muy por encima de lo habitual: es cuando más lesiones se ven. Afloja."
        }
        StatLine("RELACIÓN FATIGA / FORMA", ratioText, advice)
        if (result.state == TrainingLoadEngine.State.BUILDING) {
            Text("Se afina a partir de ${TrainingLoadEngine.standard.establishedDays} días (llevas ${result.contiguousDays}).",
                style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// ---------------- Ritmo diario ----------------

private data class RhythmData(
    val sri: Pair<Double, Int>?,
    val jetlag: PulseRhythmEngine.Jetlag?,
    val rhythm: PulseRhythmEngine.Rhythm?,
)

private fun clock(h: Double): String {
    val x = ((h % 24) + 24) % 24
    return "%d:%02d".format(x.toInt(), ((x - x.toInt()) * 60).roundToInt().coerceAtMost(59))
}

@Composable
fun RhythmCard(vm: AppViewModel, days: List<DailyMetric>) {
    val signals by vm.v5Signals.collectAsStateWithLifecycle()
    var data by remember { mutableStateOf<RhythmData?>(null) }
    LaunchedEffect(days.size) {
        data = withContext(Dispatchers.Default) {
            runCatching {
                val zone = ZoneId.systemDefault()
                val now = System.currentTimeMillis() / 1000
                val sleeps = vm.repo.sleepSessionsUnion(vm.activeStrapId, now - 22 * 86_400L, now)
                    .map { PulseRhythmEngine.Sleep(it.effectiveStartTs, it.endTs) }
                val sri = PulseRhythmEngine.sri(sleeps, zone, LocalDate.now(zone).minusDays(1))
                val jet = PulseRhythmEngine.socialJetlag(sleeps.filter { it.endTs >= now - 28 * 86_400L }, zone)
                // Hourly mean heart rate over the last 14 whole days (activity proxy, as noop's body clock uses).
                val end = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
                val start = end - 14 * 86_400L
                val slots = arrayOfNulls<Double>(14 * 24)
                vm.repo.hrBucketsUnion(vm.activeStrapId, start, end, 3600L).forEach { b ->
                    val i = ((b.bucket - start) / 3600L).toInt()
                    if (i in slots.indices) slots[i] = b.avgBpm
                }
                val firstHour = Instant.ofEpochSecond(start).atZone(zone).hour
                val hourly = slots.toList()
                RhythmData(sri, jet, PulseRhythmEngine.rhythm(hourly, firstHour))
            }.getOrNull()
        }
    }
    PulseCard(cardPad) {
        CardHeader("Tu ritmo diario", showChevron = false)
        val d = data
        val body = signals?.bodyClock?.takeIf { it.confidence != CircadianEngine.PhaseConfidence.UNREADABLE }
        if (d == null || (d.sri == null && d.jetlag == null && d.rhythm == null && body == null)) {
            Spacer(Modifier.height(8.dp))
            Text("Necesito al menos una semana de noches y días con la pulsera para calcularlo.", style = PulseText.body, color = PulseColors.textSecondary)
            return@PulseCard
        }
        d.sri?.let { (v, n) ->
            val note = when { v >= 80 -> "Muy regular"; v >= 60 -> "Bastante regular"; v >= 40 -> "Irregular"; else -> "Muy irregular" }
            StatLine("REGULARIDAD DEL SUEÑO", "${v.roundToInt()}", "$note · $n días (índice de Phillips, −100 a 100)")
        }
        d.jetlag?.let { j ->
            val h = abs(j.socialJetlagH)
            val note = if (h < 0.5) "Mismo horario entre semana y en fin de semana"
            else "El fin de semana duermes ${if (j.socialJetlagH > 0) "más tarde" else "antes"}. Por encima de 1 h se nota como un pequeño jet lag."
            StatLine("JET LAG SOCIAL", "%d:%02d h".format(h.toInt(), ((h - h.toInt()) * 60).roundToInt().coerceAtMost(59)), note)
            val type = when (PulseRhythmEngine.chronotype(j.msfscHour)) {
                PulseRhythmEngine.Chronotype.EARLY -> "Madrugador"
                PulseRhythmEngine.Chronotype.INTERMEDIATE -> "Intermedio"
                PulseRhythmEngine.Chronotype.LATE -> "Trasnochador"
            }
            StatLine("CRONOTIPO", type, "Mitad de tu sueño libre: ${clock(j.msfscHour)} (corregido por el sueño recuperado)")
        }
        body?.let { b ->
            val type = when (CircadianEngine.chronotype(b)) {
                CircadianEngine.Chronotype.MORNING -> "matutino"
                CircadianEngine.Chronotype.EVENING -> "vespertino"
                CircadianEngine.Chronotype.INTERMEDIATE, null -> "intermedio"
            }
            StatLine("RELOJ BIOLÓGICO", clock(b.tempMinHour),
                "Punto más bajo de tu ritmo (cuerpo $type)${if (b.confidence == CircadianEngine.PhaseConfidence.WIDE) " · estimación amplia" else ""}")
        }
        d.rhythm?.let { r ->
            StatLine("ESTABILIDAD DÍA A DÍA", String.format(Locale.US, "%.2f", r.interdailyStability),
                if (r.interdailyStability >= 0.6) "Tus días se parecen entre sí" else "Tus días cambian bastante de uno a otro")
            StatLine("FRAGMENTACIÓN", String.format(Locale.US, "%.2f", r.intradailyVariability),
                if (r.intradailyVariability <= 0.8) "Actividad y descanso en bloques claros" else "Muchos cambios de actividad a lo largo del día")
            StatLine("AMPLITUD", String.format(Locale.US, "%.2f", r.relativeAmplitude),
                "Diferencia entre tus 10 h más activas (desde las ${r.m10StartHour}:00) y tus 5 h más tranquilas (desde las ${r.l5StartHour}:00)")
        }
    }
}

// ---------------- Parecido a tu WHOOP ----------------

/** Personal calibration towards the user's own WHOOP export (see PulseCalibrationEngine). */
@Composable
fun CalibrationCard(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val st by com.noop.pulse.engine.PulseCalibration.state.collectAsStateWithLifecycle()
    PulseCard(cardPad) {
        CardHeader("Ajustar a mi WHOOP", showChevron = false)
        Spacer(Modifier.height(8.dp))
        if (!st.anyFit) {
            Text(
                "Importa tu exportación de WHOOP (Ajustes → Importar datos) con días en los que también llevaste la pulsera con Pulse. " +
                    "Con ${com.noop.pulse.engine.PulseCalibrationEngine.MIN_PAIRS} días o más en común, Pulse ajusta su escala a la de tu WHOOP" +
                    (if (st.pairedDays > 0) " (ahora hay ${st.pairedDays})." else "."),
                style = PulseText.body, color = PulseColors.textSecondary,
            )
            return@PulseCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usar tu escala de WHOOP en los días que mide Pulse", style = PulseText.body, color = PulseColors.text, modifier = Modifier.weight(1f))
            PulseSwitch(st.enabled) { com.noop.pulse.engine.PulseCalibration.setEnabled(ctx, it) }
        }
        listOf(Triple("ESFUERZO", st.effort, true), Triple("RECUPERACIÓN", st.recovery, false), Triple("SUEÑO", st.sleep, false)).forEach { (name, f, dec) ->
            val v = if (f == null) "--" else if (dec) String.format(Locale.US, "±%.1f → ±%.1f", f.maeBefore, f.maeAfter)
                else "±${f.maeBefore.roundToInt()} → ±${f.maeAfter.roundToInt()}"
            val note = when {
                f == null -> "Faltan días en común"
                f.helps -> "Diferencia media con tu WHOOP antes → después (${f.n} días, comprobado con días que el ajuste no vio)"
                else -> "El ajuste no mejora con tus datos: se queda como NOOP"
            }
            StatLine(name, v, note)
        }
        Text("Los días importados se muestran tal cual vienen de WHOOP. Los datos guardados no cambian: solo cómo se muestran.",
            style = PulseText.body, color = PulseColors.textTertiary, modifier = Modifier.padding(top = 12.dp))
    }
}
