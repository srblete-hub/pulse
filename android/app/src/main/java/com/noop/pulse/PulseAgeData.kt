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

/** Measured inputs plus how many days they cover, so the screen can say how solid the estimate is. */
data class PulseAgeMeasurement(val inputs: PulseAgeEngine.Inputs, val daysWithData: Int, val chronoAge: Double)

object PulseAgeData {
    /** Window the factors are averaged over: the last 4 weeks, ending [endOffsetDays] before today. */
    const val windowDays = 28

    suspend fun measure(
        context: Context,
        vm: AppViewModel,
        allDays: List<com.noop.data.DailyMetric>,
        workouts: List<WorkoutRow>,
        vo2max: Double?,
        endOffsetDays: Int = 0,
    ): PulseAgeMeasurement = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val profile = ProfileStore.from(context)
        val endDay = LocalDate.now().minusDays(endOffsetDays.toLong())
        val startDay = endDay.minusDays((windowDays - 1).toLong())
        val from = startDay.atStartOfDay(zone).toEpochSecond()
        val to = endDay.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val strap = vm.activeStrapId

        // Sleep: main sleep per night (longest session ending that day).
        val sessions = runCatching { vm.repo.sleepSessionsUnion(strap, from - 86400, to) }.getOrDefault(emptyList())
            .map { (it.startTsAdjusted ?: it.startTs) to it.endTs }
            .filter { it.second > it.first }
        val mainNights = sessions.groupBy { Instant.ofEpochSecond(it.second).atZone(zone).toLocalDate() }
            .filterKeys { it in startDay..endDay }
            .mapValues { (_, v) -> v.maxBy { it.second - it.first } }
            .values.toList()
        val days = allDays.filter { it.day >= startDay.toString() && it.day <= endDay.toString() }
        val sleepH = days.mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0 }?.div(60.0) }.takeIf { it.isNotEmpty() }?.average()
            ?: mainNights.takeIf { it.isNotEmpty() }?.map { (it.second - it.first) / 3600.0 }?.average()
        val consistency = PulseAgeMath.sleepConsistency(mainNights, zone)

        // Activity.
        val steps = days.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val buckets = runCatching { vm.repo.hrBucketsUnion(strap, from, to, 60L) }.getOrDefault(emptyList())
        val weeks = (buckets.map { Instant.ofEpochSecond(it.bucket).atZone(zone).toLocalDate() }.toSet().size / 7.0).coerceAtLeast(1.0)
        val zones = if (buckets.isEmpty()) null else PulseAgeMath.zoneMinutes(buckets.map { it.bucket to it.avgBpm }, 60L, sessions, profile.hrMax)
        val strength = workouts.filter { w -> w.startTs in from until to && PulseActivity.isStrength(w) }
            .sumOf { ((it.durationS ?: (it.endTs - it.startTs).toDouble()) / 60.0) } / (windowDays / 7.0)

        // Fitness.
        val rhr = days.mapNotNull { it.restingHr?.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val bodyFat = runCatching {
            vm.repo.metricSeries(com.noop.ingest.HealthConnectImporter.HC_DEVICE, "body_fat", startDay.minusDays(90).toString(), endDay.toString()).lastOrNull()?.value
        }.getOrNull()

        PulseAgeMeasurement(
            inputs = PulseAgeEngine.Inputs(
                sleepHours = sleepH,
                sleepRegularityPct = consistency,
                stepsPerDay = steps,
                zones13MinPerWeek = zones?.let { (it[0] + it[1] + it[2]) / weeks },
                zones45MinPerWeek = zones?.let { (it[3] + it[4]) / weeks },
                strengthMinPerWeek = if (workouts.isEmpty() && buckets.isEmpty()) null else strength,
                vo2max = vo2max,
                restingHr = rhr,
                leanMassPct = PulseAgeMath.leanPctFromBodyFat(bodyFat),
                sex = profile.sex,
            ),
            daysWithData = days.count { (it.totalSleepMin ?: 0.0) > 0 || (it.steps ?: 0) > 0 },
            chronoAge = ProfileStore.from(context).dateOfBirthMillis.let { dob ->
                val b = Instant.ofEpochMilli(dob).atZone(zone).toLocalDate()
                java.time.temporal.ChronoUnit.DAYS.between(b, endDay) / 365.25
            },
        )
    }

    suspend fun result(
        context: Context,
        vm: AppViewModel,
        allDays: List<com.noop.data.DailyMetric>,
        workouts: List<WorkoutRow>,
        vo2max: Double?,
        endOffsetDays: Int = 0,
    ): Pair<PulseAgeMeasurement, PulseAgeEngine.Result?> {
        val m = measure(context, vm, allDays, workouts, vo2max, endOffsetDays)
        return m to PulseAgeEngine.compute(m.chronoAge, m.inputs)
    }
}
