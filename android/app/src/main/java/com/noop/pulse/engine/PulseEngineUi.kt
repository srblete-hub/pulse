package com.noop.pulse.engine

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.FitnessAgeEngine
import com.noop.analytics.PulseAgeEngine
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.data.WorkoutRow
import com.noop.pulse.PrefsCoachMemory
import com.noop.pulse.PulseData
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Pulse shows noop's own daily rows: every metric is measured with noop's method. */
@Composable
fun pulseDays(vm: AppViewModel): State<List<DailyMetric>> = vm.recentDays.collectAsStateWithLifecycle()

@Composable
fun pulseToday(vm: AppViewModel): State<DailyMetric?> = vm.today.collectAsStateWithLifecycle()

/** noop's personal sleep need: 75th percentile of recent nights, at least 8 h (9 h under 18), at most 9.5 h. */
object PulseSleepNeed {
    fun needMin(context: Context, days: List<DailyMetric>, day: LocalDate = LocalDate.now()): Double {
        PrefsCoachMemory(context).get("sleep_need")?.toDoubleOrNull()?.let { return it * 60.0 }
        val before = days.filter { it.day < day.toString() }.takeLast(30).mapNotNull { it.totalSleepMin?.div(60.0) }
        return RestScorer.personalizedNeedHours(before, ProfileStore.from(context).age) * 60.0
    }
}

/**
 * Physiological age = noop's Fitness Age (Nes et al.: median resting heart rate and a physical-activity
 * index over the last 7 days), shown once there are [PulseData.pulseAgeDays] days of data.
 * noop has no pace of ageing, so none is reported.
 */
object PulseAge {
    /** Why there is no age yet, as shown instead of a number. */
    fun waitText(days: List<DailyMetric>): String {
        val missing = PulseData.pulseAgeDays - PulseData.dataDays(days)
        if (missing > 0) return "Faltan $missing ${if (missing == 1) "día" else "días"} de datos para calcularla."
        val rhrNights = days.takeLast(7).count { it.restingHr != null }
        val need = FitnessAgeEngine.minCoverageDays - rhrNights
        return if (need > 0) "Necesita tu pulso en reposo de $need ${if (need == 1) "noche" else "noches"} más esta semana."
        else "Calculándola con tus datos…"
    }

    data class Out(val result: PulseAgeEngine.Result, val pace: Double?, val daysUsed: Int)

    suspend fun compute(
        context: Context,
        vm: AppViewModel,
        days: List<DailyMetric>,
        @Suppress("UNUSED_PARAMETER") workouts: List<WorkoutRow>,
        vo2: Double?,
        endOffsetDays: Int = 0,
    ): Out? = withContext(Dispatchers.Default) {
        val end = LocalDate.now().minusDays(endOffsetDays.toLong()).toString()
        val upto = days.filter { it.day <= end }
        val n = PulseData.dataDays(upto)
        if (n < PulseData.pulseAgeDays) return@withContext null
        val profile = ProfileStore.from(context)
        val age = profile.age.toDouble()
        val sex = profile.sex
        val week = upto.takeLast(7)
        val rhrs = week.mapNotNull { it.restingHr?.toDouble() }.sorted()
        // noop's readiness gate: resting HR on at least 4 of the last 7 nights, otherwise no age at all.
        if (rhrs.size < FitnessAgeEngine.minCoverageDays) return@withContext null
        val rhr = if (rhrs.size % 2 == 1) rhrs[rhrs.size / 2] else (rhrs[rhrs.size / 2 - 1] + rhrs[rhrs.size / 2]) / 2
        val active = week.mapNotNull { it.strain }.filter { it >= 30.0 }
        val pai = FitnessAgeEngine.physicalActivityIndexFromStrain(active.size, if (active.isEmpty()) 0.0 else active.average())

        // noop's stored weekly value when there is one for this window, otherwise the same formula now.
        val stored = runCatching {
            if (endOffsetDays == 0) vm.repo.latestMetricComputedUnion(vm.activeStrapId, "fitness_age")?.value
            else vm.repo.metricSeriesComputedUnion(vm.activeStrapId, "fitness_age", "0000-01-01", end).lastOrNull()?.value
        }.getOrNull()
        val fitnessAge = stored ?: FitnessAgeEngine.fitnessAge(age, sex, rhr, pai)

        // The formula is linear, so each input's contribution in years is its difference from noop's
        // reference values (resting HR 65, activity index 5).
        val ref = FitnessAgeEngine.fitnessAge(age, sex, FitnessAgeEngine.restingHRReference, FitnessAgeEngine.paiReference)
        val yRhr = FitnessAgeEngine.fitnessAge(age, sex, rhr, FitnessAgeEngine.paiReference) - ref
        val yPai = FitnessAgeEngine.fitnessAge(age, sex, FitnessAgeEngine.restingHRReference, pai) - ref
        val factors = listOf(
            PulseAgeEngine.Factor("resting_hr", rhr, yRhr),
            PulseAgeEngine.Factor("activity_index", pai, yPai),
            PulseAgeEngine.Factor("active_days", active.size.toDouble(), null),
            PulseAgeEngine.Factor("vo2max", vo2, null),
        )
        Out(PulseAgeEngine.Result(age, fitnessAge, age - fitnessAge, factors, 2), null, n)
    }
}
