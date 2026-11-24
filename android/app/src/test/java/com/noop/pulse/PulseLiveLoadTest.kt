package com.noop.pulse

import com.noop.analytics.StrainScorer
import com.noop.analytics.UserProfile
import com.noop.data.HrSample
import com.noop.pulse.engine.PulseLiveLoad
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseLiveLoadTest {
    private val profile = UserProfile(weightKg = 75.0, heightCm = 178.0, age = 35.0, sex = "male")
    private fun session(minutes: Int, bpm: Int) = (0 until minutes * 60).map { HrSample("d", 1_000_000L + it, bpm) }

    @Test fun tooFewSamplesGiveNothing() {
        val l = PulseLiveLoad.compute(session(0, 150), profile, 185.0, 60.0, StrainScorer.Method.BANISTER)
        assertNull(l.kcal); assertNull(l.projectedEffort21)
    }

    @Test fun hardSessionBurnsAndProjectsUpward() {
        val s = session(20, 155)
        val l = PulseLiveLoad.compute(s, profile, 185.0, 60.0, StrainScorer.Method.BANISTER)
        val now = StrainScorer.strain(s, 185.0, 60.0, StrainScorer.Method.BANISTER, "male")!! * 21 / 100
        assertNotNull(l.kcal)
        assertTrue("kcal/min ${l.kcalPerMin}", l.kcalPerMin!! in 8.0..20.0)
        assertTrue("MET ${l.met}", l.met!! in 6.0..16.0)
        assertTrue("projected ${l.projectedEffort21} > now $now", l.projectedEffort21!! > now)
        assertTrue(l.projectedEffort21!! <= 21.0)
    }
}
