package com.noop.pulse

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noop.ui.CoachOrb

/** Remembers which first-visit explanations the user has dismissed. */
object PulseTips {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("pulse_tips", Context.MODE_PRIVATE)
    fun seen(c: Context, key: String) = prefs(c).getBoolean(key, false)
    fun markSeen(c: Context, key: String) = prefs(c).edit().putBoolean(key, true).apply()
}

private val pageBg = PulseColors.raised
private val pageBrush = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)
private val topBrush = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)
private val noticeBg = PulseColors.divider
private val noticeSeg = PulseColors.textTertiary
private val panelBrush = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)
private val panelBorder = PulseColors.divider
private val chartCardBrush = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)
private val tipScrim = Brush.verticalGradient(0f to PulseColors.base.copy(alpha = 0.55f), 0.55f to PulseColors.base.copy(alpha = 0.80f), 1f to PulseColors.base.copy(alpha = 0.96f))
private val coachBarBg = PulseColors.overlay.copy(alpha = 0.95f)

data class MetricRow(val icon: ImageVector, val label: String, val value: String, val base: String)

data class LegendItem(val name: String, val color: Color, val round: Boolean = false)

/**
 * One weekly chart card. [values] are the last 7 days, oldest first, today last. [max] scales bars.
 * Empty weeks show the mockup's "SIN DATOS" state.
 */
data class TrendChart(
    val title: String,
    val color: Color,
    val max: Double,
    val values: List<Double?>,
    val format: (Double) -> String,
    val legend: List<LegendItem> = emptyList(),
    val valueColor: Color? = null,
    val locked: String? = null,
)

/** Shared layout for the Sueño / Recuperación / Esfuerzo pages, copied from the mockups. */
@Composable
fun MetricPage(
    tipKey: String,
    tipTitle: String,
    tipBody: String,
    heroIcon: ImageVector,
    heroColor: Color,
    heroFraction: Float?,
    heroValue: String,
    heroValueSize: Int,
    heroLabel: String,
    notice: Pair<String, Int>?,
    rows: List<MetricRow>,
    rowHeight: Dp = 58.dp,
    coachLine: String,
    onBack: () -> Unit,
    onCoach: () -> Unit,
    charts: List<TrendChart>,
    middle: @Composable () -> Unit = {},
    heroCaption: String? = null,
) {
    val context = LocalContext.current
    var tip by remember { mutableStateOf(!PulseTips.seen(context, tipKey)) }
    Box(Modifier.fillMaxSize().background(pageBg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(pageBrush).statusBarsPadding()) {
            Spacer(Modifier.height(80.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp).size(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 16.dp.toPx()
                    val r = 114.dp.toPx()
                    val tl = Offset(center.x - r, center.y - r)
                    drawArc(PulseColors.track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(sw))
                    if (heroFraction != null && heroFraction > 0f) {
                        drawArc(heroColor, -90f, (360f * heroFraction.coerceIn(0f, 1f)).coerceAtLeast(1f), false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(heroIcon, null, tint = heroColor, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(heroValue, style = PulseText.bigNumber.copy(fontSize = heroValueSize.sp, lineHeight = heroValueSize.sp), color = PulseColors.text)
                    Spacer(Modifier.height(12.dp))
                    Text(heroLabel, style = PulseText.overline.copy(fontSize = 11.sp, lineHeight = 16.sp), color = PulseColors.text, textAlign = TextAlign.Center)
                }
            }
            if (heroCaption != null) {
                Text(heroCaption, style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp))
            }
            if (notice != null) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(noticeBg).padding(16.dp)) {
                    Text(notice.first, style = PulseText.body, color = PulseColors.text)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(notice.second) { Box(Modifier.width(44.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(noticeSeg)) }
                    }
                }
            }
            // Breakdown panel with the little pointer.
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).border(1.dp, panelBorder, RoundedCornerShape(PulseMetrics.cardRadius))
                        .background(panelBrush).padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    rows.forEachIndexed { i, r ->
                        Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                            Icon(r.icon, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(r.label, style = PulseText.overlineLg.copy(letterSpacing = PulseText.overline.letterSpacing * 0.83f), color = PulseColors.text, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(r.value, style = PulseText.bigNumber.copy(fontSize = 15.sp), color = PulseColors.text)
                                Text(r.base, style = PulseText.body.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = PulseColors.textTertiary)
                            }
                        }
                        if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(panelBorder))
                    }
                }
                Box(
                    Modifier.align(Alignment.TopCenter).offset(y = (-6).dp).size(12.dp).rotate(45f)
                        .background(PulseColors.overlay),
                )
            }
            middle()
            Text("Tendencias de la semana", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 36.dp, bottom = 16.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                charts.forEach { TrendCard(it) }
            }
            Spacer(Modifier.height(112.dp))
            Spacer(Modifier.navigationBarsPadding())
        }

        // Fixed top bar
        Row(
            Modifier.fillMaxWidth().background(topBrush).statusBarsPadding().height(80.dp).padding(start = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp))
            }
            Text("HOY", style = PulseText.tabTitle.copy(letterSpacing = PulseText.overline.letterSpacing * 1.16f), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(bottom = 16.dp))
            Box(
                Modifier.padding(bottom = 4.dp).size(34.dp).clip(RoundedCornerShape(17.dp)).border(1.5.dp, PulseColors.textSecondary, RoundedCornerShape(17.dp)).pulseClick { tip = true },
                contentAlignment = Alignment.Center,
            ) { Text("i", style = PulseText.ringNumber.copy(fontSize = 14.sp), color = PulseColors.textQuiet) }
        }

        // Fixed coach bar
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                .fillMaxWidth().height(66.dp).shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(coachBarBg).border(1.dp, PulseColors.hairline, RoundedCornerShape(24.dp)).pulseClick {
                    CoachLaunch.offer(
                        coachLine,
                        when (tipKey) {
                            "effort" -> "como va mi esfuerzo de hoy"
                            "recovery" -> "como esta mi recuperacion"
                            else -> "consejos para dormir mejor"
                        },
                    )
                    onCoach()
                }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoachOrb(diameter = 40.dp)
            Spacer(Modifier.width(16.dp))
            Text(coachLine, style = PulseText.body.copy(fontSize = 13.5.sp, lineHeight = 18.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PulseColors.text, modifier = Modifier.size(16.dp))
        }

        if (tip) {
            FirstVisitTip(tipTitle, tipBody) {
                PulseTips.markSeen(context, tipKey)
                tip = false
            }
        }
    }
}

