package com.noop.pulse

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.PulseAgeEngine
import com.noop.analytics.WorkoutSport
import com.noop.ui.AppViewModel
import com.noop.ui.ProfileStore
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val es = Locale("es", "ES")

// ---------------------------------------------------------------------------------------------
// Comunidad · introducción
// ---------------------------------------------------------------------------------------------

@Composable
fun PulseCommunityIntro(onClose: () -> Unit, onDone: () -> Unit) {
    IntroFlow(
        lastLabel = "ENTENDIDO",
        steps = listOf(
            IntroStep("Te damos la bienvenida a Comunidad", listOf("Un sitio para animaros, compartir avances y no perder la constancia."), textTop = 600.dp,
                background = Brush.radialGradient(listOf(PulseColors.violet.copy(alpha = 0.45f), PulseColors.base), radius = 900f)) {
                Icon(Icons.Outlined.Groups, null, tint = PulseColors.accent, modifier = Modifier.align(Alignment.TopCenter).padding(top = 220.dp).size(130.dp))
            },
            IntroStep("Conecta con quien te importa", listOf("Envía solicitudes a amigos y familia. Solo compartes tus datos con quien tú aceptes.")) {
                Column(Modifier.align(Alignment.TopCenter).padding(start = 30.dp, end = 30.dp, top = 120.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PulseColors.raised).padding(16.dp)) {
                    Text("COMUNIDAD", style = PulseText.overline, color = PulseColors.textSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(PulseColors.overlay).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("MR"); Spacer(Modifier.width(10.dp))
                        Text("SOLICITUDES (1)", style = PulseText.overline, color = PulseColors.text, modifier = Modifier.weight(1f))
                        Text("ACEPTAR", style = PulseText.overline, color = PulseColors.accent)
                    }
                    Text("Mi comunidad", style = PulseText.cardTitle, color = PulseColors.text, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    listOf(Triple("AL", "72%", "11.4" to "81%"), Triple("JP", "48%", "6.2" to "64%"), Triple("SC", "21%", "15.0" to "52%")).forEach { (n, r, es2) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(n); Spacer(Modifier.width(10.dp))
                            Text("$r recup. · ${es2.first} esf. · ${es2.second} sueño", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textQuiet)
                        }
                    }
                }
            },
            IntroStep("Apoyaos en los días buenos y en los malos", listOf("Comparte tu sueño, tu recuperación, tu esfuerzo y tus entrenos con tus seguidores.")) {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 220.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf("12.5" to "Esfuerzo", "5:42/km" to "Ritmo medio", "48:10" to "Duración").forEach { (v, l) ->
                        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, style = PulseText.bigNumber, color = PulseColors.text)
                            Text(l, style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textSecondary)
                        }
                    }
                }
            },
            IntroStep("Crea grupos con tu gente", listOf("Tu equipo, tus amigos del gimnasio o tu familia, cada uno en su grupo.")) {
                Column(Modifier.align(Alignment.TopCenter).padding(top = 200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mis grupos", style = PulseText.sectionTitle, color = PulseColors.text)
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("12", "8", "5").forEach {
                            Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(PulseColors.raised), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Groups, null, tint = PulseColors.accent, modifier = Modifier.size(26.dp))
                                    Text(it, style = PulseText.small, color = PulseColors.text)
                                }
                            }
                        }
                    }
                }
            },
            IntroStep("Tú decides quién ve tus datos", listOf("Desde los ajustes de privacidad puedes dejar de compartir, ocultarte de las búsquedas o quitar a alguien cuando quieras.")) {
                Icon(Icons.Outlined.Lock, null, tint = PulseColors.textTertiary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 240.dp).size(110.dp))
            },
            IntroStep("Mejor juntos", listOf("En los días en verde y en los días en rojo, acompañaos de cerca.")) {
                Row(Modifier.align(Alignment.TopCenter).padding(top = 260.dp), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    listOf(PulseColors.recoveryGreen, PulseColors.recoveryYellow, PulseColors.recoveryRed).forEach {
                        Box(Modifier.size(90.dp).clip(RoundedCornerShape(45.dp)).border(6.dp, it, RoundedCornerShape(45.dp)).background(PulseColors.base))
                    }
                }
            },
            IntroStep("Estamos preparándolo", listOf("Comunidad necesita un servidor para compartir datos. Mientras tanto, todo lo tuyo sigue solo en tu teléfono."), kicker = "PRÓXIMAMENTE") {
                Icon(Icons.Outlined.Groups, null, tint = PulseColors.textTertiary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 240.dp).size(110.dp))
            },
        ),
        onClose = onClose, onDone = onDone,
    )
}

