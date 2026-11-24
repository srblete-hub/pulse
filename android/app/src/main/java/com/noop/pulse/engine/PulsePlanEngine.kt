package com.noop.pulse.engine

import kotlin.math.roundToInt

/**
 * Pulse's own day plan, built on noop's numbers with transparent rules (no WHOOP internals):
 *
 *  Tonight's sleep need = personal base need (noop's personalised need)
 *    + debt repayment: a third of the current sleep debt, at most [MAX_DEBT_REPAY_MIN]
 *      (paying debt back over ~3 nights rather than in one long night)
 *    + extra for a hard day: [EXTRA_PER_EFFORT_POINT_MIN] min per Effort point above [EFFORT_EXTRA_FROM],
 *      at most [MAX_EFFORT_EXTRA_MIN] (more training load → more recovery sleep).
 *  Bedtime = wake time − need − [LATENCY_MIN] (time to fall asleep).
 *
 *  Effort target from recovery, anchored on the user's own usual load (noop's 42-day chronic load):
 *    green (≥ 67 %) → 105–130 % of usual · yellow (34–66 %) → 80–100 % · red (< 34 %) → 45–70 %
 *  With no usual load yet, the anchor is [DEFAULT_ANCHOR_21].
 */
object PulsePlanEngine {
    const val MAX_DEBT_REPAY_MIN = 60.0
    const val EFFORT_EXTRA_FROM = 10.0
    const val EXTRA_PER_EFFORT_POINT_MIN = 6.0
    const val MAX_EFFORT_EXTRA_MIN = 45.0
    const val LATENCY_MIN = 15
    const val DEFAULT_ANCHOR_21 = 10.0
    const val MIN_ANCHOR_21 = 7.0

    data class Need(val baseMin: Double, val debtMin: Double, val effortMin: Double) {
        val totalMin: Double get() = baseMin + debtMin + effortMin
    }

    /** [debtMin] is the current debt as a positive number of minutes (0 when none). */
    fun tonightNeed(baseNeedMin: Double, debtMin: Double, effort21Today: Double?): Need {
        val debt = (debtMin.coerceAtLeast(0.0) / 3.0).coerceAtMost(MAX_DEBT_REPAY_MIN)
        val eff = ((effort21Today ?: 0.0) - EFFORT_EXTRA_FROM).coerceAtLeast(0.0) * EXTRA_PER_EFFORT_POINT_MIN
        return Need(baseNeedMin, debt, eff.coerceAtMost(MAX_EFFORT_EXTRA_MIN))
    }

    /** Bedtime in minutes after midnight (0–1439) for a wake time given the same way. */
    fun bedtime(wakeMin: Int, needMin: Double): Int = Math.floorMod(wakeMin - needMin.roundToInt() - LATENCY_MIN, 1440)

    /** Median wake time (minutes after midnight) of recent nights, or null with fewer than 3. */
    fun habitualWake(wakeMinutes: List<Int>): Int? {
        if (wakeMinutes.size < 3) return null
        val s = wakeMinutes.sorted()
        return s[s.size / 2]
    }

    data class Target(val low21: Double, val high21: Double, val zone: String)

    fun effortTarget(recovery: Double?, usualLoad21: Double?): Target? {
        val r = recovery ?: return null
        val anchor = (usualLoad21 ?: DEFAULT_ANCHOR_21).coerceAtLeast(MIN_ANCHOR_21)
        val (lo, hi, zone) = when {
            r >= 67 -> Triple(1.05, 1.30, "green")
            r >= 34 -> Triple(0.80, 1.00, "yellow")
            else -> Triple(0.45, 0.70, "red")
        }
        return Target((anchor * lo).coerceIn(0.0, 21.0), (anchor * hi).coerceIn(0.0, 21.0), zone)
    }
}
