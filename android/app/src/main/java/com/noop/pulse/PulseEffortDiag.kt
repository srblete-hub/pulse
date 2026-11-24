package com.noop.pulse

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.analytics.StrainScorer
import com.noop.ui.AppViewModel
import com.noop.ui.NoopPrefs
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How noop scored one calendar day's Effort, recomputed from the same heart-rate samples with noop's own
 * [StrainScorer], so the page can show where a low number comes from: missing data, the max heart rate
 * used, or how few minutes reached the zones.
 */
data class EffortDiag(
    val day: String,
    val samples: Int,
    /** Hours the samples cover (each reading credited up to 2 min, as noop does). */
    val coveredHours: Double,
    val restingHr: Double,
    val maxHr: Double,
    val maxHrSource: String,
    /** Highest heart rate held (99.5th percentile) that day. */
    val peakHr: Double?,
    /** Minutes below zone 1, then zones 1–5 (Edwards zones on % of heart-rate reserve). */
    val zoneMin: List<Double>,
    /** Heart rate (bpm) where each zone starts: 50, 60, 70, 80, 90 % of reserve. */
    val zoneStartBpm: List<Int>,
    /** Effort 0–21 recomputed now, and the stored value the rings show. */
    val effort21: Double?,
    val storedEffort21: Double?,
) {
    val zones13: Double get() = zoneMin.getOrElse(1) { 0.0 } + zoneMin.getOrElse(2) { 0.0 } + zoneMin.getOrElse(3) { 0.0 }
    val zones45: Double get() = zoneMin.getOrElse(4) { 0.0 } + zoneMin.getOrElse(5) { 0.0 }
}

object EffortDiagnostics {
    suspend fun forDay(context: Context, vm: AppViewModel, day: LocalDate): EffortDiag = withContext(Dispatchers.Default) {
        val zone = ZoneId.systemDefault()
        val from = day.atStartOfDay(zone).toEpochSecond()
        val to = minOf(day.plusDays(1).atStartOfDay(zone).toEpochSecond(), System.currentTimeMillis() / 1000)
        val hr = runCatching { vm.repo.hrSamplesUnion(vm.activeStrapId, from, to, 400_000) }.getOrDefault(emptyList()).sortedBy { it.ts }
        val profile = ProfileStore.from(context)
        val row = vm.recentDays.value.lastOrNull { it.day == day.toString() }
        val rest = row?.restingHr?.toDouble() ?: StrainScorer.defaultRestingHR
        val maxHr = profile.hrMax.toDouble()
        val source = if (profile.hrMaxOverride > 0) "tu valor" else "fórmula por edad (208 − 0,7 × edad)"
        val durations = StrainScorer.sampleDurationsMinutes(hr)
        val reserve = maxHr - rest
        val zones = if (hr.isEmpty() || reserve <= 0) List(6) { 0.0 } else StrainScorer.zoneMinutes(hr, rest, reserve, durations)
        val method = runCatching { NoopPrefs.effortMethod(context) }.getOrDefault(StrainScorer.Method.EDWARDS)
        val effort = if (hr.isEmpty()) null else StrainScorer.strain(hr, maxHr, rest, method, profile.sex)?.let { it * 21.0 / 100.0 }
        val peak = if (hr.size >= 60) hr.map { it.bpm.toDouble() }.sorted().let { s -> s[((s.size - 1) * 0.995).toInt()] } else null
        EffortDiag(
            day = day.toString(),
            samples = hr.size,
            coveredHours = durations.sum() / 60.0,
            restingHr = rest,
            maxHr = maxHr,
            maxHrSource = source,
            peakHr = peak,
            zoneMin = zones,
            zoneStartBpm = listOf(50, 60, 70, 80, 90).map { (rest + reserve * it / 100.0).roundToInt() },
            effort21 = effort,
            storedEffort21 = row?.strain?.let { it * 21.0 / 100.0 },
        )
    }
}

