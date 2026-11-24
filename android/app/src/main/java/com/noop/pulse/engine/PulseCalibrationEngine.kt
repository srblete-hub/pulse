package com.noop.pulse.engine

import com.noop.analytics.StrainScorer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Personal calibration of noop's scores towards the user's OWN WHOOP numbers (their official data export),
 * from days where both exist. Nothing of WHOOP's method is used: only the user's paired values.
 *
 *  - Effort: noop's own log map (100·ln(TRIMP+1)/ln D) is kept; only D is refitted to the pairs with
 *    noop's [StrainScorer.fitStrainDenominator] (through-origin least squares in ln(TRIMP+1)).
 *  - Recovery and Sleep: an ordinary least-squares line y = a + b·x from noop's value to WHOOP's.
 *
 * Every fit is judged by leave-one-out error (each day predicted by a fit that never saw it) and is only
 * marked [Fit.helps] when that held-out error beats the uncalibrated error.
 */
object PulseCalibrationEngine {
    const val MIN_PAIRS = 7

    data class Fit(
        val n: Int,
        /** Mean absolute difference vs WHOOP before calibration. */
        val maeBefore: Double,
        /** Held-out (leave-one-out) mean absolute difference after calibration. */
        val maeAfter: Double,
        val a: Double,
        val b: Double,
    ) {
        val helps: Boolean get() = maeAfter < maeBefore
    }

    // ---------------- Effort ----------------

    /** [pairs] = (noop effort 0–100, WHOOP strain 0–21); [d0] = the log denominator noop scored with. */
    fun fitEffort(pairs: List<Pair<Double, Double>>, d0: Double): Fit? {
        val p = pairs.filter { it.first > 0 && it.second > 0 }
        if (p.size < MIN_PAIRS) return null
        fun trimp(s100: Double) = exp(s100 / 100.0 * ln(d0)) - 1.0
        fun fitD(rows: List<Pair<Double, Double>>) =
            runCatching { StrainScorer.fitStrainDenominator(rows.map { trimp(it.first) to it.second * 100.0 / 21.0 }) }.getOrNull()
        val before = p.map { abs(it.first * 21.0 / 100.0 - it.second) }.average()
        val loo = p.indices.mapNotNull { i ->
            val d = fitD(p.filterIndexed { j, _ -> j != i }) ?: return@mapNotNull null
            abs(mapEffort(p[i].first, d0, d) * 21.0 / 100.0 - p[i].second)
        }
        val dAll = fitD(p) ?: return null
        if (loo.size < MIN_PAIRS - 1) return null
        return Fit(p.size, before, loo.average(), dAll, 0.0)
    }

    /** Re-express a noop effort (0–100, scored with [d0]) on the calibrated denominator [d]. */
    fun mapEffort(s100: Double, d0: Double, d: Double): Double {
        val t = exp(s100 / 100.0 * ln(d0)) - 1.0
        return (100.0 * ln(t + 1.0) / ln(d)).coerceIn(0.0, 100.0)
    }

    // ---------------- Recovery / Sleep ----------------

    /** [pairs] = (noop value 0–100, WHOOP value 0–100). */
    fun fitLinear(pairs: List<Pair<Double, Double>>): Fit? {
        if (pairs.size < MIN_PAIRS) return null
        fun ols(rows: List<Pair<Double, Double>>): Pair<Double, Double>? {
            val mx = rows.map { it.first }.average(); val my = rows.map { it.second }.average()
            val sxx = rows.sumOf { (it.first - mx) * (it.first - mx) }
            if (sxx < 1e-9) return null
            val b = rows.sumOf { (it.first - mx) * (it.second - my) } / sxx
            if (b <= 0) return null
            return (my - b * mx) to b
        }
        val before = pairs.map { abs(it.first - it.second) }.average()
        val loo = pairs.indices.mapNotNull { i ->
            val (a, b) = ols(pairs.filterIndexed { j, _ -> j != i }) ?: return@mapNotNull null
            abs(mapLinear(pairs[i].first, a, b) - pairs[i].second)
        }
        val (a, b) = ols(pairs) ?: return null
        if (loo.size < MIN_PAIRS - 1) return null
        return Fit(pairs.size, before, loo.average(), a, b)
    }

    fun mapLinear(x: Double, a: Double, b: Double): Double = (a + b * x).coerceIn(1.0, 99.0)
}
