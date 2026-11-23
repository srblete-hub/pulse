package com.noop.pulse.engine

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The three places where Pulse computes something itself; everything else (sleep detection and stages,
 * personal baselines, resting HR, HRV, recovery, effort, stress, VO₂max, sleep debt) is noop's engine.
 *
 *  1. Calories: noop's heart-rate model plus a step component for minutes where heart rate stays low,
 *     so walking counts.
 *  2. Sleep need: noop's personal base and validated debt, plus an extra for yesterday's effort.
 *  3. Physiological age: nine factors, hazard ratios → years (Gompertz), overlap correction, and the
 *     pace of ageing.
 *
 * Constants marked "starting value" are initial calibration, to be re-fitted on the user's own data.
 */
object PulseSpec {

    data class Profile(val age: Double, val male: Boolean, val kg: Double, val cm: Double)

    fun interp(x: Double, anchors: List<Pair<Double, Double>>): Double {
        if (x <= anchors.first().first) return anchors.first().second
        if (x >= anchors.last().first) return anchors.last().second
        for (i in 1 until anchors.size) {
            val (x1, y1) = anchors[i]
            if (x <= x1) {
                val (x0, y0) = anchors[i - 1]
                return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
            }
        }
        return anchors.last().second
    }

    // ── 1. Calories: step component ────────────────────────────────────────────────────────────

    /** Heart-rate-reserve fraction, 0…1. */
    fun hrr(bpm: Double, rest: Double, maxHr: Double): Double =
        if (maxHr <= rest) 0.0 else ((bpm - rest) / (maxHr - rest)).coerceIn(0.0, 1.0)

    /**
     * noop credits active energy only above resting + 50 % of heart-rate reserve; below that it counts
     * basal only. Pulse adds steps · kg · 0.0005 kcal for those low-heart-rate minutes (≈ 0.04 kcal per
     * step at 80 kg), which is the walking energy a heart-rate-only model misses.
     */
    const val NOOP_ACTIVE_HRR = 0.5
    const val KCAL_PER_STEP_PER_KG = 0.0005

    fun stepKcal(steps: Double, kg: Double): Double = max(0.0, steps) * kg * KCAL_PER_STEP_PER_KG

    // ── 2. Sleep need ──────────────────────────────────────────────────────────────────────────

    /** Extra need after a hard day: max(0, effort − 10) × 5 min (≈ 55 min after a 21). */
    fun effortExtraMin(effortYesterday21: Double?): Double = max(0.0, (effortYesterday21 ?: 0.0) - 10.0) * 5.0

    /** need = personal base + effort extra + carried debt (noop's ledger, naps already credited). */
    fun sleepNeedMin(baseHours: Double, effortYesterday21: Double?, debtMin: Double): Double =
        baseHours * 60.0 + effortExtraMin(effortYesterday21) + max(0.0, debtMin)

    // ── 3. Physiological age ───────────────────────────────────────────────────────────────────

    /** Hazard-ratio curves; orders of magnitude from published meta-analyses (refine against sources). */
    object Hazard {
        /** ≈ 13 % lower risk per 3.5 ml/kg/min (1 MET) above the age/sex reference. */
        fun vo2(vo2: Double, ref: Double) = 0.87.pow((vo2 - ref) / 3.5)
        /** ≈ 9 % higher risk per 10 bpm above 60; no extra credit below 50. */
        fun restingHr(rhr: Double) = 1.09.pow((max(rhr, 50.0) - 60.0) / 10.0)
        /** Steep fall up to 8–10 k/day (earlier plateau from 60), clear rise below 4 k. */
        fun steps(steps: Double, age: Double): Double = if (age < 60)
            interp(steps, listOf(2000.0 to 1.6, 4000.0 to 1.25, 7000.0 to 1.0, 9000.0 to 0.88, 12000.0 to 0.82))
        else interp(steps, listOf(2000.0 to 1.6, 4000.0 to 1.2, 6000.0 to 1.0, 8000.0 to 0.88, 11000.0 to 0.84))
        /** U-shape, lowest at 7–8 h. */
        fun sleepHours(h: Double) = interp(h, listOf(4.5 to 1.35, 5.0 to 1.25, 6.0 to 1.12, 7.0 to 1.0, 8.0 to 1.0, 9.0 to 1.12, 10.0 to 1.30))
        /** Night-to-night consistency 0–100 (70 neutral). */
        fun regularity(pct: Double) = interp(pct, listOf(30.0 to 1.40, 50.0 to 1.20, 70.0 to 1.0, 85.0 to 0.93, 95.0 to 0.90))
        /** Moderate minutes per week: 150–300 lowers risk 20–30 % against none. */
        fun zones13(minPerWeek: Double) = interp(minPerWeek, listOf(0.0 to 1.33, 150.0 to 1.0, 300.0 to 0.88))
        fun zones45(minPerWeek: Double) = interp(minPerWeek, listOf(0.0 to 1.15, 75.0 to 1.0, 150.0 to 0.90))
        /** Strength: 30–60 min/week lowers risk 10–20 %, J-shape above. */
        fun strength(minPerWeek: Double) = interp(minPerWeek, listOf(0.0 to 1.15, 30.0 to 0.95, 60.0 to 0.88, 140.0 to 0.95, 200.0 to 1.02))
        /** Defensive: only penalises below 80 % of the reference lean share. */
        fun leanMass(leanPct: Double, refPct: Double) = interp(leanPct / refPct, listOf(0.6 to 1.30, 0.8 to 1.0))
    }

