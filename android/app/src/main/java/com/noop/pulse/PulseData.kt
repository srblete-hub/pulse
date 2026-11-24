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
/** The sleep need the planner, the tonight card, the sleep page and the coach all read. A value the user
 *  told the coach wins; otherwise the computed need ([com.noop.pulse.engine.PulseSleepNeed]). */
object PulseNeed {
    fun hours(c: android.content.Context): Double {
        val mem = PrefsCoachMemory(c)
        return mem.get("sleep_need")?.toDoubleOrNull() ?: mem.get("auto_need")?.toDoubleOrNull() ?: 8.0
    }
    fun save(c: android.content.Context, minutes: Double) = PrefsCoachMemory(c).set("auto_need", String.format(java.util.Locale.US, "%.2f", minutes / 60.0))
}

object PulseData {

    /** Nights needed before the personal baseline (and the Health monitor) is considered calibrated. */
    const val calibrationNights = 7
    /** Nights before recovery scores, mirroring noop's Baselines.minNightsSeed. */
    const val recoverySeedNights = 4
    /** Days of history the physiological age waits for. */
    /** Days of real data (sleep or steps) before the physiological age is shown. */
    const val pulseAgeDays = 10

    /** Days with real strap data — sleep or steps — not just a row. */
    fun dataDays(days: List<com.noop.data.DailyMetric>): Int = days.count { (it.totalSleepMin ?: 0.0) > 0 || (it.steps ?: 0) > 0 }

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
        // noop's Today: offset 0 shows the live `today` analysis first, else the stored row.
        val key = dayKey(offset)
        val stored = days.lastOrNull { it.day == key }
        return if (offset == 0) today ?: stored else stored
    }

    /** Sleep performance 0–100 from noop's rest composite. */
    /** Sleep score 0–100 as noop shows it (resolved `sleep_performance` series). */
    fun sleepPct(d: DailyMetric?): Double? = d?.let { com.noop.pulse.engine.PulseCalibration.sleep(it.day, com.noop.pulse.engine.PulseSleepScore.values.value[it.day]) }

    /** Recovery as displayed (calibrated to the user's WHOOP export when that is switched on). */
    fun rec(d: DailyMetric?): Double? = d?.let { com.noop.pulse.engine.PulseCalibration.recovery(it.day, it.recovery) }

    /** Stored Effort is 0–100; Pulse shows it on the 0–21 scale. */
    fun effort21(d: DailyMetric?): Double? = d?.let { com.noop.pulse.engine.PulseCalibration.effort100(it.day, it.strain) }?.let { it * 21.0 / 100.0 }

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
