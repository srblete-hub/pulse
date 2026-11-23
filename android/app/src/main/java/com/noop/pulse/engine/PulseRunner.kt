package com.noop.pulse.engine

import android.content.Context
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Per-day figures Pulse adds on top of noop's own daily row. */
data class PulseDay(
    val day: String,
    /** Minutes at 50–80 % and ≥ 80 % of max heart rate (zones 1–3 / 4–5), awake time only. */
    val zone13Min: Double = 0.0,
    val zone45Min: Double = 0.0,
    /** Steps taken while heart rate stayed below noop's active threshold, and their energy. */
    val lowHrSteps: Double = 0.0,
    val stepKcal: Double = 0.0,
    val complete: Boolean = false,
) {
    fun toJson() = JSONObject().put("day", day).put("z13", zone13Min).put("z45", zone45Min)
        .put("lowSteps", lowHrSteps).put("stepKcal", stepKcal).put("complete", complete)

    companion object {
        fun fromJson(o: JSONObject) = PulseDay(
            o.getString("day"), o.optDouble("z13", 0.0), o.optDouble("z45", 0.0),
            o.optDouble("lowSteps", 0.0), o.optDouble("stepKcal", 0.0), o.optBoolean("complete", false),
        )
    }
}

/** On-device store of computed Pulse days, observable by the UI. */
object PulseStore {
    private val _days = MutableStateFlow<Map<String, PulseDay>>(emptyMap())
    val days: StateFlow<Map<String, PulseDay>> = _days.asStateFlow()
    private var loaded = false

    private fun dir(c: Context) = File(c.applicationContext.filesDir, "pulse_days_v2").apply { mkdirs() }

    @Synchronized fun load(c: Context) {
        if (loaded) return
        val map = HashMap<String, PulseDay>()
        dir(c).listFiles()?.forEach { f -> runCatching { PulseDay.fromJson(JSONObject(f.readText())) }.getOrNull()?.let { map[it.day] = it } }
        _days.value = map
        loaded = true
    }

    fun put(c: Context, d: PulseDay) {
        File(dir(c), "${d.day}.json").writeText(d.toJson().toString())
        _days.value = _days.value + (d.day to d)
    }

    fun get(day: String) = _days.value[day]
}

/**
 * Computes [PulseDay] from noop's stored heart rate and steps, one calendar day at a time. Finished days
 * are computed once (back to [HISTORY_DAYS]); today is redone on every run.
 */
object PulseRunner {
    private val lock = Mutex()
    const val HISTORY_DAYS = 183
    private const val LIMIT = 400_000

    suspend fun run(context: Context, vm: AppViewModel) = lock.withLock {
        withContext(Dispatchers.Default) {
            PulseStore.load(context)
            val today = LocalDate.now()
            for (k in HISTORY_DAYS downTo 0) {
                val day = today.minusDays(k.toLong())
                val existing = PulseStore.get(day.toString())
                if (existing != null && existing.complete) continue
                runCatching { computeDay(context, vm, day) }.getOrNull()?.let { PulseStore.put(context, it) }
            }
        }
    }

    private suspend fun computeDay(c: Context, vm: AppViewModel, day: LocalDate): PulseDay? {
        val zone = ZoneId.systemDefault()
        val from = day.atStartOfDay(zone).toEpochSecond()
        val to = minOf(day.plusDays(1).atStartOfDay(zone).toEpochSecond(), System.currentTimeMillis() / 1000)
        if (to <= from) return null
        val complete = day.isBefore(LocalDate.now())
        val repo = vm.repo
        val strap = vm.activeStrapId
        val profile = ProfileStore.from(c)
        val noopDay = vm.recentDays.value.lastOrNull { it.day == day.toString() }

        // Per-minute heart rate (noop's 60 s buckets) outside sleep.
        val buckets = repo.hrBucketsUnion(strap, from, to, 60L)
        if (buckets.isEmpty()) return if (complete) PulseDay(day.toString(), complete = true) else null
        val sleeps = runCatching { repo.sleepSessionsUnion(strap, from - 86400, to) }.getOrDefault(emptyList())
            .map { (it.startTsAdjusted ?: it.startTs) to it.endTs }
        fun asleep(ts: Long) = sleeps.any { ts >= it.first && ts < it.second }
        val maxHr = profile.hrMax.toDouble()
        val rest = noopDay?.restingHr?.toDouble() ?: 60.0
        var z13 = 0.0; var z45 = 0.0
        val lowMinutes = HashSet<Long>()
        var lightlyUp = 0; var anyUp = 0
        for (b in buckets) {
            if (asleep(b.bucket)) continue
            val f = b.avgBpm / maxHr
            if (f >= 0.5 && f < 0.8) z13 += 1.0 else if (f >= 0.8) z45 += 1.0
            val x = PulseSpec.hrr(b.avgBpm, rest, maxHr)
            if (x < PulseSpec.NOOP_ACTIVE_HRR) lowMinutes.add(b.bucket / 60)
            if (x >= 0.1) { anyUp++; if (x < PulseSpec.NOOP_ACTIVE_HRR) lightlyUp++ }
        }

        // Steps in low-heart-rate minutes: the strap's own counter when it has one (5.0/MG), otherwise the
        // day's total split by the share of lightly-raised minutes (walking pace) in all raised minutes.
        val samples = runCatching { repo.stepSamples(strap, from, to, LIMIT) }.getOrDefault(emptyList()).sortedBy { it.ts }
        val lowSteps = if (samples.size > 1) {
            var s = 0.0
            for (i in 1 until samples.size) {
                var d = samples[i].counter - samples[i - 1].counter
                if (d < 0) d += 65536
                if (d in 1..512 && (samples[i].ts / 60) in lowMinutes) s += d
            }
            val total = noopDay?.steps?.toDouble()
            val raw = (1 until samples.size).sumOf { i -> (samples[i].counter - samples[i - 1].counter).let { if (it < 0) it + 65536 else it }.takeIf { it in 1..512 } ?: 0 }.toDouble()
            // Keep noop's step calibration: scale the low-HR share onto its calibrated daily total.
            if (total != null && raw > 0) total * (s / raw) else s
        } else {
            val total = noopDay?.steps?.toDouble() ?: 0.0
            if (anyUp == 0) total else total * lightlyUp / anyUp
        }
        return PulseDay(
            day = day.toString(), zone13Min = z13, zone45Min = z45,
            lowHrSteps = lowSteps, stepKcal = PulseSpec.stepKcal(lowSteps, profile.weightKg), complete = complete,
        )
    }
}