/** "Cómo se ha calculado": the numbers behind a day's Effort, in plain language. */
@Composable
fun EffortDiagCard(d: EffortDiag?, dayLabel: String, onToggleDay: () -> Unit) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius))
            .background(PulseColors.raised).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CÓMO SE HA CALCULADO", style = PulseText.overlineLg, color = PulseColors.text, modifier = Modifier.weight(1f))
            Text(dayLabel, style = PulseText.overline, color = PulseColors.accent, modifier = Modifier.pulseClick(onToggleDay))
        }
        if (d == null) {
            Text("Calculando…", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            return@Column
        }
        Spacer(Modifier.height(8.dp))
        val warning = when {
            d.samples == 0 -> "No hay pulso registrado ese día. Si llevabas la pulsera, aún no se ha descargado: abre Pulse con la pulsera cerca para que sincronice."
            d.coveredHours < 12 && LocalDate.parse(d.day).isBefore(LocalDate.now()) ->
                "Solo hay ${String.format(Locale("es"), "%.1f", d.coveredHours)} h de pulso ese día. Falta parte del día (pulsera sin sincronizar, quitada o sin batería), y lo que falta no suma esfuerzo."
            d.peakHr != null && d.peakHr > d.maxHr -> "Tu pulso llegó a ${d.peakHr.roundToInt()} lpm, por encima del máximo usado (${d.maxHr.roundToInt()}). Pon tu máximo real en tu perfil para que las zonas sean correctas."
            else -> null
        }
        warning?.let {
            Text(it, style = PulseText.body, color = PulseColors.stressHigh)
            Spacer(Modifier.height(8.dp))
        }
        DiagRow("Pulso registrado", "${String.format(Locale("es"), "%.1f", d.coveredHours)} h (${d.samples} lecturas)")
        DiagRow("Pulso en reposo usado", "${d.restingHr.roundToInt()} lpm")
        DiagRow("Pulso máximo usado", "${d.maxHr.roundToInt()} lpm · ${d.maxHrSource}")
        d.peakHr?.let { DiagRow("Pulso más alto mantenido", "${it.roundToInt()} lpm") }
        Spacer(Modifier.height(8.dp))
        Text("MINUTOS POR ZONA", style = PulseText.overline, color = PulseColors.textSecondary)
        val labels = listOf("Por debajo de zona 1", "Zona 1", "Zona 2", "Zona 3", "Zona 4", "Zona 5")
        val colors = listOf(PulseColors.textTertiary, PulseColors.textSecondary, PulseColors.stressLow, PulseColors.recoveryGreen, PulseColors.stressHigh, PulseColors.recoveryRed)
        d.zoneMin.forEachIndexed { i, m ->
            val from = if (i == 0) "< ${d.zoneStartBpm[0]} lpm" else "desde ${d.zoneStartBpm[i - 1]} lpm"
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(colors[i]))
                Spacer(Modifier.width(8.dp))
                Text("${labels[i]} ($from)", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                Text(PulseActivity.mmss(m), style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = PulseColors.text)
            }
        }
        Spacer(Modifier.height(8.dp))
        DiagRow("Esfuerzo con estos datos", d.effort21?.let { String.format(Locale.US, "%.1f", it) } ?: "--")
        d.storedEffort21?.let { DiagRow("Esfuerzo guardado", String.format(Locale.US, "%.1f", it)) }
        Text(
            "Como en NOOP, el esfuerzo empieza a sumar en zona 1 (50 % de tu reserva de pulso: máximo menos reposo) y cada zona pesa más que la anterior.",
            style = PulseText.body.copy(fontSize = 11.5.sp, lineHeight = 16.sp), color = PulseColors.textTertiary, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = PulseText.body.copy(fontWeight = FontWeight.SemiBold), color = PulseColors.text)
    }
}

