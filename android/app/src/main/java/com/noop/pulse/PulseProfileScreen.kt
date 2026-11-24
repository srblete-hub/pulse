package com.noop.pulse

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.data.DailyMetric
import com.noop.data.WorkoutRow
import com.noop.ui.AppViewModel
import com.noop.ui.NoopType
import com.noop.ui.ProfileStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val profileBrush = Brush.verticalGradient(0f to Color(0xFF2A2140), 0.22f to Color(0xFF1B1F23), 1f to PulseColors.base)
private val segBg = Color(0xFF1A1E22)
private val gold = Color(0xFFC9A45C)
private val coachRowBrush = Brush.horizontalGradient(listOf(Color(0xFF2E2A45), Color(0xFF24303A)))
private val es = Locale("es", "ES")

/** Profile-page statistics derived from the stored daily rows. Pure, unit-testable. */
object ProfileStats {
    /** Level from the number of scored recoveries: 0, 10, 30, 60, 100, 150, 210… */
    fun level(recoveries: Int): Int {
        var lvl = 1
        var need = 10
        var step = 20
        var acc = 0
        while (recoveries >= acc + need) { acc += need; lvl++; need = step; step += 10 }
        return lvl
    }

    /** Longest run of consecutive calendar days whose row satisfies [ok]. */
    fun longestRun(days: List<DailyMetric>, ok: (DailyMetric) -> Boolean): Int {
        val good = days.filter(ok).mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }.toSortedSet()
        var best = 0
        var cur = 0
        var prev: LocalDate? = null
        for (d in good) {
            cur = if (prev != null && prev.plusDays(1) == d) cur + 1 else 1
            best = maxOf(best, cur)
            prev = d
        }
        return best
    }

    fun inRange(days: List<DailyMetric>, months: Int?): List<DailyMetric> {
        if (months == null) return days
        val from = LocalDate.now().minusMonths(months.toLong()).toString()
        return days.filter { it.day >= from }
    }
}

