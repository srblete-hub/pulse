package com.noop.pulse.engine

import android.content.Context
import com.noop.analytics.StrainScorer
import com.noop.ui.AppViewModel
import com.noop.ui.NoopPrefs
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Applies [PulseCalibrationEngine] to the user's data: pairs noop's on-device scores with the user's own
 * imported WHOOP export day by day, fits, and — when the user switches it on and the held-out error says
 * it helps — re-expresses noop's numbers on the user's WHOOP scale for DISPLAY. Imported days are shown
 * as imported (already WHOOP's numbers); noop's stored data is never changed.
 */
object PulseCalibration {
    data class State(
        val enabled: Boolean = false,
        val effort: PulseCalibrationEngine.Fit? = null,
        val recovery: PulseCalibrationEngine.Fit? = null,
        val sleep: PulseCalibrationEngine.Fit? = null,
        /** Log denominator noop scored Effort with when the effort fit was made. */
        val d0: Double = StrainScorer.strainDenominator,
        /** Days that carry imported WHOOP values (shown as-is, never re-mapped). */
        val importedDays: Set<String> = emptySet(),
        val pairedDays: Int = 0,
    ) {
        val effortOn get() = enabled && effort?.helps == true
        val recoveryOn get() = enabled && recovery?.helps == true
        val sleepOn get() = enabled && sleep?.helps == true
        val anyFit get() = effort != null || recovery != null || sleep != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_calibration", Context.MODE_PRIVATE)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).apply()
        _state.value = _state.value.copy(enabled = on)
    }

    suspend fun refresh(c: Context, vm: AppViewModel) {
        val enabled = prefs(c).getBoolean("enabled", false)
        _state.value = withContext(Dispatchers.Default) {
            runCatching {
                val repo = vm.repo; val strap = vm.activeStrapId
                val from = "0000-00-00"; val to = "9999-99-99"
                val imported = repo.importedSourceIds(strap).flatMap { repo.dailyMetrics(it, from, to) }.groupBy { it.day }
                    .mapValues { (_, rows) -> rows.first() }
                val computed = repo.computedDailyUnion(strap, from, to).associateBy { it.day }
                val profile = ProfileStore.from(c)
                val d0 = StrainScorer.logMapDenominator(NoopPrefs.effortMethod(c), profile.sex)
                val both = imported.keys.intersect(computed.keys)
                val effort = PulseCalibrationEngine.fitEffort(both.mapNotNull { d ->
                    val n = computed[d]?.strain; val w = imported[d]?.strain
                    if (n != null && w != null) n to w * 21.0 / 100.0 else null
                }, d0)
                val recovery = PulseCalibrationEngine.fitLinear(both.mapNotNull { d ->
                    val n = computed[d]?.recovery; val w = imported[d]?.recovery
                    if (n != null && w != null) n to w else null
                })
                val sleepImported = repo.importedSourceIds(strap).flatMap { repo.metricSeries(it, "sleep_performance", from, to) }.associate { it.day to it.value }
                val sleepComputed = repo.metricSeriesComputedUnion(strap, "sleep_performance", from, to).associate { it.day to it.value }
                val sleep = PulseCalibrationEngine.fitLinear(sleepImported.keys.intersect(sleepComputed.keys).map { sleepComputed.getValue(it) to sleepImported.getValue(it) })
                State(enabled, effort, recovery, sleep, d0, imported.keys + sleepImported.keys, both.size)
            }.getOrDefault(_state.value.copy(enabled = enabled))
        }
        prefs(c).edit().putString("last", JSONObject().put("paired", _state.value.pairedDays).toString()).apply()
    }

    // ---- display mapping (no-ops unless calibration is on and helps) ----

    fun effort100(day: String?, s100: Double?): Double? {
        val st = _state.value; val f = st.effort
        if (s100 == null || !st.effortOn || f == null || (day != null && day in st.importedDays)) return s100
        return PulseCalibrationEngine.mapEffort(s100, st.d0, f.a)
    }

    fun recovery(day: String?, v: Double?): Double? {
        val st = _state.value; val f = st.recovery
        if (v == null || !st.recoveryOn || f == null || (day != null && day in st.importedDays)) return v
        return PulseCalibrationEngine.mapLinear(v, f.a, f.b)
    }

    fun sleep(day: String?, v: Double?): Double? {
        val st = _state.value; val f = st.sleep
        if (v == null || !st.sleepOn || f == null || (day != null && day in st.importedDays)) return v
        return PulseCalibrationEngine.mapLinear(v, f.a, f.b)
    }
}
