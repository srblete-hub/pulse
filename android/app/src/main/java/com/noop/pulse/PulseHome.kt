package com.noop.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ui.AppViewModel
import com.noop.ui.PulseMark
import com.noop.widget.StressPoint
import com.noop.widget.StressWidgetProducer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class HomeActions(
    val openProfile: () -> Unit,
    val openStrap: () -> Unit,
    val openSleep: () -> Unit,
    val openRecovery: () -> Unit,
    val openEffort: () -> Unit,
    val openStress: () -> Unit,
    val openCalibration: () -> Unit,
    val openGoals: () -> Unit,
    val openCharging: () -> Unit,
    val openSleepSchedule: () -> Unit,
    val openStartActivity: () -> Unit,
    val openHrv: () -> Unit,
    val openSteps: () -> Unit,
    val openCalories: () -> Unit,
)

@Composable
fun PulseHomeScreen(vm: AppViewModel, actions: HomeActions) {
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    var offset by rememberSaveable { mutableStateOf(0) }
    val metric = PulseData.metricFor(days, today, offset)
    val nights = PulseData.nightsWithSleep(days)

    var stress by remember { mutableStateOf<List<StressPoint>>(emptyList()) }
    LaunchedEffect(days.size, today?.day) {
        stress = runCatching { StressWidgetProducer.todayCurve(vm.repo, vm.activeStrapId)?.points }.getOrNull().orEmpty()
    }

    Column(
        Modifier.fillMaxSize().background(PulseColors.screenBrush).verticalScroll(rememberScrollState()).statusBarsPadding(),
    ) {
        HomeHeader(
            streak = streaks.current,
            dayLabel = PulseData.dayLabel(offset),
            canGoForward = offset < 0,
            battery = live.batteryPct,
            connected = live.connected,
            onPrev = { offset -= 1 },
            onNext = { if (offset < 0) offset += 1 },
            onProfile = actions.openProfile,
            onBattery = actions.openStrap,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseMark(size = 17.dp)
            Spacer(Modifier.width(6.dp))
            Text("PULSE", style = PulseText.wordmark, color = PulseColors.text)
        }

        // Rings: Sueño · Recuperación · Esfuerzo
        val sleep = PulseData.sleepPct(metric)
        val rec = metric?.recovery
        val eff = PulseData.effort21(metric)
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 22.dp)) {
            RingTile("SUEÑO", sleep?.let { (it / 100).toFloat() }, PulseColors.sleep, PulseData.pct(sleep), Modifier.weight(1f), actions.openSleep)
            RingTile("RECUPERACIÓN", rec?.let { (it / 100).toFloat() }, PulseColors.recovery(rec), PulseData.pct(rec), Modifier.weight(1f), actions.openRecovery)
            RingTile("ESFUERZO", eff?.let { (it / 21).toFloat() }, PulseColors.strain, PulseData.oneDecimal(eff ?: 0.0), Modifier.weight(1f), actions.openEffort, large = true)
        }

        if (nights < PulseData.calibrationNights) {
            SectionTitle("Primeros pasos", trailing = {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(PulseColors.text).pulseClick(actions.openStartActivity),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, "Iniciar actividad", tint = PulseColors.onAccent, modifier = Modifier.size(18.dp)) }
            })
            Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StepCard(
                    "Define tus objetivos",
                    "Responde unas preguntas y adaptamos los consejos diarios a lo que quieres mejorar.",
                    "Empezar", Icons.Outlined.TrackChanges, highlighted = true, onClick = actions.openGoals,
                )
                StepCard(
                    "Cómo cargar la pulsera",
                    "Llega con batería suficiente para registrar tu primer día y tu primera noche.",
                    "Ver consejos", Icons.Outlined.Battery5Bar, onClick = actions.openCharging,
                )
                StepCard(
                    "Configura tu horario de sueño",
                    "Te avisamos para desconectar y la pulsera te despierta con vibración suave.",
                    "Crear horario", Icons.Outlined.Bedtime, onClick = actions.openSleepSchedule,
                )
            }

            SectionTitle("Lo que viene")
            PulseCard(Modifier.padding(horizontal = PulseMetrics.gutter), onClick = actions.openCalibration) {
                CardHeader("Calibración")
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Duerme con la pulsera cada noche y vuelve aquí para ver tu línea base personal.",
                        style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(14.dp))
                    DashedProgressRing(nights.coerceAtMost(PulseData.calibrationNights), PulseData.calibrationNights)
                }
            }
        } else {
            SectionTitle("Primeros pasos", trailing = {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(PulseColors.text).pulseClick(actions.openStartActivity),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Add, "Iniciar actividad", tint = PulseColors.onAccent, modifier = Modifier.size(18.dp)) }
            })
        }

        SectionTitle("Mi panel")
        Column(Modifier.padding(horizontal = PulseMetrics.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (nights < PulseData.calibrationNights) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius))
                        .border(1.dp, PulseColors.hairline, RoundedCornerShape(PulseMetrics.cardRadius)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Aprendiendo de ti", style = PulseText.cardTitle, color = PulseColors.text)
                        Spacer(Modifier.height(6.dp))
                        Text("Con cada día de datos, aquí aparecerán tus tendencias.", style = PulseText.body, color = PulseColors.textSecondary)
                    }
                    MiniSparkline(listOf(10f, 18f, 14f, 26f, 22f, 34f), Modifier.size(80.dp, 48.dp))
                }
            }
            NavRow("VFC", Icons.Outlined.MonitorHeart, value = metric?.avgHrv?.let { "${it.roundToInt()} ms" }, onClick = actions.openHrv)
            NavRow("Rendimiento del sueño", Icons.Outlined.Bedtime, value = sleep?.let { PulseData.pct(it) }, onClick = actions.openSleep)
            NavRow("Pasos", Icons.Outlined.DirectionsWalk, value = metric?.steps?.let { PulseData.int(it) }, onClick = actions.openSteps)
            NavRow("Calorías", Icons.Outlined.LocalFireDepartment, value = metric?.activeKcalEst?.let { PulseData.int(it.roundToInt()) }, onClick = actions.openCalories)
            StressCard(stress, nights, actions.openStress)
        }
        EffortRecoveryChart(days, Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 12.dp))
        Spacer(Modifier.height(PulseMetrics.barClearance))
    }
}

