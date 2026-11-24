package com.noop.pulse.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Daily-rhythm metrics noop does not compute, each implemented from its published method (no third-party
 * code): every result is null when the inputs aren't there.
 *
 *  - Sleep Regularity Index — Phillips AJK et al., Sci Rep 2017;7:3216. Probability that the sleep/wake
 *    state is the same at any two time points 24 h apart, scaled −100…100:
 *      SRI = −100 + 200 / (M·(N−1)) · Σ_j Σ_i δ(s_{i,j}, s_{i+1,j})  over N days of M epochs.
 *    Here the state comes from the detected sleep windows at 1-minute epochs (window-based, so it is an
 *    estimate: in-window awakenings count as sleep).
 *  - Social jetlag and chronotype — Wittmann M et al., Chronobiol Int 2006;23:497; Roenneberg T et al.,
 *    Curr Biol 2012 (MCTQ). Mid-sleep on free days (MSF) minus mid-sleep on work days (MSW); chronotype is
 *    MSF corrected for catch-up sleep: MSFsc = MSF − (SDf − SDweek)/2 when free-day sleep is longer.
 *  - Non-parametric rhythm — Witting W et al., Biol Psychiatry 1990;27:563; Van Someren EJW et al.,
 *    Chronobiol Int 1999;16:505. From hourly activity: interdaily stability IS, intradaily variability IV,
 *    M10 / L5 (most / least active 10 h / 5 h) and relative amplitude RA = (M10 − L5)/(M10 + L5).
 */
object PulseRhythmEngine {
    const val MIN_SRI_DAYS = 5
    const val MIN_WORK_NIGHTS = 3
    const val MIN_FREE_NIGHTS = 2
    const val MIN_RHYTHM_DAYS = 5

    data class Sleep(val startTs: Long, val endTs: Long)

    // ---------------- Sleep Regularity Index ----------------

