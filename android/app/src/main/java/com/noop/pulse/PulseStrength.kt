package com.noop.pulse

import android.content.Context
import com.noop.protocol.Whoop5RawImu
import com.noop.pulse.engine.PulseRepEngine
import com.noop.testcentre.GroundTruthCollector
import com.noop.testcentre.ImuSessionFileStore
import com.noop.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strength sessions with set / repetition detection (WHOOP 5.0/MG only).
 *
 * Starting a "Fuerza" activity asks the strap for its 100 Hz six-axis motion stream through noop's
 * hardware-verified, reversible raw-capture commands (the same bounded, user-started capture noop's
 * collector uses); ending the activity switches it off again. [PulseRepEngine] turns the recording into
 * sets and repetitions. The user can correct any count: corrections are kept so Pulse can report how
 * often it got the count right.
 */
object PulseStrength {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_strength", Context.MODE_PRIVATE)

    data class SetResult(val startTs: Long, val endTs: Long, val detected: Int, val corrected: Int?, val rhythm: Double) {
        val reps get() = corrected ?: detected
    }
    data class Session(val id: String, val startMs: Long, val endMs: Long?, val sets: List<SetResult>)

    fun activeId(c: Context): String? = prefs(c).getString("active", null)

    /** Start the motion capture. Returns false when the connected strap can't stream it (not a 5.0/MG). */
    fun start(c: Context, vm: AppViewModel): Boolean {
        if (activeId(c) != null) return true
        val collector = GroundTruthCollector.from(c)
        val snap = collector.start(vm.activeStrapId)
        val id = snap.sessionId ?: return false
        if (!vm.ble.startGroundTruthImuCapture(id)) {
            collector.stop(); vm.ble.finishGroundTruthImuCapture(id); collector.deleteSession(id)
            return false
        }
        prefs(c).edit().putString("active", id).putLong("$id.start", System.currentTimeMillis()).apply()
        return true
    }

    /** Re-arm after a reconnect while the activity is still running. */
    fun rearm(c: Context, vm: AppViewModel) { activeId(c)?.let { vm.ble.startGroundTruthImuCapture(it) } }

    /** Stop the capture and analyse the whole session. */
    suspend fun stop(c: Context, vm: AppViewModel): Session? {
        val id = activeId(c) ?: return null
        GroundTruthCollector.from(c).stop()
        vm.ble.stopGroundTruthImuCapture()
        vm.ble.finishGroundTruthImuCapture(id)
        val end = System.currentTimeMillis()
        prefs(c).edit().remove("active").putLong("$id.end", end).putString("last", id).apply()
        val sets = analyze(c, id)
        save(c, id, sets.map { SetResult(it.startTs, it.endTs, it.reps, null, it.rhythm) })
        return load(c, id)
    }

    suspend fun analyze(c: Context, id: String): List<PulseRepEngine.DetectedSet> = withContext(Dispatchers.Default) {
        val start = prefs(c).getLong("$id.start", 0L) / 1000
        val end = prefs(c).getLong("$id.end", System.currentTimeMillis()) / 1000
        val raw = ImuSessionFileStore(c).seconds(id, start, end + 1)
        val n = PulseRepEngine.HZ
        val seconds = raw.filter { it.second.size == 6 * n }.map { (ts, col) ->
            fun ch(k: Int, scale: Double) = DoubleArray(n) { col[k * n + it] * scale }
            PulseRepEngine.Second(ts, ch(0, Whoop5RawImu.accelScale), ch(1, Whoop5RawImu.accelScale), ch(2, Whoop5RawImu.accelScale),
                ch(3, Whoop5RawImu.gyroScale), ch(4, Whoop5RawImu.gyroScale), ch(5, Whoop5RawImu.gyroScale))
        }
        PulseRepEngine.analyze(seconds)
    }

    /** Seconds of motion received so far for the running session. */
    fun coveredSeconds(c: Context): Int {
        val id = activeId(c) ?: return 0
        val start = prefs(c).getLong("$id.start", 0L) / 1000
        return ImuSessionFileStore(c).seconds(id, start, System.currentTimeMillis() / 1000 + 1).size
    }

    fun last(c: Context): Session? = prefs(c).getString("last", null)?.let { load(c, it) }

    fun correct(c: Context, id: String, index: Int, reps: Int) {
        val s = load(c, id) ?: return
        save(c, id, s.sets.mapIndexed { i, r -> if (i == index) r.copy(corrected = reps.coerceAtLeast(0)) else r })
    }

    /** (sets whose count the user confirmed or corrected, of which Pulse got exactly right). */
    fun accuracy(c: Context): Pair<Int, Int> {
        val ids = prefs(c).getStringSet("ids", emptySet()).orEmpty()
        val checked = ids.mapNotNull { load(c, it) }.flatMap { it.sets }.filter { it.corrected != null }
        return checked.size to checked.count { it.corrected == it.detected }
    }

    fun confirm(c: Context, id: String) {
        val s = load(c, id) ?: return
        save(c, id, s.sets.map { it.copy(corrected = it.corrected ?: it.detected) })
    }

    private fun save(c: Context, id: String, sets: List<SetResult>) {
        val arr = JSONArray()
        sets.forEach { arr.put(JSONObject().put("s", it.startTs).put("e", it.endTs).put("d", it.detected).put("r", it.rhythm).apply { it.corrected?.let { v -> put("c", v) } }) }
        val ids = prefs(c).getStringSet("ids", emptySet()).orEmpty() + id
        prefs(c).edit().putString("$id.sets", arr.toString()).putStringSet("ids", ids).apply()
    }

    private fun load(c: Context, id: String): Session? {
        val p = prefs(c)
        val json = p.getString("$id.sets", null) ?: return null
        val arr = JSONArray(json)
        val sets = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SetResult(o.getLong("s"), o.getLong("e"), o.getInt("d"), if (o.has("c")) o.getInt("c") else null, o.optDouble("r", 0.0))
        }
        return Session(id, p.getLong("$id.start", 0L), p.getLong("$id.end", 0L).takeIf { it > 0 }, sets)
    }
}