@Composable
private fun HomeHeader(
    streak: Int,
    dayLabel: String,
    canGoForward: Boolean,
    battery: Double?,
    connected: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onProfile: () -> Unit,
    onBattery: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(16.dp)).background(PulseColors.overlay).pulseClick(onProfile).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initials = PulseProfile.initials(androidx.compose.ui.platform.LocalContext.current)
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(PulseColors.accentBrush), contentAlignment = Alignment.Center) {
                if (initials.isNotEmpty()) Text(initials, style = PulseText.small.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.onAccent)
                else Icon(Icons.Outlined.Person, "Perfil", tint = PulseColors.onAccent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Whatshot, null, tint = PulseColors.gold, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("$streak", style = PulseText.small, color = PulseColors.text)
        }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(PulseColors.overlay).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp, 26.dp).pulseClick(onPrev), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronLeft, "Día anterior", tint = PulseColors.textTertiary, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(13.dp)).background(PulseColors.chip).padding(horizontal = 15.dp, vertical = 6.dp)) {
                Text(dayLabel, style = PulseText.overline.copy(letterSpacing = PulseText.tabTitle.letterSpacing), color = if (canGoForward) PulseColors.text else PulseColors.textTertiary)
            }
            Box(Modifier.size(30.dp, 26.dp).pulseClick(onNext), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronRight, "Día siguiente", tint = if (canGoForward) PulseColors.textTertiary else PulseColors.hairlineStrong, modifier = Modifier.size(16.dp))
            }
        }
        Row(Modifier.pulseClick(onBattery), verticalAlignment = Alignment.CenterVertically) {
            Text(battery?.let { "${it.roundToInt()}%" } ?: "--%", style = PulseText.small, color = PulseColors.text)
            Spacer(Modifier.width(7.dp))
            Box(Modifier.size(19.dp, 25.dp)) {
                Box(
                    Modifier.align(Alignment.BottomStart).size(15.dp, 21.dp)
                        .border(1.6.dp, PulseColors.text, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val f = ((battery ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.padding(3.dp).fillMaxWidth().height((15 * f).dp).clip(RoundedCornerShape(1.5.dp)).background(PulseColors.text))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (connected) PulseColors.recoveryGreen else PulseColors.textTertiary),
                )
            }
        }
    }
}

