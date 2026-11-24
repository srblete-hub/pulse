package com.noop.pulse.engine

import com.noop.analytics.Calories
import com.noop.analytics.StrainScorer
import com.noop.analytics.UserProfile
import com.noop.data.HrSample

/**
 * Live figures for an activity in progress, computed on the phone from the session's heart rate
 * (already median-smoothed by noop as it arrives) with noop's own published-method models:
 *  - energy: Keytel 2005 active + Harris–Benedict resting ([Calories.estimateBoutCalories]);
 *  - burn rate: the same model over the last minute, plus METs (kcal/min ÷ body mass per minute);
 *  - projected Effort: the session extended by [PROJECT_MIN] minutes at the last five minutes'
 *    average heart rate, scored with the same method as live Effort ([StrainScorer.strain]).
 */
object PulseLiveLoad {
    const val PROJECT_MIN = 30

    data class Live(
        val kcal: Double?,
        val kcalPerMin: Double?,
        val met: Double?,
        /** Effort 0–21 if the current pace holds for [PROJECT_MIN] more minutes. */
        val projectedEffort21: Double?,
    )

    fun compute(
        samples: List<HrSample>,
        profile: UserProfile,
        hrMax: Double,
        restingHr: Double?,
        method: StrainScorer.Method,
    ): Live {
        if (samples.size < 2) return Live(null, null, null, null)
        val total = Calories.estimateBoutCalories(samples, profile, hrMax, restingHr).first.takeIf { it > 0 }

        val endTs = samples.last().ts
        val lastMin = samples.filter { it.ts > endTs - 60 }
        val span = (lastMin.last().ts - lastMin.first().ts).toDouble()
        val perMin = if (lastMin.size >= 2 && span >= 20) {
            Calories.estimateBoutCalories(lastMin, profile, hrMax, restingHr).first * 60.0 / span
        } else null
        val weight = if (profile.weightKg > 0) profile.weightKg else 70.0
        // 1 MET ≈ 1 kcal per kg per hour.
        val met = perMin?.let { it * 60.0 / weight }

        val recent = samples.filter { it.ts > endTs - 300 }
        val pace = recent.map { it.bpm }.average().toInt()
        val extension = (1..PROJECT_MIN * 60).map { s -> HrSample(samples.last().deviceId, endTs + s, pace) }
        val projected = StrainScorer.strain(
            samples + extension, maxHR = hrMax, restingHR = restingHr ?: StrainScorer.defaultRestingHR,
            method = method, sex = profile.sex,
        )?.let { it * 21.0 / 100.0 }

        return Live(total, perMin, met, projected)
    }
}
