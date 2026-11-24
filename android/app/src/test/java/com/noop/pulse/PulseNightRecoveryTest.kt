package com.noop.pulse

import com.noop.pulse.engine.PulseNightEngine
import com.noop.pulse.engine.PulseNightEngine.Beat
import com.noop.pulse.engine.PulseRecoveryEngine
import com.noop.pulse.engine.PulseRecoveryEngine.Inputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class PulseNightRecoveryTest {
    /** A night at [hr] bpm whose successive differences have RMSSD ≈ [rmssd], with [artefacts] share of bad beats. */
    private fun night(hr: Double, rmssd: Double, hours: Double = 6.0, artefacts: Double = 0.0, seed: Int = 3): List<Beat> {
        val rnd = Random(seed)
        val base = 60_000.0 / hr
        val out = mutableListOf<Beat>()
        var t = 0.0; var rr = base
        // Alternating ±d steps around the base give RMSSD = 2d·(…); use random walk with mean reversion.
        val step = rmssd / sqrt(2.0)
        while (t < hours * 3600) {
            rr = base + (rr - base) * 0.0 + step * (if (rnd.nextBoolean()) 1 else -1) * 0.5 * 2 / sqrt(2.0)
            var v = rr.toInt()
            if (rnd.nextDouble() < artefacts) v = if (rnd.nextBoolean()) (v * 0.55).toInt() else (v * 1.7).toInt()
            out += Beat(1_700_000_000L + t.toLong(), v)
            t += rr / 1000
        }
        return out
    }

    private fun rmssdOf(b: List<Beat>): Double {
        val d = b.zipWithNext { a, c -> (c.rrMs - a.rrMs).toDouble() }
        return sqrt(d.sumOf { it * it } / d.size)
    }

    @Test fun recoversDifferentHrvAndHeartRates() {
        for ((hr, target) in listOf(50.0 to 70.0, 58.0 to 45.0, 66.0 to 25.0)) {
            val beats = night(hr, target)
            val truth = rmssdOf(beats)
            val n = PulseNightEngine.analyze(beats)!!
            assertEquals("rmssd at $hr bpm", truth, n.rmssdMs, truth * 0.10)
            assertEquals("rhr at $hr bpm", hr, n.restingHr, 1.5)
        }
    }

    @Test fun artefactsAreRemovedNotAveragedIn() {
        val clean = night(55.0, 50.0, seed = 7)
        val truth = rmssdOf(clean)
        val dirty = night(55.0, 50.0, seed = 7, artefacts = 0.03)
        val n = PulseNightEngine.analyze(dirty)!!
        assertEquals(truth, n.rmssdMs, truth * 0.15)
        assertTrue(n.quality < 1.0)
    }

    @Test fun tooShortNightIsNotScored() {
        assertNull(PulseNightEngine.analyze(night(55.0, 50.0, hours = 0.1)))
    }

    private fun hist(n: Int, hrv: Double = 50.0, rhr: Double = 55.0) = (1..n).map {
        Inputs("2026-10-%02d".format(it), hrv * (1 + 0.05 * ((it % 3) - 1)), rhr + ((it % 3) - 1), 85.0 + (it % 4), 14.5 + 0.1 * (it % 3))
    }

    @Test fun usualNightIsAroundFiftyAndDirectionIsRight() {
        val h = hist(20)
        val usual = PulseRecoveryEngine.score(Inputs("2026-10-25", 50.0, 55.0, 86.5, 14.6), h)!!
        assertTrue("usual ${usual.recovery}", usual.recovery in 35.0..65.0)
        val good = PulseRecoveryEngine.score(Inputs("2026-10-25", 65.0, 51.0, 95.0, 14.4), h)!!
        val bad = PulseRecoveryEngine.score(Inputs("2026-10-25", 35.0, 61.0, 65.0, 15.6), h)!!
        assertTrue(good.recovery > 85 && bad.recovery < 15)
        assertEquals(4, good.factors.size)
    }

    @Test fun needsFourPriorNights() {
        assertNull(PulseRecoveryEngine.score(Inputs("2026-10-25", 50.0, 55.0, 86.0, 14.6), hist(3)))
        assertNotNull(PulseRecoveryEngine.score(Inputs("2026-10-25", 50.0, 55.0, 86.0, 14.6), hist(4)))
    }
}
