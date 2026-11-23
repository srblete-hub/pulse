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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.PulseAgeEngine
import com.noop.data.DailyMetric
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, info: Pair<String, String>?, content: @Composable () -> Unit) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        PulseTopBar(title, onBack, trailing = info?.let {
            {
                Box(
                    Modifier.size(22.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(11.dp)).pulseClick { showInfo = true },
                    contentAlignment = Alignment.Center,
                ) { Text("i", style = PulseText.overline, color = PulseColors.textSecondary) }
            }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            content()
            Spacer(Modifier.height(40.dp))
        }
    }
    if (showInfo && info != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = PulseColors.overlay,
            title = { Text(info.first, style = PulseText.headline.copy(fontSize = 20.sp), color = PulseColors.text) },
            text = { Text(info.second, style = PulseText.bodyLg, color = PulseColors.textSecondary) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("ENTENDIDO", style = PulseText.overline, color = PulseColors.accent) } },
        )
    }
}

@Composable
fun PulseAgeScreen(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    var vo2 by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull() }
    val have = days.size
    val workoutsAll by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect("workouts") { runCatching { vm.loadWorkouts() } }
    val result = remember(days, vo2, workoutsAll) { PulseData.pulseAge(context, days, vo2, workoutsAll) }
    val previous = remember(days, vo2, workoutsAll) { if (days.size > 14) PulseData.pulseAge(context, days.dropLast(7), vo2, workoutsAll) else null }
    DetailScaffold(
        "Edad fisiológica", onBack,
        "Edad fisiológica" to "Partimos de tu edad real y sumamos o restamos años según nueve hábitos: horas y regularidad del sueño, pasos, minutos en zonas de pulso, fuerza, VO₂ máx., pulso en reposo y masa magra. Usa guías públicas (OMS 2020) y estudios publicados; no es un dispositivo médico.",
    ) {
        if (have < PulseData.pulseAgeDays || result == null) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 30.dp, end = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DashedProgressRing(have.coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 120.dp)
                Spacer(Modifier.height(20.dp))
                Text("Faltan ${PulseData.pulseAgeDays - have} días para calcular tu edad fisiológica.", style = PulseText.bodyLg, color = PulseColors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Lleva la pulsera día y noche. Necesitamos 3 semanas de datos para que el cálculo sea fiable.", style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center)
            }
        } else {
            AgeResult(result, previous, onCoach)
        }
    }
}