private fun sportLabel(raw: String): String = when (raw.lowercase().replace('_', ' ').trim()) {
    "running", "run", "carrera" -> "Correr"
    "walking", "walk" -> "Caminar"
    "cycling", "bike", "biking", "ciclismo" -> "Ciclismo"
    "strength", "strength training", "weightlifting", "weights", "functional fitness" -> "Fuerza"
    "soccer", "football", "futbol" -> "Fútbol"
    "swimming", "swim" -> "Natación"
    "yoga" -> "Yoga"
    "hiking" -> "Senderismo"
    "tennis" -> "Tenis"
    "padel", "pádel" -> "Pádel"
    "basketball" -> "Baloncesto"
    "hiit" -> "HIIT"
    "rowing" -> "Remo"
    "elliptical" -> "Elíptica"
    "other", "activity", "" -> "Actividad"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun sportIcon(label: String): ImageVector = when (label) {
    "Fuerza" -> Icons.Outlined.FitnessCenter
    "Fútbol" -> Icons.Outlined.SportsSoccer
    "Correr", "Caminar", "Senderismo" -> Icons.AutoMirrored.Outlined.DirectionsRun
    else -> Icons.Outlined.Bolt
}

@Composable
fun PulseProfileScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onEdit: () -> Unit,
    onAge: () -> Unit,
    onCoachMemory: () -> Unit,
    onCalibration: () -> Unit,
) {
    val context = LocalContext.current
    val days by com.noop.pulse.engine.pulseDays(vm)
    val streaks by vm.streaks.collectAsStateWithLifecycle()
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { vm.loadWorkouts() } }
    val profile = remember { ProfileStore.from(context) }
    val first = PulseProfile.firstName(context)
    val last = PulseProfile.lastName(context)
    val initials = PulseProfile.initials(context)
    val joined = (PulseProfile.joinedDay(context) ?: days.firstOrNull()?.day)?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    } ?: LocalDate.now()
    val recCount = days.count { it.recovery != null }
    val level = ProfileStats.level(recCount)

    Column(Modifier.fillMaxSize().background(profileBrush).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).pulseClick(onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = PulseColors.text, modifier = Modifier.size(22.dp))
            }
            Text("PERFIL", style = PulseText.tabTitle, color = PulseColors.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).pulseClick(onSettings), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Settings, "Ajustes", tint = PulseColors.text, modifier = Modifier.size(23.dp))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 30.dp)) {
            Box(Modifier.size(94.dp).clip(RoundedCornerShape(47.dp)).background(PulseColors.accentBrush), contentAlignment = Alignment.Center) {
                if (initials.isNotEmpty()) {
                    Text(initials, style = TextStyle(fontFamily = NoopType.label, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp), color = Color(0xFF15121C))
                } else {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Edit, null, tint = Color(0xFF15121C), modifier = Modifier.size(30.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "Tu nombre" }, style = PulseText.headline.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold), color = PulseColors.text)
                    Spacer(Modifier.height(4.dp))
                    Text("${profile.age} años · ${Locale.getDefault().country.ifEmpty { "ES" }}", style = PulseText.body, color = PulseColors.textSecondary)
                }
                Row(
                    Modifier.height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1AFFFFFF)).pulseClick(onEdit).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = PulseColors.text, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("EDITAR", style = PulseText.overline, color = PulseColors.text)
                }
            }
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xB3121518)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.noop.ui.PulseMark(size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Text("Usando Pulse desde ", style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary)
                Text(
                    joined.month.getDisplayName(JTextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() } + " " + joined.year,
                    style = PulseText.body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = PulseColors.text,
                )
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigTile(Modifier.weight(1f), onCalibration, {
                    Box(Modifier.size(34.dp, 40.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width; val h = size.height
                            val p = Path().apply {
                                moveTo(w / 2, 0f); lineTo(w, h * 0.25f); lineTo(w, h * 0.75f); lineTo(w / 2, h); lineTo(0f, h * 0.75f); lineTo(0f, h * 0.25f); close()
                            }
                            drawPath(p, PulseColors.accent, style = Stroke(2.dp.toPx()))
                        }
                        Text("$level", style = PulseText.ringNumber.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = PulseColors.text)
                    }
                }, "NIVEL $level", "$recCount ${if (recCount == 1) "recuperación" else "recuperaciones"}")
                var ageReady by remember { mutableStateOf(false) }
                LaunchedEffect(days.size) {
                    ageReady = PulseData.dataDays(days) >= PulseData.pulseAgeDays &&
                        runCatching { com.noop.pulse.engine.PulseAge.compute(context, vm, days, emptyList(), null) }.getOrNull() != null
                }
                val ageText = if (!ageReady) com.noop.pulse.engine.PulseAge.waitText(days) else "Ver tu edad fisiológica"
                BigTile(Modifier.weight(1f), onAge, {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(Brush.radialGradient(listOf(Color(0xFF4A4258), Color(0xFF2B2635)))), contentAlignment = Alignment.Center) {
                        DashedProgressRing(PulseData.dataDays(days).coerceAtMost(PulseData.pulseAgeDays) * 7 / PulseData.pulseAgeDays, 7, 52.dp)
                    }
                }, "EDAD FISIOLÓGICA", ageText)
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(PulseColors.overlay).pulseClick(onCalibration).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RACHA DE DÍAS", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Whatshot, null, tint = PulseColors.gold, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(10.dp))
                Text("${streaks.current} ${if (streaks.current == 1) "día" else "días"}", style = PulseText.small.copy(fontSize = 13.sp), color = PulseColors.text)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(16.dp))
            }
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp)).background(coachRowBrush).pulseClick(onCoachMemory).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Psychology, null, tint = Color(0xFF8FB8FF), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("LO QUE RECUERDA EL COACH", style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        // Logros
        var showAllBadges by rememberSaveable { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 34.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Logros", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.weight(1f))
            Row(Modifier.pulseClick { showAllBadges = !showAllBadges }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (showAllBadges) "VER MENOS" else "VER TODOS", style = PulseText.overline, color = PulseColors.text)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = PulseColors.text, modifier = Modifier.size(14.dp))
            }
        }
        val badges = badgesFor(days, workouts)
        (if (showAllBadges) badges else badges.take(3)).chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 18.dp)) {
                row.forEach { b -> Badge(b, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Mejores marcas
        var bestRange by rememberSaveable { mutableStateOf(2) }
        Text("Tus mejores marcas", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 16.dp))
        val bestDays = ProfileStats.inRange(days, listOf(1, 3, null)[bestRange])
        Column(Modifier.padding(horizontal = 15.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(16.dp)) {
            RangeTabs(bestRange) { bestRange = it }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                val bs = bestDays.mapNotNull { PulseData.sleepPct(it) }.maxOrNull()
                val br = bestDays.mapNotNull { it.recovery }.maxOrNull()
                val be = bestDays.mapNotNull { PulseData.effort21(it) }.maxOrNull()
                BestRing(PulseColors.sleep, bs?.div(100), PulseData.pct(bs), "Mejor sueño", Modifier.weight(1f))
                BestRing(PulseColors.recoveryGreen, br?.div(100), PulseData.pct(br), "Mejor recuperación", Modifier.weight(1f))
                BestRing(PulseColors.strain, be?.div(21), be?.let { PulseData.oneDecimal(it) } ?: "--", "Mayor esfuerzo", Modifier.weight(1f))
            }
            DividerLabel("Rachas")
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                StreakCell(PulseColors.sleep, Icons.Outlined.Bedtime, ProfileStats.longestRun(bestDays) { (PulseData.sleepPct(it) ?: 0.0) >= 70 }, "Sueño +70%", Modifier.weight(1f))
                StreakCell(PulseColors.recoveryGreen, Icons.Outlined.Favorite, ProfileStats.longestRun(bestDays) { (it.recovery ?: 0.0) >= 67 }, "Recuperación verde", Modifier.weight(1f))
                StreakCell(PulseColors.strain, Icons.Outlined.ShowChart, ProfileStats.longestRun(bestDays) { (PulseData.effort21(it) ?: 0.0) >= 10 }, "Esfuerzo +10", Modifier.weight(1f))
            }
            DividerLabel("Estadísticas")
            val rhr = bestDays.mapNotNull { it.restingHr }
            val hrv = bestDays.mapNotNull { it.avgHrv?.roundToInt() }
            val maxHr = workouts.filter { w -> listOf(1, 3, null)[bestRange]?.let { m -> Instant.ofEpochSecond(w.startTs).isAfter(Instant.now().minusSeconds(m * 31L * 86400)) } ?: true }.mapNotNull { it.maxHr }.maxOrNull()
            val rows = listOf(
                Triple(Icons.Outlined.TrendingDown, "FC en reposo más baja", rhr.minOrNull()?.toString() to "lpm"),
                Triple(Icons.Outlined.TrendingUp, "FC en reposo más alta", rhr.maxOrNull()?.toString() to "lpm"),
                Triple(Icons.Outlined.TrendingDown, "VFC más baja", hrv.minOrNull()?.toString() to "ms"),
                Triple(Icons.Outlined.TrendingUp, "VFC más alta", hrv.maxOrNull()?.toString() to "ms"),
                Triple(Icons.Outlined.Favorite, "Pulso máximo", maxHr?.toString() to "lpm"),
                Triple(Icons.Outlined.Timer, "Sueño más largo", bestDays.mapNotNull { it.totalSleepMin }.maxOrNull()?.let { PulseData.hoursMinutes(it) } to "h"),
            )
            rows.forEachIndexed { i, (icon, label, v) ->
                Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).border(1.3.dp, gold, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = gold, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(label, style = PulseText.body.copy(fontSize = 13.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                    Text(v.first ?: "--", style = PulseText.bigNumber, color = PulseColors.text)
                    Spacer(Modifier.width(4.dp))
                    Text(v.second, style = PulseText.chartAxis, color = PulseColors.textSecondary)
                }
                if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.hairline))
            }
        }

        // Tu actividad
        var actRange by rememberSaveable { mutableStateOf(2) }
        var showAllActs by rememberSaveable { mutableStateOf(false) }
        Text("Tu actividad", style = PulseText.sectionTitle, color = PulseColors.text, modifier = Modifier.padding(start = 20.dp, top = 40.dp, bottom = 16.dp))
        val months = listOf(1, 3, null)[actRange]
        val ws = workouts.filter { w -> months?.let { Instant.ofEpochSecond(w.startTs).isAfter(Instant.now().minusSeconds(it * 31L * 86400)) } ?: true }
        val grouped = ws.groupBy { sportLabel(it.sport) }
            .map { (k, v) -> Triple(k, v.mapNotNull { it.strain?.let { s -> s * 21 / 100 } }.takeIf { it.isNotEmpty() }?.average(), v.size) }
            .sortedByDescending { it.third }
        Column(Modifier.padding(horizontal = 15.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PulseColors.raised).padding(16.dp)) {
            RangeTabs(actRange) { actRange = it }
            Text("${ws.size}", style = PulseText.bigNumber.copy(fontSize = 26.sp), color = PulseColors.text, modifier = Modifier.padding(top = 18.dp))
            Text("ACTIVIDADES EN TOTAL", style = PulseText.overline, color = PulseColors.textSecondary)
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text("ACTIVIDAD · ESFUERZO MEDIO", style = PulseText.overline.copy(fontSize = 9.5.sp), color = PulseColors.textSecondary, modifier = Modifier.weight(1f))
                Text("VECES", style = PulseText.overline.copy(fontSize = 9.5.sp), color = PulseColors.textSecondary)
            }
            if (grouped.isEmpty()) {
                Text("Aún no hay actividades registradas.", style = PulseText.body, color = PulseColors.textSecondary, modifier = Modifier.padding(vertical = 14.dp))
            }
            val shown = if (showAllActs) grouped else grouped.take(3)
            shown.forEachIndexed { i, (label, eff, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(sportIcon(label), null, tint = PulseColors.textQuiet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label.uppercase(es), style = PulseText.overlineLg.copy(letterSpacing = PulseText.overline.letterSpacing * 0.66f), color = PulseColors.text)
                    Spacer(Modifier.width(6.dp))
                    Text(eff?.let { PulseData.oneDecimal(it) } ?: "--", style = PulseText.overlineLg, color = PulseColors.textSecondary, modifier = Modifier.weight(1f))
                    Text("$count", style = PulseText.small.copy(fontSize = 13.sp), color = PulseColors.text)
                }
                if (i < shown.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.hairline))
            }
            if (grouped.size > 3) {
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF353C43)).pulseClick { showAllActs = !showAllActs },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (showAllActs) "VER MENOS" else "VER TODAS", style = PulseText.overlineLg, color = PulseColors.text)
                    Icon(if (showAllActs) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, tint = PulseColors.text, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BigTile(modifier: Modifier, onClick: () -> Unit, art: @Composable () -> Unit, title: String, subtitle: String) {
    Box(modifier.height(170.dp).clip(RoundedCornerShape(14.dp)).background(PulseColors.overlay).pulseClick(onClick)) {
        Icon(Icons.Outlined.ChevronRight, null, tint = PulseColors.textSecondary, modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(16.dp))
        Box(Modifier.align(Alignment.TopCenter).padding(top = 32.dp)) { art() }
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = PulseText.overline.copy(fontSize = 11.sp), color = PulseColors.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = PulseText.body.copy(fontSize = 12.sp, lineHeight = 16.sp), color = PulseColors.textQuiet, textAlign = TextAlign.Center)
        }
    }
}