@Composable
private fun Avatar(initials: String) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(PulseColors.accentBrush), contentAlignment = Alignment.Center) {
        Text(initials, style = PulseText.overline, color = PulseColors.onAccent)
    }
}

// ---------------------------------------------------------------------------------------------
// Iniciar actividad
// ---------------------------------------------------------------------------------------------

private data class ActivityChoice(val label: String, val sport: String, val icon: ImageVector, val distance: Boolean)

private val activities = listOf(
    ActivityChoice("Correr", "Running", Icons.AutoMirrored.Outlined.DirectionsRun, true),
    ActivityChoice("Caminar", "Walking", Icons.AutoMirrored.Outlined.DirectionsWalk, true),
    ActivityChoice("Ciclismo", "Cycling", Icons.Outlined.DirectionsBike, true),
    ActivityChoice("Fuerza", "Strength", Icons.Outlined.FitnessCenter, false),
    ActivityChoice("Yoga", "Yoga", Icons.Outlined.SelfImprovement, false),
    ActivityChoice("Natación", "Swim", Icons.Outlined.Pool, false),
)

@Composable
fun PulseStartActivityScreen(vm: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()
    val active by vm.activeWorkout.collectAsStateWithLifecycle()
    val days by vm.recentDays.collectAsStateWithLifecycle()
    var i by rememberSaveable { mutableStateOf(0) }
    var route by rememberSaveable { mutableStateOf(false) }
    var target by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { vm.requestRealtimeHr(); onDispose { vm.releaseRealtimeHr() } }
    val rec = PulseData.metricFor(days, null, 0)?.recovery
    val targetValue = rec?.let { r -> if (r >= 67) 15.0 else if (r >= 34) 12.0 else 8.0 } ?: 12.0
    val act = activities[i]
    fun start() {
        val sport = WorkoutSport.all.firstOrNull { it.name.equals(act.sport, true) } ?: WorkoutSport.all.firstOrNull { it.name.contains(act.sport, true) } ?: WorkoutSport.default
        vm.startWorkout(sport, gpsEnabled = route && act.distance)
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) route = false; start() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A3036), Color(0xFF0E1013)))).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 18.dp).size(44.dp).pulseClick(onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(20.dp))
        }
        val w = active
        if (w != null) {
            var now by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(w.startMs) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val secs = ((now - w.startMs) / 1000).toInt()
            Column(Modifier.fillMaxSize().padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(act.label.uppercase(es), style = PulseText.tabTitle, color = PulseColors.text)
                Text("%d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60), style = PulseText.bigNumber.copy(fontSize = 52.sp), color = PulseColors.text, modifier = Modifier.padding(top = 24.dp))
                Row(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    LiveStat("${live.heartRate ?: "--"}", "LPM")
                    LiveStat(PulseData.oneDecimal(w.liveStrain * 21 / 100), "ESFUERZO")
                    if (w.distanceM > 0) LiveStat(String.format(Locale.US, "%.2f", w.distanceM / 1000), "KM")
                }
                if (target) {
                    val f = (w.liveStrain * 21 / 100 / targetValue).toFloat().coerceIn(0f, 1f)
                    Box(Modifier.padding(top = 40.dp).size(160.dp), contentAlignment = Alignment.Center) {
                        ScoreRing(f, PulseColors.strain, "${(f * 100).roundToInt()}%", size = 160.dp, stroke = 10.dp, large = true)
                    }
                    Text("del objetivo de esfuerzo (${PulseData.oneDecimal(targetValue)})", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.padding(start = 24.dp, end = 24.dp, bottom = 30.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(PulseColors.recoveryRed).pulseClick { vm.endWorkout() },
                    contentAlignment = Alignment.Center,
                ) { Text("FINALIZAR ACTIVIDAD", style = PulseText.overline.copy(fontSize = 12.sp), color = PulseColors.text) }
            }
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.clip(RoundedCornerShape(24.dp)).background(PulseColors.overlay).pulseClick { i = (i + 1) % activities.size }.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(act.icon, null, tint = PulseColors.text, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(act.label, style = PulseText.cardTitleLg, color = PulseColors.text)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.SwapHoriz, "Cambiar actividad", tint = PulseColors.textSecondary, modifier = Modifier.size(18.dp))
            }
            if (act.distance) {
                Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("GRABAR RUTA", style = PulseText.overlineLg, color = if (route) PulseColors.text else PulseColors.textSecondary)
                    Spacer(Modifier.width(12.dp))
                    PulseSwitch(route) { route = it }
                }
            }
            Column(Modifier.padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Favorite, null, tint = PulseColors.recoveryRed, modifier = Modifier.size(26.dp))
                Text("${live.heartRate ?: "--"}", style = PulseText.bigNumber.copy(fontSize = 64.sp), color = PulseColors.text)
                Text("LPM", style = PulseText.overline, color = PulseColors.textSecondary)
            }
            Box(Modifier.padding(top = 40.dp).size(170.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(if (target) PulseColors.stressLow else PulseColors.textQuiet, size.minDimension / 2 - 4.dp.toPx(), style = Stroke(3.dp.toPx(), pathEffect = if (target) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (target) PulseData.oneDecimal(targetValue) else "---", style = PulseText.bigNumber.copy(fontSize = 34.sp), color = PulseColors.text)
                    Text("OBJETIVO DE ESFUERZO", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (target) "SÍ" else "NO", style = PulseText.overline, color = PulseColors.text)
                        Spacer(Modifier.width(8.dp))
                        PulseSwitch(target) { target = it }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 30.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(PulseColors.accentBrush).pulseClick {
                    if (route && act.distance && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else start()
                },
                contentAlignment = Alignment.Center,
            ) { Text("EMPEZAR ACTIVIDAD", style = PulseText.overline.copy(fontSize = 12.sp), color = PulseColors.onAccent) }
        }
    }
}

@Composable
private fun LiveStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text)
        Text(label, style = PulseText.overline, color = PulseColors.textSecondary)
    }
}

