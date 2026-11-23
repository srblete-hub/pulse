package com.noop.analytics

// PulseAgeEngine.kt — Pulse "physiological age": chronological age plus a bounded adjustment per
// factor, so the screen can show how many years each habit adds or removes.
//
// Independent, deliberately transparent model built from public health guidance and published
// cohort research. It is NOT a clinical biological-age test and does not reproduce any vendor's
// proprietary algorithm or weights. Nine factors in three blocks; each maps a measurement to years
// by linear interpolation between a few anchors:
//
// SLEEP
//   • Duration — 7–9 h is the lowest-risk band (Cappuccio et al., Sleep 2010; AASM/SRS consensus).
//   • Regularity — consistent sleep/wake timing is associated with lower mortality
//     (Windred et al., Sleep 2024). 70 % is neutral, higher is credited.
// ACTIVITY
//   • Daily steps, age-adjusted — mortality falls steeply up to ~8–10 k/day under 60 and ~6–8 k/day
//     from 60 (Paluch et al., Lancet Public Health 2022).
//   • HR zones 1–3 minutes/week — WHO 2020 moderate activity, 150–300 min/week.
//   • HR zones 4–5 minutes/week — WHO 2020 vigorous activity, 75–150 min/week (any amount helps).
//   • Strength minutes/week — WHO 2020 asks for 2+ days; lowest mortality around 30–60 min/week
//     with a J-shape above (Momma et al., BJSM 2022).
// FITNESS
//   • VO₂max vs. an age/sex reference — the strongest single predictor (Kokkinos et al., JACC 2022);
//     every ~3.5 ml/kg/min (1 MET) above reference credits ~2 years, capped −6 / +4.
//   • Resting heart rate — lower is better (Aune et al., NMCD 2017); 60 bpm neutral.
//   • Lean body mass — defensive only: penalises when below 80 % of the reference share of body
//     weight, never credits above it.
//
// Double counting: activity improves VO₂max and lowers resting HR, so the fitness block is shrunk
// by [fitnessBlockWeight] when the activity block already has data. A factor with no measurement
// contributes nothing and is reported as missing.
object PulseAgeEngine {

    data class Factor(val key: String, val value: Double?, val years: Double?)

    data class Result(
        val chronoAge: Double,
        val pulseAge: Double,
        /** Positive = younger than chronological age. */
        val yearsYounger: Double,
        val factors: List<Factor>,
        val coverage: Int,
    )

    data class Inputs(
        val sleepHours: Double? = null,
        val sleepRegularityPct: Double? = null,
        val stepsPerDay: Double? = null,
        val zones13MinPerWeek: Double? = null,
        val zones45MinPerWeek: Double? = null,
        val strengthMinPerWeek: Double? = null,
        val vo2max: Double? = null,
        val restingHr: Double? = null,
        /** Lean mass as % of body weight. */
        val leanMassPct: Double? = null,
        /** "male" / "female" / anything else (treated as the average of both references). */
        val sex: String = "",
    )

    const val minAge = 18.0
    const val maxAge = 90.0
    const val fitnessBlockWeight = 0.6