@Composable
private fun AgeResult(result: PulseAgeEngine.Result, previous: PulseAgeEngine.Result?, onCoach: () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format(Locale.US, "%.1f", result.pulseAge), style = PulseText.heroNumber.copy(fontSize = 56.sp), color = PulseColors.text)
            Overline("Edad fisiológica", large = true)
            Spacer(Modifier.height(8.dp))
            val y = abs(result.yearsYounger)
            val word = if (result.yearsYounger >= 0) "menos" else "más"
            Text(String.format(Locale("es"), "%.1f años %s que tu edad", y, word), style = PulseText.body, color = if (result.yearsYounger >= 0) PulseColors.recoveryGreen else PulseColors.stressHigh)
        }
        val pace = previous?.let { PulseAgeEngine.paceOfAgeing(it, result, 7) }
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 20.dp)) {
            CardHeader("Ritmo de envejecimiento", showChevron = false)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pace?.let { String.format(Locale("es"), "%.1f×", it.coerceIn(-1.0, 3.0)) } ?: "--", style = PulseText.ringNumberLg, color = PulseColors.text)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        pace == null -> "Necesitamos dos semanas para comparar."
                        pace < 1.0 -> "Envejeces más lento que el calendario."
                        else -> "Envejeces al ritmo del calendario o más rápido."
                    },
                    style = PulseText.body, color = PulseColors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                drawRoundRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(PulseColors.recoveryGreen, PulseColors.recoveryYellow, PulseColors.recoveryRed)), cornerRadius = CornerRadius(4.dp.toPx()))
                pace?.let {
                    val x = ((it.coerceIn(-1.0, 3.0) + 1.0) / 4.0).toFloat() * size.width
                    drawCircle(PulseColors.text, 6.dp.toPx(), Offset(x, size.height / 2))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("−1,0×", "1,0×", "3,0×").forEach { Text(it, style = PulseText.chartAxis, color = PulseColors.textSecondary) }
            }
        }
        listOf("SUEÑO", "ACTIVIDAD", "FORMA FÍSICA").forEach { group ->
            PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 12.dp)) {
                CardHeader(group, showChevron = false)
                result.factors.filter { PulseData.factorGroup(it.key) == group }.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(PulseData.factorLabel(f.key), style = PulseText.cardTitle, color = PulseColors.text)
                            Text(PulseData.factorValue(f), style = PulseText.body, color = PulseColors.textSecondary)
                        }
                        val yrs = f.years
                        Text(
                            yrs?.let { String.format(Locale("es"), "%+.1f años", it) } ?: "—",
                            style = PulseText.small,
                            color = when {
                                yrs == null -> PulseColors.textTertiary
                                yrs <= 0 -> PulseColors.recoveryGreen
                                else -> PulseColors.stressHigh
                            },
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
            AccentCard(onClick = onCoach) {
                Text("Pide un plan al coach", style = PulseText.cardTitle, color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text("Te propone pasos concretos para mejorar las métricas que más te restan.", style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                CtaLink("Crear plan", onClick = onCoach)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Pulse calcula tu edad fisiológica con tus propios datos y guías públicas; no es un dispositivo médico.",
                style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
}

/** Calibration progress: one row per night of the first week. */
@Composable
fun PulseCalibrationScreen(vm: AppViewModel, onBack: () -> Unit) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val nights = PulseData.nightsWithSleep(days)
    DetailScaffold("Calibración", onBack, "Calibración" to "Pulse aprende cómo eres tú: tu VFC, tu pulso en reposo y tu respiración normales. Con 4 noches ya calcula tu recuperación; con 7 tu línea base queda completa.") {
        Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DashedProgressRing(nights.coerceAtMost(7), 7, 140.dp)
            Spacer(Modifier.height(16.dp))
            Text("Duerme con la pulsera cada noche y vuelve aquí para ver tu línea base personal.", style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
        }
        Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                1 to "Primer sueño y primer esfuerzo",
                4 to "Primera recuperación",
                7 to "Línea base completa y monitor de salud",
                21 to "Edad fisiológica",
            ).forEach { (n, label) ->
                val done = nights >= n
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(if (done) PulseColors.accent else PulseColors.hairlineStrong),
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", style = PulseText.overline, color = if (done) PulseColors.onAccent else PulseColors.text) }
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = PulseText.cardTitle, color = PulseColors.text, modifier = Modifier.weight(1f))
                    Text(if (done) "LISTO" else "NOCHE $n", style = PulseText.overline, color = if (done) PulseColors.accent else PulseColors.textTertiary)
                }
            }
        }
    }
}

/** Simple informational page (charging tips, goals, help). */
@Composable
fun PulseInfoScreen(title: String, heading: String, paragraphs: List<String>, onBack: () -> Unit, cta: Pair<String, () -> Unit>? = null) {
    DetailScaffold(title, onBack, null) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(heading, style = PulseText.headline, color = PulseColors.text)
            paragraphs.forEach {
                Spacer(Modifier.height(14.dp))
                Text(it, style = PulseText.bodyLg, color = PulseColors.textSecondary)
            }
            if (cta != null) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(PulseColors.accent).pulseClick(cta.second),
                    contentAlignment = Alignment.Center,
                ) { Text(cta.first.uppercase(), style = PulseText.overlineLg, color = PulseColors.onAccent) }
            }
        }
    }
}
