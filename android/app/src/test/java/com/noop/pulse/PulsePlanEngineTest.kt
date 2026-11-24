package com.noop.pulse

import com.noop.pulse.engine.PulsePlanEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PulsePlanEngineTest {
    @Test fun needAddsCappedDebtAndEffort() {
        assertEquals(480.0, PulsePlanEngine.tonightNeed(480.0, 0.0, 6.0).totalMin, 1e-9)
        assertEquals(480.0 + 30 + 24, PulsePlanEngine.tonightNeed(480.0, 90.0, 14.0).totalMin, 1e-9)
        val heavy = PulsePlanEngine.tonightNeed(480.0, 600.0, 21.0)
        assertEquals(60.0, heavy.debtMin, 1e-9); assertEquals(45.0, heavy.effortMin, 1e-9)
    }

    @Test fun bedtimeWrapsAroundMidnight() {
        assertEquals(22 * 60 + 45, PulsePlanEngine.bedtime(7 * 60, 480.0))   // 07:00 − 8 h − 15 min
        assertEquals(23 * 60 + 15, PulsePlanEngine.bedtime(7 * 60 + 30, 480.0))
        assertEquals(0 * 60 + 45, PulsePlanEngine.bedtime(9 * 60, 480.0))
    }

    @Test fun habitualWakeIsTheMedian() {
        assertNull(PulsePlanEngine.habitualWake(listOf(400, 420)))
        assertEquals(420, PulsePlanEngine.habitualWake(listOf(400, 480, 420, 410, 430)))
    }

    @Test fun targetFollowsRecoveryAndUsualLoad() {
        val g = PulsePlanEngine.effortTarget(80.0, 12.0)!!; val y = PulsePlanEngine.effortTarget(50.0, 12.0)!!; val r = PulsePlanEngine.effortTarget(20.0, 12.0)!!
        assertTrue(g.low21 > y.high21 && y.low21 > r.high21)
        assertEquals(12.0 * 1.05, g.low21, 1e-9)
        assertEquals(PulsePlanEngine.DEFAULT_ANCHOR_21 * 0.8, PulsePlanEngine.effortTarget(50.0, null)!!.low21, 1e-9)
        assertNull(PulsePlanEngine.effortTarget(null, 12.0))
    }
}
