package com.noop.pulse.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pulse's own set / repetition detector for strength training, from the WHOOP 5.0/MG 100 Hz six-axis
 * IMU (accelerometer in g, gyroscope in deg/s). Classic signal processing, no trained model:
 *
 *  1. Smoothing — a centred moving average of [SMOOTH_S] applied twice (a triangular low-pass), which
 *     keeps the 0.1–2 Hz band repetitions live in and removes tremor and sensor noise.
 *  2. Gravity split — a slow exponential average per accelerometer axis ([GRAVITY_TAU_S]) is the
 *     gravity estimate; accelerometer minus that is the movement the arm adds.
 *  3. Sets — per second, a movement intensity (RMS of the added acceleration plus scaled rotation).
 *     A second is "active" above max([MIN_ACTIVE], [REST_FACTOR] × the session's quiet level). Active
 *     runs separated by ≤ [BRIDGE_S] seconds join; a set must last ≥ [MIN_SET_S] seconds.
 *  4. Repetitions — inside a set, the rotation (or, if stronger, the added acceleration) is projected
 *     onto its own dominant axis (power iteration on the 3×3 covariance), so the count does not depend
 *     on how the strap sits on the wrist. Its autocorrelation over [MIN_PERIOD_S]..[MAX_PERIOD_S] gives
 *     the repetition period and how rhythmic the set is; peaks at least 0.6 periods apart and with
 *     enough prominence are the repetitions.
 *
 * A set is only reported with ≥ [MIN_REPS] repetitions and a rhythm of at least [MIN_RHYTHM];
 * every set carries that rhythm as its confidence.
 */
object PulseRepEngine {
    const val HZ = 100
    const val SMOOTH_S = 0.12
    const val GRAVITY_TAU_S = 2.0
    const val MIN_ACTIVE = 0.035
    const val REST_FACTOR = 2.5
    const val BRIDGE_S = 3
    const val MIN_SET_S = 6
    const val MIN_PERIOD_S = 0.7
    const val MAX_PERIOD_S = 7.0
    const val MIN_REPS = 3
    const val MIN_RHYTHM = 0.40

    /** One second of samples: six planar columns of [HZ] values each (ax, ay, az, gx, gy, gz). */
    class Second(val ts: Long, val ax: DoubleArray, val ay: DoubleArray, val az: DoubleArray,
                 val gx: DoubleArray, val gy: DoubleArray, val gz: DoubleArray)

    data class DetectedSet(
        val startTs: Long,
        val endTs: Long,
        val reps: Int,
        /** Seconds per repetition. */
        val periodS: Double,
        /** 0–1: how regular the movement was (autocorrelation at the repetition period). */
        val rhythm: Double,
    ) { val durationS: Long get() = endTs - startTs + 1 }

    fun analyze(seconds: List<Second>): List<DetectedSet> {
        val secs = seconds.sortedBy { it.ts }.distinctBy { it.ts }
        if (secs.size < MIN_SET_S) return emptyList()
        val n = secs.size * HZ
        fun flat(pick: (Second) -> DoubleArray) = DoubleArray(n).also { out ->
            secs.forEachIndexed { i, s -> pick(s).copyInto(out, i * HZ, 0, min(HZ, pick(s).size)) }
        }
        val acc = arrayOf(flat { it.ax }, flat { it.ay }, flat { it.az }).map { smooth(it) }
        val gyr = arrayOf(flat { it.gx }, flat { it.gy }, flat { it.gz }).map { smooth(it) }
        val dyn = acc.map { a -> val g = ema(a, GRAVITY_TAU_S * HZ); DoubleArray(n) { a[it] - g[it] } }

        // Movement intensity per second: added acceleration (g) + rotation scaled so 250 °/s ≈ 0.1 g.
        val intensity = DoubleArray(secs.size) { s ->
            var sum = 0.0
            for (k in s * HZ until (s + 1) * HZ) {
                val a = dyn[0][k] * dyn[0][k] + dyn[1][k] * dyn[1][k] + dyn[2][k] * dyn[2][k]
                val r = sqrt(gyr[0][k] * gyr[0][k] + gyr[1][k] * gyr[1][k] + gyr[2][k] * gyr[2][k]) / 2500.0
                sum += a + r * r
            }
            sqrt(sum / HZ)
        }
        val quiet = intensity.sorted()[intensity.size / 5]
        val threshold = max(MIN_ACTIVE, REST_FACTOR * quiet)
        val active = BooleanArray(secs.size) { intensity[it] > threshold }

        // Active runs, bridging short pauses (and ignoring gaps in the recording).
        val runs = mutableListOf<IntRange>()
        var start = -1; var lastOn = -1
        for (i in active.indices) {
            val gap = i > 0 && secs[i].ts - secs[i - 1].ts > 1
            if (start >= 0 && (gap || (!active[i] && i - lastOn > BRIDGE_S))) { runs += start..lastOn; start = -1 }
            if (active[i]) { if (start < 0) start = i; lastOn = i }
        }
        if (start >= 0) runs += start..lastOn

        return runs.filter { it.last - it.first + 1 >= MIN_SET_S }.mapNotNull { r ->
            val from = r.first * HZ; val to = (r.last + 1) * HZ
            val g = projectDominant(gyr.map { it.copyOfRange(from, to) })
            val a = projectDominant(dyn.map { it.copyOfRange(from, to) })
            val best = listOf(g, a).map { it to period(it) }.maxByOrNull { it.second?.second ?: -1.0 } ?: return@mapNotNull null
            val (lag, rhythm) = best.second ?: return@mapNotNull null
            if (rhythm < MIN_RHYTHM) return@mapNotNull null
            val reps = countPeaks(best.first, lag)
            if (reps < MIN_REPS) null
            else DetectedSet(secs[r.first].ts, secs[r.last].ts, reps, lag.toDouble() / HZ, rhythm)
        }
    }

    // ---- building blocks (internal for tests) ----

    internal fun smooth(x: DoubleArray): DoubleArray {
        val w = max(1, (SMOOTH_S * HZ).roundToInt())
        return movingAverage(movingAverage(x, w), w)
    }

    internal fun movingAverage(x: DoubleArray, w: Int): DoubleArray {
        if (w <= 1 || x.isEmpty()) return x.copyOf()
        val half = w / 2
        val prefix = DoubleArray(x.size + 1).also { for (i in x.indices) it[i + 1] = it[i] + x[i] }
        return DoubleArray(x.size) { i ->
            val lo = max(0, i - half); val hi = min(x.size, i + half + 1)
            (prefix[hi] - prefix[lo]) / (hi - lo)
        }
    }

    private fun ema(x: DoubleArray, tauSamples: Double): DoubleArray {
        if (x.isEmpty()) return x
        val alpha = 1.0 / (tauSamples + 1.0)
        val out = DoubleArray(x.size); out[0] = x[0]
        for (i in 1 until x.size) out[i] = out[i - 1] + alpha * (x[i] - out[i - 1])
        // Backward pass too, so the gravity estimate has no lag at the start of a set.
        for (i in x.size - 2 downTo 0) out[i] = out[i + 1] + alpha * (out[i] - out[i + 1])
        return out
    }

    /** Project three channels onto their direction of largest variance (orientation-independent). */
    internal fun projectDominant(ch: List<DoubleArray>): DoubleArray {
        val n = ch[0].size
        val m = ch.map { it.average() }
        val c = Array(3) { i -> DoubleArray(3) { j -> var s = 0.0; for (k in 0 until n) s += (ch[i][k] - m[i]) * (ch[j][k] - m[j]); s / max(1, n) } }
        var v = doubleArrayOf(1.0, 1.0, 1.0)
        repeat(30) {
            val nv = DoubleArray(3) { i -> c[i][0] * v[0] + c[i][1] * v[1] + c[i][2] * v[2] }
            val norm = sqrt(nv.sumOf { it * it })
            if (norm < 1e-12) return DoubleArray(n)
            v = DoubleArray(3) { nv[it] / norm }
        }
        return DoubleArray(n) { k -> (ch[0][k] - m[0]) * v[0] + (ch[1][k] - m[1]) * v[1] + (ch[2][k] - m[2]) * v[2] }
    }

    /** (lag in samples, normalised autocorrelation) of the strongest repetition period, or null. */
    internal fun period(x: DoubleArray): Pair<Int, Double>? {
        val n = x.size
        val minLag = (MIN_PERIOD_S * HZ).toInt()
        val maxLag = min((MAX_PERIOD_S * HZ).toInt(), n * 2 / 3)
        if (maxLag <= minLag) return null
        // Decimate by 5 for speed (20 Hz is far above any repetition rate).
        val step = 5
        val d = DoubleArray(n / step) { x[it * step] }
        val mean = d.average()
        val z = DoubleArray(d.size) { d[it] - mean }
        val energy = z.sumOf { it * it }
        if (energy < 1e-12) return null
        var bestLag = -1; var best = -1.0
        var prev = Double.NaN; var rising = false
        for (lag in minLag / step..maxLag / step) {
            var s = 0.0
            for (i in 0 until z.size - lag) s += z[i] * z[i + lag]
            val r = s / energy * z.size / (z.size - lag)
            // First clear local maximum after the curve has dipped: the fundamental period, not a multiple.
            if (!prev.isNaN() && r < prev && rising && prev > best + 0.05) { best = prev; bestLag = (lag - 1) * step; if (best > 0.5) break }
            rising = !prev.isNaN() && r > prev
            prev = r
        }
        return if (bestLag > 0) bestLag to best.coerceIn(0.0, 1.0) else null
    }

    internal fun countPeaks(x: DoubleArray, lag: Int): Int {
        val s = movingAverage(x, max(1, lag / 4))
        val sorted = s.sorted()
        val p5 = sorted[sorted.size / 20]; val p95 = sorted[sorted.size * 19 / 20]
        val minProm = 0.30 * (p95 - p5)
        val minDist = (0.6 * lag).toInt()
        val peaks = mutableListOf<Int>()
        for (i in 1 until s.size - 1) {
            if (s[i] <= s[i - 1] || s[i] < s[i + 1]) continue
            val lo = max(0, i - lag / 2); val hi = min(s.size, i + lag / 2)
            var leftMin = s[i]; for (k in lo until i) leftMin = min(leftMin, s[k])
            var rightMin = s[i]; for (k in i + 1 until hi) rightMin = min(rightMin, s[k])
            if (s[i] - max(leftMin, rightMin) < minProm) continue
            if (peaks.isNotEmpty() && i - peaks.last() < minDist) {
                if (s[i] > s[peaks.last()]) peaks[peaks.size - 1] = i
                continue
            }
            peaks += i
        }
        return peaks.size
    }

    @Suppress("unused")
    private fun absMax(x: DoubleArray) = x.maxOfOrNull { abs(it) } ?: 0.0
}
