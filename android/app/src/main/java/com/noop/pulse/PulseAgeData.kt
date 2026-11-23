package com.noop.pulse

import android.content.Context
import com.noop.analytics.PulseAgeEngine
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Pure measurement helpers behind the physiological-age inputs. Each one turns raw strap data into the
 * quantity the factor is defined on, instead of a proxy:
 *  - sleep consistency from real bed and wake times (night-to-night overlap of the sleep window),
 *  - heart-rate zone minutes from the whole day's heart rate (not only logged workouts),
 *  - lean mass from the body-fat reading imported from Health Connect.
 */
object PulseAgeMath {

    /**
     * Sleep consistency 0–100: for each pair of consecutive main sleeps, map both onto the same 24 h frame
     * (noon to noon) and take overlap ÷ union of the two sleep windows; the score is the mean × 100.
     * Identical bed and wake times score 100; a 2 h shift on an 8 h night scores ~60.
     */
    fun sleepConsistency(nights: List<Pair<Long, Long>>, zone: ZoneId = ZoneId.systemDefault()): Double? {
        if (nights.size < 2) return null
        fun frame(ts: Long): Long {
            val z = Instant.ofEpochSecond(ts).atZone(zone)
            val sec = z.toLocalTime().toSecondOfDay().toLong()
            return (sec - 12 * 3600 + 86400) % 86400 // seconds since noon
        }
        val scores = nights.sortedBy { it.first }.zipWithNext().mapNotNull { (a, b) ->
            val a0 = frame(a.first); val a1 = a0 + (a.second - a.first)
            val b0 = frame(b.first); val b1 = b0 + (b.second - b.first)
            if (a1 <= a0 || b1 <= b0) return@mapNotNull null
            val overlap = max(0L, min(a1, b1) - max(a0, b0))
            val union = max(a1, b1) - min(a0, b0)
            if (union <= 0) null else overlap.toDouble() / union
        }
        return scores.takeIf { it.isNotEmpty() }?.average()?.times(100)
    }

    /**
     * Minutes per heart-rate zone (index 0 = zone 1 … 4 = zone 5) from per-minute heart-rate buckets,
     * excluding time asleep. Zones are % of maximum heart rate: 50–60, 60–70, 70–80, 80–90, ≥90.
     */
    fun zoneMinutes(buckets: List<Pair<Long, Double>>, bucketSeconds: Long, sleeps: List<Pair<Long, Long>>, hrMax: Int): DoubleArray {
        val out = DoubleArray(5)
        if (hrMax <= 0) return out
        val sorted = sleeps.sortedBy { it.first }
        for ((ts, bpm) in buckets) {
            if (sorted.any { ts >= it.first && ts < it.second }) continue
            val f = bpm / hrMax
            val z = when {
                f < 0.5 -> -1
                f < 0.6 -> 0
                f < 0.7 -> 1
                f < 0.8 -> 2
                f < 0.9 -> 3
                else -> 4
            }
            if (z >= 0) out[z] += bucketSeconds / 60.0
        }
        return out
    }

    /** Lean mass as % of body weight from a body-fat percentage. */
    fun leanPctFromBodyFat(bodyFatPct: Double?): Double? = bodyFatPct?.takeIf { it in 3.0..70.0 }?.let { 100.0 - it }
}
