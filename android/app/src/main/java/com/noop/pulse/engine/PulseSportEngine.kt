package com.noop.pulse.engine

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pulse's own sport recognition for automatically detected activities. Three layers:
 *
 *  1. Heart-rate signature — how hard (mean % of heart-rate reserve) and how intermittent: the
 *     30-second heart rate is compared with its own 5-minute trend and every swing of more than
 *     [SURGE_BPM] bpm counts as a surge. Steady running is flat; team and racket sports surge.
 *  2. Movement — the strap's step cadence and its own per-record still/walk/run flag, plus how much
 *     the wrist turns (angle between consecutive 1 Hz gravity vectors).
 *  3. Personal context — the sessions the user has confirmed: how similar this one is to each of
 *     their labels (nearest neighbours in feature space), how often they do it, and whether they
 *     usually do it at this time of the week.
 *
 * Generic categories come from layers 1–2; the user's own labels ("Fútbol", "Pádel"…) come from layer
 * 3 and win only when the signal agrees. The answer carries its confidence; below [MIN_CONFIDENCE]
 * the activity stays unnamed and Pulse asks.
 */
object PulseSportEngine {
    const val SURGE_BPM = 6.0
    const val MIN_CONFIDENCE = 0.55
    /** The winner must beat the runner-up by this much, or the activity stays unnamed. */
    const val MIN_MARGIN = 0.15
    /** Raw signal score of the "nothing fits" class: a sport must match better than this to be named. */
    const val NONE_RAW = 0.15
    const val NONE_LABEL = "Actividad"

    /**
     * Is this really exercise, or just a raised heart rate (heat, stress, coffee, sitting)? It needs real
     * intensity (≥ 30 % of heart-rate reserve) AND movement that fits it: steps, a turning wrist, the
     * surge pattern of a gym session, or — with no steps and a still wrist — the high, steady load of
     * cycling. With no motion data at all, only a clearly high intensity counts.
     */
    fun isExercise(f: Features): Boolean {
        if (f.durationMin < 12 || f.hrr < 0.30) return false
        val noMotion = f.cadence == null && f.wristTurn == null && f.runShare == null
        if (noMotion) return f.hrr >= 0.40
        val stepping = (f.cadence ?: 0.0) >= 40 || (f.runShare ?: 0.0) >= 0.3
        val arms = (f.wristTurn ?: 0.0) >= 4.0
        val gym = f.surges >= 2.0 && (f.wristTurn ?: 0.0) >= 2.0
        val cycling = (f.cadence ?: 0.0) < 20 && f.hrr >= 0.45 && f.surges < 1.5
        return stepping || arms || gym || cycling
    }

    enum class Kind(val label: String) {
        WALK("Caminar"), RUN("Correr"), CYCLE("Ciclismo"), STRENGTH("Fuerza"), INTERMITTENT("Deporte intermitente")
    }

    data class Features(
        val durationMin: Double,
        /** Mean fraction of heart-rate reserve, 0–1. */
        val hrr: Double,
        /** Heart-rate surges per 10 minutes. */
        val surges: Double,
        /** Steps per minute, null when the strap gave no step data. */
        val cadence: Double?,
        /** Share of step records the strap flagged as running (0–1), null if unknown. */
        val runShare: Double?,
        /** Mean wrist turn in degrees per second, null without gravity data. */
        val wristTurn: Double?,
        val weekday: Int,
        val hour: Int,
    )

    data class Confirmed(val label: String, val f: Features)
    data class Guess(val label: String, val confidence: Double, val scores: Map<String, Double>)

    // ---- feature extraction ----

