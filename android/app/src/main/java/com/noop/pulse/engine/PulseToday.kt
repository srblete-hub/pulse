package com.noop.pulse.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.StrainScorer
import com.noop.data.DailyMetric
import com.noop.pulse.PulseData
import com.noop.ui.AppViewModel
import com.noop.ui.NoopPrefs
import com.noop.ui.ProfileStore
import com.noop.ui.activeDayCycleStart
import com.noop.ui.lastScoredRecoveryDay
import com.noop.ui.chargeConfidenceTier
import com.noop.analytics.ScoreConfidence
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * What the Pulse screens show for one day, resolved exactly the way noop's Today screen resolves it:
 *  - the row: today = noop's live `today` analysis first, else the stored row (noop `displayMetric`);
 *  - Effort: today's raw heart rate integrated now with StrainScorer, floored at the stored value
 *    (noop `liveTodayStrain` + `StrainScorer.effectiveEffort`), so it moves during the day;
 *  - Recovery: while tonight isn't scored yet, the last scored night is carried over and labelled
 *    as such (noop `lastScoredRecoveryDay`). Nothing is ever invented.
 */
data class PulseDayView(
    val metric: DailyMetric?,
    /** Effort 0–21 (noop's 0–100 × 21/100). */
    val effort21: Double?,
    val recovery: Double?,
    /** Day the recovery comes from when it is carried from a previous night, else null. */
    val recoveryFromDay: String?,
    /** Sleep score 0–100 (noop's Rest), with noop's fresh carry-over on today. */
    val sleep: Double? = null,
    /** noop's own certainty tier for each score (ScoreConfidence), read, never recomputed differently. */
    val recoveryConfidence: ScoreConfidence = ScoreConfidence.CALIBRATING,
    val effortConfidence: ScoreConfidence = ScoreConfidence.CALIBRATING,
    val sleepConfidence: ScoreConfidence = ScoreConfidence.CALIBRATING,
) {
    val recoveryCaption: String? get() = recoveryFromDay?.let { "De la última noche medida · ${shortDate(it)}" }

    companion object {
        private val es = Locale("es", "ES")
        fun shortDate(key: String): String = runCatching {
            val d = LocalDate.parse(key)
            "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')}"
        }.getOrDefault(key)
    }
}

@Composable
fun pulseDayView(vm: AppViewModel, offset: Int = 0): PulseDayView {
    val context = LocalContext.current
    val days by pulseDays(vm)
    val today by pulseToday(vm)
    val activeDayCycle by vm.activeDayCycle.collectAsStateWithLifecycle()
    val metric = PulseData.metricFor(days, today, offset)
    val sleepScores by PulseSleepScore.values.collectAsStateWithLifecycle()

    val chargeBaselines by vm.chargeBaselines.collectAsStateWithLifecycle()
    // HR samples behind the day's Effort, and whether the night was staged on sparse motion (noop #345).
    var hrCount by remember { mutableStateOf(0) }
    var sleepSparse by remember { mutableStateOf(false) }
    LaunchedEffect(days, offset) {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now().plusDays(offset.toLong())
        val from = day.atStartOfDay(zone).toEpochSecond()
        val to = minOf(day.plusDays(1).atStartOfDay(zone).toEpochSecond(), System.currentTimeMillis() / 1000)
        hrCount = runCatching { vm.repo.hrSamplesUnion(vm.activeStrapId, from, to, limit = 200_000).size }.getOrDefault(0)
        sleepSparse = runCatching {
            vm.repo.sleepSessionsUnion(vm.activeStrapId, from - 12 * 3600, from + 14 * 3600).any { it.stagingSparse == true }
        }.getOrDefault(false)
    }

    var live by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(days, today, offset, activeDayCycle) {
        live = if (offset != 0) null else runCatching {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis() / 1000
            val start = activeDayCycleStart(
                mode = NoopPrefs.dayCycleMode(context),
                confirmedOrSyntheticOnset = activeDayCycle?.onsetTs,
                calendarStart = LocalDate.now().atStartOfDay(zone).toEpochSecond(),
            )
            val hr = vm.repo.hrSamplesUnion(vm.activeStrapId, start, now)
            val profile = ProfileStore.from(context)
            val maxHr = profile.hrMaxOverride.takeIf { it > 0 }?.toDouble()
                ?: if (profile.age > 0) StrainScorer.tanakaHRmax(profile.age.toDouble()) else null
            StrainScorer.strain(
                hr = hr,
                maxHR = maxHr,
                restingHR = metric?.restingHr?.toDouble() ?: StrainScorer.defaultRestingHR,
                method = NoopPrefs.effortMethod(context),
                sex = profile.sex,
            )
        }.getOrNull()
    }
    val effort = StrainScorer.effectiveEffort(live = if (offset == 0) live else null, stored = metric?.strain)

    val todayKey = maxOf(today?.day ?: "", LocalDate.now().toString())
    val carried = lastScoredRecoveryDay(
        days = days,
        selectedDayKey = metric?.day ?: PulseData.dayKey(offset),
        isToday = offset == 0,
        todayScored = metric?.recovery != null,
        isCalibrating = days.none { it.recovery != null },
        today = todayKey,
    )
    val recoveryValue = metric?.recovery ?: carried?.recovery
    val recoveryRow = if (metric?.recovery != null) metric else carried
    val sleepValue = PulseSleepScore.forDay(metric?.day ?: PulseData.dayKey(offset), offset == 0, sleepScores)
    val sleepRow = metric?.takeIf { (it.totalSleepMin ?: 0.0) > 0 }
    val sleepConf = when {
        sleepValue == null -> ScoreConfidence.CALIBRATING
        sleepRow == null -> ScoreConfidence.BUILDING
        else -> {
            val restorative = ((sleepRow.deepMin ?: 0.0) + (sleepRow.remMin ?: 0.0)) * 60.0
            val eff = sleepRow.efficiency
            if (eff == null) ScoreConfidence.forRest(true, restorative > 0)
            else ScoreConfidence.forRest(true, restorative > 0, (sleepRow.totalSleepMin ?: 0.0) * 60.0, restorative, eff, gravitySparse = sleepSparse)
        }
    }
    return PulseDayView(
        recoveryConfidence = if (recoveryValue == null) ScoreConfidence.CALIBRATING else chargeConfidenceTier(chargeBaselines, recoveryRow),
        effortConfidence = ScoreConfidence.forEffort(effort, hrCount),
        sleepConfidence = sleepConf,
        metric = metric,
        effort21 = effort?.let { it * 21.0 / 100.0 },
        sleep = PulseSleepScore.forDay(metric?.day ?: PulseData.dayKey(offset), offset == 0, sleepScores),
        recovery = metric?.recovery ?: carried?.recovery,
        recoveryFromDay = if (metric?.recovery == null) carried?.day else null,
    )
}