    /** SRI over the last whole days covered by [sleeps], or null with fewer than [MIN_SRI_DAYS] days. */
    fun sri(sleeps: List<Sleep>, zone: ZoneId, endDay: LocalDate): Pair<Double, Int>? {
        if (sleeps.isEmpty()) return null
        val first = sleeps.minOf { it.startTs }
        val firstDay = Instant.ofEpochSecond(first).atZone(zone).toLocalDate().plusDays(1)
        val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(endDay) }.toList().takeLast(14)
        if (days.size < MIN_SRI_DAYS) return null
        val m = 1440
        val state = days.map { d ->
            val t0 = d.atStartOfDay(zone).toEpochSecond()
            BooleanArray(m) { i -> val t = t0 + i * 60L; sleeps.any { t >= it.startTs && t < it.endTs } }
        }
        // Only days with some sleep recorded count (a missing night is not "awake all night").
        val valid = days.indices.filter { j -> state[j].any { it } || state.getOrNull(j - 1)?.any { it } == true }
        if (valid.size < MIN_SRI_DAYS) return null
        var agree = 0L; var pairs = 0L
        for (k in 0 until valid.size - 1) {
            val a = valid[k]; val b = valid[k + 1]
            if (b != a + 1) continue
            for (i in 0 until m) { if (state[a][i] == state[b][i]) agree++; pairs++ }
        }
        if (pairs == 0L) return null
        return (-100.0 + 200.0 * agree / pairs) to valid.size
    }

    // ---------------- Social jetlag & chronotype ----------------

    data class Jetlag(
        /** MSF − MSW in hours (positive = later on free days). */
        val socialJetlagH: Double,
        /** Chronotype as the sleep-corrected mid-sleep on free days, clock hours (may exceed 24 → wrap). */
        val msfscHour: Double,
        val workNights: Int,
        val freeNights: Int,
    )

    /** A night is a "free" night when the wake-up day is a weekend day (the next day is not a work day). */
    fun socialJetlag(sleeps: List<Sleep>, zone: ZoneId, freeDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)): Jetlag? {
        val main = sleeps.filter { it.endTs - it.startTs >= 3 * 3600 }
        if (main.isEmpty()) return null
        data class N(val mid: Double, val dur: Double, val free: Boolean)
        val nights = main.map { s ->
            val wake = Instant.ofEpochSecond(s.endTs).atZone(zone)
            val midTs = (s.startTs + s.endTs) / 2
            val mid = Instant.ofEpochSecond(midTs).atZone(zone)
            // Mid-sleep as hours after the previous midday folded to 12..36, so 23:00 and 01:00 average right.
            var h = mid.hour + mid.minute / 60.0
            if (h < 12) h += 24
            N(h, (s.endTs - s.startTs) / 3600.0, wake.dayOfWeek in freeDays)
        }
        val work = nights.filter { !it.free }; val free = nights.filter { it.free }
        if (work.size < MIN_WORK_NIGHTS || free.size < MIN_FREE_NIGHTS) return null
        val msw = work.map { it.mid }.average(); val msf = free.map { it.mid }.average()
        val sdw = work.map { it.dur }.average(); val sdf = free.map { it.dur }.average()
        val workDays = 7 - freeDays.size
        val sdWeek = (sdw * workDays + sdf * freeDays.size) / 7.0
        val msfsc = if (sdf > sdw) msf - (sdf - sdWeek) / 2 else msf
        return Jetlag(msf - msw, msfsc % 24.0, work.size, free.size)
    }

    enum class Chronotype { EARLY, INTERMEDIATE, LATE }

    /** Coarse bands around the MCTQ adult distribution (mean MSFsc ≈ 4:15; ±0:45 ≈ intermediate). */
    fun chronotype(msfscHour: Double): Chronotype = when {
        msfscHour in 12.0..24.0 -> Chronotype.EARLY   // mid-sleep before midnight: very early
        msfscHour < 3.5 -> Chronotype.EARLY
        msfscHour <= 5.0 -> Chronotype.INTERMEDIATE
        else -> Chronotype.LATE
    }

    // ---------------- Non-parametric rhythm ----------------

    data class Rhythm(val interdailyStability: Double, val intradailyVariability: Double,
                      val relativeAmplitude: Double, val m10StartHour: Int, val l5StartHour: Int, val days: Int)

    /**
     * [hourly] = consecutive hourly activity values (any non-negative activity measure), oldest first,
     * starting at local hour [firstHour]; nulls are hours without data. Needs [MIN_RHYTHM_DAYS] full days.
     */
    fun rhythm(hourly: List<Double?>, firstHour: Int): Rhythm? {
        val n = hourly.size
        if (n < MIN_RHYTHM_DAYS * 24) return null
        val present = hourly.filterNotNull()
        if (present.size < n * 0.8) return null
        val mean = present.average()
        val totalVar = present.sumOf { (it - mean) * (it - mean) } / present.size
        if (totalVar <= 1e-12) return null
        // Average 24-h profile.
        val byHour = Array(24) { mutableListOf<Double>() }
        hourly.forEachIndexed { i, v -> if (v != null) byHour[(firstHour + i) % 24] += v }
        if (byHour.any { it.isEmpty() }) return null
        val profile = DoubleArray(24) { byHour[it].average() }
        val profVar = profile.sumOf { (it - mean) * (it - mean) } / 24.0
        val isv = (profVar / totalVar).coerceIn(0.0, 1.0)
        // Intradaily variability: mean squared successive difference over variance (consecutive present hours).
        var ss = 0.0; var c = 0
        for (i in 1 until n) { val a = hourly[i - 1]; val b = hourly[i]; if (a != null && b != null) { ss += (b - a) * (b - a); c++ } }
        val iv = if (c > 0) (ss / c) / totalVar else return null
        fun windowMean(start: Int, len: Int) = (0 until len).map { profile[(start + it) % 24] }.average()
        val m10 = (0 until 24).maxByOrNull { windowMean(it, 10) }!!
        val l5 = (0 until 24).minByOrNull { windowMean(it, 5) }!!
        val m10v = windowMean(m10, 10); val l5v = windowMean(l5, 5)
        val ra = if (m10v + l5v > 0) (m10v - l5v) / (m10v + l5v) else return null
        return Rhythm(isv, iv, ra, m10, l5, n / 24)
    }

    @Suppress("unused") private fun absH(x: Double) = abs(x)
}