    fun features(
        hr: List<Pair<Long, Int>>, restingHr: Double, maxHr: Double,
        steps: List<Triple<Long, Int, Int?>>, ticksPerStep: Double,
        gravity: List<Triple<Double, Double, Double>>,
        weekday: Int, hour: Int,
    ): Features? {
        if (hr.size < 60) return null
        val h = hr.sortedBy { it.first }
        val dur = (h.last().first - h.first().first) / 60.0
        if (dur < 5) return null
        val reserve = max(1.0, maxHr - restingHr)
        val hrr = h.map { ((it.second - restingHr) / reserve).coerceIn(0.0, 1.2) }.average()

        // Per-second series (gaps carried forward), then 30 s and 5 min means.
        val t0 = h.first().first
        val n = (h.last().first - t0 + 1).toInt()
        val series = DoubleArray(n); var j = 0; var last = h[0].second.toDouble()
        for (s in 0 until n) { while (j < h.size && h[j].first - t0 <= s) { last = h[j].second.toDouble(); j++ }; series[s] = last }
        val m30 = mean(series, 30); val m300 = mean(series, 300)
        var surges = 0; var state = 0
        for (s in series.indices) {
            val d = m30[s] - m300[s]
            val now = if (d > SURGE_BPM) 1 else if (d < -SURGE_BPM) -1 else 0
            if (now == 1 && state != 1) surges++
            if (now != 0) state = now
        }
        val surgeRate = surges / dur * 10.0

        val st = steps.sortedBy { it.first }
        val cadence = if (st.size >= 2) {
            val ticks = (st.last().second - st.first().second).coerceAtLeast(0)
            val mins = (st.last().first - st.first().first) / 60.0
            if (mins >= 3) ticks / max(0.5, ticksPerStep) / mins else null
        } else null
        val flagged = st.mapNotNull { it.third }
        val runShare = if (flagged.size >= 5) flagged.count { it == 2 }.toDouble() / flagged.size else null

        val wrist = if (gravity.size >= 30) gravity.zipWithNext { a, b -> angle(a, b) }.average() else null
        return Features(dur, hrr, surgeRate, cadence, runShare, wrist, weekday, hour)
    }

    // ---- layer 1+2: generic categories ----

    fun generic(f: Features): Map<Kind, Double> {
        val c = f.cadence
        val run = f.runShare
        val w = f.wristTurn
        fun band(x: Double?, lo: Double, hi: Double, soft: Double) = when {
            x == null -> 0.5
            x < lo -> exp(-(lo - x) * (lo - x) / (2 * soft * soft))
            x > hi -> exp(-(x - hi) * (x - hi) / (2 * soft * soft))
            else -> 1.0
        }
        val steady = band(f.surges, 0.0, 1.2, 0.8)
        val surgy = band(f.surges, 2.0, 20.0, 0.8)
        val raw = mapOf(
            Kind.WALK to band(c, 85.0, 135.0, 15.0) * band(f.hrr, 0.1, 0.5, 0.1) * steady * band(run, 0.0, 0.3, 0.2),
            Kind.RUN to band(c, 140.0, 200.0, 15.0) * band(f.hrr, 0.45, 1.0, 0.1) * band(f.surges, 0.0, 2.0, 1.0) * band(run, 0.5, 1.0, 0.2) * band(w, 2.5, 60.0, 2.0),
            Kind.CYCLE to band(c, 0.0, 20.0, 15.0) * band(f.hrr, 0.3, 1.0, 0.1) * steady * band(w, 0.0, 4.0, 3.0),
            Kind.STRENGTH to band(c, 0.0, 35.0, 15.0) * band(f.hrr, 0.15, 0.6, 0.1) * surgy * band(w, 5.0, 60.0, 3.0),
            Kind.INTERMITTENT to band(c, 40.0, 140.0, 20.0) * band(f.hrr, 0.4, 1.0, 0.1) * surgy * band(run, 0.1, 0.9, 0.2),
        )
        return raw
    }

    // ---- layer 3: personal context + decision ----

    fun guess(f: Features, history: List<Confirmed>): Guess {
        val gen = generic(f)
        val scores = HashMap<String, Double>()
        val total = history.size
        val labels = history.groupBy { it.label }
        val k = Kind.values().size + labels.size
        gen.forEach { (kind, s) -> scores[kind.label] = s * (1.0 / (total + k)) }
        // "Nothing fits": competes like any class, so a weak match loses instead of being forced into a sport.
        scores[NONE_LABEL] = NONE_RAW * (1.0 / (total + k))
        labels.forEach { (label, rows) ->
            val sims = rows.map { similarity(f, it.f) }.sortedDescending().take(3)
            val sim = sims.average()
            val cat = rows.map { generic(it.f).maxByOrNull { e -> e.value }!!.key }.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            val signal = 0.6 * sim + 0.4 * gen.getValue(cat)
            val prior = (rows.size + 1.0) / (total + k)
            val slot = rows.count { it.f.weekday == f.weekday && abs(it.f.hour - f.hour) <= 2 } +
                0.4 * rows.count { it.f.weekday != f.weekday && abs(it.f.hour - f.hour) <= 2 }
            val context = 1.0 + 2.0 * slot / rows.size
            // A personal label replaces the generic one it is made of.
            scores[label] = signal * prior * context
            if (cat.label != label) scores.remove(cat.label)
        }
        val sum = scores.values.sum()
        if (sum <= 0) return Guess(NONE_LABEL, 0.0, emptyMap())
        val norm = scores.mapValues { it.value / sum }
        val ranked = norm.entries.sortedByDescending { it.value }
        val best = ranked.first(); val second = ranked.getOrNull(1)?.value ?: 0.0
        return if (best.key != NONE_LABEL && best.value >= MIN_CONFIDENCE && best.value - second >= MIN_MARGIN) Guess(best.key, best.value, norm)
        else Guess(NONE_LABEL, best.value, norm)
    }

