package com.noop.pulse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.ui.AppViewModel
import com.noop.ui.CoachOrb
import com.noop.widget.StressPoint
import com.noop.widget.StressWidgetProducer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(ts: Long) = Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalTime().format(hhmm)
private val stressBar = Brush.horizontalGradient(listOf(PulseColors.stressLow, PulseColors.stressMid, PulseColors.stressHigh))

fun stressColor(level: Double?): Color = when {
    level == null -> PulseColors.textSecondary
    level < 1 -> PulseColors.stressLowLabel
    level < 2 -> PulseColors.accentHover
    else -> PulseColors.stressHigh
}

/** Draws a stress curve (0–3) with the lilac→blue vertical gradient. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStressCurve(points: List<StressPoint>, stroke: Float) {
    val valid = points.filter { it.level != null }
    if (valid.size < 2) return
    val t0 = valid.first().ts.toFloat()
    val span = (valid.last().ts - valid.first().ts).coerceAtLeast(1).toFloat()
    val p = Path()
    valid.forEachIndexed { i, pt ->
        val x = (pt.ts - t0) / span * size.width
        val y = size.height - (pt.level!!.toFloat() / 3f).coerceIn(0f, 1f) * size.height
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, Brush.verticalGradient(listOf(PulseColors.stressHigh, PulseColors.stressMid, PulseColors.stressLow)), style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun demoCurve(): List<StressPoint> {
    val base = 0L
    val ys = listOf(0.3, 0.5, 0.4, 1.1, 1.6, 1.2, 2.3, 2.6, 1.8, 1.0, 1.4, 0.8, 0.5, 0.9, 1.5, 1.2)
    return ys.mapIndexed { i, y -> StressPoint(base + i * 600L, y) }
}

@Composable
fun PulseStressIntro(onClose: () -> Unit, onDone: () -> Unit) {
    IntroFlow(
        steps = listOf(
            IntroStep(
                "Conoce tu nivel de estrés",
                listOf("Mira cómo reacciona tu cuerpo a lo largo del día, en el trabajo, entrenando o descansando.", "Y bájalo cuando lo necesites con ejercicios de respiración guiados."),
                textTop = 610.dp,
                background = androidx.compose.ui.graphics.SolidColor(PulseColors.base),
            ) {
                Canvas(Modifier.align(Alignment.TopCenter).padding(top = 140.dp, start = 32.dp, end = 32.dp).fillMaxWidth().height(300.dp)) {
                    drawStressCurve(demoCurve(), 4.dp.toPx())
                }
            },
            IntroStep("Una escala de 0 a 3", listOf("Tu nivel de estrés resume cómo está activado tu cuerpo ahora mismo. No es bueno ni malo: bajo te ayuda a descansar y alto te prepara para rendir.")) {
                Column(Modifier.align(Alignment.TopStart).padding(start = 40.dp, end = 40.dp, top = 168.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(
                        Triple("3.0", "ALTO", PulseColors.stressHigh to 0.86f),
                        Triple("2.0", "MEDIO", PulseColors.accent to 0.64f),
                        Triple("1.0", "BAJO", PulseColors.stressLow to 0.42f),
                    ).forEach { (n, label, cf) ->
                        ScaleLine(n)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(44.dp))
                            Box(
                                Modifier.fillMaxWidth(cf.second).height(64.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius))
                                    .background(Brush.horizontalGradient(listOf(cf.first.copy(alpha = 0.05f), cf.first.copy(alpha = 0.55f)))).padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Text(label, style = PulseText.overlineLg, color = PulseColors.text) }
                        }
                    }
                    ScaleLine("0.0")
                }
            },
            IntroStep("Sigue tu estrés en directo", listOf("Recorre la gráfica del día para descubrir qué situaciones te activan y cuáles te ayudan a calmarte.")) {
                Column(Modifier.align(Alignment.TopCenter).padding(start = 40.dp, end = 40.dp, top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("1.2", style = PulseText.bigNumber.copy(fontSize = 54.sp, lineHeight = 54.sp), color = PulseColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text("Hoy a las 10:05", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(16.dp))
                    StressGauge(1.2)
                    Spacer(Modifier.height(32.dp))
                    Canvas(Modifier.fillMaxWidth().height(140.dp)) { drawStressCurve(demoCurve(), 3.dp.toPx()) }
                    Spacer(Modifier.height(24.dp))
                    Text("← Desliza para ver cada momento →", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
                }
            },
            IntroStep("Respira para cambiar tu estado", listOf("Algunos ritmos de respiración te calman y otros te activan. Practicarlos a menudo te ayuda a gestionar mejor los días intensos.")) {
                BreathCircle(Modifier.align(Alignment.TopCenter).padding(top = 128.dp), "INHALA", "4", 1f)
            },
            IntroStep("Resumen cada noche", listOf("Al final del día te avisamos de cuánto tiempo has pasado en cada nivel, para que entiendas cómo te afecta el estrés y puedas compensarlo respirando."), textTop = 610.dp) {
                Box(
                    Modifier.align(Alignment.TopCenter).padding(start = 24.dp, end = 24.dp, top = 140.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).padding(horizontal = 16.dp, vertical = 16.dp),
                ) { Canvas(Modifier.fillMaxWidth().height(120.dp)) { drawStressCurve(demoCurve(), 3.dp.toPx()) } }
                Row(
                    Modifier.align(Alignment.TopCenter).padding(start = 48.dp, end = 48.dp, top = 340.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(PulseMetrics.cardRadius))
                        .clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.hairlineStrong).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(PulseColors.base), contentAlignment = Alignment.Center) { com.noop.ui.PulseMark(size = 22.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tu día en estrés", style = PulseText.body.copy(fontWeight = FontWeight.Bold), color = PulseColors.text)
                        Text("2 h 10 min en nivel alto. Toca para ver el detalle.", style = PulseText.body.copy(fontSize = 11.5.sp, lineHeight = 15.sp), color = PulseColors.textQuiet)
                    }
                    Text("21:00", style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textSecondary)
                }
            },
            IntroStep(
                "¡Vamos allá!", listOf("Ya conoces lo esencial. Entra y descubre cómo ha ido tu día."), textTop = 610.dp,
                background = androidx.compose.ui.graphics.SolidColor(PulseColors.raised),
            ) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 112.dp).rotate(-8f).size(180.dp, 370.dp).shadow(8.dp, RoundedCornerShape(30.dp))
                        .clip(RoundedCornerShape(30.dp)).background(PulseColors.base).border(6.dp, PulseColors.divider, RoundedCornerShape(30.dp)).padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("NIVEL DE ESTRÉS", style = PulseText.overline.copy(fontSize = 6.5.sp), color = PulseColors.textSecondary)
                    Spacer(Modifier.height(24.dp))
                    Text("1.5", style = PulseText.bigNumber.copy(fontSize = 30.sp), color = PulseColors.text)
                    Text("MEDIO", style = PulseText.overline.copy(fontSize = 7.sp), color = PulseColors.accentHover)
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { StressGauge(1.5, thin = true) }
                    Canvas(Modifier.padding(top = 24.dp).fillMaxWidth().height(90.dp)) { drawStressCurve(demoCurve(), 2.dp.toPx()) }
                    Text(
                        "Tu cuerpo está tranquilo: el pulso está cerca de su valor de reposo.",
                        style = PulseText.body.copy(fontSize = 7.5.sp, lineHeight = 10.sp), color = PulseColors.textQuiet,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).clip(RoundedCornerShape(10.dp)).background(PulseColors.inset).padding(8.dp),
                    )
                }
            },
        ),
        onClose = onClose, onDone = onDone,
    )
}

@Composable
private fun ScaleLine(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PulseText.small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = PulseColors.textSecondary, modifier = Modifier.width(30.dp))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f).height(1.dp).background(PulseColors.hairline))
    }
}

@Composable
fun StressGauge(level: Double, thin: Boolean = false, segmented: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(if (thin) 10.dp else 20.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.fillMaxWidth().height(if (thin) 4.dp else if (segmented) 10.dp else 8.dp).clip(RoundedCornerShape(5.dp))
                .background(
                    if (segmented) Brush.horizontalGradient(
                        0f to PulseColors.stressLow, 0.33f to PulseColors.stressLow, 0.3301f to PulseColors.stressMid,
                        0.66f to PulseColors.stressMid, 0.6601f to PulseColors.stressHigh, 1f to PulseColors.stressHigh,
                    ) else stressBar,
                ),
        )
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val x = maxWidth * (level / 3.0).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier.offset(x = x - 2.dp).width(if (thin) 2.dp else if (segmented) 6.dp else 4.dp).height(if (thin) 10.dp else if (segmented) 24.dp else 20.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Color.White),
            )
        }
    }
}

@Composable
private fun BreathCircle(modifier: Modifier, label: String, count: String, scale: Float) {
    Box(modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().border(1.dp, PulseColors.accent.copy(alpha = 0.25f), RoundedCornerShape(150.dp)))
        Box(Modifier.padding(28.dp).fillMaxSize().border(1.dp, PulseColors.accent.copy(alpha = 0.4f), RoundedCornerShape(122.dp)))
        Box(
            Modifier.padding(64.dp).fillMaxSize().scale(scale).clip(RoundedCornerShape(88.dp))
                .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised))
                .border(1.5.dp, PulseColors.accent, RoundedCornerShape(88.dp)),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = PulseText.overline.copy(fontSize = 12.sp, letterSpacing = PulseText.tabTitle.letterSpacing), color = PulseColors.text)
            Spacer(Modifier.height(8.dp))
            Text(count, style = PulseText.bigNumber.copy(fontSize = 34.sp), color = PulseColors.text)
        }
    }
}

@Composable
fun PulseStressScreen(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit, onBreathe: (Boolean) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var introDone by remember { mutableStateOf(PulseTips.seen(context, "stress_intro")) }
    if (!introDone) {
        PulseStressIntro(onClose = onBack, onDone = { PulseTips.markSeen(context, "stress_intro"); introDone = true })
        return
    }
    val days by com.noop.pulse.engine.pulseDays(vm)
    val nights = PulseData.nightsWithSleep(days)
    var offset by rememberSaveable { mutableStateOf(0) }
    var points by remember { mutableStateOf<List<StressPoint>>(emptyList()) }
    var tipOpen by remember { mutableStateOf(false) }
    var showHow by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(offset, days.size) {
        val now = if (offset == 0) System.currentTimeMillis() / 1000 else
            LocalDate.now().plusDays(offset.toLong()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toEpochSecond()
        points = runCatching { StressWidgetProducer.todayCurve(vm.repo, vm.activeStrapId, nowSeconds = now)?.points }.getOrNull().orEmpty()
    }
    val valid = points.filter { it.level != null }
    var scrub by remember(points) { mutableStateOf<Int?>(null) }
    val shown = scrub?.let { valid.getOrNull(it) } ?: valid.lastOrNull()
    val level = shown?.level

    Box(Modifier.fillMaxSize().background(PulseColors.raised)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).statusBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
                Text("NIVEL DE ESTRÉS", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).pulseClick(onSettings), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Settings, "Ajustes", tint = PulseColors.text, modifier = Modifier.size(23.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).pulseClick { offset-- }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ChevronLeft, "Día anterior", tint = PulseColors.text, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(56.dp))
                Text(PulseData.dayLabel(offset), style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
                Spacer(Modifier.width(56.dp))
                Box(Modifier.size(36.dp).pulseClick { if (offset < 0) offset++ }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChevronRight, "Día siguiente", tint = if (offset < 0) PulseColors.text else PulseColors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.padding(start = 40.dp, end = 40.dp, top = 24.dp).fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(PulseData.oneDecimal(level), style = PulseText.bigNumber.copy(fontSize = 60.sp, lineHeight = 60.sp), color = PulseColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text(PulseData.stressWord(level), style = PulseText.overline.copy(fontSize = 13.sp, letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = stressColor(level))
                    Spacer(Modifier.height(8.dp))
                    Text(shown?.let { "A las ${clock(it.ts)}" } ?: "Sin lecturas", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
                    Spacer(Modifier.height(24.dp))
                    StressGauge(level ?: 0.0, segmented = true)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        listOf("BAJO" to TextAlign.Start, "MEDIO" to TextAlign.Center, "ALTO" to TextAlign.End).forEach { (t, a) ->
                            Text(t, style = PulseText.overline.copy(fontSize = 9.5.sp), color = PulseColors.textSecondary, textAlign = a, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-6).dp).size(32.dp).border(1.5.dp, PulseColors.textSecondary, RoundedCornerShape(16.dp)).pulseClick { tipOpen = true },
                    contentAlignment = Alignment.Center,
                ) { Text("i", style = PulseText.ringNumber.copy(fontSize = 13.sp), color = PulseColors.textQuiet) }
            }
            // Day curve, tap/drag to scrub.
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 48.dp).fillMaxWidth().height(180.dp)) {
                Column(Modifier.width(30.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("3.0", "2.0", "1.0", "0.0").forEach { Text(it, style = PulseText.chartAxis.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.textSecondary) }
                }
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val wPx = constraints.maxWidth.toFloat()
                    Canvas(
                        Modifier.fillMaxSize().pointerInputScrub(valid.size, wPx) { scrub = it },
                    ) {
                        for (i in 0..3) {
                            val y = size.height * i / 3f
                            drawLine(PulseColors.track, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                        for (i in 1..2) {
                            val x = size.width * i / 3f
                            drawLine(PulseColors.raised, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                        }
                        drawStressCurve(points, 2.5.dp.toPx())
                        if (valid.size >= 2 && shown != null) {
                            val t0 = valid.first().ts.toFloat()
                            val span = (valid.last().ts - valid.first().ts).coerceAtLeast(1).toFloat()
                            val x = (shown.ts - t0) / span * size.width
                            val y = size.height - (shown.level!!.toFloat() / 3f).coerceIn(0f, 1f) * size.height
                            if (scrub != null) drawLine(PulseColors.textSecondary, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            drawCircle(PulseColors.text, 3.dp.toPx(), Offset(x, y))
                        }
                    }
                }
            }
            if (valid.size >= 2) {
                Row(Modifier.padding(start = 48.dp, end = 16.dp, top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val a = valid.first().ts; val b = valid.last().ts
                    listOf(0.0, 1 / 3.0, 2 / 3.0).forEach { f -> Text(clock(a + ((b - a) * f).toLong()), style = PulseText.chartAxis.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.textSecondary) }
                    Text(clock(b), style = PulseText.chartAxis.copy(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
                }
            }
            if (nights < PulseData.recoverySeedNights) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 32.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.divider).padding(16.dp)) {
                    Text("Duerme con la pulsera ${PulseData.recoverySeedNights - nights} noches para ver tu historial completo.", style = PulseText.body, color = PulseColors.text)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i -> Box(Modifier.width(44.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i < nights) PulseColors.accent else PulseColors.textTertiary)) }
                    }
                }
            }
            Text("RESPIRACIÓN GUIADA", style = PulseText.overlineLg, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionCard("PARA RELAJARTE", "Respiración lenta", androidx.compose.ui.graphics.SolidColor(PulseColors.raised), Icons.Outlined.Air) { onBreathe(true) }
                SessionCard("PARA ACTIVARTE", "Respiración rápida", androidx.compose.ui.graphics.SolidColor(PulseColors.raised), Icons.Outlined.Bolt) { onBreathe(false) }
                Spacer(Modifier.width(16.dp))
            }
            // Cómo funciona
            Box(Modifier.padding(top = 32.dp).fillMaxWidth().background(PulseColors.base)) {
                Box(Modifier.align(Alignment.CenterStart).width(4.dp).height(if (showHow) 330.dp else 76.dp).background(Brush.verticalGradient(listOf(PulseColors.stressHigh, PulseColors.accent))))
                Column(Modifier.padding(top = 24.dp, bottom = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CÓMO FUNCIONA", style = PulseText.overlineLg, color = PulseColors.accent, modifier = Modifier.weight(1f))
                        Row(Modifier.height(32.dp).pulseClick { showHow = !showHow }, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showHow) "OCULTAR" else "MOSTRAR", style = PulseText.overline, color = PulseColors.text)
                            Spacer(Modifier.width(8.dp))
                            Icon(if (showHow) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, tint = PulseColors.text, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (showHow) {
                        Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HowCard("¿QUÉ ES EL ESTRÉS?", "Mide lo activado que está tu cuerpo. No siempre hay que evitarlo: bien usado, te ayuda a rendir.") {
                                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(PulseColors.accent.copy(alpha = 0.45f), PulseColors.overlay))), contentAlignment = Alignment.Center) {
                                    Canvas(Modifier.size(120.dp, 70.dp)) { drawStressCurve(demoCurve(), 3.dp.toPx()) }
                                }
                            }
                            HowCard("CÓMO SE CALCULA", "Pulse combina tu pulso, tu VFC y tu movimiento en una escala de 0 a 3.") {
                                Row(Modifier.fillMaxSize().background(PulseColors.overlay), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(90 to PulseColors.stressHigh, 64 to PulseColors.accent, 40 to PulseColors.stressLow).forEach { (w, c) ->
                                            Box(Modifier.width(w.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c))
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Text("0–3", style = PulseText.bigNumber.copy(fontSize = 30.sp), color = PulseColors.text)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(112.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
        val stressLine =             when {
                level == null -> "Cuando tenga lecturas de hoy, te ayudo a interpretarlas."
                level < 1 -> "Tu estrés está bajo ahora mismo. ¿Quieres que lo veamos juntos?"
                level < 2 -> "Tu estrés está en nivel medio. ¿Te propongo cómo gestionarlo?"
                else -> "Tu estrés está alto. Te ayudo a bajarlo en dos minutos."
            }
        CoachBar(stressLine, {
            CoachLaunch.offer(stressLine, if ((level ?: 0.0) >= 2) "ejercicio de respiracion" else "como esta mi estres")
            onCoach()
        },
        )
        if (tipOpen) {
            FirstVisitTip("Nivel de estrés", "Resume lo activado que está tu cuerpo en una escala de 0 a 3, a partir de tu pulso, tu VFC y tu movimiento, comparados con tus valores de reposo.") { tipOpen = false }
        }
    }
}

private fun Modifier.pointerInputScrub(count: Int, widthPx: Float, onScrub: (Int?) -> Unit): Modifier =
    this.pointerInput(count, widthPx) {
        detectDragGestures(
            onDragEnd = { },
            onDrag = { change, _ ->
                if (count > 1) onScrub(((change.position.x / widthPx) * (count - 1)).toInt().coerceIn(0, count - 1))
            },
        )
    }

@Composable
private fun SessionCard(title: String, subtitle: String, art: Brush, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(Modifier.width(160.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).pulseClick(onClick)) {
        Box(Modifier.fillMaxWidth().height(80.dp).background(art))
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
            Icon(icon, null, tint = PulseColors.textQuiet, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = PulseText.overline.copy(fontSize = 11.sp, lineHeight = 15.sp), color = PulseColors.text)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
        }
    }
}

@Composable
private fun HowCard(title: String, body: String, art: @Composable BoxScope.() -> Unit) {
    Column(Modifier.width(236.dp).clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.inset)) {
        Box(Modifier.fillMaxWidth().height(150.dp)) { art() }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Text(title, style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
            Spacer(Modifier.height(8.dp))
            Text(body, style = PulseText.body.copy(lineHeight = 18.sp), color = PulseColors.textSecondary)
        }
    }
}

/** Fixed coach prompt bar at the bottom of a page. */
@Composable
fun BoxScope.CoachBar(text: String, onClick: () -> Unit) {
    Row(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            .fillMaxWidth().height(66.dp).shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(PulseColors.overlay.copy(alpha = 0.95f)).border(1.dp, PulseColors.hairline, RoundedCornerShape(24.dp)).pulseClick(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoachOrb(diameter = 40.dp)
        Spacer(Modifier.width(16.dp))
        Text(text, style = PulseText.body.copy(fontSize = 13.5.sp, lineHeight = 18.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PulseColors.text, modifier = Modifier.size(16.dp))
    }
}

/** Guided breathing: slow (relax, 4 in / 6 out) or fast (activate, 2 in / 2 out), 2 minutes. */
@Composable
fun PulseBreatheScreen(relax: Boolean, onBack: () -> Unit) {
    val inhale = if (relax) 4 else 2
    val exhale = if (relax) 6 else 2
    val totalCycles = if (relax) 12 else 30
    var running by remember { mutableStateOf(false) }
    var phaseIn by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf(inhale) }
    var cycle by remember { mutableStateOf(0) }
    val scale = remember { Animatable(0.75f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (running && cycle < totalCycles) {
            phaseIn = true
            val grow = launch { scale.animateTo(1f, tween(inhale * 1000, easing = LinearEasing)) }
            for (s in inhale downTo 1) { count = s; delay(1000) }
            grow.join()
            phaseIn = false
            val shrink = launch { scale.animateTo(0.75f, tween(exhale * 1000, easing = LinearEasing)) }
            for (s in exhale downTo 1) { count = s; delay(1000) }
            shrink.join()
            cycle++
        }
        running = false
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.padding(start = 12.dp, top = 16.dp).size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(22.dp))
        }
        Text(
            if (relax) "PARA RELAJARTE" else "PARA ACTIVARTE", style = PulseText.tabTitle, color = PulseColors.text,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
        )
        BreathCircle(Modifier.align(Alignment.Center).offset(y = (-40).dp), if (!running) "LISTO" else if (phaseIn) "INHALA" else "EXHALA", if (running) "$count" else "", scale.value)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (relax) "Inspira $inhale segundos y exhala $exhale. La exhalación larga frena el corazón." else "Respira rápido y rítmico: $inhale segundos dentro, $exhale fuera. Para despejarte antes de entrenar.",
                style = PulseText.bodyLg, color = PulseColors.textQuiet, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text("Ciclo ${cycle.coerceAtMost(totalCycles)} de $totalCycles", style = PulseText.overline, color = PulseColors.textSecondary)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (running) PulseColors.overlay else PulseColors.accent)
                    .pulseClick { if (running) running = false else { cycle = 0; running = true } }.padding(horizontal = 40.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (running) "PARAR" else "EMPEZAR", style = PulseText.overlineLg, color = if (running) PulseColors.text else PulseColors.onAccent) }
        }
    }
}
