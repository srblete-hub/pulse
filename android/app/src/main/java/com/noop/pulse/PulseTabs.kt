package com.noop.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.BuildConfig
import com.noop.ui.AppViewModel
import java.util.Locale
import kotlin.math.abs

private val saludBrush = androidx.compose.ui.graphics.SolidColor(PulseColors.raised)

class HealthActions(
    val openAge: () -> Unit,
    val openTrends: () -> Unit,
    val openMonitor: () -> Unit,
    val openStress: () -> Unit,
)

@Composable
fun PulseHealthScreen(vm: AppViewModel, actions: HealthActions) {
    val context = LocalContext.current
    val today by com.noop.pulse.engine.pulseToday(vm)
    val days by com.noop.pulse.engine.pulseDays(vm)
    var vo2 by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull() }
    val workoutsAll by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect("workouts") { runCatching { vm.loadWorkouts() } }
    var ageState by remember { mutableStateOf<com.noop.analytics.PulseAgeEngine.Result?>(null) }
    LaunchedEffect(days.size, vo2, workoutsAll.size) {
        ageState = if (PulseData.dataDays(days) >= PulseData.pulseAgeDays) com.noop.pulse.engine.PulseAge.compute(context, vm, days, workoutsAll, vo2)?.result else null
    }
    val age = ageState
    val nights = PulseData.nightsWithSleep(days)
    var highStressMin by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(days.size) {
        highStressMin = runCatching {
            val pts = com.noop.widget.StressWidgetProducer.todayCurve(vm.repo, vm.activeStrapId)?.points.orEmpty().filter { it.level != null }
            if (pts.size < 2) null else {
                val step = pts.zipWithNext { a, b -> b.ts - a.ts }.sorted().let { it[it.size / 2] } / 60.0
                pts.count { it.level!! >= 2.0 } * step
            }
        }.getOrNull()
    }

    Column(Modifier.fillMaxSize().background(saludBrush).verticalScroll(rememberScrollState())) {
        TabTitle("Salud")
        // Hero: physiological age
        Column(
            Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 24.dp).fillMaxWidth()
                .clip(RoundedCornerShape(PulseMetrics.cardRadius))
                .border(1.5.dp, androidx.compose.ui.graphics.SolidColor(Color.Transparent), RoundedCornerShape(PulseMetrics.cardRadius))
                .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised))
                .clickable(onClick = actions.openAge)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp).clip(RoundedCornerShape(75.dp))
                        .background(androidx.compose.ui.graphics.SolidColor(PulseColors.raised)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (age != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(Locale.US, "%.1f", age.pulseAge), style = PulseText.heroNumber, color = PulseColors.text)
                            Text("AÑOS", style = PulseText.overline, color = PulseColors.textSecondary)
                        }
                    } else {
                        DashedProgressRing(PulseData.dataDays(days).coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 110.dp)
                    }
                }
            }
            Overline("Tu edad fisiológica", large = true)
            Spacer(Modifier.height(8.dp))
            Text(
                if (age == null) com.noop.pulse.engine.PulseAge.waitText(days)
                else String.format(Locale("es"), "%.1f años %s que tu edad real.", abs(age.yearsYounger), if (age.yearsYounger >= 0) "menos" else "más"),
                style = PulseText.body, color = PulseColors.text,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.hairlineStrong)) {
                Box(
                    Modifier.fillMaxWidth((PulseData.dataDays(days).toFloat() / PulseData.pulseAgeDays).coerceIn(0f, 1f)).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(PulseColors.accent),
                )
            }
        }
        // 90-day trends
        AccentCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp), onClick = actions.openTrends) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TENDENCIAS DE 90 DÍAS", style = PulseText.cardTitleLg, color = PulseColors.text, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Compara cómo evolucionan tu VFC, tu pulso en reposo y tu sueño mes a mes.", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    CtaLink("Ver tendencias", onClick = actions.openTrends)
                }
                Spacer(Modifier.width(8.dp))
                MiniSparkline(listOf(20f, 16f, 28f, 24f, 36f, 32f, 44f), Modifier.size(78.dp, 60.dp))
            }
        }
        // Health monitor
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp), onClick = actions.openMonitor) {
            CardHeader("Monitor de salud")
            val d = today ?: days.lastOrNull()
            val ready = nights >= PulseData.calibrationNights
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                VitalCell("RESP", Icons.Outlined.Air, d?.respRateBpm?.let { String.format(Locale.US, "%.1f", it) }, ready, Modifier.weight(1f), first = true)
                VitalCell("FCR", Icons.Outlined.Favorite, d?.restingHr?.toString(), ready, Modifier.weight(1f))
                VitalCell("VFC", Icons.Outlined.MonitorHeart, d?.avgHrv?.let { "${it.toInt()}" }, ready, Modifier.weight(1f))
                VitalCell("TEMP", Icons.Outlined.Thermostat, d?.skinTempDevC?.takeIf { abs(it) < 5 }?.let { String.format(Locale.US, "%+.1f", it) }, ready, Modifier.weight(1f))
            }
            if (!ready) {
                Spacer(Modifier.height(16.dp))
                UnlockPanel("Duerme con la pulsera ${PulseData.calibrationNights - nights} noches para desbloquearlo.", nights, PulseData.calibrationNights)
            }
        }
        // Stress
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp), onClick = actions.openStress) {
            CardHeader("Nivel de estrés")
            Spacer(Modifier.height(16.dp))
            Overline("Estrés alto hoy", color = PulseColors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(highStressMin?.let { PulseActivity.mmss(it) } ?: "--:--", style = PulseText.ringNumberLg, color = PulseColors.text)
                Spacer(Modifier.width(4.dp))
                Text("h", style = PulseText.small, color = PulseColors.textSecondary)
            }
            if (nights < PulseData.recoverySeedNights) {
                Spacer(Modifier.height(16.dp))
                UnlockPanel("Duerme con la pulsera ${PulseData.recoverySeedNights - nights} noches para desbloquearlo.", nights, PulseData.recoverySeedNights)
            }
        }
        HealthAlertCard(vm)
        TrainingLoadCardPulse(days)
        RhythmCard(vm, days)
        Spacer(Modifier.height(PulseMetrics.barClearance))
    }
}

