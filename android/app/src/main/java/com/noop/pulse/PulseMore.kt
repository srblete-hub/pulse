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
import kotlinx.coroutines.launch
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
                        Column(Modifier.clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
    val days by com.noop.pulse.engine.pulseDays(vm)
    var i by rememberSaveable { mutableStateOf(0) }
    var route by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(Unit) { vm.requestRealtimeHr(); onDispose { vm.releaseRealtimeHr() } }
    val act = activities[i]
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Strength: motion capture for set / repetition detection (WHOOP 5.0/MG).
    var strengthOn by remember { mutableStateOf(PulseStrength.activeId(context) != null) }
    var strengthNote by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<PulseStrength.Session?>(null) }
    fun start() {
        val sport = WorkoutSport.all.firstOrNull { it.name.equals(act.sport, true) } ?: WorkoutSport.all.firstOrNull { it.name.contains(act.sport, true) } ?: WorkoutSport.default
        summary = null
        vm.startWorkout(sport, gpsEnabled = route && act.distance)
        if (act.sport == "Strength") {
            strengthOn = PulseStrength.start(context, vm)
            strengthNote = if (strengthOn) null else "El conteo de series necesita una WHOOP 5.0/MG conectada."
        }
    }
    fun finish() {
        vm.endWorkout()
        if (strengthOn) scope.launch {
            summary = PulseStrength.stop(context, vm)
            strengthOn = false
        }
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) route = false; start() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PulseColors.divider, PulseColors.base))).statusBarsPadding().navigationBarsPadding()) {
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
                val profileStore = remember { com.noop.ui.ProfileStore.from(context) }
                val rest = days.lastOrNull { it.restingHr != null }?.restingHr?.toDouble()
                var load by remember { mutableStateOf<com.noop.pulse.engine.PulseLiveLoad.Live?>(null) }
                LaunchedEffect(w.samples.size / 5) {
                    load = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        runCatching {
                            com.noop.pulse.engine.PulseLiveLoad.compute(
                                w.samples, profileStore.toUserProfile(), profileStore.hrMax.toDouble(), rest,
                                com.noop.ui.NoopPrefs.effortMethod(context),
                            )
                        }.getOrNull()
                    }
                }
                Row(Modifier.padding(top = 30.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    LiveStat("${live.heartRate ?: "--"}", "LPM")
                    LiveStat(PulseData.oneDecimal(w.liveStrain * 21 / 100), "ESFUERZO")
                    if (w.distanceM > 0) LiveStat(String.format(Locale.US, "%.2f", w.distanceM / 1000), "KM")
                }
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    LiveStat(load?.kcal?.let { "${it.roundToInt()}" } ?: "--", "KCAL")
                    LiveStat(load?.kcalPerMin?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "KCAL/MIN")
                    LiveStat(load?.met?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "MET")
                }
                if (strengthOn) StrengthLive(vm)
                strengthNote?.let { Text(it, style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) }
                load?.projectedEffort21?.let { p ->
                    Text(
                        "Si sigues ${com.noop.pulse.engine.PulseLiveLoad.PROJECT_MIN} min más a este ritmo: esfuerzo ${PulseData.oneDecimal(p)}",
                        style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.padding(start = 24.dp, end = 24.dp, bottom = 30.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(PulseMetrics.buttonRadius)).background(PulseColors.recoveryRed).pulseClick { finish() },
                    contentAlignment = Alignment.Center,
                ) { Text("FINALIZAR ACTIVIDAD", style = PulseText.overline.copy(fontSize = 12.sp), color = PulseColors.text) }
            }
            return@Box
        }
        summary?.let { s ->
            StrengthSummary(s, onChange = { summary = it }, onClose = { summary = null })
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
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 30.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(PulseMetrics.buttonRadius)).background(PulseColors.accentBrush).pulseClick {
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
private fun StrengthLive(vm: AppViewModel) {
    val context = LocalContext.current
    val live by vm.live.collectAsStateWithLifecycle()
    var sets by remember { mutableStateOf<List<com.noop.pulse.engine.PulseRepEngine.DetectedSet>>(emptyList()) }
    var covered by remember { mutableStateOf(0) }
    LaunchedEffect(live.connected) { if (live.connected) PulseStrength.rearm(context, vm) }
    LaunchedEffect(Unit) {
        while (true) {
            val id = PulseStrength.activeId(context) ?: break
            covered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PulseStrength.coveredSeconds(context) }
            sets = runCatching { PulseStrength.analyze(context, id) }.getOrDefault(emptyList())
            delay(5_000)
        }
    }
    Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        LiveStat("${sets.size}", "SERIES")
        LiveStat(sets.lastOrNull()?.reps?.toString() ?: "--", "REPS ÚLTIMA")
    }
    Text(
        if (covered == 0) "Esperando el movimiento de la pulsera…" else "Movimiento recibido: ${covered / 60}:%02d".format(covered % 60),
        style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp),
    )
}

