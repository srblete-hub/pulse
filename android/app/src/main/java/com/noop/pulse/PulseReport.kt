package com.noop.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weekly / monthly report: this period against the previous one, from the numbers Pulse shows. */
object PulseReportData {
    data class Line(val label: String, val now: Double?, val before: Double?, val fmt: (Double) -> String, val higherIsBetter: Boolean)
    data class Report(
        val days: Int, val lines: List<Line>,
        val green: Int, val yellow: Int, val red: Int,
        val bestRecovery: Pair<String, Double>?, val topEffort: Pair<String, Double>?,
    )

    fun build(all: List<DailyMetric>, period: Int, today: LocalDate = LocalDate.now()): Report {
        val curFrom = today.minusDays(period - 1L); val prevFrom = curFrom.minusDays(period.toLong())
        fun inRange(d: DailyMetric, a: LocalDate, b: LocalDate) = runCatching { LocalDate.parse(d.day) }.getOrNull()?.let { !it.isBefore(a) && it.isBefore(b) } == true
        val cur = all.filter { inRange(it, curFrom, today.plusDays(1)) }
        val prev = all.filter { inRange(it, prevFrom, curFrom) }
        fun avg(rows: List<DailyMetric>, f: (DailyMetric) -> Double?) = rows.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
        val f0 = { v: Double -> "${v.roundToInt()}" }
        val lines = listOf(
            Line("Recuperación media", avg(cur) { PulseData.rec(it) }, avg(prev) { PulseData.rec(it) }, { "${it.roundToInt()} %" }, true),
            Line("Esfuerzo medio", avg(cur) { PulseData.effort21(it) }, avg(prev) { PulseData.effort21(it) }, { String.format(Locale.US, "%.1f", it) }, true),
            Line("Sueño medio", avg(cur) { PulseData.sleepPct(it) }, avg(prev) { PulseData.sleepPct(it) }, { "${it.roundToInt()} %" }, true),
            Line("Horas dormidas", avg(cur) { it.totalSleepMin?.takeIf { m -> m > 0 }?.div(60) }, avg(prev) { it.totalSleepMin?.takeIf { m -> m > 0 }?.div(60) },
                { "${it.toInt()} h ${"%02d".format(((it - it.toInt()) * 60).roundToInt().coerceAtMost(59))}" }, true),
            Line("VFC", avg(cur) { it.avgHrv }, avg(prev) { it.avgHrv }, { "${it.roundToInt()} ms" }, true),
            Line("Pulso en reposo", avg(cur) { it.restingHr?.toDouble() }, avg(prev) { it.restingHr?.toDouble() }, { "${it.roundToInt()} lpm" }, false),
            Line("Pasos al día", avg(cur) { it.steps?.toDouble() }, avg(prev) { it.steps?.toDouble() }, f0, true),
        )
        val recs = cur.mapNotNull { d -> PulseData.rec(d)?.let { d.day to it } }
        return Report(
            period, lines,
            recs.count { it.second >= 67 }, recs.count { it.second in 34.0..66.999 }, recs.count { it.second < 34 },
            recs.maxByOrNull { it.second }, cur.mapNotNull { d -> PulseData.effort21(d)?.let { d.day to it } }.maxByOrNull { it.second },
        )
    }

    /** The one or two biggest changes, in plain words. */
    fun headline(r: Report): String {
        val moves = r.lines.mapNotNull { l ->
            if (l.now == null || l.before == null || l.before == 0.0) null else l to (l.now - l.before) / abs(l.before)
        }.filter { abs(it.second) >= 0.05 }.sortedByDescending { abs(it.second) }.take(2)
        if (moves.isEmpty()) return if (r.lines.all { it.now == null }) "Aún no hay datos de este periodo." else "Periodo estable: nada se ha movido más de un 5 % respecto al anterior."
        return moves.joinToString(" ") { (l, ch) ->
            val better = (ch > 0) == l.higherIsBetter
            "${l.label} ${if (ch > 0) "sube" else "baja"} un ${(abs(ch) * 100).roundToInt()} %${if (better) " (bien)" else " (a vigilar)"}."
        }
    }
}

@Composable
fun PulseReportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val days by com.noop.pulse.engine.pulseDays(vm)
    var month by rememberSaveable { mutableStateOf(false) }
    val period = if (month) 30 else 7
    val r = PulseReportData.build(days, period)
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(24.dp)) }
            Text("INFORME", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        Row(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.pillRadius)).background(PulseColors.raised)) {
            listOf(false to "SEMANA", true to "MES").forEach { (m, label) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(PulseMetrics.pillRadius)).background(if (month == m) PulseColors.overlay else PulseColors.raised).pulseClick { month = m }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center) { Text(label, style = PulseText.overline, color = if (month == m) PulseColors.text else PulseColors.textSecondary) }
            }
        }
        Text(PulseReportData.headline(r), style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp))
        Text("Últimos $period días frente a los $period anteriores.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp))
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
            CardHeader("Días de recuperación", showChevron = false)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                listOf(Triple(r.green, "VERDES", PulseColors.statusHigh), Triple(r.yellow, "AMARILLOS", PulseColors.statusMid), Triple(r.red, "ROJOS", PulseColors.statusLow)).forEach { (n, l, c) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", style = PulseText.heroNumber, color = c)
                        Overline(l, color = PulseColors.textSecondary)
                    }
                }
            }
        }
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
            CardHeader("Medias", showChevron = false)
            r.lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.label, style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.weight(1f))
                    val delta = if (l.now != null && l.before != null) l.now - l.before else null
                    delta?.takeIf { abs(it) > 1e-9 }?.let {
                        val better = (it > 0) == l.higherIsBetter
                        Text("${if (it > 0) "▲" else "▼"} ", style = PulseText.body, color = if (better) PulseColors.textQuiet else PulseColors.textTertiary)
                    }
                    Text(l.now?.let(l.fmt) ?: "--", style = PulseText.cardTitle, color = PulseColors.text)
                    Text("  antes ${l.before?.let(l.fmt) ?: "--"}", style = PulseText.body, color = PulseColors.textTertiary)
                }
            }
        }
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
            CardHeader("Destacados", showChevron = false)
            fun date(k: String) = runCatching { LocalDate.parse(k) }.getOrNull()?.let { "${it.dayOfMonth}/${it.monthValue}" } ?: k
            r.bestRecovery?.let { Text("Mejor recuperación: ${it.second.roundToInt()} % el ${date(it.first)}", style = PulseText.body, color = PulseColors.text, modifier = Modifier.padding(top = 12.dp)) }
            r.topEffort?.let { Text(String.format(Locale.US, "Día más exigente: %.1f el %s", it.second, date(it.first)), style = PulseText.body, color = PulseColors.text, modifier = Modifier.padding(top = 8.dp)) }
            if (r.bestRecovery == null && r.topEffort == null) Text("Sin datos todavía.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}
