package com.noop.pulse

import com.noop.pulse.engine.PulseCalibrationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.random.Random

class PulseCalibrationEngineTest {
    private val d0 = 7201.0

    /** noop effort (0–100, scored on d0) for a given TRIMP. */
    private fun noop(t: Double) = 100 * ln(t + 1) / ln(d0)

    @Test fun recoversSeveralHiddenEffortScales() {
        for (dTrue in listOf(2000.0, 7201.0, 20000.0)) {
            val rnd = Random(4)
            val trimps = (1..30).map { rnd.nextDouble(5.0, 3000.0) }
            val pairs = trimps.map { t -> noop(t) to 21 * ln(t + 1) / ln(dTrue) + rnd.nextDouble(-0.2, 0.2) }
            val f = PulseCalibrationEngine.fitEffort(pairs, d0)!!
            assertEquals("D for $dTrue", ln(dTrue), ln(f.a), 0.05 * ln(dTrue))
            if (dTrue != 7201.0) assertTrue("helps for $dTrue: ${f.maeAfter} < ${f.maeBefore}", f.helps)
            assertTrue(f.maeAfter < 0.3)
        }
    }

    @Test fun recoversSeveralHiddenLines() {
        for ((a, b) in listOf(10.0 to 0.8, -5.0 to 1.1, 0.0 to 1.0)) {
            val rnd = Random(9)
            val pairs = (1..25).map { val x = rnd.nextDouble(10.0, 95.0); x to (a + b * x + rnd.nextDouble(-2.0, 2.0)) }
            val f = PulseCalibrationEngine.fitLinear(pairs)!!
            assertEquals(a, f.a, 4.0); assertEquals(b, f.b, 0.06)
            assertTrue(f.maeAfter < 2.5)
        }
    }

    @Test fun needsEnoughDaysAndOnlyHelpsWhenItHelps() {
        assertNull(PulseCalibrationEngine.fitLinear((1..5).map { it * 10.0 to it * 10.0 }))
        // Pure noise: no relation → calibration must not claim to help.
        val rnd = Random(1)
        val noise = (1..20).map { rnd.nextDouble(0.0, 100.0) to rnd.nextDouble(0.0, 100.0) }
        PulseCalibrationEngine.fitLinear(noise)?.let { assertTrue(it.maeAfter > 15) }
    }
}