/** Dark overlay with a glow on the «i» button and the explanation near the bottom, as in the mockups. */
@Composable
fun BoxScope.FirstVisitTip(title: String, body: String, onDismiss: () -> Unit) {
    Box(Modifier.matchParentSize().background(tipScrim).pulseClick { }) {
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 32.dp, end = 8.dp).size(46.dp)
                .background(Brush.radialGradient(listOf(Color(0x59FFFFFF), Color.Transparent)), RoundedCornerShape(23.dp)),
        )
        Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 32.dp, end = 32.dp, bottom = 120.dp)) {
            Text(title, style = PulseText.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
            Spacer(Modifier.height(12.dp))
            Text(body, style = PulseText.bodyLg.copy(lineHeight = 21.sp), color = PulseColors.text)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).border(2.dp, PulseColors.accentBrush, RoundedCornerShape(21.dp))
                    .background(PulseColors.base).pulseClick(onDismiss).padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) { Text("ENTENDIDO", style = PulseText.overline.copy(fontSize = 11.sp, letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = PulseColors.accentHover) }
        }
    }
}

@Composable
fun TrendCard(chart: TrendChart) {
    val keys = remember { (-6..0).map { PulseData.dayKey(it) } }
    val hasData = chart.values.any { it != null && it > 0 }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(chartCardBrush).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(chart.title.uppercase(), style = PulseText.overlineLg, color = PulseColors.text, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(16.dp))
        }
        if (chart.legend.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)) {
                chart.legend.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (l.round) Box(Modifier.size(8.dp).border(2.dp, l.color, RoundedCornerShape(4.dp)))
                        else Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(l.color))
                        Spacer(Modifier.width(8.dp))
                        Text(l.name, style = PulseText.overline.copy(fontSize = 9.5.sp), color = PulseColors.text)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            // Today's column highlight runs behind the bars and the day labels.
            Box(
                Modifier.align(Alignment.TopEnd).offset(y = (-6).dp).fillMaxWidth(0.13f).height(170.dp + 6.dp + 48.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x14FFFFFF)),
            )
            Column {
                Box(Modifier.fillMaxWidth().height(170.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        var y = 0f
                        val step = 42.dp.toPx()
                        while (y < size.height) {
                            drawLine(PulseColors.hairline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            y += step
                        }
                        drawLine(PulseColors.hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                        if (chart.locked == null) {
                            val slot = size.width / 7f
                            val w = size.width * 0.06f
                            chart.values.forEachIndexed { i, v ->
                                if (v != null && v > 0) {
                                    val h = (v / chart.max).toFloat().coerceIn(0.02f, 1f) * (size.height - 22.dp.toPx())
                                    val cx = slot * i + slot / 2
                                    drawRoundRect(chart.color, Offset(cx - w / 2, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
                                }
                            }
                        }
                    }
                    if (chart.locked == null) {
                        val today = chart.values.lastOrNull()
                        val todayFrac = today?.let { (it / chart.max).toFloat().coerceIn(0f, 1f) } ?: 0f
                        Text(
                            today?.takeIf { it > 0 }?.let(chart.format) ?: "--",
                            style = PulseText.small.copy(fontWeight = FontWeight.Bold),
                            color = if (today != null && today > 0) chart.valueColor ?: chart.color else PulseColors.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.13f).padding(bottom = (6 + 142 * todayFrac).dp),
                        )
                    }
                    if (!hasData && chart.locked == null) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.BarChart, null, tint = PulseColors.textSecondary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("SIN DATOS", style = PulseText.overline, color = PulseColors.textSecondary)
                        }
                    }
                    if (chart.locked != null) {
                        Column(
                            Modifier.align(Alignment.Center).padding(horizontal = 4.dp).fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(PulseColors.inset).padding(16.dp),
                        ) {
                            Text(chart.locked, style = PulseText.body, color = PulseColors.text)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(4) { Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.hairlineStrong)) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    keys.forEachIndexed { i, k ->
                        val isToday = i == keys.lastIndex
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val st = PulseText.body.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                            Text(PulseData.shortWeekday(k), style = st, color = if (isToday) PulseColors.text else PulseColors.textSecondary)
                            Text(PulseData.dayOfMonth(k), style = st, color = if (isToday) PulseColors.text else PulseColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** "Tu última noche" card: label, «i», value and the 30-day average. */
@Composable
fun NightCard(label: String, value: String, avg: String?, info: String, locked: Pair<String, Int>? = null) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.divider).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = PulseText.overlineLg, color = PulseColors.text, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(20.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(10.dp)).pulseClick { open = !open },
                contentAlignment = Alignment.Center,
            ) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(info, style = PulseText.body, color = PulseColors.textSecondary)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text)
            if (avg != null) {
                Spacer(Modifier.width(8.dp))
                Text("media 30 días: $avg", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (locked != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(PulseColors.inset).padding(16.dp)) {
                Text(locked.first, style = PulseText.body, color = PulseColors.text)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { i -> Box(Modifier.width(40.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i < locked.second) PulseColors.accent else PulseColors.hairlineStrong)) }
                }
            }
        }
    }
}

/** Habits card under the recovery breakdown. */
@Composable
fun HabitsCard(icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).pulseClick(onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Descubre qué te ayuda a recuperarte", style = PulseText.cardTitleLg, color = PulseColors.text)
            Spacer(Modifier.height(8.dp))
            Text("Apunta tus hábitos en el diario y verás cuáles suben o bajan tu recuperación.", style = PulseText.body, color = PulseColors.textSecondary)
            Spacer(Modifier.height(8.dp))
            CtaLink("Ver hábitos que influyen", onClick = onClick)
        }
        Spacer(Modifier.width(12.dp))
        Icon(icon, null, tint = PulseColors.textTertiary, modifier = Modifier.size(56.dp))
    }
}
