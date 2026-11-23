package com.noop.pulse

import org.junit.Assert.assertEquals
import org.junit.Test

class VitalRangeTest {
    private val hist = listOf(14.0, 14.2, 13.8, 14.1, 13.9, 14.0, 14.2)

    @Test fun `calibrating before seven nights`() =
        assertEquals(VitalStatus.Calibrating, VitalRange.status(hist, 14.0, 6, 0.6))

    @Test fun `in range, above and below`() {
        assertEquals(VitalStatus.InRange, VitalRange.status(hist, 14.3, 10, 0.6))
        assertEquals(VitalStatus.High, VitalRange.status(hist, 15.0, 10, 0.6))
        assertEquals(VitalStatus.Low, VitalRange.status(hist, 13.0, 10, 0.6))
        assertEquals(VitalStatus.Missing, VitalRange.status(hist, null, 10, 0.6))
    }

    @Test fun `profile level thresholds`() {
        assertEquals(1, ProfileStats.level(0))
        assertEquals(2, ProfileStats.level(10))
        assertEquals(3, ProfileStats.level(30))
        assertEquals(4, ProfileStats.level(60))
    }
}