private data class BadgeDef(val title: String, val color: Color, val icon: ImageVector, val earned: Boolean)

private fun badgesFor(days: List<DailyMetric>, workouts: List<WorkoutRow>): List<BadgeDef> = listOf(
    BadgeDef("Buen dormilón", PulseColors.sleep, Icons.Outlined.Bedtime, days.any { (PulseData.sleepPct(it) ?: 0.0) >= 85 }),
    BadgeDef("A tope", PulseColors.strain, Icons.Outlined.Bolt, days.any { (PulseData.effort21(it) ?: 0.0) >= 18 }),
    BadgeDef("Semana en verde", PulseColors.recoveryGreen, Icons.Outlined.Favorite, ProfileStats.longestRun(days) { (it.recovery ?: 0.0) >= 67 } >= 7),
    BadgeDef("Primera semana", PulseColors.accent, Icons.Outlined.CalendarMonth, PulseData.nightsWithSleep(days) >= 7),
    BadgeDef("10.000 pasos", PulseColors.strain, Icons.AutoMirrored.Outlined.DirectionsRun, days.any { (it.steps ?: 0) >= 10_000 }),
    BadgeDef("Constancia", PulseColors.gold, Icons.Outlined.Whatshot, ProfileStats.longestRun(days) { (it.totalSleepMin ?: 0.0) > 0 } >= 30),
    BadgeDef("Deportista", PulseColors.stressHigh, Icons.Outlined.FitnessCenter, workouts.size >= 10),
    BadgeDef("Ocho horas", PulseColors.sleep, Icons.Outlined.Timer, days.any { (it.totalSleepMin ?: 0.0) >= 480 }),
    BadgeDef("Recuperación top", PulseColors.recoveryGreen, Icons.Outlined.TrendingUp, days.any { (it.recovery ?: 0.0) >= 90 }),
)