@Composable
private fun StrengthSummary(s: PulseStrength.Session, onChange: (PulseStrength.Session) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val (checked, right) = remember(s) { PulseStrength.accuracy(context) }
    Column(Modifier.fillMaxSize().padding(top = 80.dp, start = 18.dp, end = 18.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SERIES DETECTADAS", style = PulseText.tabTitle, color = PulseColors.text)
        Text(
            if (s.sets.isEmpty()) "No he encontrado series claras. Puede que la pulsera no enviara el movimiento o que los ejercicios fueran sin ritmo (planchas, isométricos)."
            else "Revisa los números y corrige los que no cuadren: así sé cuánto acierto.",
            style = PulseText.body, color = PulseColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
        )
        s.sets.forEachIndexed { idx, set ->
            val restBefore = s.sets.getOrNull(idx - 1)?.let { set.startTs - it.endTs }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Serie ${idx + 1}", style = PulseText.cardTitleLg, color = PulseColors.text)
                    Text("${set.endTs - set.startTs + 1} s" + (restBefore?.let { " · descanso ${it / 60}:%02d".format(it % 60) } ?: "") +
                        (if (set.corrected != null && set.corrected != set.detected) " · Pulse contó ${set.detected}" else ""),
                        style = PulseText.body, color = PulseColors.textSecondary)
                }
                Box(Modifier.size(36.dp).pulseClick { PulseStrength.correct(context, s.id, idx, set.reps - 1); PulseStrength.last(context)?.let(onChange) }, contentAlignment = Alignment.Center) { Text("−", style = PulseText.cardTitleLg, color = PulseColors.text) }
                Text("${set.reps}", style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text, modifier = Modifier.padding(horizontal = 8.dp))
                Box(Modifier.size(36.dp).pulseClick { PulseStrength.correct(context, s.id, idx, set.reps + 1); PulseStrength.last(context)?.let(onChange) }, contentAlignment = Alignment.Center) { Text("+", style = PulseText.cardTitleLg, color = PulseColors.text) }
            }
        }
        if (checked > 0) Text("Acierto exacto hasta ahora: $right de $checked series revisadas.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.padding(bottom = 30.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(PulseMetrics.buttonRadius)).background(PulseColors.accentBrush)
                .pulseClick { PulseStrength.confirm(context, s.id); onClose() },
            contentAlignment = Alignment.Center,
        ) { Text("GUARDAR", style = PulseText.overline.copy(fontSize = 12.sp), color = PulseColors.onAccent) }
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
    val days by com.noop.pulse.engine.pulseDays(vm)
    val nights = PulseData.nightsWithSleep(days)
    var info by remember { mutableStateOf<Milestone?>(null) }
    val milestones = listOf(
        Milestone("Análisis del sueño", 1, "desbloquear", Icons.Outlined.Bedtime),
        Milestone("Recuperación personal", 4, "calibrar", Icons.AutoMirrored.Outlined.TrendingUp),
        Milestone("Nivel de estrés", 4, "calibrar", Icons.Outlined.Speed),
        Milestone("Monitor de salud", 7, "calibrar", Icons.Outlined.MonitorHeart),
        Milestone("Resumen semanal", 7, "desbloquear", Icons.Outlined.Article),
    )
    val later = listOf(
        Milestone("Edad fisiológica", 10, "desbloquear", Icons.AutoMirrored.Outlined.TrendingUp),
        Milestone("Tendencias de 90 días", 21, "desbloquear", Icons.Outlined.BarChart),
    )
    Box(Modifier.fillMaxSize().background(PulseColors.base)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(PulseColors.hairlineStrong, PulseColors.divider, PulseColors.base)))
                    .statusBarsPadding().padding(bottom = 30.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, "Cerrar", tint = PulseColors.text, modifier = Modifier.size(24.dp)) }
                    Text("CALIBRACIÓN", style = PulseText.tabTitle.copy(fontSize = 12.sp), color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(44.dp))
                }
                Row(Modifier.padding(start = 22.dp, end = 16.dp, top = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Desbloquea tus hitos", style = PulseText.headline.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
                        Spacer(Modifier.height(10.dp))
                        Text("Cada noche cuenta. Duerme con la pulsera y Pulse irá desbloqueando funciones y consejos más precisos.", style = PulseText.bodyLg, color = PulseColors.textQuiet)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DashedProgressRing(nights.coerceAtMost(7), 7, 96.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("NOCHES\nREGISTRADAS", style = PulseText.overline.copy(lineHeight = 14.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
            Text("Tus hitos", style = PulseText.sectionTitle.copy(fontSize = 20.sp), color = PulseColors.text, modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 14.dp))
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                milestones.forEach { m -> MilestoneCard(m, nights, bars = true) { info = m } }
            }
            AccentCard(Modifier.padding(start = 18.dp, end = 18.dp, top = 26.dp), onClick = onHealth) {
                Text("MIENTRAS TANTO", style = PulseText.overline, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Text("¿Cómo está tu salud?", style = PulseText.cardTitleLg, color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text("Consulta tu nivel de estrés, tus constantes y tus tendencias en la pestaña Salud.", style = PulseText.body, color = PulseColors.textSecondary)
                Spacer(Modifier.height(6.dp))
                CtaLink("Ir a salud", onClick = onHealth)
            }
            Text("Más allá de la primera semana", style = PulseText.sectionTitle.copy(fontSize = 20.sp), color = PulseColors.text, modifier = Modifier.padding(start = 22.dp, top = 30.dp, bottom = 14.dp))
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                later.forEach { m -> MilestoneCard(m, days.size, bars = false) { info = m } }
            }
            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
        info?.let { m ->
            FirstVisitTip(m.title, milestoneInfo(m.title)) { info = null }
        }
    }
}

private fun milestoneInfo(title: String) = when (title) {
    "Análisis del sueño" -> "Tras tu primera noche verás cuánto has dormido, tus fases y tu rendimiento del sueño."
    "Recuperación personal" -> "Con 4 noches Pulse conoce tu VFC y tu pulso en reposo normales y puede decirte cada mañana cómo de recuperado estás."
    "Nivel de estrés" -> "Con 4 noches Pulse sabe cuál es tu estado de reposo y puede medir lo activado que estás durante el día."
    "Monitor de salud" -> "Con 7 noches Pulse conoce el rango habitual de tu respiración, oxígeno, pulso, VFC y temperatura."
    "Resumen semanal" -> "Al completar tu primera semana recibirás un resumen con tus tendencias."
    "Edad fisiológica" -> "Con 10 días de datos Pulse calcula tu edad fisiológica; el ritmo de envejecimiento llega cuando tengas más de un mes."
    else -> "Con 3 semanas de datos podrás comparar tu evolución mes a mes."
}

@Composable
private fun MilestoneCard(m: Milestone, have: Int, bars: Boolean, onInfo: () -> Unit) {
    val done = have >= m.nights
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, PulseColors.overlay, RoundedCornerShape(18.dp)).padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 26.dp)) {
        Box(Modifier.align(Alignment.TopEnd).offset(y = (-8).dp).size(26.dp).border(1.3.dp, PulseColors.textSecondary, RoundedCornerShape(PulseMetrics.cardRadius)).pulseClick(onInfo), contentAlignment = Alignment.Center) {
            Text("i", style = PulseText.overline.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(26.dp))
                    .background(Brush.radialGradient(listOf(if (done) PulseColors.accent.copy(alpha = 0.35f) else PulseColors.hairlineStrong, PulseColors.overlay))),
                contentAlignment = Alignment.Center,
            ) { Icon(m.icon, null, tint = if (done) PulseColors.accent else PulseColors.textTertiary, modifier = Modifier.size(38.dp)) }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(m.title, style = PulseText.cardTitleLg.copy(fontSize = 16.sp), color = PulseColors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (done) "Desbloqueado" else "${m.nights} ${if (m.nights == 1) "noche" else "noches"} para ${m.verb}",
                    style = PulseText.bodyLg, color = if (done) PulseColors.accent else PulseColors.textSecondary,
                )
                if (bars) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(m.nights) { k -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (k < have) PulseColors.accent else PulseColors.hairline)) }
                    }
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
    val days by com.noop.pulse.engine.pulseDays(vm)
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    var vo2 by remember { mutableStateOf<Double?>(null) }
    var info by remember { mutableStateOf(!PulseTips.seen(context, "age")) }
    LaunchedEffect(Unit) {
        runCatching { vm.loadWorkouts() }
        vo2 = runCatching { vm.repo.latestMetricComputedUnion(vm.activeStrapId, "vo2max_est")?.value }.getOrNull()
    }
    val profileAge = remember { ProfileStore.from(context).age }
    val ready = PulseData.dataDays(days) >= PulseData.pulseAgeDays
    var result by remember { mutableStateOf<PulseAgeEngine.Result?>(null) }
    var paceNow by remember { mutableStateOf<Double?>(null) }
    var pacePrevWeek by remember { mutableStateOf<Double?>(null) }
    val monthLabels = remember { (5 downTo 0).map { k -> LocalDate.now().minusMonths(k.toLong()).month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() } } }
    var history by remember { mutableStateOf(monthLabels.map { it to (null as Double?) }) }
    // Every figure below is measured from the strap over a 4-week window (see PulseAgeData), off the UI thread.
    LaunchedEffect(days.size, vo2, workouts.size) {
        if (PulseData.dataDays(days) < PulseData.pulseAgeDays) return@LaunchedEffect
        val now = com.noop.pulse.engine.PulseAge.compute(context, vm, days, workouts, vo2)
        result = now?.result
        paceNow = now?.pace
        pacePrevWeek = com.noop.pulse.engine.PulseAge.compute(context, vm, days, workouts, vo2, 7)?.pace
        history = (5 downTo 0).mapIndexed { i, k ->
            val end = if (k == 0) LocalDate.now() else LocalDate.now().minusMonths(k.toLong()).withDayOfMonth(1).plusMonths(1).minusDays(1)
            val offset = java.time.temporal.ChronoUnit.DAYS.between(end, LocalDate.now()).toInt()
            val enough = PulseData.dataDays(days.filter { it.day <= end.toString() }) >= PulseData.pulseAgeDays
            monthLabels[i] to (if (enough) com.noop.pulse.engine.PulseAge.compute(context, vm, days, workouts, vo2, offset)?.result?.pulseAge else null)
        }
    }
    val pace = paceNow
    val pacePrev = pacePrevWeek
    val weekLabel = run {
        val a = LocalDate.now().minusDays(6); val b = LocalDate.now()
        "${a.dayOfMonth} – ${b.dayOfMonth} ${b.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').uppercase(es)}"
    }
    fun f(k: String) = result?.factors?.firstOrNull { it.key == k }
    fun pos(v: Double?, lo: Double, hi: Double) = v?.let { ((it - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) }
    // noop's Fitness Age (Nes): the two inputs that move it, plus the context it is built from.
    val groups = listOf(
        "Lo que la calcula" to listOf(
            AgeRow("PULSO EN REPOSO (MEDIANA 7 DÍAS)", f("resting_hr")?.value?.let { "${it.roundToInt()} lpm" } ?: "--", pos(f("resting_hr")?.value?.let { 120 - it }, 40.0, 80.0), "80", "40", f("resting_hr")?.years),
            AgeRow("ÍNDICE DE ACTIVIDAD (7 DÍAS)", f("activity_index")?.value?.let { String.format(es, "%.1f", it) } ?: "--", pos(f("activity_index")?.value, 0.0, 15.0), "0", "15", f("activity_index")?.years),
        ),
        "Contexto" to listOf(
            AgeRow("DÍAS ACTIVOS ESTA SEMANA", f("active_days")?.value?.roundToInt()?.toString() ?: "--", pos(f("active_days")?.value, 0.0, 7.0), "0", "7", null),
            AgeRow("VO₂ MÁX. ESTIMADO", f("vo2max")?.value?.roundToInt()?.toString() ?: "--", pos(f("vo2max")?.value, 15.0, 70.0), "15", "70", null),
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
                Box(Modifier.size(170.dp).clip(RoundedCornerShape(85.dp)).background(Brush.radialGradient(listOf(PulseColors.hairlineStrong, PulseColors.divider))), contentAlignment = Alignment.Center) {
                    if (ready && result != null) { val result = result!!
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format(Locale.US, "%.1f", result.pulseAge), style = PulseText.bigNumber.copy(fontSize = 48.sp), color = PulseColors.text)
                            Text("EDAD FISIOLÓGICA", style = PulseText.overline.copy(fontSize = 9.sp), color = PulseColors.textSecondary)
                        }
                    } else DashedProgressRing(PulseData.dataDays(days).coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 130.dp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (ready && result != null) String.format(es, "%.1f años %s que tu edad", abs(result!!.yearsYounger), if (result!!.yearsYounger >= 0) "menos" else "más")
                    else com.noop.pulse.engine.PulseAge.waitText(days) + " Mientras, abajo ves cómo van tus hábitos.",
                    style = PulseText.body, color = if (ready && (result?.yearsYounger ?: 0.0) >= 0) PulseColors.recoveryGreen else PulseColors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 30.dp),
                )
            }
            // Pace of ageing: noop does not compute one, so the card only appears if a pace exists.
            if (pace != null) Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).padding(16.dp)) {
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
                Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).padding(16.dp)) {
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
            Column(Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.raised).padding(16.dp)) {
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
                if (history.all { it.second == null }) Text("Aparecerá cuando lleves 10 días con la pulsera.", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textTertiary, modifier = Modifier.padding(top = 10.dp))
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
                "Es la edad que corresponde a tu forma física según el estudio HUNT (Nes et al.): parte de tu edad real y la ajusta con tu pulso en reposo (mediana de 7 días) y tu índice de actividad (cuántos días te esfuerzas de verdad y cuánto). Se calcula como en NOOP. No es un dispositivo médico.",
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