@Composable
private fun VitalCell(label: String, icon: ImageVector, value: String?, ready: Boolean, modifier: Modifier, first: Boolean = false) {
    Row(modifier.height(80.dp)) {
        if (!first) Box(Modifier.width(1.dp).fillMaxSize().background(PulseColors.hairlineStrong))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = PulseColors.textQuiet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = PulseText.overline.copy(letterSpacing = PulseText.overline.letterSpacing), color = PulseColors.text)
            Spacer(Modifier.height(8.dp))
            if (ready && value != null) {
                Text(value, style = PulseText.small, color = PulseColors.text)
            } else {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(PulseColors.hairlineStrong), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(PulseColors.textSecondary))
                }
            }
        }
    }
}

@Composable
fun PulseCommunityScreen() {
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush).verticalScroll(rememberScrollState())) {
        TabTitle("Comunidad")
        AccentCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Comparte tu progreso", style = PulseText.cardTitleLg, color = PulseColors.text, modifier = Modifier.weight(1f))
                Pill("Próximamente")
            }
            Spacer(Modifier.height(8.dp))
            Text("Sigue las métricas diarias de las personas que te importan y animaos a mantener buenos hábitos.", style = PulseText.body, color = PulseColors.textSecondary)
        }
        SectionTitle("Mis grupos", trailing = { Text("BUSCAR GRUPOS", style = PulseText.overline, color = PulseColors.textTertiary) })
        PulseCard(Modifier.padding(horizontal = PulseMetrics.gutter), color = PulseColors.raised, padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("¿Entrenas en grupo?", style = PulseText.cardTitle, color = PulseColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text("Pronto podrás crear grupos con tu equipo o tus amigos y comparar vuestra semana.", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("VER GRUPOS", style = PulseText.overline, color = PulseColors.textTertiary)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.Groups, null, tint = PulseColors.textTertiary, modifier = Modifier.size(54.dp))
            }
        }
        Column(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Overline("Tus contactos", large = true)
                    Spacer(Modifier.height(4.dp))
                    Text("Gestiona a quién sigues y quién te sigue", style = PulseText.body, color = PulseColors.textSecondary)
                }
                Pill("Próximamente")
            }
        }
        Spacer(Modifier.height(PulseMetrics.barClearance))
    }
}

/** One Ajustes row: icon, label, optional subtitle, destination. */
data class SettingsItem(val label: String, val subtitle: String? = null, val icon: ImageVector? = null, val onClick: () -> Unit)

@Composable
fun PulseSettingsScreen(vm: AppViewModel, groups: List<Pair<String?, List<SettingsItem>>>, onCalibration: () -> Unit, onBackup: () -> Unit) {
    val days by com.noop.pulse.engine.pulseDays(vm)
    val nights = PulseData.nightsWithSleep(days)
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush).verticalScroll(rememberScrollState())) {
        TabTitle("Ajustes")
        if (nights < PulseData.calibrationNights) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, PulseColors.accentBrush, RoundedCornerShape(12.dp)).background(PulseColors.base)
                    .clickable(onClick = onCalibration).padding(16.dp),
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = PulseColors.textQuiet, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("TU PRIMERA SEMANA", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text("Duerme con la pulsera cada noche y vuelve aquí para ver cómo se calibra tu línea base. (${nights}/${PulseData.calibrationNights})", style = PulseText.body, color = PulseColors.textSecondary)
                }
            }
        }
        groups.forEach { (title, items) ->
            if (title != null) {
                Text(title.uppercase(), style = PulseText.overline.copy(fontSize = 11.sp, letterSpacing = PulseText.tabTitle.letterSpacing * 0.875f), color = PulseColors.textTertiary, modifier = Modifier.padding(start = 16.dp, top = 32.dp, bottom = 12.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp)).background(PulseColors.overlay)
                            .clickable(onClick = item.onClick).padding(horizontal = 16.dp, vertical = if (item.subtitle == null) 0.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.icon?.let {
                            Icon(it, null, tint = PulseColors.textQuiet, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                        }
                        Column(Modifier.weight(1f).padding(vertical = if (item.subtitle == null) 18.dp else 0.dp)) {
                            Text(item.label.uppercase(), style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text)
                            if (item.subtitle != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(item.subtitle, style = PulseText.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = PulseColors.textSecondary)
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp).width(230.dp).height(46.dp).clip(RoundedCornerShape(23.dp))
                .border(1.5.dp, PulseColors.text, RoundedCornerShape(23.dp)).clickable(onClick = onBackup),
            contentAlignment = Alignment.Center,
        ) { Text("COPIA DE SEGURIDAD", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text) }
        Text(
            "Versión ${BuildConfig.VERSION_NAME} · todos los datos en tu teléfono",
            style = PulseText.small.copy(fontSize = 10.sp, letterSpacing = PulseText.overline.letterSpacing * 0.5f), color = PulseColors.textQuiet,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Spacer(Modifier.height(PulseMetrics.barClearance))
    }
}
