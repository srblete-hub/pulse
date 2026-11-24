package com.noop.pulse.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Pulse recovery: every input judged only against the user's own history.
 *
 *  - Personal baseline: exponentially weighted mean and standard deviation of each input over the
 *    previous nights (half-life [HALF_LIFE_NIGHTS]), so recent weeks weigh more. HRV is handled in ln,
 *    where its night-to-night spread is close to normal.
 *  - z-score per input against that baseline: HRV (higher is better), resting HR (inverted), sleep
 *    performance (higher is better), breathing rate (inverted: a rise is a strain sign).
 *  - Weighted fusion: [W_HRV] / [W_RHR] / [W_SLEEP] / [W_RESP], renormalised over the inputs present;
 *    each z is clipped to ±3 so one broken night cannot dominate.
 *  - 0–100: the standard normal CDF of the combined z, so 50 % is "your usual" and the scale means the
 *    same thing for everybody.
 *
 * Needs [MIN_NIGHTS] prior nights with HRV before it scores; until then the caller falls back.
 */
object PulseRecoveryEngine {
    const val HALF_LIFE_NIGHTS = 14.0
    const val MIN_NIGHTS = 4
    const val W_HRV = 0.50
    const val W_RHR = 0.25
    const val W_SLEEP = 0.15
    const val W_RESP = 0.10
    /** Floors on the personal spread, so a very steady baseline does not turn tiny changes into big z. */
    const val MIN_SD_LN_HRV = 0.06
    const val MIN_SD_RHR = 1.5
    const val MIN_SD_SLEEP = 5.0
    const val MIN_SD_RESP = 0.4

    data class Inputs(val day: String, val hrvMs: Double?, val restingHr: Double?, val sleepPct: Double?, val respBpm: Double?)
    data class Factor(val key: String, val value: Double, val mean: Double, val z: Double, val weight: Double)
    data class Result(val day: String, val recovery: Double, val factors: List<Factor>, val nights: Int)

    /** Score [today] against [history] (prior nights, any order; entries on or after today's day ignored). */
    fun score(today: Inputs, history: List<Inputs>): Result? {
        val prior = history.filter { it.day < today.day }.sortedBy { it.day }
        if (today.hrvMs == null || today.hrvMs <= 0) return null
        if (prior.count { (it.hrvMs ?: 0.0) > 0 } < MIN_NIGHTS) return null

        val factors = listOfNotNull(
            factor("hrv", today.hrvMs.let(::ln), prior.mapNotNull { it.hrvMs?.takeIf { v -> v > 0 }?.let(::ln) }, MIN_SD_LN_HRV, W_HRV, +1),
            today.restingHr?.let { factor("rhr", it, prior.mapNotNull { p -> p.restingHr }, MIN_SD_RHR, W_RHR, -1) },
            today.sleepPct?.let { factor("sleep", it, prior.mapNotNull { p -> p.sleepPct }, MIN_SD_SLEEP, W_SLEEP, +1) },
            today.respBpm?.let { factor("resp", it, prior.mapNotNull { p -> p.respBpm }, MIN_SD_RESP, W_RESP, -1) },
        )
        val wSum = factors.sumOf { it.weight }
        if (wSum <= 0) return null
        // Weighted mean of z, rescaled so the combination keeps unit variance for independent inputs.
        val zMix = factors.sumOf { it.weight * it.z } / sqrt(factors.sumOf { it.weight * it.weight })
        val rec = (100 * phi(zMix)).coerceIn(1.0, 99.0)
        return Result(today.day, rec, factors.map { if (it.key == "hrv") it.copy(value = exp(it.value), mean = exp(it.mean)) else it }, prior.size)
    }

    private fun factor(key: String, x: Double, past: List<Double>, minSd: Double, w: Double, sign: Int): Factor? {
        if (past.size < 3) return null
        val (m, sd) = ewm(past)
        val z = (sign * (x - m) / max(sd, minSd)).coerceIn(-3.0, 3.0)
        return Factor(key, x, m, z, w)
    }

    /** Exponentially weighted mean and SD, newest last. */
    internal fun ewm(x: List<Double>): Pair<Double, Double> {
        val decay = ln(2.0) / HALF_LIFE_NIGHTS
        val w = x.indices.map { exp(-decay * (x.size - 1 - it)) }
        val ws = w.sum()
        val m = x.indices.sumOf { w[it] * x[it] } / ws
        val v = x.indices.sumOf { w[it] * (x[it] - m) * (x[it] - m) } / ws
        return m to sqrt(v)
    }

    /** Standard normal CDF (Abramowitz–Stegun 7.1.26, error < 1.5e-7). */
    internal fun phi(z: Double): Double {
        val t = 1 / (1 + 0.3275911 * abs(z) / sqrt(2.0))
        val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-z * z / 2)
        return if (z >= 0) (1 + y) / 2 else (1 - y) / 2
    }
}
