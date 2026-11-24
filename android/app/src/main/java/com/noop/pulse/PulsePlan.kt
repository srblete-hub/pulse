package com.noop.pulse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noop.analytics.SleepDebt
import com.noop.analytics.TrainingLoadEngine
import com.noop.data.DailyMetric
import com.noop.pulse.engine.PulsePlanEngine
import com.noop.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

private fun hm(min: Double): String { val m = min.roundToInt(); return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)} min" else "$m min" }

/** Tonight's sleep need from noop's personal need + noop's sleep-debt ledger + today's Effort. */
@Composable
fun rememberTonightNeed(vm: AppViewModel, days: List<DailyMetric>, effort21Today: Double?): PulsePlanEngine.Need? {
    val ctx = LocalContext.current
    if (days.none { (it.totalSleepMin ?: 0.0) > 0 }) return null
    val base = remember(days) { com.noop.pulse.engine.PulseSleepNeed.needMin(ctx, days) }
    val debt = remember(days, base) {
        val l = SleepDebt.ledger(days.map { it.day to it.totalSleepMin }, needHours = base / 60.0)
        if (l.isDebt) l.magnitudeMin else 0.0
    }
    return PulsePlanEngine.tonightNeed(base, debt, effort21Today)
}

@Composable
fun NeedBreakdown(n: PulsePlanEngine.Need, modifier: Modifier = Modifier) {
    val parts = listOfNotNull(
        "${hm(n.baseMin)} de base",
        n.debtMin.takeIf { it >= 1 }?.let { "+${hm(it)} para recuperar deuda" },
        n.effortMin.takeIf { it >= 1 }?.let { "+${hm(it)} por el esfuerzo de hoy" },
    )
    Text("Esta noche necesitas ${hm(n.totalMin)}: ${parts.joinToString(", ")}.", style = PulseText.body, color = PulseColors.textSecondary, modifier = modifier)
}

/** Recommended bedtime when there is no sleep schedule: from the habitual wake time of recent nights. */
@Composable
fun SleepPlanCard(vm: AppViewModel, need: PulsePlanEngine.Need, modifier: Modifier, onSchedule: () -> Unit) {
    var wake by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val zone = ZoneId.systemDefault(); val now = System.currentTimeMillis() / 1000
        wake = runCatching {
            vm.repo.sleepSessionsUnion(vm.activeStrapId, now - 14 * 86_400L, now)
                .filter { it.endTs - it.startTs >= 3 * 3600 }
                .map { Instant.ofEpochSecond(it.endTs).atZone(zone).let { t -> t.hour * 60 + t.minute } }
                .let { PulsePlanEngine.habitualWake(it) }
        }.getOrNull()
    }
    val w = wake ?: return
    val bed = PulsePlanEngine.bedtime(w, need.totalMin)
    PulseCard(modifier, onClick = onSchedule) {
        CardHeader("Hora recomendada para dormir")
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("%d:%02d".format(bed / 60, bed % 60), style = PulseText.heroNumber, color = PulseColors.text)
                Overline("Acuéstate", color = PulseColors.textSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%d:%02d".format(w / 60, w % 60), style = PulseText.ringNumberLg, color = PulseColors.text)
                Overline("Tu hora habitual de despertar", color = PulseColors.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        NeedBreakdown(need)
        Text("Incluye ${PulsePlanEngine.LATENCY_MIN} min para dormirte. Configura tu horario para fijar la hora de despertar y la alarma.",
            style = PulseText.body, color = PulseColors.textTertiary, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Today's recommended Effort range from recovery and the user's usual (42-day) load. */
fun effortTargetFor(days: List<DailyMetric>, recovery: Double?): PulsePlanEngine.Target? {
    val tl = TrainingLoadEngine.evaluate(days.map { TrainingLoadEngine.DailyLoad(it.day, it.strain) })
    val usual = if (tl.isAvailable) tl.ctl?.let { it * 21.0 / 100.0 } else null
    return PulsePlanEngine.effortTarget(recovery, usual)
}

fun targetText(t: PulsePlanEngine.Target): String =
    String.format(Locale.US, "%.1f–%.1f", t.low21, t.high21)

fun targetAdvice(t: PulsePlanEngine.Target): String = when (t.zone) {
    "green" -> "Estás recuperado: buen día para apretar algo más de lo habitual."
    "yellow" -> "Recuperación media: mantén tu carga habitual sin pasarte."
    else -> "Recuperación baja: hoy toca algo suave (paseo, movilidad, técnica)."
}