// ---------------------------------------------------------------------------------------------
// Calibración
// ---------------------------------------------------------------------------------------------

private data class Milestone(val title: String, val nights: Int, val verb: String, val icon: ImageVector)

@Composable
fun PulseCalibrationScreen(vm: AppViewModel, onBack: () -> Unit, onHealth: () -> Unit) {
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val nights = PulseData.nightsWithSleep(days)
    var info by remember { mutableStateOf(false) }
    val milestones = listOf(
        Milestone("Análisis del sueño", 1, "desbloquear", Icons.Outlined.Bedtime),
        Milestone("Recuperación personal", 4, "calibrar", Icons.AutoMirrored.Outlined.TrendingUp),
        Milestone("Objetivo de esfuerzo", 4, "desbloquear", Icons.Outlined.Bolt),
        Milestone("Nivel de estrés", 4, "calibrar", Icons.Outlined.Speed),
        Milestone("Monitor de salud", 7, "calibrar", Icons.Outlined.MonitorHeart),
        Milestone("Resumen semanal", 7, "desbloquear", Icons.Outlined.Article),
    )
    val later = listOf(
        Milestone("Edad física", 14, "desbloquear", Icons.AutoMirrored.Outlined.TrendingUp),
        Milestone("Tendencias de 90 días", 21, "desbloquear", Icons.Outlined.BarChart),
    )
    Box(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
                Text("CALIBRACIÓN", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Desbloquea tus hitos", style = PulseText.headline.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
                    Spacer(Modifier.height(8.dp))
                    Text("Cada noche cuenta. Duerme con la pulsera y Pulse irá conociendo tu cuerpo.", style = PulseText.body, color = PulseColors.textSecondary)
                }
                Spacer(Modifier.width(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    DashedProgressRing(nights.coerceAtMost(7), 7, 96.dp)
                    Text("NOCHES", style = PulseText.overline.copy(fontSize = 8.sp), color = PulseColors.textSecondary, modifier = Modifier.padding(top = 36.dp))
                }
            }
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 30.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tus hitos", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.weight(1f))
                Box(Modifier.size(22.dp).border(1.2.dp, PulseColors.textSecondary, RoundedCornerShape(11.dp)).pulseClick { info = !info }, contentAlignment = Alignment.Center) {
                    Text("i", style = PulseText.overline, color = PulseColors.textSecondary)
                }
            }
            if (info) Text("Con 4 noches Pulse ya calcula tu recuperación; con 7, tu línea base queda completa.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp))
            Column(Modifier.padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                milestones.forEach { MilestoneRow(it, nights, bars = true) }
            }
            AccentCard(Modifier.padding(start = 15.dp, end = 15.dp, top = 24.dp), onClick = onHealth) {
                Text("MIENTRAS TANTO", style = PulseText.overline, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text("¿Cómo está tu salud?", style = PulseText.cardTitleLg, color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text("Consulta tu nivel de estrés, tus constantes y tus tendencias en la pestaña Salud.", style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                CtaLink("Ir a salud", onClick = onHealth)
            }
            Text("Más allá de la primera semana", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 30.dp, bottom = 12.dp))
            Column(Modifier.padding(horizontal = 15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                later.forEach { MilestoneRow(it, days.size, bars = false) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MilestoneRow(m: Milestone, have: Int, bars: Boolean) {
    val done = have >= m.nights
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(PulseColors.overlay).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(if (done) PulseColors.accentFillTop else PulseColors.inset), contentAlignment = Alignment.Center) {
            Icon(m.icon, null, tint = if (done) PulseColors.accent else PulseColors.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = PulseText.cardTitle, color = PulseColors.text)
            Text(if (done) "Desbloqueado" else "${m.nights} ${if (m.nights == 1) "noche" else "noches"} para ${m.verb}", style = PulseText.body.copy(fontSize = 12.sp), color = if (done) PulseColors.accent else PulseColors.textSecondary)
            if (bars) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(m.nights) { k -> Box(Modifier.width(18.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (k < have) PulseColors.accent else PulseColors.hairlineStrong)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Edad fisiológica (calcada de la maqueta)
// ---------------------------------------------------------------------------------------------

private data class AgeRow(val label: String, val value: String, val pos: Float?, val min: String, val max: String, val effect: Double?)

@Composable
fun PulseAgeScreenV2(vm: AppViewModel, onBack: () -> Unit, onCoach: () -> Unit) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var vo2 by remember { mutableStateOf<Double?>(null) }
    var info by remember { mutableStateOf(!PulseTips.seen(context, "age")) }
    LaunchedEffect(Unit) {
        runCatching { vm.loadWorkouts() }
        vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull()
    }
    val profileAge = remember { ProfileStore.from(context).age }
    val ready = days.size >= PulseData.pulseAgeDays
    val result = remember(days, vo2, workouts) { PulseData.pulseAge(context, days, vo2, workouts) }
    val prev = remember(days, vo2, workouts) { if (days.size > 14) PulseData.pulseAge(context, days.dropLast(7), vo2, workouts) else null }
    val prev2 = remember(days, vo2, workouts) { if (days.size > 21) PulseData.pulseAge(context, days.dropLast(14), vo2, workouts) else null }
    val pace = if (prev != null && result != null) PulseAgeEngine.paceOfAgeing(prev, result, 7) else null
    val pacePrev = if (prev2 != null && prev != null) PulseAgeEngine.paceOfAgeing(prev2, prev, 7) else null
    // Monthly history: physiological age computed on the data up to the end of each month.
    val history = remember(days, vo2, workouts) {
        (5 downTo 0).mapNotNull { k ->
            val end = LocalDate.now().minusMonths(k.toLong()).withDayOfMonth(1).plusMonths(1).minusDays(1).toString()
            val upto = days.filter { it.day <= end }
            val label = LocalDate.now().minusMonths(k.toLong()).month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() }
            if (upto.size < PulseData.pulseAgeDays) label to null else label to PulseData.pulseAge(context, upto, vo2, workouts)?.pulseAge
        }
    }
    val weekLabel = run {
        val a = LocalDate.now().minusDays(6); val b = LocalDate.now()
        "${a.dayOfMonth} – ${b.dayOfMonth} ${b.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').uppercase(es)}"
    }
    fun f(k: String) = result?.factors?.firstOrNull { it.key == k }
    fun pos(v: Double?, lo: Double, hi: Double) = v?.let { ((it - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) }
    val groups = listOf(
        "Sueño" to listOf(
            AgeRow("REGULARIDAD", f("sleep_regularity")?.value?.let { "${it.roundToInt()} %" } ?: "--", pos(f("sleep_regularity")?.value, 40.0, 100.0), "40 %", "100 %", f("sleep_regularity")?.years),
            AgeRow("HORAS DE SUEÑO", f("sleep_hours")?.value?.let { PulseData.hoursMinutes(it * 60) + " h" } ?: "--", pos(f("sleep_hours")?.value, 5.0, 8.0), "5 h", "8 h", f("sleep_hours")?.years),
        ),
        "Esfuerzo" to listOf(
            AgeRow("ZONAS 1–3 (SEMANA)", f("zones_1_3")?.value?.let { PulseActivity.mmss(it) + " h" } ?: "--", pos(f("zones_1_3")?.value, 0.0, 300.0), "0 h", "5 h", f("zones_1_3")?.years),
            AgeRow("ZONAS 4–5 (SEMANA)", f("zones_4_5")?.value?.let { PulseActivity.mmss(it) + " h" } ?: "--", pos(f("zones_4_5")?.value, 0.0, 60.0), "0 h", "1 h", f("zones_4_5")?.years),
            AgeRow("FUERZA (SEMANA)", f("strength")?.value?.let { PulseActivity.mmss(it) + " h" } ?: "--", pos(f("strength")?.value, 0.0, 120.0), "0 h", "2 h", f("strength")?.years),
            AgeRow("PASOS", f("steps")?.value?.let { PulseData.int(it.roundToInt()) } ?: "--", pos(f("steps")?.value, 0.0, 16000.0), "0", "16.000", f("steps")?.years),
        ),
        "Forma física" to listOf(
            AgeRow("VO₂ MÁX ESTIMADO", f("vo2max")?.value?.roundToInt()?.toString() ?: "--", pos(f("vo2max")?.value, 15.0, 70.0), "15", "70", f("vo2max")?.years),
            AgeRow("PULSO EN REPOSO", f("resting_hr")?.value?.let { "${it.roundToInt()} lpm" } ?: "--", pos(f("resting_hr")?.value?.let { 120 - it }, 40.0, 80.0), "80", "40", f("resting_hr")?.years),
            AgeRow("MASA MAGRA", f("lean_mass")?.value?.let { "${it.roundToInt()} %" } ?: "--", pos(f("lean_mass")?.value, 50.0, 90.0), "50 %", "90 %", f("lean_mass")?.years),
        ),
    )

    Box(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp)) }
                Text("EDAD FISIOLÓGICA", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Box(Modifier.size(34.dp).border(1.5.dp, PulseColors.textSecondary, RoundedCornerShape(17.dp)).pulseClick { info = true }, contentAlignment = Alignment.Center) {
                    Text("i", style = PulseText.ringNumber.copy(fontSize = 14.sp), color = PulseColors.textQuiet)
                }
            }
            Text(weekLabel, style = PulseText.overline, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(170.dp).clip(RoundedCornerShape(85.dp)).background(Brush.radialGradient(listOf(Color(0xFF4A4258), Color(0xFF2B2635)))), contentAlignment = Alignment.Center) {
                    if (ready && result != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(Locale.US, "%.1f", result.pulseAge), style = PulseText.bigNumber.copy(fontSize = 48.sp), color = PulseColors.text)
                            Text("EDAD FISIOLÓGICA", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textSecondary)
                        }
                    } else DashedProgressRing(days.size.coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 130.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (ready && result != null) String.format(es, "%.1f años %s que tu edad", abs(result.yearsYounger), if (result.yearsYounger >= 0) "menos" else "más")
                    else "Faltan ${PulseData.pulseAgeDays - days.size} días para calcularla. Mientras, abajo ves cómo van tus hábitos.",
                    style = PulseText.body, color = if (ready && (result?.yearsYounger ?: 0.0) >= 0) PulseColors.recoveryGreen else PulseColors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp),
                )
            }
            // Pace of ageing
            Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(16.dp)) {
                Text("RITMO DE ENVEJECIMIENTO", style = PulseText.overlineLg, color = PulseColors.text)
                if (pace != null && pacePrev != null) {
                    Text(if (pace < pacePrev) "▼ más lento que la semana pasada" else "▲ más rápido que la semana pasada", style = PulseText.body.copy(fontSize = 12.sp), color = if (pace < pacePrev) PulseColors.accentHover else PulseColors.stressHigh, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pace == null) "—" else if (pace < 1) "Más lento" else "Más rápido", style = PulseText.cardTitle, color = PulseColors.text, modifier = Modifier.weight(1f))
                    Text(pace?.let { String.format(es, "%.1f×", it.coerceIn(-1.0, 3.0)) } ?: "--", style = PulseText.ringNumberLg, color = PulseColors.text)
                }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(14.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(Brush.horizontalGradient(listOf(PulseColors.accent, PulseColors.stressLow, PulseColors.stressHigh)), Offset(0f, 4.dp.toPx()), Size(size.width, 6.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                        val one = size.width * 0.5f
                        drawLine(PulseColors.text, Offset(one, 0f), Offset(one, size.height), 1.dp.toPx())
                        pace?.let { drawCircle(Color.White, 7.dp.toPx(), Offset(((it.coerceIn(-1.0, 3.0) + 1) / 4).toFloat() * size.width, size.height / 2)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("−1,0×", "1,0×", "3,0×").forEach { Text(it, style = PulseText.chartAxis, color = PulseColors.textSecondary) }
                }
            }
            groups.forEach { (title, rows) ->
                Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = PulseText.cardTitleLg, color = PulseColors.text, modifier = Modifier.weight(1f))
                        Text("años", style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textSecondary)
                    }
                    rows.forEachIndexed { k, r ->
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.label, style = PulseText.overline, color = PulseColors.text, modifier = Modifier.weight(1f))
                                Text(r.value, style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = PulseColors.text)
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    r.effect?.let { String.format(es, "%+.1f", it).replace("-", "−") } ?: "—",
                                    style = PulseText.small.copy(fontWeight = FontWeight.Bold),
                                    color = when { r.effect == null -> PulseColors.textTertiary; r.effect > 0 -> PulseColors.stressHigh; else -> PulseColors.accentHover },
                                    textAlign = TextAlign.End, modifier = Modifier.width(44.dp),
                                )
                            }
                            Box(Modifier.padding(top = 8.dp, end = 58.dp).fillMaxWidth().height(10.dp)) {
                                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.hairline))
                                r.pos?.let { p ->
                                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                                        Box(Modifier.offset(x = (maxWidth - 10.dp) * p).size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
                                    }
                                }
                            }
                            Row(Modifier.padding(top = 4.dp, end = 58.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(r.min, style = PulseText.chartAxis, color = PulseColors.textTertiary)
                                Text(r.max, style = PulseText.chartAxis, color = PulseColors.textTertiary)
                            }
                        }
                        if (k < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.hairline))
                    }
                }
            }
            // Evolution chart
            Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(16.dp)) {
                Text("EVOLUCIÓN DE TU EDAD FISIOLÓGICA", style = PulseText.overlineLg, color = PulseColors.text)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(PulseColors.accent, "FISIOLÓGICA"); Legend(PulseColors.textSecondary, "REAL")
                }
                val vals = history.mapNotNull { it.second } + profileAge.toDouble()
                val lo = (vals.minOrNull() ?: profileAge.toDouble()) - 2
                val hi = (vals.maxOrNull() ?: profileAge.toDouble()) + 2
                Canvas(Modifier.padding(top = 14.dp).fillMaxWidth().height(150.dp)) {
                    for (g in 0..4) { val y = size.height * g / 4f; drawLine(PulseColors.hairline, Offset(0f, y), Offset(size.width, y), 1f) }
                    val yReal = size.height - ((profileAge - lo) / (hi - lo)).toFloat() * size.height
                    drawLine(PulseColors.textSecondary, Offset(0f, yReal), Offset(size.width, yReal), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    val pts = history.mapIndexedNotNull { idx, (_, v) -> v?.let { Offset(size.width * idx / (history.size - 1).coerceAtLeast(1), size.height - ((it - lo) / (hi - lo)).toFloat() * size.height) } }
                    if (pts.size >= 2) drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, PulseColors.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                    pts.forEach { drawCircle(PulseColors.accent, 4.dp.toPx(), it) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    history.forEach { Text(it.first, style = PulseText.chartAxis, color = PulseColors.textSecondary) }
                }
                if (history.all { it.second == null }) Text("Aparecerá cuando lleves 3 semanas con la pulsera.", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textTertiary, modifier = Modifier.padding(top = 10.dp))
            }
            AccentCard(Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp), onClick = onCoach) {
                Text("Pide un plan al coach", style = PulseText.cardTitleLg, color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text("Te propone pasos concretos para mejorar las métricas que más te restan.", style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                CtaLink("Crear plan", onClick = onCoach)
            }
            Text(
                "Pulse calcula tu edad fisiológica con tus propios datos; no es un dispositivo médico.",
                style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textTertiary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 16.dp, bottom = 40.dp),
            )
        }
        if (info) {
            FirstVisitTip(
                "Edad fisiológica",
                "Partimos de tu edad real y sumamos o restamos años según nueve hábitos: horas y regularidad del sueño, pasos, minutos en zonas de pulso, fuerza, VO₂ máx., pulso en reposo y masa magra. Se basa en guías públicas (OMS 2020) y estudios publicados.",
            ) { PulseTips.markSeen(context, "age"); info = false }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textQuiet)
    }
}
