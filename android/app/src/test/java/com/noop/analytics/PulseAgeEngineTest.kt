package com.noop.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseAgeEngineTest {

    private val eps = 1e-9

    @Test
    fun `no data means physiological age equals chronological age`() {
        val r = PulseAgeEngine.compute(25.0, PulseAgeEngine.Inputs())!!
        assertEquals(25.0, r.pulseAge, eps)
        assertEquals(0, r.coverage)
        assertEquals(9, r.factors.size)
    }

    @Test
    fun `neutral anchors leave the age unchanged`() {
        val r = PulseAgeEngine.compute(
            40.0,
            PulseAgeEngine.Inputs(sleepRegularityPct = 70.0, zones13MinPerWeek = 150.0, stepsPerDay = 8000.0, zones45MinPerWeek = 30.0),
        )!!
        assertEquals(40.0, r.pulseAge, eps)
        assertEquals(4, r.coverage)
    }

    @Test
    fun `steps plateau earlier from sixty`() {
        val young = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs(stepsPerDay = 6000.0))!!
        val older = PulseAgeEngine.compute(65.0, PulseAgeEngine.Inputs(stepsPerDay = 6000.0))!!
        assertTrue(young.factors.single { it.key == "steps" }.years!! > 0)
        assertEquals(0.0, older.factors.single { it.key == "steps" }.years!!, eps)
    }

    @Test
    fun `vo2max is capped at six years younger`() {
        val r = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs(vo2max = 90.0, sex = "male"))!!
        assertEquals(-6.0, r.factors.single { it.key == "vo2max" }.years!!, eps)
    }

    @Test
    fun `fitness block shrinks when activity is measured`() {
        val alone = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs(restingHr = 75.0))!!
        val withAct = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs(restingHr = 75.0, stepsPerDay = 8000.0))!!
        assertEquals(1.2, alone.factors.single { it.key == "resting_hr" }.years!!, eps)
        assertEquals(1.2 * PulseAgeEngine.fitnessBlockWeight, withAct.factors.single { it.key == "resting_hr" }.years!!, eps)
    }

    @Test
    fun `lean mass only penalises`() {
        val high = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs(leanMassPct = 90.0, sex = "male"))!!
        val low = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs(leanMassPct = 48.0, sex = "male"))!!
        assertEquals(0.0, high.factors.single { it.key == "lean_mass" }.years!!, eps)
        assertEquals(2.0, low.factors.single { it.key == "lean_mass" }.years!!, eps)
    }

    @Test
    fun `pace is one when physiological age moves with the calendar`() {
        val a = PulseAgeEngine.compute(30.0, PulseAgeEngine.Inputs())!!
        val b = PulseAgeEngine.compute(30.0 + 7 / 365.25, PulseAgeEngine.Inputs())!!
        assertEquals(1.0, PulseAgeEngine.paceOfAgeing(a, b, 7)!!, 1e-6)
        assertNull(PulseAgeEngine.paceOfAgeing(a, b, 0))
    }
}
