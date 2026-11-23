package com.noop.pulse

import android.content.Context
import com.noop.analytics.PulseAgeEngine
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.ui.ProfileStore
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Read-side helpers that turn noop's stored rows into what the Pulse screens show. Pure functions over
 * [DailyMetric]; the scoring itself stays in noop's analytics (recovery, strain, rest composite).
 */
object PulseData {

    /** Nights needed before the personal baseline (and the Health monitor) is considered calibrated. */
    const val calibrationNights = 7
    /** Nights before recovery scores, mirroring noop's Baselines.minNightsSeed. */
    const val recoverySeedNights = 4
    /** Days of history the physiological age waits for. */
    const val pulseAgeDays = 21

    private val es = Locale("es", "ES")

    fun dayKey(offset: Int, today: LocalDate = LocalDate.now()): String = today.plusDays(offset.toLong()).toString()

    fun dayLabel(offset: Int, today: LocalDate = LocalDate.now()): String = when (offset) {
        0 -> "HOY"
        -1 -> "AYER"
        else -> {
            val d = today.plusDays(offset.toLong())
            "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, es).trimEnd('.')} ${d.dayOfMonth}".uppercase(es)
        }
    }

    fun shortWeekday(day: String): String = runCatching {
        LocalDate.parse(day).dayOfWeek.getDisplayName(TextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() }
    }.getOrDefault("")

    fun dayOfMonth(day: String): String = runCatching { LocalDate.parse(day).dayOfMonth.toString() }.getOrDefault("")

    fun metricFor(days: List<DailyMetric>, today: DailyMetric?, offset: Int): DailyMetric? {
        val key = dayKey(offset)
        return days.lastOrNull { it.day == key } ?: if (offset == 0) today else null
    }

    /** Sleep performance 0–100 from noop's rest composite. */
    fun sleepPct(d: DailyMetric?): Double? = d?.let { RestScorer.restFromDaily(it) }

    /** Stored Effort is 0–100; Pulse shows it on the 0–21 scale. */
    fun effort21(d: DailyMetric?): Double? = d?.strain?.let { it * 21.0 / 100.0 }

    fun nightsWithSleep(days: List<DailyMetric>): Int = days.count { (it.totalSleepMin ?: 0.0) > 0.0 }

    fun hoursMinutes(min: Double?): String {
        if (min == null || min <= 0) return "--:--"
        val m = min.roundToInt()
        return "%d:%02d".format(m / 60, m % 60)
    }

    fun pct(v: Double?): String = v?.let { "${it.roundToInt()}%" } ?: "--%"

    fun oneDecimal(v: Double?): String = v?.let { String.format(Locale.US, "%.1f", it) } ?: "--"

    fun int(v: Number?): String = v?.let { "%,d".format(es, it.toLong()) } ?: "--"

    fun recoveryWord(pct: Double?): String = when {
        pct == null -> "SIN DATOS"
        pct >= 67 -> "ALTA"
        pct >= 34 -> "MEDIA"
        else -> "BAJA"
    }

    fun stressWord(level: Double?): String = when {
        level == null -> "--"
        level < 1.0 -> "BAJO"
        level < 2.0 -> "MEDIO"
        else -> "ALTO"
    }

    /**
     * Builds the physiological-age inputs from the last 30 days that noop has: sleep duration, sleep
     * regularity (spread of nightly duration as a proxy until bedtimes are exposed), daily steps and
     * resting HR, plus the VO₂max estimate noop computes. Factors without data stay null and are
     * reported as missing on screen rather than guessed.
     */
    fun pulseAge(context: Context, days: List<DailyMetric>, vo2max: Double?): PulseAgeEngine.Result? {
        val profile = ProfileStore.from(context)
        val recent = days.takeLast(30)
        fun avg(xs: List<Double>) = xs.takeIf { it.isNotEmpty() }?.average()
        val sleepH = avg(recent.mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0 }?.div(60.0) })
        val sleepMins = recent.mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0 } }
        val regularity = if (sleepMins.size >= 7) {
            val mean = sleepMins.average()
            val sd = kotlin.math.sqrt(sleepMins.sumOf { (it - mean) * (it - mean) } / sleepMins.size)
            // 0 min spread → 100 %, 90 min spread → 40 %.
            (100.0 - sd / 90.0 * 60.0).coerceIn(0.0, 100.0)
        } else null
        val steps = avg(recent.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toDouble() })
        val rhr = avg(recent.mapNotNull { it.restingHr?.toDouble() })
        return PulseAgeEngine.compute(
            profile.age.toDouble(),
            PulseAgeEngine.Inputs(
                sleepHours = sleepH,
                sleepRegularityPct = regularity,
                stepsPerDay = steps,
                vo2max = vo2max,
                restingHr = rhr,
                sex = profile.sex,
            ),
        )
    }

    fun factorLabel(key: String): String = when (key) {
        "sleep_hours" -> "Horas de sueño"
        "sleep_regularity" -> "Regularidad del sueño"
        "steps" -> "Pasos diarios"
        "zones_1_3" -> "Tiempo en zonas 1–3"
        "zones_4_5" -> "Tiempo en zonas 4–5"
        "strength" -> "Entrenamiento de fuerza"
        "vo2max" -> "VO₂ máx."
        "resting_hr" -> "Pulso en reposo"
        "lean_mass" -> "Masa magra"
        else -> key
    }

    fun factorValue(f: PulseAgeEngine.Factor): String {
        val v = f.value ?: return "Sin datos"
        return when (f.key) {
            "sleep_hours" -> hoursMinutes(v * 60) + " h"
            "sleep_regularity", "lean_mass" -> "${v.roundToInt()} %"
            "steps" -> int(v.roundToInt())
            "zones_1_3", "zones_4_5", "strength" -> "${v.roundToInt()} min/sem"
            "vo2max" -> String.format(Locale.US, "%.1f", v)
            "resting_hr" -> "${v.roundToInt()} lpm"
            else -> v.toString()
        }
    }

    fun factorGroup(key: String): String = when (key) {
        "sleep_hours", "sleep_regularity" -> "SUEÑO"
        "steps", "zones_1_3", "zones_4_5", "strength" -> "ACTIVIDAD"
        else -> "FORMA FÍSICA"
    }
}