@Composable
private fun RingTile(
    label: String,
    fraction: Float?,
    color: androidx.compose.ui.graphics.Color,
    value: String,
    modifier: Modifier,
    onClick: () -> Unit,
    large: Boolean = false,
) {
    Column(modifier.pulseClick(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreRing(fraction, color, value, large = large)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = PulseText.overline, color = PulseColors.text)
            Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.text, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    cta: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PulseText.cardTitle, color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text(body, style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                CtaLink(cta, onClick = onClick)
            }
            Spacer(Modifier.width(10.dp))
            if (highlighted) {
                Box(
                    Modifier.size(70.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(PulseColors.hairline, PulseColors.inset))),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = PulseColors.accent, modifier = Modifier.size(36.dp)) }
            } else {
                Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = PulseColors.textTertiary, modifier = Modifier.size(50.dp))
                }
            }
        }
    }
    if (highlighted) {
        AccentCard(padding = 20.dp, onClick = onClick) { content() }
    } else {
        PulseCard(color = PulseColors.raised, padding = 20.dp, onClick = onClick) { content() }
    }
}

@Composable
fun DashedProgressRing(done: Int, total: Int, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = 3.5.dp.toPx()
            val r = this.size.minDimension / 2 - sw - 2.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val gap = 22f
            val seg = 360f / total
            for (i in 0 until total) {
                drawArc(
                    if (i < done) PulseColors.accent else PulseColors.hairlineStrong,
                    -90f + i * seg + gap / 2, seg - gap, false, tl, Size(r * 2, r * 2),
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        Text("$done/$total", style = PulseText.ringNumber.copy(fontSize = 14.sp), color = PulseColors.text)
    }
}

@Composable
fun MiniSparkline(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val step = size.width / (values.size - 1)
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - v / max * size.height * 0.9f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, PulseColors.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun StressCard(points: List<StressPoint>, nights: Int, onClick: () -> Unit) {
    val valid = points.filter { it.level != null }
    val last = valid.lastOrNull()
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    fun hhmm(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalTime().format(fmt)
    PulseCard(onClick = onClick) {
        CardHeader("Nivel de estrés")
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                last?.let { "Actualizado a las ${hhmm(it.ts)}" } ?: "Sin lecturas todavía hoy",
                style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.weight(1f),
            )
            Text(PulseData.stressWord(last?.level), style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.stressLowLabel)
            Spacer(Modifier.width(8.dp))
            Text(PulseData.oneDecimal(last?.level), style = PulseText.bigNumber, color = PulseColors.text)
        }
        Row(Modifier.fillMaxWidth().height(130.dp).padding(top = 14.dp)) {
            Column(Modifier.width(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("3.0", "2.0", "1.0", "0.0").forEach { Text(it, style = PulseText.chartAxis, color = PulseColors.textSecondary) }
            }
            Canvas(Modifier.weight(1f).fillMaxSize()) {
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(PulseColors.hairline, Offset(0f, y), Offset(size.width, y), 1f)
                }
                if (valid.size >= 2) {
                    val t0 = valid.first().ts.toFloat()
                    val span = (valid.last().ts - valid.first().ts).coerceAtLeast(1).toFloat()
                    val p = Path()
                    valid.forEachIndexed { i, pt ->
                        val x = (pt.ts - t0) / span * size.width
                        val y = size.height - (pt.level!!.toFloat() / 3f).coerceIn(0f, 1f) * size.height
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, Brush.verticalGradient(listOf(PulseColors.stressMid, PulseColors.stressLow)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                if (last != null) {
                    val y = size.height - (last.level!!.toFloat() / 3f).coerceIn(0f, 1f) * size.height
                    drawCircle(PulseColors.text, 3.5.dp.toPx(), Offset(size.width - 3.dp.toPx(), y.coerceAtMost(size.height - 3.dp.toPx())))
                }
            }
        }
        if (valid.size >= 2) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val first = valid.first().ts
                val lastTs = valid.last().ts
                listOf(0.0, 0.33, 0.66).forEach { f -> Text(hhmm(first + ((lastTs - first) * f).toLong()), style = PulseText.chartAxis, color = PulseColors.textSecondary) }
                Text(hhmm(lastTs), style = PulseText.chartAxis.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.text)
            }
        }
        if (nights < PulseData.recoverySeedNights) {
            Spacer(Modifier.height(14.dp))
            UnlockPanel(
                "Duerme con la pulsera ${PulseData.recoverySeedNights - nights} noches para calibrar",
                nights, PulseData.recoverySeedNights, fixedSegments = true,
            )
        }
    }
}

@Composable
fun EffortRecoveryChart(days: List<com.noop.data.DailyMetric>, modifier: Modifier = Modifier) {
    val keys = remember { (-6..0).map { PulseData.dayKey(it) } }
    val byDay = remember(days) { days.associateBy { it.day } }
    PulseCard(modifier) {
        CardHeader("Esfuerzo y recuperación", trailing = {
            Box(
                Modifier.size(20.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
        })
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(PulseColors.strain))
                Spacer(Modifier.width(5.dp))
                Text("ESFUERZO", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textQuiet)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).border(2.dp, PulseColors.recoveryGreen, RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(5.dp))
                Text("RECUPERACIÓN", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textQuiet)
            }
        }
        Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 14.dp)) {
            Column(Modifier.width(22.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("21", "14", "7", "0").forEach { Text(it, style = PulseText.chartAxis.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.strain) }
            }
            Canvas(Modifier.weight(1f).fillMaxSize()) {
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(PulseColors.hairline, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                }
                val n = 7
                val slot = size.width / n
                val barW = slot * 0.42f
                val pts = mutableListOf<Offset>()
                for (i in 0 until n) {
                    val d = byDay[keys[i]]
                    val cx = slot * i + slot / 2
                    val e = PulseData.effort21(d)
                    if (e != null && e > 0) {
                        val h = (e / 21.0).toFloat().coerceIn(0f, 1f) * size.height
                        drawRoundRect(PulseColors.strain, Offset(cx - barW / 2, size.height - h), Size(barW, h), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    }
                    d?.recovery?.let { pts += Offset(cx, size.height - (it / 100.0).toFloat().coerceIn(0f, 1f) * size.height) }
                }
                if (pts.size >= 2) {
                    val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(p, PulseColors.recoveryGreen, style = Stroke(2.dp.toPx()))
                }
                pts.forEach {
                    drawCircle(PulseColors.overlay, 4.dp.toPx(), it)
                    drawCircle(PulseColors.recoveryGreen, 4.dp.toPx(), it, style = Stroke(2.dp.toPx()))
                }
            }
            Column(Modifier.width(34.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                listOf("100%", "66%", "33%", "0%").forEach { Text(it, style = PulseText.chartAxis.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PulseColors.recoveryGreen) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 34.dp, top = 8.dp)) {
            keys.forEachIndexed { i, k ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val isToday = i == keys.lastIndex
                    Text(if (isToday) "Hoy" else PulseData.shortWeekday(k), style = PulseText.chartAxis, color = if (isToday) PulseColors.text else PulseColors.textSecondary, textAlign = TextAlign.Center)
                    if (!isToday) Text(PulseData.dayOfMonth(k), style = PulseText.chartAxis, color = PulseColors.textSecondary)
                }
            }
        }
    }
}
