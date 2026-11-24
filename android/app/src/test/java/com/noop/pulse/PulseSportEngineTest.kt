package com.noop.pulse

import com.noop.pulse.engine.PulseSportEngine
import com.noop.pulse.engine.PulseSportEngine.Confirmed
import com.noop.pulse.engine.PulseSportEngine.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class PulseSportEngineTest {
    private val rest = 55.0; private val max = 190.0

    /** A session: heart rate around [base] with surges of ±[swing] bpm every [periodS] s, steps at [spm]. */
    private fun session(minutes: Int, base: Double, swing: Double, periodS: Double, spm: Double?, runFlag: Int?, turn: Double, seed: Int = 1, weekday: Int = 2, hour: Int = 19): Features {
        val rnd = Random(seed)
        val t0 = 1_700_000_000L
        val hr = (0 until minutes * 60).map { s -> t0 + s to (base + swing * sin(2 * PI * s / periodS) + rnd.nextDouble(-2.0, 2.0)).toInt() }
        val steps = if (spm == null) emptyList() else (0..minutes).map { m -> Triple(t0 + m * 60L, (spm * m).toInt(), runFlag) }
        var g = Triple(0.0, 0.0, 1.0)
        val grav = (0 until minutes * 60).map { s ->
            val a = Math.toRadians(turn) * s
            g = Triple(sin(a) * 0.5, 0.2, 1.0); g
        }
        return PulseSportEngine.features(hr, rest, max, steps, 1.0, grav, weekday, hour)!!
    }

    private fun run(seed: Int = 1) = session(40, 160.0, 2.0, 600.0, 170.0, 2, 0.5, seed)
    private fun walk(seed: Int = 1) = session(45, 100.0, 2.0, 600.0, 110.0, 1, 0.5, seed)
    private fun bike(seed: Int = 1) = session(60, 145.0, 2.0, 900.0, null, 0, 0.5, seed)
    private fun gym(seed: Int = 1) = session(50, 115.0, 15.0, 150.0, 5.0, 0, 12.0, seed)
    private fun football(seed: Int = 1, weekday: Int = 2, hour: Int = 19) = session(70, 150.0, 18.0, 90.0, 90.0, 1, 6.0, seed, weekday, hour)

    @Test fun genericCategoriesFromSignalAlone() {
        assertEquals("Correr", PulseSportEngine.guess(run(), emptyList()).label)
        assertEquals("Caminar", PulseSportEngine.guess(walk(), emptyList()).label)
        assertEquals("Ciclismo", PulseSportEngine.guess(bike(), emptyList()).label)
        assertEquals("Fuerza", PulseSportEngine.guess(gym(), emptyList()).label)
        assertEquals("Deporte intermitente", PulseSportEngine.guess(football(), emptyList()).label)
    }

    @Test fun learnsTheUsersOwnLabelAndUsesTheirSchedule() {
        val history = (1..4).map { Confirmed("Fútbol", football(seed = it, weekday = 2, hour = 19)) } +
            (1..3).map { Confirmed("Correr", run(seed = it)) }
        val g = PulseSportEngine.guess(football(seed = 9, weekday = 2, hour = 19), history)
        assertEquals("Fútbol", g.label)
        assertTrue(g.confidence >= PulseSportEngine.MIN_CONFIDENCE)
        // A run is still a run even with football in the history.
        assertEquals("Correr", PulseSportEngine.guess(run(seed = 9), history).label)
    }

    @Test fun twoSimilarSportsAreSplitByWhenTheUserPlaysThem() {
        val history = (1..4).map { Confirmed("Fútbol", football(seed = it, weekday = 2, hour = 19)) } +
            (1..4).map { Confirmed("Pádel", football(seed = 10 + it, weekday = 6, hour = 10)) }
        assertEquals("Fútbol", PulseSportEngine.guess(football(seed = 30, weekday = 2, hour = 20), history).label)
        assertEquals("Pádel", PulseSportEngine.guess(football(seed = 31, weekday = 6, hour = 11), history).label)
    }
}
