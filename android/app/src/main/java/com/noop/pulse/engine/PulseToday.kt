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
    return PulseDayView(
        metric = metric,
        effort21 = effort?.let { it * 21.0 / 100.0 },
        recovery = metric?.recovery ?: carried?.recovery,
        recoveryFromDay = if (metric?.recovery == null) carried?.day else null,
    )
}
