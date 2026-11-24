package com.noop.pulse.engine

import android.content.Context
import com.noop.data.DailyMetric
import com.noop.pulse.PulseData
import com.noop.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Runs [PulseNightEngine] over every recent sleep and [PulseRecoveryEngine] over the result, keeping a
 * per-night cache (a night's beats don't change once it is over). Also measures Pulse against the
 * user's imported WHOOP days, when there are any.
 */
object PulseNights {
    const val DAYS_BACK = 60L
    private val lock = Mutex()

    data class NightRow(val day: String, val rmssd: Double, val rhr: Double, val quality: Double)
    data class Validation(val days: Int, val hrvMae: Double?, val rhrMae: Double?, val recoveryMae: Double?)
    data class State(
        val nights: Map<String, NightRow> = emptyMap(),
        val recovery: Map<String, PulseRecoveryEngine.Result> = emptyMap(),
        val validation: Validation? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_nights", Context.MODE_PRIVATE)

    suspend fun refresh(context: Context, vm: AppViewModel, days: List<DailyMetric>) = lock.withLock {
        val out = withContext(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis() / 1000
            val from = now - DAYS_BACK * 86_400
            val sessions = runCatching { vm.repo.sleepSessionsUnion(vm.activeStrapId, from, now) }.getOrDefault(emptyList())
                .filter { it.endTs - it.startTs >= 3 * 3600 }
            val p = prefs(context)
            val nights = HashMap<String, NightRow>()
            // Longest sleep per wake-up day.
            sessions.groupBy { Instant.ofEpochSecond(it.endTs).atZone(zone).toLocalDate().toString() }.forEach { (day, list) ->
                val s = list.maxByOrNull { it.endTs - it.startTs } ?: return@forEach
                val key = "n.${s.startTs}.${s.endTs}"
                val cached = p.getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
                val row = if (cached != null) {
                    if (cached.has("none")) null else NightRow(day, cached.getDouble("r"), cached.getDouble("h"), cached.getDouble("q"))
                } else {
                    val beats = runCatching { vm.repo.rrIntervalsUnion(vm.activeStrapId, s.startTs, s.endTs, limit = 200_000) }
                        .getOrDefault(emptyList()).map { PulseNightEngine.Beat(it.ts, it.rrMs) }
                    val n = PulseNightEngine.analyze(beats)
                    // Only cache nights that are clearly over (beats can still arrive in the next sync).
                    if (s.endTs < now - 6 * 3600) p.edit().putString(key,
                        if (n == null) JSONObject().put("none", 1).toString()
                        else JSONObject().put("r", n.rmssdMs).put("h", n.restingHr).put("q", n.quality).toString()).apply()
                    n?.let { NightRow(day, it.rmssdMs, it.restingHr, it.quality) }
                }
                row?.let { nights[day] = it }
            }
            val byDay = days.associateBy { it.day }
            val inputs = nights.values.sortedBy { it.day }.map { n ->
                val d = byDay[n.day]
                PulseRecoveryEngine.Inputs(n.day, n.rmssd, n.rhr, PulseData.sleepPct(d), d?.respRateBpm)
            }
            val recovery = inputs.mapNotNull { PulseRecoveryEngine.score(it, inputs) }.associateBy { it.day }

            // Against the user's own WHOOP export (imported rows live under the strap id, not "-noop").
            val validation = runCatching {
                val whoop = vm.repo.dailyMetrics("my-whoop", nights.keys.minOrNull() ?: return@runCatching null, nights.keys.maxOrNull()!!)
                    .associateBy { it.day }
                fun mae(pairs: List<Pair<Double, Double>>) = pairs.takeIf { it.isNotEmpty() }?.map { abs(it.first - it.second) }?.average()
                val hrv = nights.values.mapNotNull { n -> whoop[n.day]?.avgHrv?.let { n.rmssd to it } }
                val rhr = nights.values.mapNotNull { n -> whoop[n.day]?.restingHr?.let { n.rhr to it.toDouble() } }
                val rec = recovery.values.mapNotNull { r -> whoop[r.day]?.recovery?.let { r.recovery to it } }
                val count = maxOf(hrv.size, rhr.size, rec.size)
                if (count == 0) null else Validation(count, mae(hrv), mae(rhr), mae(rec))
            }.getOrNull()
            State(nights, recovery, validation)
        }
        _state.value = out
    }
}