    fun vo2Reference(age: Double, male: Boolean): Double =
        if (male) (48.0 - 0.40 * (age - 25.0)).coerceAtLeast(20.0) else (40.0 - 0.33 * (age - 25.0)).coerceAtLeast(17.0)

    /** Gompertz: years = ln(HR) / ln(1.10) — 10 % more risk ≈ one year older. */
    fun hazardToYears(hr: Double): Double = ln(hr) / ln(1.10)

    const val OVERLAP = 0.7 // starting value

    data class AgeInputs(
        val sleepHours: Double? = null, val regularity: Double? = null, val steps: Double? = null,
        val zones13: Double? = null, val zones45: Double? = null, val strength: Double? = null,
        val vo2: Double? = null, val rhr: Double? = null, val leanPct: Double? = null,
    )

    data class AgeFactor(val key: String, val value: Double?, val hazard: Double?, val years: Double?)
    data class AgeResult(val chronoAge: Double, val age: Double, val factors: List<AgeFactor>)

    fun physiologicalAge(chronoAge: Double, male: Boolean, x: AgeInputs): AgeResult {
        val f = listOf(
            AgeFactor("sleep_hours", x.sleepHours, x.sleepHours?.let(Hazard::sleepHours), null),
            AgeFactor("sleep_regularity", x.regularity, x.regularity?.let(Hazard::regularity), null),
            AgeFactor("steps", x.steps, x.steps?.let { Hazard.steps(it, chronoAge) }, null),
            AgeFactor("zones_1_3", x.zones13, x.zones13?.let(Hazard::zones13), null),
            AgeFactor("zones_4_5", x.zones45, x.zones45?.let(Hazard::zones45), null),
            AgeFactor("strength", x.strength, x.strength?.let(Hazard::strength), null),
            AgeFactor("vo2max", x.vo2, x.vo2?.let { Hazard.vo2(it, vo2Reference(chronoAge, male)) }, null),
            AgeFactor("resting_hr", x.rhr, x.rhr?.let(Hazard::restingHr), null),
            AgeFactor("lean_mass", x.leanPct, x.leanPct?.let { Hazard.leanMass(it, if (male) 80.0 else 70.0) }, null),
        ).map { it.copy(years = it.hazard?.let { h -> (OVERLAP * hazardToYears(h)).coerceIn(-8.0, 8.0) }) }
        val age = (chronoAge + f.sumOf { it.years ?: 0.0 }).coerceIn(18.0, 100.0)
        return AgeResult(chronoAge, age, f)
    }

    /**
     * Pace = 1 + 2·(age from the last 30 days − age from 6 months), clamped −1…3: the 30-day difference is
     * projected over half a year, so each year of difference spread over 0.5 calendar years.
     */
    fun paceOfAgeing(age30d: Double, age6m: Double): Double = (1.0 + 2.0 * (age30d - age6m)).coerceIn(-1.0, 3.0)

    /** Minutes per week from a total over [days] days of data. */
    fun perWeek(total: Double, days: Int): Double = total / (max(days, 1) / 7.0).coerceAtLeast(1.0)

    fun clampPct(x: Double) = min(100.0, max(0.0, x))
}
