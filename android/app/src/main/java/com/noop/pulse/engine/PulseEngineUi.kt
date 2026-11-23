package com.noop.pulse.engine

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.RestScorer
import com.noop.analytics.PulseAgeEngine
import com.noop.analytics.SleepDebt
import com.noop.data.DailyMetric
import com.noop.data.WorkoutRow
import com.noop.pulse.PulseActivity
import com.noop.pulse.PulseAgeMath
import com.noop.pulse.PrefsCoachMemory
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** noop's daily rows with Pulse's calorie step component added. Everything else is noop's own value. */
object PulseMerge {
    fun merge(noop: List<DailyMetric>, store: Map<String, PulseDay>): List<DailyMetric> =
        noop.map { d -> store[d.day]?.let { p -> d.copy(activeKcalEst = d.activeKcalEst?.plus(p.stepKcal)) } ?: d }
}

@Composable
fun pulseDays(vm: AppViewModel): State<List<DailyMetric>> {
    val noop by vm.recentDays.collectAsStateWithLifecycle()
    val store by PulseStore.days.collectAsState()
    return remember(vm) { derivedStateOf { PulseMerge.merge(noop, store) } }
}

@Composable
fun pulseToday(vm: AppViewModel): State<DailyMetric?> {
    val noopToday by vm.today.collectAsStateWithLifecycle()
    val store by PulseStore.days.collectAsState()
    return remember(vm) { derivedStateOf { noopToday?.let { PulseMerge.merge(listOf(it), store).first() } } }
}

/** Sleep need for the night ending on [day]: noop's personal base + yesterday's effort extra + noop's debt. */
object PulseSleepNeed {
    fun needMin(context: Context, days: List<DailyMetric>, day: LocalDate = LocalDate.now()): Double {
        val key = day.toString()
        val before = days.filter { it.day < key }
        val override = PrefsCoachMemory(context).get("sleep_need")?.toDoubleOrNull()
        val base = override ?: RestScorer.personalizedNeedHours(before.takeLast(30).mapNotNull { it.totalSleepMin?.div(60.0) }, ProfileStore.from(context).age)
        val debt = -SleepDebt.ledger(before.map { it.day to it.totalSleepMin }, needHours = base).balanceMin
        val effortYesterday = days.lastOrNull { it.day == day.minusDays(1).toString() }?.strain?.let { it * 21.0 / 100.0 }
        return PulseSpec.sleepNeedMin(base, effortYesterday, debt)
    }
}

/** Physiological age (nine factors, Gompertz) from 6-month means, and the pace from the last 30 days. */
object PulseAge {
    data class Out(val result: PulseAgeEngine.Result, val pace: Double?, val daysUsed: Int)

    suspend fun compute(
        context: Context,
        vm: AppViewModel,
        days: List<DailyMetric>,
        workouts: List<WorkoutRow>,
        vo2: Double?,
        endOffsetDays: Int = 0,
    ): Out? = withContext(Dispatchers.Default) {
        val zone = ZoneId.systemDefault()
        val profile = ProfileStore.from(context)
        val male = profile.sex.lowercase() != "female"
        val end = LocalDate.now().minusDays(endOffsetDays.toLong())
        val dob = Instant.ofEpochMilli(profile.dateOfBirthMillis).atZone(zone).toLocalDate()
        val chrono = ChronoUnit.DAYS.between(dob, end) / 365.25
        val store = PulseStore.days.value
        val bodyFat = runCatching {
            vm.repo.metricSeries(com.noop.ingest.HealthConnectImporter.HC_DEVICE, "body_fat", end.minusMonths(6).toString(), end.toString()).lastOrNull()?.value
        }.getOrNull()
        val lean = PulseAgeMath.leanPctFromBodyFat(bodyFat)

        suspend fun inputs(from: LocalDate): Pair<PulseSpec.AgeInputs, Int> {
            val f = from.toString(); val t = end.toString()
            val win = days.filter { it.day in f..t }
            val dataDays = win.count { (it.totalSleepMin ?: 0.0) > 0 || (it.steps ?: 0) > 0 }
            val pw = store.values.filter { it.day in f..t && (it.zone13Min + it.zone45Min) > 0 }
            fun avg(xs: List<Double>) = xs.takeIf { it.isNotEmpty() }?.average()
            val fromTs = from.atStartOfDay(zone).toEpochSecond()
            val toTs = end.plusDays(1).atStartOfDay(zone).toEpochSecond()
            val nights = runCatching { vm.repo.sleepSessionsUnion(vm.activeStrapId, fromTs, toTs) }.getOrDefault(emptyList())
                .map { (it.startTsAdjusted ?: it.startTs) to it.endTs }
                .groupBy { Instant.ofEpochSecond(it.second).atZone(zone).toLocalDate() }
                .values.map { v -> v.maxBy { it.second - it.first } }
            val strength = workouts.filter { it.startTs in fromTs until toTs && PulseActivity.isStrength(it) }
                .sumOf { ((it.durationS ?: (it.endTs - it.startTs).toDouble()) / 60.0) }
            return PulseSpec.AgeInputs(
                sleepHours = avg(win.mapNotNull { it.totalSleepMin?.takeIf { m -> m > 0 }?.div(60.0) }),
                regularity = PulseAgeMath.sleepConsistency(nights, zone),
                steps = avg(win.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toDouble() }),
                zones13 = if (pw.isEmpty()) null else PulseSpec.perWeek(pw.sumOf { it.zone13Min }, pw.size),
                zones45 = if (pw.isEmpty()) null else PulseSpec.perWeek(pw.sumOf { it.zone45Min }, pw.size),
                strength = if (dataDays == 0) null else PulseSpec.perWeek(strength, dataDays),
                vo2 = vo2,
                rhr = avg(win.mapNotNull { it.restingHr?.toDouble() }),
                leanPct = lean,
            ) to dataDays
        }

        val (i6, n6) = inputs(end.minusMonths(6))
        if (n6 < 21) return@withContext null
        val r6 = PulseSpec.physiologicalAge(chrono, male, i6)
        val (i30, n30) = inputs(end.minusDays(29))
        val pace = if (n30 >= 21) PulseSpec.paceOfAgeing(PulseSpec.physiologicalAge(chrono, male, i30).age, r6.age) else null
        Out(
            PulseAgeEngine.Result(
                chronoAge = chrono, pulseAge = r6.age, yearsYounger = chrono - r6.age,
                factors = r6.factors.map { PulseAgeEngine.Factor(it.key, it.value, it.years) },
                coverage = r6.factors.count { it.years != null },
            ),
            pace, n6,
        )
    }
}
