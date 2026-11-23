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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.BuildConfig
import com.noop.ui.AppViewModel
import java.util.Locale
import kotlin.math.abs

private val saludBrush = Brush.verticalGradient(
    0f to Color(0xFF2A2436), 0.35f to Color(0xFF1A1A22), 1f to PulseColors.base,
)

class HealthActions(
    val openAge: () -> Unit,
    val openTrends: () -> Unit,
    val openMonitor: () -> Unit,
    val openStress: () -> Unit,
)

@Composable
fun PulseHealthScreen(vm: AppViewModel, actions: HealthActions) {
    val context = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    var vo2 by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull() }
    val age = remember(days, vo2) { if (days.size >= PulseData.pulseAgeDays) PulseData.pulseAge(context, days, vo2) else null }
    val nights = PulseData.nightsWithSleep(days)

    Column(Modifier.fillMaxSize().background(saludBrush).verticalScroll(rememberScrollState())) {
        TabTitle("Salud")
        // Hero: physiological age
        Column(
            Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 24.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 1f to PulseColors.accent), RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0xFF2A2338), 1f to Color(0xFF241E31)))
                .clickable(onClick = actions.openAge)
                .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp).clip(RoundedCornerShape(75.dp))
                        .background(Brush.radialGradient(listOf(Color(0xFF4A4258), Color(0xFF2B2635)))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (age != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(Locale.US, "%.1f", age.pulseAge), style = PulseText.heroNumber, color = PulseColors.text)
                            Text("AÑOS", style = PulseText.overline, color = PulseColors.textSecondary)
                        }
                    } else {
                        DashedProgressRing(days.size.coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 110.dp)
                    }
                }
            }
            Overline("Tu edad fisiológica", large = true)
            Spacer(Modifier.height(10.dp))
            Text(
                if (age == null) "Faltan ${PulseData.pulseAgeDays - days.size} días para calcular tu edad fisiológica."
                else String.format(Locale("es"), "%.1f años %s que tu edad real.", abs(age.yearsYounger), if (age.yearsYounger >= 0) "menos" else "más"),
                style = PulseText.body, color = PulseColors.text,
            )
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.hairlineStrong)) {
                Box(
                    Modifier.fillMaxWidth((days.size.toFloat() / PulseData.pulseAgeDays).coerceIn(0f, 1f)).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(PulseColors.accent),
                )
            }
        }
        // 90-day trends
        AccentCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 18.dp), onClick = actions.openTrends) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TENDENCIAS DE 90 DÍAS", style = PulseText.cardTitleLg, color = PulseColors.text, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Compara cómo evolucionan tu VFC, tu pulso en reposo y tu sueño mes a mes.", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    CtaLink("Ver tendencias", onClick = actions.openTrends)
                }
                Spacer(Modifier.width(10.dp))
                MiniSparkline(listOf(20f, 16f, 28f, 24f, 36f, 32f, 44f), Modifier.size(78.dp, 60.dp))
            }
        }
        // Health monitor
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 18.dp), onClick = actions.openMonitor) {
            CardHeader("Monitor de salud")
            val d = today ?: days.lastOrNull()
            val ready = nights >= PulseData.calibrationNights
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                VitalCell("RESP", Icons.Outlined.Air, d?.respRateBpm?.let { String.format(Locale.US, "%.1f", it) }, ready, Modifier.weight(1f), first = true)
                VitalCell("SPO₂", Icons.Outlined.WaterDrop, d?.spo2Pct?.let { "${it.toInt()}%" }, ready, Modifier.weight(1f))
                VitalCell("FCR", Icons.Outlined.Favorite, d?.restingHr?.toString(), ready, Modifier.weight(1f))
                VitalCell("VFC", Icons.Outlined.MonitorHeart, d?.avgHrv?.let { "${it.toInt()}" }, ready, Modifier.weight(1f))
                VitalCell("TEMP", Icons.Outlined.Thermostat, d?.skinTempDevC?.takeIf { abs(it) < 5 }?.let { String.format(Locale.US, "%+.1f", it) }, ready, Modifier.weight(1f))
            }
            if (!ready) {
                Spacer(Modifier.height(18.dp))
                UnlockPanel("Duerme con la pulsera ${PulseData.calibrationNights - nights} noches para desbloquearlo.", nights, PulseData.calibrationNights)
            }
        }
        // Stress
        PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 18.dp), onClick = actions.openStress) {
            CardHeader("Nivel de estrés")
            Spacer(Modifier.height(16.dp))
            Overline("Estrés alto hoy", color = PulseColors.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("--:--", style = PulseText.ringNumberLg, color = PulseColors.text)
                Spacer(Modifier.width(4.dp))
                Text("h", style = PulseText.small, color = PulseColors.textSecondary)
            }
            if (nights < PulseData.recoverySeedNights) {
                Spacer(Modifier.height(14.dp))
                UnlockPanel("Duerme con la pulsera ${PulseData.recoverySeedNights - nights} noches para desbloquearlo.", nights, PulseData.recoverySeedNights)
            }
        }
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
                    Spacer(Modifier.height(6.dp))
                    Text("Pronto podrás crear grupos con tu equipo o tus amigos y comparar vuestra semana.", style = PulseText.body, color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("VER GRUPOS", style = PulseText.overline, color = PulseColors.textTertiary)
                }
                Spacer(Modifier.width(10.dp))
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

/** One Ajustes row: label, optional subtitle, destination. */
data class SettingsItem(val label: String, val subtitle: String? = null, val onClick: () -> Unit)

@Composable
fun PulseSettingsScreen(vm: AppViewModel, groups: List<Pair<String?, List<SettingsItem>>>, onCalibration: () -> Unit) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val nights = PulseData.nightsWithSleep(days)
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush).verticalScroll(rememberScrollState())) {
        TabTitle("Ajustes")
        if (nights < PulseData.calibrationNights) {
            PulseCard(Modifier.padding(start = PulseMetrics.gutter, end = PulseMetrics.gutter, top = 24.dp), onClick = onCalibration) {
                CardHeader("Tu primera semana")
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Duerme con la pulsera cada noche y vuelve aquí para ver cómo se calibra tu línea base.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    DashedProgressRing(nights, PulseData.calibrationNights)
                }
            }
        }
        groups.forEach { (title, items) ->
            if (title != null) {
                Text(title.uppercase(), style = PulseText.overline, color = PulseColors.textTertiary, modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 10.dp))
            } else {
                Spacer(Modifier.height(16.dp))
            }
            Column(
                Modifier.padding(horizontal = PulseMetrics.gutter).fillMaxWidth()
                    .clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay),
            ) {
                items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(PulseColors.hairline))
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = item.onClick).padding(horizontal = 16.dp, vertical = if (item.subtitle == null) 19.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.label.uppercase(), style = PulseText.overlineLg, color = PulseColors.text)
                            if (item.subtitle != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(item.subtitle, style = PulseText.body, color = PulseColors.textSecondary)
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Text(
            "Versión ${BuildConfig.VERSION_NAME} · todos los datos en tu teléfono",
            style = PulseText.body.copy(fontSize = PulseText.chartAxis.fontSize), color = PulseColors.textTertiary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        Spacer(Modifier.height(PulseMetrics.barClearance))
    }
}