@Composable
private fun Badge(b: BadgeDef, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = if (b.earned) b.color else PulseColors.hairlineStrong
                drawCircle(c.copy(alpha = if (b.earned) 0.18f else 0.25f), size.minDimension / 2)
                drawCircle(c, size.minDimension / 2 - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            Icon(b.icon, null, tint = if (b.earned) b.color else PulseColors.textTertiary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(b.title, style = PulseText.body.copy(fontWeight = FontWeight.SemiBold), color = if (b.earned) PulseColors.text else PulseColors.textTertiary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RangeTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(segBg).padding(3.dp)) {
        listOf("1 MES", "3 MESES", "SIEMPRE").forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (i == selected) PulseColors.overlay else Color.Transparent).pulseClick { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = PulseText.overline.copy(letterSpacing = PulseText.overline.letterSpacing * 0.83f), color = if (i == selected) PulseColors.text else PulseColors.textSecondary) }
        }
    }
}

@Composable
private fun BestRing(color: Color, fraction: Double?, value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 4.dp.toPx()
                val r = size.minDimension / 2 - sw
                val tl = Offset(center.x - r, center.y - r)
                drawArc(PulseColors.track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(sw))
                fraction?.let { drawArc(color, -90f, 360f * it.toFloat().coerceIn(0f, 1f), false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round)) }
            }
            Text(value, style = PulseText.ringNumber.copy(fontSize = 17.sp), color = PulseColors.text)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textQuiet, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DividerLabel(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = PulseText.overline, color = PulseColors.textSecondary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(PulseColors.hairline))
    }
}

