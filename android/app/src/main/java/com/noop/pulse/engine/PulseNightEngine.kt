package com.noop.pulse.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pulse's own overnight HRV and resting heart rate from the strap's beat-to-beat (R-R) intervals.
 *
 * 1. Beat cleaning — a beat is kept when it lies in 300–2000 ms and within [MAX_JUMP] of the median of
 *    its [MEDIAN_BEATS] neighbours (ectopic beats and optical artefacts break that rule). Gaps over 3 s
 *    in time also break the chain, so RMSSD never differences two beats that were not consecutive.
 * 2. Quality mask — the night is cut into [WINDOW_S] windows. A window's signal-quality index is the
 *    share of its beats that survived cleaning; windows under [MIN_SQI] or with fewer than [MIN_BEATS]
 *    beats are left out (a controlled gap instead of a corrupt value).
 * 3. RMSSD per window over clean successive pairs; the night's HRV is the median of the windows.
 * 4. Resting heart rate — the lowest mean HR over [RHR_WINDOWS] consecutive good windows (the lowest
 *    *stable* stretch, not a single dip).
 */
object PulseNightEngine {
    const val MEDIAN_BEATS = 11
    const val MAX_JUMP = 0.20
    const val WINDOW_S = 300L
    const val MIN_SQI = 0.80
    const val MIN_BEATS = 120
    const val RHR_WINDOWS = 2
    const val MIN_WINDOWS = 3

    data class Beat(val ts: Long, val rrMs: Int)
    data class Night(
        val rmssdMs: Double,
        val restingHr: Double,
        /** Share of the night's beats that passed cleaning. */
        val quality: Double,
        val windowsUsed: Int,
    )

    fun analyze(beats: List<Beat>): Night? {
        val b = beats.sortedBy { it.ts }
        if (b.size < MIN_BEATS * MIN_WINDOWS) return null
        val keep = clean(b)
        val windows = b.indices.groupBy { b[it].ts / WINDOW_S }.toSortedMap()
        data class W(val rmssd: Double, val hr: Double, val idx: Long)
        val good = windows.mapNotNull { (idx, ids) ->
            val kept = ids.filter { keep[it] }
            if (ids.size < MIN_BEATS || kept.size.toDouble() / ids.size < MIN_SQI) return@mapNotNull null
            var sum = 0.0; var n = 0
            for (k in 1 until ids.size) {
                val i = ids[k - 1]; val j = ids[k]
                if (!keep[i] || !keep[j] || b[j].ts - b[i].ts > 3) continue
                val d = (b[j].rrMs - b[i].rrMs).toDouble(); sum += d * d; n++
            }
            if (n < MIN_BEATS / 2) return@mapNotNull null
            val meanRr = kept.map { b[it].rrMs }.average()
            W(sqrt(sum / n), 60_000.0 / meanRr, idx)
        }
        if (good.size < MIN_WINDOWS) return null
        val rmssd = good.map { it.rmssd }.sorted().let { it[it.size / 2] }
        // Lowest mean HR over RHR_WINDOWS adjacent good windows.
        val rhr = good.windowed(RHR_WINDOWS).filter { it.last().idx - it.first().idx == (RHR_WINDOWS - 1).toLong() }
            .minOfOrNull { w -> w.map { it.hr }.average() } ?: good.minOf { it.hr }
        return Night(rmssd, rhr, keep.count { it }.toDouble() / b.size, good.size)
    }

    internal fun clean(b: List<Beat>): BooleanArray {
        val half = MEDIAN_BEATS / 2
        return BooleanArray(b.size) { i ->
            val rr = b[i].rrMs
            if (rr !in 300..2000) return@BooleanArray false
            val lo = max(0, i - half); val hi = min(b.size, i + half + 1)
            val med = b.subList(lo, hi).map { it.rrMs }.filter { it in 300..2000 }.sorted().let { if (it.isEmpty()) return@BooleanArray false else it[it.size / 2] }
            abs(rr - med) <= MAX_JUMP * med
        }
    }
}
