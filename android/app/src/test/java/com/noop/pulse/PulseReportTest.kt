package com.noop.pulse

import com.noop.data.DailyMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PulseReportTest {
    private val today = LocalDate.of(2026, 11, 24)
    private fun day(k: Int, rec: Double, rhr: Int) = DailyMetric(deviceId = "t", day = today.minusDays(k.toLong()).toString(), recovery = rec, restingHr = rhr)

    @Test fun comparesThisWeekWithTheLastAndCountsColours() {
        val rows = (0 until 7).map { day(it, if (it < 2) 80.0 else 50.0, 52) } + (7 until 14).map { day(it, 40.0, 56) }
        val r = PulseReportData.build(rows, 7, today)
        val rec = r.lines.first { it.label == "Recuperación media" }
        assertEquals((80.0 * 2 + 50.0 * 5) / 7, rec.now!!, 1e-9); assertEquals(40.0, rec.before!!, 1e-9)
        assertEquals(2, r.green); assertEquals(5, r.yellow); assertEquals(0, r.red)
        val h = PulseReportData.headline(r)
        assertTrue(h, h.contains("Recuperación media sube") && h.contains("(bien)"))
        assertTrue(h, h.contains("Pulso en reposo baja") || r.lines.first { it.label == "Pulso en reposo" }.now!! < 56)
    }
}
