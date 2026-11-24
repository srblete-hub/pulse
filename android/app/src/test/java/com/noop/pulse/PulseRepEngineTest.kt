package com.noop.pulse

import com.noop.pulse.engine.PulseRepEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PulseRepEngineTest {
    private val hz = PulseRepEngine.HZ

    /** A session: rest, then each (reps, period, axis) set separated by rest, with sensor noise. */
    private fun session(sets: List<Triple<Int, Double, Int>>, restS: Int = 60, seed: Int = 1): List<PulseRepEngine.Second> {
        val rnd = Random(seed)
        val samples = mutableListOf<DoubleArray>() // ax, ay, az, gx, gy, gz
        fun rest(s: Int) = repeat(s * hz) { samples += doubleArrayOf(0.02, -0.05, 1.0, 0.0, 0.0, 0.0).map { it + rnd.nextDouble(-0.004, 0.004) }.toDoubleArray() }
        rest(restS)
        for ((reps, period, axis) in sets) {
            val n = (reps * period * hz).toInt()
            for (k in 0 until n) {
                val ph = 2 * PI * k / (period * hz)
                // Wrist rotates back and forth each rep; added acceleration follows. Tilted axis per set.
                val w = 120.0 * sin(ph); val a = 0.25 * (1 - cos(ph)) / 2
                val g = DoubleArray(3); g[axis] = w; g[(axis + 1) % 3] = 0.4 * w
                val acc = doubleArrayOf(0.02, -0.05, 1.0); acc[(axis + 2) % 3] += a
                samples += doubleArrayOf(acc[0], acc[1], acc[2], g[0], g[1], g[2]).map { it + rnd.nextDouble(-0.01, 0.01) * (if (it > 2) 50 else 1) }.toDoubleArray()
            }
            rest(restS)
        }
        return samples.chunked(hz).filter { it.size == hz }.mapIndexed { i, c ->
            PulseRepEngine.Second(1_000L + i, c.map { it[0] }.toDoubleArray(), c.map { it[1] }.toDoubleArray(), c.map { it[2] }.toDoubleArray(),
                c.map { it[3] }.toDoubleArray(), c.map { it[4] }.toDoubleArray(), c.map { it[5] }.toDoubleArray())
        }
    }

    @Test fun recoversSeveralSetsWithDifferentCountsPacesAndOrientations() {
        val spec = listOf(Triple(8, 2.5, 0), Triple(12, 1.6, 1), Triple(5, 4.0, 2), Triple(10, 3.0, 0))
        val found = PulseRepEngine.analyze(session(spec))
        assertEquals("sets: $found", spec.size, found.size)
        spec.zip(found).forEach { (want, got) ->
            assertEquals("reps for $want → $got", want.first, got.reps)
            assertEquals("period for $want → $got", want.second, got.periodS, 0.25)
            assertTrue(got.rhythm >= PulseRepEngine.MIN_RHYTHM)
        }
    }

    @Test fun restAloneHasNoSets() {
        assertTrue(PulseRepEngine.analyze(session(emptyList(), restS = 300)).isEmpty())
    }
}