    internal fun interp(x: Double, anchors: List<Pair<Double, Double>>): Double {
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

    val sleepHoursAnchors = listOf(5.0 to 1.5, 6.0 to 0.7, 7.0 to -0.5, 9.0 to -0.5, 10.0 to 0.5)
    val regularityAnchors = listOf(40.0 to 1.0, 70.0 to 0.0, 90.0 to -1.0)
    val zones13Anchors = listOf(0.0 to 1.5, 150.0 to 0.0, 300.0 to -1.0)
    val zones45Anchors = listOf(0.0 to 0.6, 30.0 to 0.0, 75.0 to -0.6, 150.0 to -0.9)
    val strengthAnchors = listOf(0.0 to 0.6, 40.0 to -0.5, 60.0 to -0.7, 180.0 to -0.3)
    val restingHrAnchors = listOf(45.0 to -1.2, 60.0 to 0.0, 75.0 to 1.2, 90.0 to 2.5)

    /** Steps anchors shift with age: the plateau arrives earlier from 60 (Paluch 2022). */
    fun stepsAnchors(age: Double): List<Pair<Double, Double>> =
        if (age < 60) listOf(2000.0 to 1.5, 8000.0 to 0.0, 10000.0 to -0.5, 14000.0 to -0.7)
        else listOf(2000.0 to 1.5, 6000.0 to 0.0, 8000.0 to -0.5, 12000.0 to -0.7)

    /** Approximate median VO₂max by age and sex (ml/kg/min), declining roughly linearly with age. */
    fun vo2Reference(age: Double, sex: String): Double {
        val men = (48.0 - 0.40 * (age - 25.0)).coerceAtLeast(20.0)
        val women = (40.0 - 0.33 * (age - 25.0)).coerceAtLeast(17.0)
        return when (sex.lowercase()) {
            "male" -> men
            "female" -> women
            else -> (men + women) / 2.0
        }
    }

    fun vo2Years(vo2: Double, age: Double, sex: String): Double =
        (-(vo2 - vo2Reference(age, sex)) / 3.5 * 2.0).coerceIn(-6.0, 4.0)

    /** Reference lean-mass share of body weight. */
    fun leanReferencePct(sex: String): Double = when (sex.lowercase()) {
        "male" -> 80.0
        "female" -> 70.0
        else -> 75.0
    }

    /** Defensive: 0 at or above 80 % of reference, up to +2 years at 60 %. */
    fun leanYears(leanPct: Double, sex: String): Double {
        val ratio = leanPct / leanReferencePct(sex)
        return interp(ratio, listOf(0.6 to 2.0, 0.8 to 0.0))
    }

    fun compute(chronoAge: Double, inputs: Inputs): Result? {
        if (chronoAge <= 0) return null
        val activity = listOf(
            Factor("steps", inputs.stepsPerDay, inputs.stepsPerDay?.let { interp(it, stepsAnchors(chronoAge)) }),
            Factor("zones_1_3", inputs.zones13MinPerWeek, inputs.zones13MinPerWeek?.let { interp(it, zones13Anchors) }),
            Factor("zones_4_5", inputs.zones45MinPerWeek, inputs.zones45MinPerWeek?.let { interp(it, zones45Anchors) }),
            Factor("strength", inputs.strengthMinPerWeek, inputs.strengthMinPerWeek?.let { interp(it, strengthAnchors) }),
        )
        val shrink = if (activity.any { it.years != null }) fitnessBlockWeight else 1.0
        val fitness = listOf(
            Factor("vo2max", inputs.vo2max, inputs.vo2max?.let { vo2Years(it, chronoAge, inputs.sex) * shrink }),
            Factor("resting_hr", inputs.restingHr, inputs.restingHr?.let { interp(it, restingHrAnchors) * shrink }),
            Factor("lean_mass", inputs.leanMassPct, inputs.leanMassPct?.let { leanYears(it, inputs.sex) }),
        )
        val sleep = listOf(
            Factor("sleep_hours", inputs.sleepHours, inputs.sleepHours?.let { interp(it, sleepHoursAnchors) }),
            Factor("sleep_regularity", inputs.sleepRegularityPct, inputs.sleepRegularityPct?.let { interp(it, regularityAnchors) }),
        )
        val factors = sleep + activity + fitness
        val age = (chronoAge + factors.sumOf { it.years ?: 0.0 }).coerceIn(minAge, maxAge)
        return Result(chronoAge, age, chronoAge - age, factors, factors.count { it.years != null })
    }

    /** Physiological years gained per calendar year between two results [days] apart. */
    fun paceOfAgeing(previous: Result, current: Result, days: Int): Double? {
        if (days <= 0) return null
        val calendarYears = days / 365.25
        // Both results already include the calendar ageing in their chronological age, so the ratio of
        // physiological change to calendar time is the pace directly (1.0 = same rate as the calendar).
        return (current.pulseAge - previous.pulseAge) / calendarYears
    }
}
