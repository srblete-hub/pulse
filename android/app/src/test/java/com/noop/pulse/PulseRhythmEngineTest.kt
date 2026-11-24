package com.noop.pulse

import com.noop.pulse.engine.PulseRhythmEngine
import com.noop.pulse.engine.PulseRhythmEngine.Sleep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin

class PulseRhythmEngineTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val start = LocalDate.of(2026, 11, 2) // a Monday

    /** One sleep per night from [bed] (hours after midnight of day d, may be <0 for the evening before) for [dur] h. */
    private fun nights(n: Int, bed: (LocalDate) -> Double, dur: (LocalDate) -> Double) = (0 until n).map { k ->
        val wakeDay = start.plusDays(k.toLong())
        val t0 = wakeDay.atStartOfDay(zone).toEpochSecond() + (bed(wakeDay) * 3600).toLong()
        Sleep(t0, t0 + (dur(wakeDay) * 3600).toLong())
    }
    private val weekend = { d: LocalDate -> d.dayOfWeek.value >= 6 }

    @Test fun perfectlyRegularSleepScoresNearHundredAndShiftedScoresLower() {
        val regular = nights(14, { -1.0 }, { 8.0 })
        val (sri, _) = PulseRhythmEngine.sri(regular, zone, start.plusDays(13))!!
        assertTrue("regular $sri", sri > 95)
        val irregular = nights(14, { d -> if (d.dayOfMonth % 2 == 0) -1.0 else 2.0 }, { 8.0 })
        val (sri2, _) = PulseRhythmEngine.sri(irregular, zone, start.plusDays(13))!!
        assertTrue("irregular $sri2 < regular $sri", sri2 < sri - 10)
    }

    @Test fun socialJetlagRecoversTheWeekendShift() {
        for (shift in listOf(0.0, 1.5, 3.0)) {
            val s = nights(14, { d -> if (weekend(d)) -1.0 + shift else -1.0 }, { 7.5 })
            val j = PulseRhythmEngine.socialJetlag(s, zone)!!
            assertEquals("shift $shift", shift, j.socialJetlagH, 0.05)
        }
    }

    @Test fun chronotypeCorrectsForCatchUpSleep() {
        // Work: 23:30–06:30 (mid 03:00). Free: 23:30–08:30 (mid 04:00), 2 h longer.
        val s = nights(14, { -0.5 }, { d -> if (weekend(d)) 9.0 else 7.0 })
        val j = PulseRhythmEngine.socialJetlag(s, zone)!!
        // SDweek = (7·5 + 9·2)/7 = 7.571; MSFsc = 4.0 − (9 − 7.571)/2 = 3.286
        assertEquals(3.286, j.msfscHour, 0.02)
        assertEquals(PulseRhythmEngine.Chronotype.EARLY, PulseRhythmEngine.chronotype(j.msfscHour))
    }

    @Test fun nonParametricRhythmSeparatesStableFromFragmented() {
        val days = 7
        val stable = (0 until days * 24).map { h -> 50 + 45 * sin(2 * PI * (h % 24 - 8) / 24.0) }
        val r = PulseRhythmEngine.rhythm(stable, 0)!!
        assertTrue("IS ${r.interdailyStability}", r.interdailyStability > 0.95)
        assertTrue("IV ${r.intradailyVariability}", r.intradailyVariability < 0.2)
        assertTrue("RA ${r.relativeAmplitude}", r.relativeAmplitude > 0.6)
        val noisy = (0 until days * 24).map { h -> if (h % 2 == 0) 90.0 else 10.0 }
        val rn = PulseRhythmEngine.rhythm(noisy, 0)!!
        assertTrue("fragmented IV ${rn.intradailyVariability}", rn.intradailyVariability > 1.5)
    }

    @Test fun refusesWithoutEnoughData() {
        assertNull(PulseRhythmEngine.sri(nights(3, { -1.0 }, { 8.0 }), zone, start.plusDays(2)))
        assertNull(PulseRhythmEngine.socialJetlag(nights(4, { -1.0 }, { 8.0 }), zone))
        assertNull(PulseRhythmEngine.rhythm(List(48) { 1.0 }, 0))
    }
}