@Composable
private fun StreakCell(color: Color, icon: ImageVector, days: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(34.dp).border(1.4.dp, color, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("$days ${if (days == 1) "día" else "días"}", style = PulseText.small.copy(fontWeight = FontWeight.Bold), color = PulseColors.text)
        Text(label, style = PulseText.body.copy(fontSize = 11.sp), color = PulseColors.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Editar perfil: the same fields as the first-run setup. */
@Composable
fun PulseEditProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { ProfileStore.from(context) }
    var first by rememberSaveable { mutableStateOf(PulseProfile.firstName(context)) }
    var last by rememberSaveable { mutableStateOf(PulseProfile.lastName(context)) }
    var dob by rememberSaveable { mutableStateOf(profile.dateOfBirthMillis) }
    var sex by rememberSaveable { mutableStateOf(profile.sex) }
    var height by rememberSaveable { mutableStateOf(profile.heightCm.roundToInt().toString()) }
    var weight by rememberSaveable { mutableStateOf(String.format(Locale.US, "%.1f", profile.weightKg)) }
    var hrMax by rememberSaveable { mutableStateOf(if (profile.hrMaxOverride > 0) profile.hrMaxOverride.toString() else "") }
    fun save() {
        PulseProfile.setName(context, first, last)
        profile.dateOfBirthMillis = dob
        profile.sex = sex
        height.replace(',', '.').toDoubleOrNull()?.let { profile.heightCm = it }
        weight.replace(',', '.').toDoubleOrNull()?.let { profile.weightKg = it }
        // Empty = noop's age formula; a value between 120 and 230 overrides it for zones and Effort.
        profile.hrMaxOverride = hrMax.toIntOrNull()?.takeIf { it in 120..230 } ?: 0
        onBack()
    }
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        PulseTopBar("Editar perfil", onBack, trailing = { Text("GUARDAR", style = PulseText.overline, color = PulseColors.accent, modifier = Modifier.pulseClick { save() }) })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()) {
            FieldLabel("Nombre"); PulseField(first, { first = it }, "Tu nombre")
            Spacer(Modifier.height(18.dp))
            FieldLabel("Apellido (opcional)"); PulseField(last, { last = it }, "Tu apellido")
            Spacer(Modifier.height(18.dp))
            FieldLabel("Fecha de nacimiento")
            val fmt = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
            val date = Instant.ofEpochMilli(dob).atZone(ZoneId.systemDefault()).toLocalDate()
            Row(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, PulseColors.hairline, RoundedCornerShape(12.dp))
                    .pulseClick {
                        DatePickerDialog(context, { _, y, m, d ->
                            dob = LocalDate.of(y, m + 1, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(date.format(fmt), style = PulseText.bodyLg.copy(fontSize = 16.sp), color = PulseColors.text, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.CalendarMonth, null, tint = PulseColors.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(18.dp))
            FieldLabel("Sexo para los cálculos")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("female" to "MUJER", "male" to "HOMBRE", "nonbinary" to "OTRO").forEach { (k, label) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.5.dp, if (sex == k) PulseColors.accent else PulseColors.hairline, RoundedCornerShape(10.dp))
                            .background(if (sex == k) PulseColors.accentFillTop else Color.Transparent).pulseClick { sex = k },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = PulseText.overline, color = PulseColors.text) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { FieldLabel("Altura (cm)"); PulseField(height, { height = it }, "175", KeyboardType.Decimal) }
                Column(Modifier.weight(1f)) { FieldLabel("Peso (kg)"); PulseField(weight, { weight = it }, "72", KeyboardType.Decimal) }
            }
            Spacer(Modifier.height(18.dp))
            FieldLabel("Pulso máximo (opcional)")
            PulseField(hrMax, { hrMax = it.filter { c -> c.isDigit() }.take(3) }, "${profile.hrMaxAuto} (por edad)", KeyboardType.Number)
            Spacer(Modifier.height(6.dp))
            Text(
                "Si conoces tu pulso máximo real (de una prueba de esfuerzo o el más alto que hayas visto en un entreno muy duro), ponlo aquí: las zonas y el esfuerzo serán más precisos. Vacío = fórmula por edad.",
                style = PulseText.body.copy(fontSize = 12.sp), color = PulseColors.textSecondary,
            )
        }
    }
}

/** "Lo que recuerda el coach": the coach's local memory, with a button to forget it. */
@Composable
fun PulseCoachMemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val mem = remember { PrefsCoachMemory(context) }
    var tick by remember { mutableStateOf(0) }
    val items = remember(tick) {
        buildList {
            mem.get("name")?.let { add("Nombre" to it) }
            mem.get("goal")?.let {
                add("Objetivo" to (mapOf("dormir" to "Dormir mejor", "rendimiento" to "Rendir más", "peso" to "Perder grasa", "musculo" to "Ganar músculo", "estres" to "Bajar el estrés", "longevidad" to "Envejecer mejor", "salud" to "Salud general")[it] ?: it))
            }
            mem.get("sport")?.let { add("Deporte" to it.replaceFirstChar { c -> c.uppercase() }) }
            mem.get("usual_sleep")?.toDoubleOrNull()?.let { add("Sueles dormir" to "${PulseData.oneDecimal(it)} h") }
            mem.get("sleep_need")?.toDoubleOrNull()?.let { add("Necesitas dormir" to "${PulseData.oneDecimal(it)} h") }
            mem.get("wake_time")?.let { add("Te levantas a las" to it) }
            mem.get("coffees")?.toDoubleOrNull()?.let { add("Cafés al día" to it.roundToInt().toString()) }
            mem.get("drinks_week")?.toDoubleOrNull()?.let { add("Copas a la semana" to it.roundToInt().toString()) }
        }
    }
    Column(Modifier.fillMaxSize().background(PulseColors.screenBrush)) {
        PulseTopBar("Lo que recuerda el coach", onBack)
        Column(Modifier.padding(horizontal = PulseMetrics.gutter, vertical = 16.dp)) {
            Text("Se guarda solo en este teléfono. El coach lo usa para adaptar sus consejos.", style = PulseText.body, color = PulseColors.textSecondary)
            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                PulseCard { Text("Todavía no le has contado nada. Dile tu nombre, tu objetivo o tu deporte en el chat.", style = PulseText.body, color = PulseColors.textSecondary) }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PulseMetrics.cardRadius)).background(PulseColors.overlay)) {
                    items.forEachIndexed { i, (k, v) ->
                        if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(PulseColors.hairline))
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(k.uppercase(), style = PulseText.overlineLg, color = PulseColors.textSecondary, modifier = Modifier.weight(1f))
                            Text(v, style = PulseText.cardTitle, color = PulseColors.text)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, PulseColors.recoveryRed, RoundedCornerShape(12.dp)).pulseClick {
                        listOf("name", "goal", "sport", "sleep_need", "usual_sleep", "coffees", "drinks_week", "pending", "last_intent", "wake_time").forEach { mem.set(it, null) }
                        tick++
                    },
                    contentAlignment = Alignment.Center,
                ) { Text("OLVIDAR TODO", style = PulseText.overlineLg, color = PulseColors.recoveryRed) }
            }
        }
    }
}
