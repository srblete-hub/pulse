package com.noop.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PulseAgeMathTest {
    private val utc = ZoneOffset.UTC
    private fun night(day: Int, bedH: Double, wakeH: Double): Pair<Long, Long> {
        val base = LocalDate.of(2026, 11, 1).plusDays(day.toLong()).atStartOfDay(utc).toEpochSecond()
        return (base + (bedH * 3600).toLong()) to (base + 86400 + (wakeH * 3600).toLong())
    }

    @Test fun `identical bed and wake times are fully consistent`() {
        val nights = (0..6).map { night(it, 23.0, 7.0) }
        assertEquals(100.0, PulseAgeMath.sleepConsistency(nights, utc)!!, 1e-6)
    }

    @Test fun `a two hour shift lowers consistency`() {
        val nights = (0..6).map { if (it % 2 == 0) night(it, 23.0, 7.0) else night(it, 25.0 - 24.0 + 24.0, 9.0) }
        val c = PulseAgeMath.sleepConsistency(nights, utc)!!
        assertTrue("$c", c in 55.0..65.0)
    }

    @Test fun `one night is not enough`() = assertNull(PulseAgeMath.sleepConsistency(listOf(night(0, 23.0, 7.0)), utc))

    @Test fun `zone minutes skip sleep and use percent of max`() {
        val hrMax = 200
        val sleep = listOf(0L to 3600L)
        val buckets = listOf(
            100L to 150.0, // asleep, ignored
            4000L to 90.0, // 45 % → below zone 1
            4060L to 110.0, // 55 % → zone 1
            4120L to 130.0, // zone 2
            4180L to 150.0, // zone 3
            4240L to 170.0, // zone 4
            4300L to 190.0, // zone 5
        )
        val z = PulseAgeMath.zoneMinutes(buckets, 60L, sleep, hrMax)
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0, 1.0), z.toList())
    }

    @Test fun `lean mass from body fat`() {
        assertEquals(78.0, PulseAgeMath.leanPctFromBodyFat(22.0)!!, 1e-9)
        assertNull(PulseAgeMath.leanPctFromBodyFat(null))
    }
}
