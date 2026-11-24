package com.noop.pulse

import android.content.Context
import com.noop.analytics.AutoWorkoutDetector
import com.noop.data.WorkoutRow
import com.noop.ingest.ActivityFileImporter
import com.noop.ingest.LiftingImporter
import com.noop.ui.AppViewModel
import com.noop.ui.AutoWorkoutPrefs
import com.noop.ui.WorkoutEditing
import com.noop.ui.mergeAutoDetectSavedRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Automatic activity logging, like WHOOP: after every sync, noop's own [AutoWorkoutDetector] (heart rate
 * ≥ resting + 30 bpm held ≥ 12 min, brief dips tolerated, motion-gated when gravity data exists) runs over
 * the last two days and every activity it finds is SAVED, through the same manual-workout path noop uses
 * when a suggestion is accepted — so noop computes its average/peak HR, Effort and calories from the strap.
 * Activities the user deletes are remembered and never re-added.
 */
object PulseAutoActivity {
    const val SPORT = "Actividad"
    private const val DAYS_BACK = 2L
    /** Skip a span that may still be going on. */
    private const val SETTLE_S = 10 * 60L
    private val lock = Mutex()

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_auto_activity", Context.MODE_PRIVATE)

    /** start:end tokens of activities Pulse logged by itself. */
    fun autoTokens(c: Context): Set<String> = prefs(c).getStringSet("logged", emptySet()) ?: emptySet()
    fun isAuto(c: Context, w: WorkoutRow) = "${w.startTs}:${w.endTs}" in autoTokens(c) || w.notes == NOTE
    const val NOTE = "Detectada automáticamente por Pulse"

    suspend fun run(context: Context, vm: AppViewModel) = lock.withLock {
        val toSave = withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis() / 1000
            val from = now - DAYS_BACK * 86_400L
            val repo = vm.repo
            val strap = vm.activeStrapId
            val hr = repo.hrSamplesUnion(strap, from, now, limit = 300_000)
            if (hr.size < 2) return@withContext emptyList()
            val gravity = runCatching { repo.gravitySamplesUnion(strap, from, now, limit = 300_000) }.getOrDefault(emptyList())
            val rest = vm.recentDays.value.lastOrNull { it.restingHr != null }?.restingHr
            val saved = mergeAutoDetectSavedRows(
                whoopRows = repo.workoutsUnion(strap, from, now),
                computedRows = repo.detectedWorkoutsUnion(strap, from, now),
                appleRows = repo.workouts("apple-health", from, now),
                healthConnectRows = repo.workouts("health-connect", from, now),
                liftingRows = repo.workouts(LiftingImporter.SOURCE_ID, from, now),
                activityFileRows = repo.workouts(ActivityFileImporter.SOURCE_ID, from, now),
            ).map { it.startTs to it.endTs }
            val candidates = AutoWorkoutDetector.detect(hr = hr, restingHR = rest, gravity = gravity, savedWorkouts = saved)
            val dismissedTokens = AutoWorkoutPrefs.dismissed(context)
            val dismissedSpans = repo.dismissedDetectedUnion(strap).map { it.startTs to it.endTs }.distinct()
            candidates
                .filter { it.endSec < now - SETTLE_S }
                .filterNot { AutoWorkoutPrefs.isDismissed(it, dismissedTokens, dismissedSpans) }
                // Already logged by Pulse (the save may still be in flight from a previous run).
                .filterNot { "${it.startSec}:${it.endSec}" in autoTokens(context) }
                .mapNotNull { w ->
                    WorkoutEditing.buildManualRowFromSpan(
                        deviceId = vm.deviceId, startSeconds = w.startSec, endSeconds = w.endSec,
                        sport = SPORT, avgHr = w.avgBpm, energyKcal = null,
                    )?.copy(notes = NOTE)
                }
        }
        if (toSave.isEmpty()) return@withLock
        val tokens = autoTokens(context) + toSave.map { "${it.startTs}:${it.endTs}" }
        prefs(context).edit().putStringSet("logged", tokens).apply()
        toSave.forEach { vm.saveManualWorkout(it) }
    }

    /** Delete an activity and make sure the detector never logs that span again. */
    fun delete(context: Context, vm: AppViewModel, w: WorkoutRow) {
        AutoWorkoutPrefs.dismiss(context, AutoWorkoutDetector.DetectedWorkout(w.startTs, w.endTs, w.avgHr ?: 0, w.maxHr ?: 0, ((w.endTs - w.startTs) / 60).toInt()))
        vm.deleteWorkout(w)
    }
}