/** The day's activities — detected automatically or logged — with duration, heart rate and Effort. */
@Composable
fun ActivitiesCard(
    rows: List<com.noop.data.WorkoutRow>,
    isAuto: (com.noop.data.WorkoutRow) -> Boolean,
    onDelete: (com.noop.data.WorkoutRow) -> Unit,
    onLabel: ((com.noop.data.WorkoutRow, String) -> Unit)? = null,
    /** noop's post-exercise heart-rate recovery for a row (Cole et al., NEJM 1999), loaded lazily. */
    hrrFor: (suspend (com.noop.data.WorkoutRow) -> com.noop.analytics.HeartRateRecovery.Result?)? = null,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var picking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.noop.data.WorkoutRow?>(null) }
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    fun clock(ts: Long) = java.time.Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.noop.data.WorkoutRow?>(null) }
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius))
            .background(PulseColors.raised).padding(16.dp),
    ) {
        Text("ACTIVIDADES", style = PulseText.overlineLg, color = PulseColors.text)
        if (rows.isEmpty()) {
            Text(
                "No se ha detectado ninguna actividad. Pulse las registra solo cuando tu pulso se mantiene alto (unos 30 lpm por encima del reposo) 12 minutos o más.",
                style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 8.dp),
            )
        }
        rows.forEach { w ->
            var hrr by androidx.compose.runtime.remember(w.startTs, w.endTs) { androidx.compose.runtime.mutableStateOf<com.noop.analytics.HeartRateRecovery.Result?>(null) }
            if (hrrFor != null) androidx.compose.runtime.LaunchedEffect(w.startTs, w.endTs) { hrr = runCatching { hrrFor(w) }.getOrNull() }
            val min = ((w.endTs - w.startTs) / 60).toInt()
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).pulseClick { if (onLabel != null) picking = if (picking == w) null else w }) {
                    Text(
                        "${if (w.sport == PulseAutoActivity.SPORT || w.sport == "Workout") "Actividad" else w.sport} · ${clock(w.startTs)}–${clock(w.endTs)}" +
                            (if (onLabel != null) " ›" else ""),
                        style = PulseText.cardTitle, color = PulseColors.text,
                    )
                    Text(
                        listOfNotNull(
                            "${min / 60}:${"%02d".format(min % 60)} h",
                            w.avgHr?.let { "media $it lpm" },
                            w.maxHr?.let { "máx. $it" },
                            if (isAuto(w)) "detectada" else null,
                            PulseSports.guessed(ctx, w)?.takeIf { tick >= 0 && !PulseSports.isConfirmed(ctx, w) && it.first != PulseAutoActivity.SPORT }
                                ?.let { "nombre sugerido (${(it.second * 100).toInt()} %)" },
                            if (isAuto(w) && w.sport == PulseAutoActivity.SPORT && !PulseSports.isConfirmed(ctx, w)) "toca para decir qué fue" else null,
                            hrr?.after1Minute?.let { "pulso tras parar −$it lpm en 1 min" },
                        ).joinToString(" · "),
                        style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary,
                    )
                }
                Text(w.strain?.let { String.format(Locale.US, "%.1f", it * 21.0 / 100.0) } ?: "--", style = PulseText.bigNumber.copy(fontSize = 18.sp), color = PulseColors.strain)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (confirm == w) "¿BORRAR?" else "✕", style = PulseText.overline, color = if (confirm == w) PulseColors.recoveryRed else PulseColors.textTertiary,
                    modifier = Modifier.pulseClick { if (confirm == w) { onDelete(w); confirm = null } else confirm = w },
                )
            }
            if (picking == w && onLabel != null) {
                var custom by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    PulseSports.choices(ctx).forEach { label ->
                        Text(label, style = PulseText.body.copy(fontSize = 13.sp), color = if (label == w.sport) PulseColors.onAccent else PulseColors.text,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius))
                                .background(if (label == w.sport) PulseColors.accent else PulseColors.overlay)
                                .pulseClick { onLabel(w, label); picking = null; tick++ }.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(custom, { custom = it.take(30) }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(PulseMetrics.inputRadius),
                        placeholder = { Text("Otro deporte…", style = PulseText.body, color = PulseColors.textTertiary) },
                        textStyle = PulseText.body.copy(color = PulseColors.text))
                    Spacer(Modifier.width(8.dp))
                    Text("GUARDAR", style = PulseText.overline, color = PulseColors.accent,
                        modifier = Modifier.pulseClick { if (custom.isNotBlank()) { onLabel(w, custom.trim().replaceFirstChar { it.uppercase() }); picking = null; tick++ } })
                }
            }
        }
    }
}