    internal fun similarity(a: Features, b: Features): Double {
        var d2 = 0.0
        fun add(x: Double?, y: Double?, scale: Double) { if (x != null && y != null) { val z = (x - y) / scale; d2 += z * z } }
        add(a.hrr, b.hrr, 0.08)
        add(a.surges, b.surges, 1.0)
        add(a.cadence, b.cadence, 15.0)
        add(a.runShare, b.runShare, 0.2)
        add(a.wristTurn, b.wristTurn, 3.0)
        add(ln(a.durationMin), ln(b.durationMin), 0.35)
        return exp(-d2 / 2)
    }

    private fun mean(x: DoubleArray, w: Int): DoubleArray {
        val p = DoubleArray(x.size + 1).also { for (i in x.indices) it[i + 1] = it[i] + x[i] }
        return DoubleArray(x.size) { i -> val lo = max(0, i - w / 2); val hi = min(x.size, i + w / 2 + 1); (p[hi] - p[lo]) / (hi - lo) }
    }

    private fun angle(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
        val na = sqrt(a.first * a.first + a.second * a.second + a.third * a.third)
        val nb = sqrt(b.first * b.first + b.second * b.second + b.third * b.third)
        if (na < 1e-6 || nb < 1e-6) return 0.0
        val c = ((a.first * b.first + a.second * b.second + a.third * b.third) / (na * nb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(c))
    }

    // ---- sustained evidence ----

    /** Smoothing of the per-slice scores (exponential moving average). */
    const val EMA_ALPHA = 0.4
    /** Share of slices whose smoothed vote must agree with the session's label. */
    const val MIN_AGREEMENT = 0.6
    const val MIN_WINDOWS = 2

    /** Which generic category a label stands for (itself, or the category its confirmed sessions are made of). */
    fun categoryOf(label: String, history: List<Confirmed>): Kind? =
        Kind.values().firstOrNull { it.label == label }
            ?: history.filter { it.label == label }.map { generic(it.f).maxByOrNull { e -> e.value }!!.key }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /**
     * Keep [g] only if the activity's slices back it up: each slice is scored (generic categories + the
     * "nothing" class), the scores are smoothed across slices, and the smoothed winner must match the label's
     * category in at least [MIN_AGREEMENT] of the slices (and in at least [MIN_WINDOWS] of them).
     */
    fun sustain(g: Guess, whole: Features, windows: List<Features>, history: List<Confirmed>): Guess {
        if (g.label == NONE_LABEL || windows.size < MIN_WINDOWS) return g
        val kind = categoryOf(g.label, history) ?: return g
        var ema: Map<String, Double>? = null
        var agree = 0
        for (w in windows) {
            val raw = generic(w).mapKeys { it.key.label } + (NONE_LABEL to NONE_RAW)
            val sum = raw.values.sum().takeIf { it > 0 } ?: continue
            val p = raw.mapValues { it.value / sum }
            ema = ema?.let { prev -> p.mapValues { (k, v) -> EMA_ALPHA * v + (1 - EMA_ALPHA) * (prev[k] ?: 0.0) } } ?: p
            if (ema.maxByOrNull { it.value }?.key == kind.label) agree++
        }
        return if (agree >= MIN_WINDOWS && agree.toDouble() / windows.size >= MIN_AGREEMENT) g else Guess(NONE_LABEL, g.confidence, g.scores)
    }
}
