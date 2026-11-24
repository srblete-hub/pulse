package com.noop.pulse

import android.content.Context
import com.noop.data.WorkoutRow
import com.noop.pulse.engine.PulseSportEngine
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * Sport naming for automatically detected activities: reads the strap data of an activity, asks
 * [PulseSportEngine], and remembers every label the user confirms or corrects so the next guesses
 * learn their sports and their schedule.
 */
object PulseSports {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_sports", Context.MODE_PRIVATE)

    /** The sports offered in the picker: the generic ones plus every label the user has used. */
    fun choices(c: Context): List<String> {
        val own = history(c).map { it.label }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val base = PulseSportEngine.Kind.values().map { it.label } + listOf("Fútbol", "Pádel", "Tenis", "Baloncesto", "Natación", "Yoga", "Spinning", "Elíptica")
        return (own + base).distinct()
    }

    suspend fun features(c: Context, vm: AppViewModel, startTs: Long, endTs: Long): PulseSportEngine.Features? = withContext(Dispatchers.Default) {
        runCatching {
            val repo = vm.repo; val strap = vm.activeStrapId
            val hr = repo.hrSamplesUnion(strap, startTs, endTs, limit = 100_000).map { it.ts to it.bpm }
            val steps = runCatching { repo.stepSamples(strap, startTs, endTs) }.getOrDefault(emptyList())
                .map { Triple(it.ts, it.counter, it.activityClass) }
            val grav = runCatching { repo.gravitySamplesUnion(strap, startTs, endTs, limit = 100_000) }.getOrDefault(emptyList())
                .sortedBy { it.ts }.map { Triple(it.x, it.y, it.z) }
            val profile = ProfileStore.from(c)
            val rest = vm.recentDays.value.lastOrNull { it.restingHr != null }?.restingHr?.toDouble() ?: 60.0
            val at = Instant.ofEpochSecond(startTs).atZone(ZoneId.systemDefault())
            PulseSportEngine.features(hr, rest, profile.hrMax.toDouble(), steps, profile.stepTicksPerStep, grav,
                at.dayOfWeek.value, at.hour)
        }.getOrNull()
    }

    suspend fun guess(c: Context, vm: AppViewModel, startTs: Long, endTs: Long): PulseSportEngine.Guess? {
        val f = features(c, vm, startTs, endTs) ?: return null
        val g = PulseSportEngine.guess(f, history(c))
        prefs(c).edit().putString("g.$startTs.$endTs", JSONObject().put("l", g.label).put("c", g.confidence).toString()).apply()
        return g
    }

    /** What Pulse guessed for this activity (label, confidence), if it did. */
    fun guessed(c: Context, w: WorkoutRow): Pair<String, Double>? =
        prefs(c).getString("g.${w.startTs}.${w.endTs}", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { it.getString("l") to it.getDouble("c") }

    fun isConfirmed(c: Context, w: WorkoutRow) = prefs(c).contains("ok.${w.startTs}.${w.endTs}")

    /** The user confirmed or chose [label] for [w]: rename it and learn from it. */
    suspend fun confirm(c: Context, vm: AppViewModel, w: WorkoutRow, label: String) {
        val f = features(c, vm, w.startTs, w.endTs)
        val p = prefs(c)
        val arr = JSONArray(p.getString("history", "[]"))
        if (f != null) arr.put(JSONObject().put("l", label).put("d", f.durationMin).put("h", f.hrr).put("s", f.surges)
            .put("c", f.cadence ?: JSONObject.NULL).put("r", f.runShare ?: JSONObject.NULL).put("w", f.wristTurn ?: JSONObject.NULL)
            .put("wd", f.weekday).put("hr", f.hour).put("guess", guessed(c, w)?.first ?: JSONObject.NULL))
        p.edit().putString("history", arr.toString()).putBoolean("ok.${w.startTs}.${w.endTs}", true).apply()
        if (label != w.sport) vm.saveManualWorkout(w.copy(sport = label), replacing = w)
    }

    fun history(c: Context): List<PulseSportEngine.Confirmed> = runCatching {
        val arr = JSONArray(prefs(c).getString("history", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun opt(k: String) = if (o.isNull(k)) null else o.getDouble(k)
            PulseSportEngine.Confirmed(o.getString("l"), PulseSportEngine.Features(o.getDouble("d"), o.getDouble("h"), o.getDouble("s"),
                opt("c"), opt("r"), opt("w"), o.getInt("wd"), o.getInt("hr")))
        }
    }.getOrDefault(emptyList())

    /** (activities Pulse had named that the user then checked, of which the name was right). */
    fun accuracy(c: Context): Pair<Int, Int> = runCatching {
        val arr = JSONArray(prefs(c).getString("history", "[]"))
        val checked = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.isNull("guess") && it.getString("guess") != PulseAutoActivity.SPORT }
        checked.size to checked.count { it.getString("guess") == it.getString("l") }
    }.getOrDefault(0 to 0)
}
