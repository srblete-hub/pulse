package com.noop.pulse

import android.content.Context
import java.time.LocalDate

/**
 * Daily streak for the 🔥 chip: consecutive calendar days the user opened Pulse. Opening it again the
 * same day changes nothing; opening it the next day adds one; skipping a day starts again at 1.
 */
object PulseStreak {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_streak", Context.MODE_PRIVATE)

    /** Record today's visit and return the current streak. */
    fun visit(c: Context, today: LocalDate = LocalDate.now()): Int {
        val p = prefs(c)
        val last = p.getString("last", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val count = p.getInt("count", 0)
        val next = when {
            last == today -> count.coerceAtLeast(1)
            last == today.minusDays(1) -> count + 1
            else -> 1
        }
        p.edit().putString("last", today.toString()).putInt("count", next).putInt("best", maxOf(next, p.getInt("best", 0))).apply()
        return next
    }

    fun best(c: Context): Int = prefs(c).getInt("best", 0)
}
