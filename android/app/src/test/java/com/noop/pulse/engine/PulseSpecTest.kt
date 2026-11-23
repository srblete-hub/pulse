package com.noop.pulse.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseSpecTest {
    private val eps = 1e-9

    @Test fun `effort extra is five minutes per point above ten`() {
        assertEquals(0.0, PulseSpec.effortExtraMin(8.0), eps)
        assertEquals(55.0, PulseSpec.effortExtraMin(21.0), eps)
        assertEquals(480.0 + 25.0 + 30.0, PulseSpec.sleepNeedMin(8.0, 15.0, 30.0), eps)
    }

    @Test fun `walking energy is steps times weight times 0_0005`() {
        assertEquals(40.0, PulseSpec.stepKcal(1000.0, 80.0), eps)
        assertEquals(0.0, PulseSpec.stepKcal(-5.0, 80.0), eps)
    }

    @Test fun `gompertz ten percent more risk is one year`() {
        assertEquals(1.0, PulseSpec.hazardToYears(1.10), eps)
        assertEquals(0.0, PulseSpec.hazardToYears(1.0), eps)
    }

    @Test fun `reference values leave age unchanged`() {
        val r = PulseSpec.physiologicalAge(
            40.0, true,
            PulseSpec.AgeInputs(sleepHours = 7.5, regularity = 70.0, steps = 7000.0, zones13 = 150.0, zones45 = 75.0, rhr = 60.0,
                vo2 = PulseSpec.vo2Reference(40.0, true)),
        )
        assertEquals(40.0, r.age, 1e-6)
    }

    @Test fun `vo2 one MET above reference makes you about a year younger after overlap`() {
        val ref = PulseSpec.vo2Reference(40.0, true)
        val r = PulseSpec.physiologicalAge(40.0, true, PulseSpec.AgeInputs(vo2 = ref + 3.5))
        val y = r.factors.single { it.key == "vo2max" }.years!!
        assertEquals(0.7 * kotlin.math.ln(0.87) / kotlin.math.ln(1.10), y, 1e-9)
        assertTrue(y < -0.9 && y > -1.1)
    }

    @Test fun `pace projects the 30-day difference over half a year`() {
        assertEquals(1.0, PulseSpec.paceOfAgeing(35.0, 35.0), eps)
        assertEquals(0.0, PulseSpec.paceOfAgeing(34.5, 35.0), eps)
        assertEquals(3.0, PulseSpec.paceOfAgeing(37.0, 35.0), eps)
        assertEquals(-1.0, PulseSpec.paceOfAgeing(30.0, 35.0), eps)
    }

    @Test fun `lean mass only penalises below eighty percent of reference`() {
        assertEquals(1.0, PulseSpec.Hazard.leanMass(85.0, 80.0), eps)
        assertEquals(1.30, PulseSpec.Hazard.leanMass(48.0, 80.0), eps)
    }
}
